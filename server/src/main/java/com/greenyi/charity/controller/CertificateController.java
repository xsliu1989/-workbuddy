package com.greenyi.charity.controller;

import com.greenyi.charity.common.Result;
import com.greenyi.charity.service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 证书验真（公开） */
@RestController
@RequestMapping("/certificate")
public class CertificateController {
    @Autowired private CertificateService certificateService;

    @GetMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestParam String certNo,
                                              @RequestParam(required = false) String verifyCode) {
        return Result.ok(certificateService.verify(certNo, verifyCode));
    }
}
