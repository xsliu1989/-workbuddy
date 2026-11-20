package com.greenyi.charity.controller;

import com.greenyi.charity.common.Result;
import com.greenyi.charity.entity.ClaimOrder;
import com.greenyi.charity.service.ClaimService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 免费申领（0元商品 + 运费） */
@RestController
@RequestMapping("/claim")
public class ClaimController {
    @Autowired private ClaimService claimService;

    @PostMapping("/freight")
    public Result<Map<String, Object>> calcFreight(@RequestBody Map<String, Object> body) {
        Long pid = Long.valueOf(body.get("productId").toString());
        String region = body.get("region") == null ? "全国" : body.get("region").toString();
        return Result.ok(claimService.calcFreight(pid, region));
    }

    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body) { return Result.ok(claimService.submit(body)); }

    @PostMapping("/pay/confirm")
    public Result<Map<String, Object>> confirmPay(@RequestBody Map<String, Object> body) {
        return Result.ok(claimService.confirmPaid(String.valueOf(body.get("claimNo"))));
    }

    @GetMapping("/mine")
    public Result<List<Map<String, Object>>> mine() { return Result.ok(claimService.myClaims()); }
}
