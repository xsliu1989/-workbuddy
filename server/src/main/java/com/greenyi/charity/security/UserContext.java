package com.greenyi.charity.security;

/**
 * 当前登录上下文（ThreadLocal）
 */
public class UserContext {
    private static final ThreadLocal<LoginInfo> HOLDER = new ThreadLocal<>();

    public static void set(LoginInfo info) { HOLDER.set(info); }
    public static LoginInfo get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public static Long requireUserId() {
        LoginInfo info = get();
        if (info == null || info.getUserId() == null) {
            throw new RuntimeException("未登录");
        }
        return info.getUserId();
    }

    public static boolean isAdmin() {
        LoginInfo info = get();
        return info != null && "ADMIN".equals(info.getRole());
    }

    public static class LoginInfo {
        private Long userId;       // C端用户id 或 后台管理员id
        private String role;       // USER / EDITOR / ASSESSOR / OPERATOR / ADMIN
        private String openid;
        private String username;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getOpenid() { return openid; }
        public void setOpenid(String openid) { this.openid = openid; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }
}
