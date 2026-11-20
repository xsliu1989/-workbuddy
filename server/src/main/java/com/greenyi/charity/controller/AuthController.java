package com.greenyi.charity.controller;

import com.greenyi.charity.common.Result;
import com.greenyi.charity.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 认证：C端登录 / 后台登录 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired private AuthService authService;

    /** 小程序登录：{code, nickname?, avatarUrl?, isMinor?, guardianAuth?} */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return Result.ok(authService.login(body));
    }

    /** 微信手机号快速验证：{code}（getPhoneNumber 回调的动态令牌），失败时 ok=false 由前端手动填写 */
    @PostMapping("/phone")
    public Result<Map<String, Object>> resolvePhone(@RequestBody Map<String, Object> body) {
        Object code = body.get("code");
        return Result.ok(authService.resolvePhone(code == null ? null : code.toString()));
    }

    /** 后台登录：{username, password} */
    @PostMapping("/admin-login")
    public Result<Map<String, Object>> adminLogin(@RequestBody Map<String, Object> body) {
        return Result.ok(authService.adminLogin(body));
    }
}
