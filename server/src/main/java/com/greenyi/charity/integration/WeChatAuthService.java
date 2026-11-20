package com.greenyi.charity.integration;

/** 微信小程序登录：用 code 换取真实 openid / unionid */
public interface WeChatAuthService {

    /** 会话信息：openid + unionid（unionid 可能为空） */
    class Session {
        private String openid;
        private String unionid;

        public String getOpenid() { return openid; }
        public void setOpenid(String openid) { this.openid = openid; }
        public String getUnionid() { return unionid; }
        public void setUnionid(String unionid) { this.unionid = unionid; }
    }

    /** 是否已配置真实微信凭证 */
    boolean isConfigured();

    /** code -> session；未配置或调用失败时由实现方决定兜底策略 */
    Session code2Session(String code);

    /**
     * code -> session，附带客户端稳定标识。
     * wx.login 的 code 是一次性的，mock 模式若用 code 派生 openid，退出后重新登录会被当成新用户；
     * 因此客户端持久化一个 clientId 并在每次登录上报，mock 模式用它作为稳定身份。
     * 真实微信模式忽略 clientId，始终以微信返回的 openid 为准。
     */
    default Session code2Session(String code, String clientId) { return code2Session(code); }

    /**
     * 手机号快速验证：用 getPhoneNumber 回调里的 code 换取用户手机号。
     * 仅对非个人主体且已认证的小程序开放；未配置凭证或调用失败时由实现方兜底。
     * 返回 null 表示暂时无法自动获取，前端应引导用户手动填写。
     */
    default String resolvePhone(String code) { return null; }
}
