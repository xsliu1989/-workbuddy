package com.greenyi.charity.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.greenyi.charity.common.PageResult;
import com.greenyi.charity.common.Result;
import com.greenyi.charity.entity.Content;
import com.greenyi.charity.service.ContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 内容宣传 */
@RestController
@RequestMapping("/content")
public class ContentController {
    @Autowired private ContentService contentService;

    @GetMapping("/list")
    public Result<PageResult<Content>> list(@RequestParam(defaultValue = "1") int current,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String kind) {
        return Result.ok(contentService.list(current, size, kind));
    }

    @GetMapping("/{id}")
    public Result<Content> detail(@PathVariable Long id) { return Result.ok(contentService.detail(id)); }

    @PostMapping("/{id}/share")
    public Result<?> share(@PathVariable Long id) { contentService.share(id); return Result.ok(); }
}
