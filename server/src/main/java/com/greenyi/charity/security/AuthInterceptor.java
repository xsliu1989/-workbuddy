package com.greenyi.charity.security;

import com.greenyi.charity.common.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 鉴权拦截器：解析 X-Token，写入 UserContext
 * - C端用户 token 由 /auth/login 颁发，role=USER
 * - 后台 token 由 /auth/admin-login 颁发，role=EDITOR/ASSESSOR/OPERATOR/ADMIN
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String token = request.getHeader("X-Token");
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(401, "请先登录");
        }
        try {
            Claims claims = jwtUtil.parse(token);
            UserContext.LoginInfo info = new UserContext.LoginInfo();
            Object uid = claims.get("userId");
            info.setUserId(uid == null ? null : Long.valueOf(uid.toString()));
            info.setRole(String.valueOf(claims.get("role")));
            Object extra = claims.get("extra");
            if (extra != null) info.setOpenid(extra.toString());
            UserContext.set(info);
            return true;
        } catch (ExpiredJwtException e) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        } catch (Exception e) {
            throw new BusinessException(401, "无效的登录凭证");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
