package com.greenyi.charity.controller;

import com.greenyi.charity.common.Result;
import com.greenyi.charity.integration.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/** 图片上传：返回以 /uploads/ 开头的相对 URL，各端按自身 API 根路径拼接展示 */
@RestController
@RequestMapping("/upload")
public class UploadController {
    @Autowired private FileStorageService fileStorageService;

    /** 单图上传：multipart 字段名固定为 file */
    @PostMapping("/image")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        String url = fileStorageService.saveImage(file);
        Map<String, Object> res = new HashMap<>();
        res.put("url", url);
        res.put("fileName", file.getOriginalFilename());
        return Result.ok(res);
    }
}
