package com.greenyi.charity.integration;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.greenyi.charity.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信小程序登录默认实现：
 * - 配置了 app-id/app-secret 时调用微信 jscode2session 换取真实 openid / unionid
 * - 未配置时回退为 mock（mockPrefix + code），保证开发调试可用
 */
@Component
public class DefaultWeChatAuthService implements WeChatAuthService {
    private static final Logger log = LoggerFactory.getLogger(DefaultWeChatAuthService.class);
    private static final String JSCODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";
    private static final String TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";

    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    @Value("${greenyi.wechat.app-id:}")
    private String appId;
    @Value("${greenyi.wechat.app-secret:}")
    private String appSecret;
    @Value("${greenyi.admin.mock-openid-prefix:mock-openid-}")
    private String mockPrefix;

    @Override
    public boolean isConfigured() {
        return usable(appId) && usable(appSecret);
    }

    @Override
    public Session code2Session(String code) {
        return code2Session(code, null);
    }

    @Override
    public Session code2Session(String code, String clientId) {
        Session s = new Session();
        if (!isConfigured()) {
            // code 每次都不同，不能作为身份；优先用客户端持久化的 clientId 保持稳定
            String stable = StrUtil.isBlank(clientId) ? null : clientId.trim();
            if (stable != null && stable.length() > 128) stable = stable.substring(0, 128);
            String mockOpenid = StrUtil.isBlank(stable) ? mockPrefix + code : stable;
            log.warn("[微信登录] 未配置 app-id/app-secret，使用 mock 模式（openid={}）", mask(mockOpenid));
            s.setOpenid(mockOpenid);
            return s;
        }
        String url = String.format(JSCODE2SESSION_URL, appId, appSecret, code);
        String resp;
        try {
            resp = HttpUtil.get(url, 10000);
        } catch (Exception e) {
            log.error("[微信登录] 调用 code2Session 失败: {}", e.getMessage());
            throw new BusinessException(500, "微信登录服务暂不可用，请稍后重试");
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(resp);
        } catch (Exception e) {
            log.error("[微信登录] 解析微信返回失败: {}", resp);
            throw new BusinessException(500, "微信登录返回异常");
        }
        if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
            log.error("[微信登录] 微信返回错误: {}", resp);
            throw new BusinessException(400, "微信登录失败：" + json.getStr("errmsg", "未知错误"));
        }
        String openid = json.getStr("openid");
        if (StrUtil.isBlank(openid)) {
            log.error("[微信登录] 未取得 openid: {}", resp);
            throw new BusinessException(500, "微信登录未返回 openid");
        }
        s.setOpenid(openid);
        s.setUnionid(json.getStr("unionid"));
        log.info("[微信登录] code2Session 成功, openid={} unionid={}", mask(openid), mask(s.getUnionid()));
        return s;
    }

    /**
     * 手机号快速验证：getPhoneNumber 的 code -> 手机号。
     * 注意：该能力仅对「非个人主体且完成微信认证」的小程序开放，按次计费（政府/非营利组织主体可免费）。
     * 未配置凭证、个人主体或调用失败时返回 null，前端引导用户手动填写手机号，保证流程不中断。
     */
    @Override
    public String resolvePhone(String code) {
        if (!isConfigured() || StrUtil.isBlank(code)) return null;
        String token;
        try {
            token = accessToken();
        } catch (Exception e) {
            log.warn("[微信手机号] 获取 access_token 失败: {}", e.getMessage());
            return null;
        }
        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + token;
        String resp;
        try {
            resp = HttpUtil.post(url, JSONUtil.createObj().set("code", code).toString(), 10000);
        } catch (Exception e) {
            log.warn("[微信手机号] 换取手机号失败: {}", e.getMessage());
            return null;
        }
        try {
            JSONObject json = JSONUtil.parseObj(resp);
            if (json.getInt("errcode", -1) != 0) {
                log.warn("[微信手机号] 微信返回错误: {}", resp);
                return null;
            }
            JSONObject info = json.getJSONObject("phone_info");
            if (info == null) return null;
            String phone = info.getStr("phoneNumber");
            log.info("[微信手机号] 获取成功, phone={}", mask(phone));
            return phone;
        } catch (Exception e) {
            log.warn("[微信手机号] 解析返回失败: {}", e.getMessage());
            return null;
        }
    }

    /** 获取并缓存 access_token（2 小时有效期，提前 5 分钟刷新） */
    private String accessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpireAt) return cachedToken;
        String url = String.format(TOKEN_URL, appId, appSecret);
        String resp = HttpUtil.get(url, 10000);
        JSONObject json = JSONUtil.parseObj(resp);
        if (json.getInt("errcode", -1) != 0 || StrUtil.isBlank(json.getStr("access_token"))) {
            throw new IllegalStateException("access_token 获取失败: " + json.getStr("errmsg", "未知错误"));
        }
        cachedToken = json.getStr("access_token");
        tokenExpireAt = now + Math.max(60, json.getInt("expires_in", 7200) - 300) * 1000L;
        return cachedToken;
    }

    private boolean usable(String v) {
        return StrUtil.isNotBlank(v) && !v.startsWith("${");
    }

    private String mask(String v) {
        if (StrUtil.isBlank(v)) return "-";
        int keep = Math.min(6, v.length());
        return v.substring(0, keep) + "***";
    }
}
