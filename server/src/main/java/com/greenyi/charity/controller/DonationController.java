package com.greenyi.charity.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenyi.charity.common.PageResult;
import com.greenyi.charity.common.Result;
import com.greenyi.charity.entity.Donation;
import com.greenyi.charity.entity.DonationAddress;
import com.greenyi.charity.mapper.DonationAddressMapper;
import com.greenyi.charity.security.UserContext;
import com.greenyi.charity.service.DonationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 物资捐赠 */
@RestController
@RequestMapping("/donation")
public class DonationController {
    @Autowired private DonationService donationService;
    @Autowired private DonationAddressMapper addressMapper;

    @PostMapping("/submit")
    public Result<Donation> submit(@RequestBody Map<String, Object> body) { return Result.ok(donationService.submit(body)); }

    @GetMapping("/mine")
    public Result<List<Map<String, Object>>> mine() { return Result.ok(donationService.myDonations()); }

    /** 可邮寄接收地址（管理端「捐赠设置」维护，仅返回启用项） */
    @GetMapping("/addresses")
    public Result<List<DonationAddress>> addresses() {
        return Result.ok(addressMapper.selectList(
                new LambdaQueryWrapper<DonationAddress>()
                        .eq(DonationAddress::getEnabled, 1)
                        .orderByAsc(DonationAddress::getSortNo)
                        .orderByAsc(DonationAddress::getId)));
    }

    /** AI 估值参考（仅参考） */
    @PostMapping("/estimate")
    public Result<Map<String, Object>> estimate(@RequestBody Map<String, Object> body) { return Result.ok(donationService.estimateValue(body)); }

    // ---------- 评估员/后台 ----------
    @GetMapping("/workbench")
    public Result<PageResult<Donation>> workbench(@RequestParam(defaultValue = "1") int current,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String status) {
        return Result.ok(donationService.workbench(current, size, status));
    }

    @PostMapping("/{id}/receive")
    public Result<?> receive(@PathVariable Long id) { donationService.receive(id); return Result.ok(); }

    @PostMapping("/{id}/assess")
    public Result<Map<String, Object>> assess(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long assessorId = UserContext.get() == null ? null : UserContext.get().getUserId();
        return Result.ok(donationService.assess(id, body, assessorId));
    }

    @PostMapping("/{id}/dispute")
    public Result<?> dispute(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : String.valueOf(body.get("reason"));
        donationService.dispute(id, reason); return Result.ok();
    }

    @PostMapping("/{id}/disposal")
    public Result<?> disposal(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        donationService.recordDisposal(id, body); return Result.ok();
    }
}
