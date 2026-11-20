package com.greenyi.charity.controller;

import com.greenyi.charity.common.Result;
import com.greenyi.charity.entity.AppUser;
import com.greenyi.charity.entity.Certificate;
import com.greenyi.charity.entity.PointAccount;
import com.greenyi.charity.entity.PointLedger;
import com.greenyi.charity.entity.UserAddress;
import com.greenyi.charity.security.UserContext;
import com.greenyi.charity.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 用户、地址、积分、荣誉档案、证书、AI */
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired private UserService userService;
    @Autowired private PointService pointService;
    @Autowired private HonorService honorService;
    @Autowired private CertificateService certificateService;
    @Autowired private AiServiceFacade aiServiceFacade;

    @GetMapping("/profile")
    public Result<AppUser> profile() { return Result.ok(userService.getProfile(UserContext.requireUserId())); }
    @PutMapping("/profile")
    public Result<?> updateProfile(@RequestBody Map<String, Object> body) { userService.updateProfile(UserContext.requireUserId(), body); return Result.ok(); }

    @GetMapping("/addresses")
    public Result<List<UserAddress>> addresses() { return Result.ok(userService.listAddresses(UserContext.requireUserId())); }
    @PostMapping("/address")
    public Result<UserAddress> saveAddress(@RequestBody Map<String, Object> body) { return Result.ok(userService.saveAddress(UserContext.requireUserId(), body)); }
    @DeleteMapping("/address/{id}")
    public Result<?> deleteAddress(@PathVariable Long id) { userService.deleteAddress(UserContext.requireUserId(), id); return Result.ok(); }

    @GetMapping("/points")
    public Result<PointAccount> points() { return Result.ok(pointService.getAccount(UserContext.requireUserId())); }
    @GetMapping("/points/ledger")
    public Result<List<PointLedger>> pointLedger() { return Result.ok(pointService.listLedger(UserContext.requireUserId())); }

    @GetMapping("/honor")
    public Result<Map<String, Object>> honor() { return Result.ok(honorService.getProfileView(UserContext.requireUserId())); }
    @GetMapping("/certificates")
    public Result<List<Certificate>> certificates() { return Result.ok(certificateService.listMyCerts(UserContext.requireUserId())); }

    /** 积分抵扣试算（前端结算页可调用） */
    @PostMapping("/points/preview")
    public Result<Map<String, Object>> pointsPreview(@RequestBody Map<String, Object> body) {
        java.math.BigDecimal amount = new java.math.BigDecimal(String.valueOf(body.get("goodsAmount")));
        Integer rp = body.get("requestPoints") == null ? null : Integer.parseInt(body.get("requestPoints").toString());
        return Result.ok(pointService.calcDiscount(UserContext.requireUserId(), amount, rp));
    }

    /** AI 客服 */
    @PostMapping("/ai/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        return Result.ok(aiServiceFacade.chat(String.valueOf(body.get("question"))));
    }

    /** 智能推荐 */
    @GetMapping("/ai/recommend")
    public Result<Map<String, Object>> recommend(@RequestParam(defaultValue = "product") String scene,
                                                 @RequestParam(defaultValue = "true") boolean enabled,
                                                 @RequestParam(defaultValue = "6") int limit) {
        return Result.ok(aiServiceFacade.recommend(scene, enabled, limit));
    }
}
