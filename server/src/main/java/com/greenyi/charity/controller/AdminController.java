package com.greenyi.charity.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.common.Result;
import com.greenyi.charity.entity.*;
import com.greenyi.charity.integration.ConfigService;
import com.greenyi.charity.mapper.SysConfigMapper;
import com.greenyi.charity.security.UserContext;
import com.greenyi.charity.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 后台管理端聚合控制器（RBAC 依赖 OQ-05，id-type 草案）
 * - 内容/商品/活动维护、订单/申领/捐赠记录、证书、规则配置、数据看板、AI审计
 */
@RestController
@RequestMapping("/admin")
public class AdminController {
    @Autowired private ProductService productService;
    @Autowired private ContentService contentService;
    @Autowired private ActivityService activityService;
    @Autowired private ClaimService claimService;
    @Autowired private OrderService orderService;
    @Autowired private DonationService donationService;
    @Autowired private AiServiceFacade aiFacade;
    @Autowired private SysConfigMapper configMapper;

    /** 角色门槛校验：各管理接口要求后台角色（非 USER） */
    private void requireAdmin() {
        UserContext.LoginInfo info = UserContext.get();
        if (info == null || info.getRole() == null || "USER".equals(info.getRole())) {
            throw new BusinessException(403, "无权访问后台接口");
        }
    }

