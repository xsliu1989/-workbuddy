package com.greenyi.charity.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.greenyi.charity.common.PageResult;
import com.greenyi.charity.common.Result;
import com.greenyi.charity.entity.Activity;
import com.greenyi.charity.entity.VolunteerSignup;
import com.greenyi.charity.service.ActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 活动与志愿者 */
@RestController
@RequestMapping("/activity")
public class ActivityController {
    @Autowired private ActivityService activityService;

    @GetMapping("/list")
    public Result<PageResult<Activity>> list(@RequestParam(defaultValue = "1") int current,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(activityService.list(current, size));
    }

    @GetMapping("/{id}")
    public Result<Activity> detail(@PathVariable Long id) { return Result.ok(activityService.detail(id)); }

    @PostMapping("/{id}/signup")
    public Result<VolunteerSignup> signup(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.ok(activityService.signup(id, body == null ? new java.util.HashMap<>() : body));
    }

    // 路径用多段，避免被 WebConfig 的 /activity/* 公开白名单放行（否则无法解析登录态）
    @GetMapping("/mine/signups")
    public Result<List<Map<String, Object>>> mySignups() { return Result.ok(activityService.mySignups()); }

    @PostMapping("/copy")
    public Result<Map<String, Object>> generateCopy(@RequestBody Map<String, Object> body) { return Result.ok(activityService.generateCopy(body)); }
}
