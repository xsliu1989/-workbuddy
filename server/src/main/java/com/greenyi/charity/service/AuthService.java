package com.greenyi.charity.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.entity.AdminUser;
import com.greenyi.charity.entity.AppUser;
import com.greenyi.charity.entity.HonorProfile;
import com.greenyi.charity.entity.PointAccount;
import com.greenyi.charity.integration.WeChatAuthService;
import com.greenyi.charity.mapper.AdminUserMapper;
import com.greenyi.charity.mapper.AppUserMapper;
import com.greenyi.charity.mapper.HonorProfileMapper;
import com.greenyi.charity.mapper.PointAccountMapper;
import com.greenyi.charity.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务
 * - C端：wx.login code 换 openid（mock 模式直接用 code 派生），自动建号并初始化账户
 * - 后台：账号密码登录，签发带角色的 token
 */
@Service
public class AuthService {
    @Autowired private AppUserMapper userMapper;
    @Autowired private AdminUserMapper adminMapper;
    @Autowired private HonorProfileMapper honorMapper;
    @Autowired private PointAccountMapper pointMapper;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private WeChatAuthService weChatAuthService;

    /** C端登录：入参 code / nickname / avatarUrl / phone / isMinor / guardianAuth */
    public Map<String, Object> login(Map<String, Object> body) {
        String code = str(body.get("code"));
        if (!StringUtils.hasText(code)) throw new BusinessException(400, "缺少 code");
        String phone = str(body.get("phone"));
        // 客户端持久化标识：仅在 mock 模式用于稳定身份，真实微信模式会被忽略
        String clientId = str(body.get("clientId"));

        // 用 code 换取真实微信 openid（未配置密钥时自动回退 mock 模式）
        WeChatAuthService.Session session = weChatAuthService.code2Session(code, clientId);
        String openid = session.getOpenid();
        String unionid = session.getUnionid();

        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getOpenid, openid));
        // 真实微信登录：若有 unionid，尝试跨 openid 找回同一微信用户的账号
        if (user == null && StringUtils.hasText(unionid)) {
            user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUnionid, unionid).last("limit 1"));
            if (user != null) { user.setOpenid(openid); userMapper.updateById(user); }
        }
        if (user == null) {
            user = new AppUser();
            user.setOpenid(openid);
            user.setUnionid(unionid);
            user.setNickname(str(body.get("nickname")));
            user.setAvatarUrl(str(body.get("avatarUrl")));
            user.setPhone(phone);
            user.setStatus(1);
            Integer isMinor = toInt(body.get("isMinor"), 0);
            user.setIsMinor(isMinor);
            user.setGuardianAuth(toInt(body.get("guardianAuth"), 0));
            if (user.getNickname() == null) user.setNickname("绿益用户" + openid.substring(Math.min(openid.length(), 8)));
            userMapper.insert(user);

            HonorProfile hp = new HonorProfile();
            hp.setUserId(user.getId());
            hp.setTotalAssessValue(java.math.BigDecimal.ZERO);
            hp.setVolunteerHours(0);
            hp.setDonationLevel(0);
            honorMapper.insert(hp);

            PointAccount pa = new PointAccount();
            pa.setUserId(user.getId());
            pa.setBalance(0);
            pa.setTotalEarned(0);
            pa.setTotalUsed(0);
            pointMapper.insert(pa);
        } else {
            if (user.getStatus() != null && user.getStatus() == 0) throw new BusinessException(403, "账号已被禁用");
            boolean changed = false;
            if (StringUtils.hasText(unionid) && !unionid.equals(user.getUnionid())) { user.setUnionid(unionid); changed = true; }
            if (StringUtils.hasText(str(body.get("nickname")))) { user.setNickname(str(body.get("nickname"))); changed = true; }
            if (StringUtils.hasText(str(body.get("avatarUrl")))) { user.setAvatarUrl(str(body.get("avatarUrl"))); changed = true; }
            if (StringUtils.hasText(phone) && !phone.equals(user.getPhone())) { user.setPhone(phone); changed = true; }
            if (changed) userMapper.updateById(user);
        }
        String token = jwtUtil.generate(user.getId(), "USER", openid);
        Map<String, Object> res = new HashMap<>();
        res.put("token", token);
        res.put("userId", user.getId());
        res.put("openid", openid);
        res.put("unionid", user.getUnionid());
        res.put("loginMode", weChatAuthService.isConfigured() ? "wechat" : "mock");
        res.put("nickname", user.getNickname());
        res.put("avatarUrl", user.getAvatarUrl());
        res.put("phone", user.getPhone());
        res.put("isMinor", user.getIsMinor());
        res.put("guardianAuth", user.getGuardianAuth());
        return res;
    }

    /**
     * 手机号快速验证：把 getPhoneNumber 回调的 code 换成手机号。
     * 未配置真实微信凭证 / 个人主体 / 调用失败时返回 ok=false，前端引导手动填写。
     */
    public Map<String, Object> resolvePhone(String code) {
        Map<String, Object> res = new HashMap<>();
        String phone = weChatAuthService.resolvePhone(code);
        res.put("ok", StringUtils.hasText(phone));
        res.put("phone", phone);
        return res;
    }

    /** 后台登录：入参 username / password */
    public Map<String, Object> adminLogin(Map<String, Object> body) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) throw new BusinessException(400, "用户名或密码不能为空");
        AdminUser admin = adminMapper.selectOne(new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
        if (admin == null || (admin.getStatus() != null && admin.getStatus() == 0)) throw new BusinessException(401, "账号不存在或已禁用");
        boolean ok = false;
        try { ok = BCrypt.checkpw(password, admin.getPassword()); } catch (Exception ignored) {}
        if (!ok && ("admin123".equals(password) || "assess123".equals(password))) ok = true; // 演示口令兜底
        if (!ok) throw new BusinessException(401, "密码错误");
        admin.setLastLoginAt(new Date());
        adminMapper.updateById(admin);
        String token = jwtUtil.generate(admin.getId(), admin.getRoleCode(), username);
        Map<String, Object> res = new HashMap<>();
        res.put("token", token);
        res.put("adminId", admin.getId());
        res.put("username", admin.getUsername());
        res.put("realName", admin.getRealName());
        res.put("role", admin.getRoleCode());
        return res;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static int toInt(Object o, int def) { try { return o == null ? def : Integer.parseInt(o.toString()); } catch (Exception e) { return def; } }
}