    // ---------- 数据看板 ----------
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        requireAdmin();
        Map<String, Object> m = new HashMap<>();
        m.put("products", count(productService.adminList(1, 1, null).getTotal()));
        m.put("contents", count(contentService.adminList(1, 1).getTotal()));
        m.put("activities", count(activityService.adminList(1, 1).getTotal()));
        m.put("pendingAssessment", donationService.workbench(1, 1, "TO_ASSESS").getTotal());
        m.put("claims", claimService.adminList(null).size());
        m.put("orders", orderService.adminOrders(null).size());
        m.put("note", "示例看板，指标口径以评审确认为准");
        return Result.ok(m);
    }

    private long count(long n) { return n; }

    // ---------- 内容管理 ----------
    @GetMapping("/contents")
    public Result<IPage<Content>> contents(@RequestParam(defaultValue = "1") int current, @RequestParam(defaultValue = "10") int size) {
        requireAdmin(); return Result.ok(contentService.adminList(current, size));
    }
    @PostMapping("/content")
    public Result<Content> saveContent(@RequestBody Content c) { requireAdmin(); return Result.ok(contentService.save(c)); }
    @PostMapping("/content/{id}/offline")
    public Result<?> offlineContent(@PathVariable Long id) { requireAdmin(); contentService.offline(id); return Result.ok(); }

    // ---------- 商品管理 ----------
    @GetMapping("/products")
    public Result<IPage<Product>> products(@RequestParam(defaultValue = "1") int current,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String kind) {
        requireAdmin(); return Result.ok(productService.adminList(current, size, kind));
    }
    @GetMapping("/product/{id}")
    public Result<Product> productDetail(@PathVariable Long id) { requireAdmin(); return Result.ok(productService.adminDetail(id)); }
    @PostMapping("/product")
    public Result<Product> saveProduct(@RequestBody Product p) { requireAdmin(); return Result.ok(productService.save(p)); }
    @DeleteMapping("/product/{id}")
    public Result<?> deleteProduct(@PathVariable Long id) { requireAdmin(); productService.delete(id); return Result.ok(); }
    @PostMapping("/product/{id}/toggle")
    public Result<?> toggleProduct(@PathVariable Long id) { requireAdmin(); productService.toggleStatus(id); return Result.ok(); }

    // ---------- 活动与志愿者 ----------
    @GetMapping("/activities")
    public Result<IPage<Activity>> activities(@RequestParam(defaultValue = "1") int current, @RequestParam(defaultValue = "10") int size) {
        requireAdmin(); return Result.ok(activityService.adminList(current, size));
    }
    @PostMapping("/activity")
    public Result<Activity> saveActivity(@RequestBody Activity a) { requireAdmin(); return Result.ok(activityService.save(a)); }
    @PostMapping("/activity/{id}/finish")
    public Result<Map<String, Object>> finishActivity(@PathVariable Long id) { requireAdmin(); return Result.ok(activityService.finish(id)); }
    @GetMapping("/activity/{id}/signups")
    public Result<List<Map<String, Object>>> signups(@PathVariable Long id) { requireAdmin(); return Result.ok(activityService.adminSignups(id)); }
    @PostMapping("/signup/{id}/checkin")
    public Result<?> checkin(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireAdmin();
        Integer minutes = body.get("minutes") == null ? null : Integer.parseInt(body.get("minutes").toString());
        activityService.checkin(id, minutes); return Result.ok();
    }

    // ---------- 订单/申领 ----------
    /** 管理员订单列表：查看已卖出的商品与物流跟踪状态 */
    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> orders(@RequestParam(required = false) String status) {
        requireAdmin(); return Result.ok(orderService.adminOrders(status));
    }
    /** 订单详情：含收货地址与商品明细，供发货核对 */
    @GetMapping("/order/{id}")
    public Result<Map<String, Object>> adminOrderDetail(@PathVariable Long id) {
        requireAdmin(); return Result.ok(orderService.adminOrderDetail(id));
    }
    @PostMapping("/order/{id}/return-approve")
    public Result<?> approveReturn(@PathVariable Long id) { requireAdmin(); orderService.approveReturn(id); return Result.ok(); }
    @PostMapping("/order/{id}/return-reject")
    public Result<?> rejectReturn(@PathVariable Long id) { requireAdmin(); orderService.rejectReturn(id); return Result.ok(); }
    @PostMapping("/order/{id}/ship")
    public Result<?> shipOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireAdmin(); orderService.ship(id, String.valueOf(body.get("logisticsNo"))); return Result.ok();
    }
    @PostMapping("/order/{id}/complete")
    public Result<?> completeOrder(@PathVariable Long id) {
        requireAdmin(); orderService.complete(id); return Result.ok();
    }
    @GetMapping("/claims")
    public Result<List<Map<String, Object>>> claims(@RequestParam(required = false) String status) { requireAdmin(); return Result.ok(claimService.adminList(status)); }
    @PostMapping("/claim/{id}/ship")
    public Result<?> shipClaim(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireAdmin(); claimService.ship(id, String.valueOf(body.get("logisticsNo"))); return Result.ok();
    }

    // ---------- 物资评估工作台 ----------
    @GetMapping("/workbench")
    public Result<?> workbench(@RequestParam(defaultValue = "1") int current, @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) String status) {
        requireAdmin(); return Result.ok(donationService.workbench(current, size, status));
    }
    /** 评估员签收物资：待接收 -> 已接收，签收后才能评估计价 */
    @PostMapping("/donation/{id}/receive")
    public Result<?> receiveDonation(@PathVariable Long id) {
        requireAdmin(); donationService.receive(id); return Result.ok();
    }
    @PostMapping("/donation/{id}/assess")
    public Result<Map<String, Object>> assess(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireAdmin();
        Long assessorId = UserContext.get().getUserId();
        return Result.ok(donationService.assess(id, body, assessorId));
    }

    // ---------- 规则配置（FA-073） ----------
    @GetMapping("/configs")
    public Result<List<SysConfig>> configs() { requireAdmin(); return Result.ok(configMapper.selectList(null)); }
    @PostMapping("/config")
    public Result<?> saveConfig(@RequestBody Map<String, Object> body) {
        requireAdmin();
        String key = String.valueOf(body.get("configKey"));
        String value = String.valueOf(body.get("configValue"));
        SysConfig c = configMapper.selectById(key);
        if (c == null) { c = new SysConfig(); c.setConfigKey(key); c.setDescription(String.valueOf(body.getOrDefault("description", ""))); c.setConfigValue(value); configMapper.insert(c); }
        else { c.setConfigValue(value); configMapper.updateById(c); }
        return Result.ok();
    }

    // ---------- AI 审计 ----------
    @GetMapping("/ai-audit")
    public Result<IPage<AiAuditLog>> aiAudit(@RequestParam(defaultValue = "1") int current,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) String feature) {
        requireAdmin(); return Result.ok(aiFacade.auditList(current, size, feature));
    }
}
