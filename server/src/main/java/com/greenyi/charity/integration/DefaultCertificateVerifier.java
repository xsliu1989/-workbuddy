package com.greenyi.charity.integration;

import cn.hutool.core.util.IdUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 默认证书编号/验真码生成
 * 编号规则（草案）：GY-类型-年份-随机数，如 GY-DL-2026-XXXXXX
 */
@Component
public class DefaultCertificateVerifier implements CertificateVerifier {
    @Override
    public String generateCertNo(String certType) {
        String prefix = "ACTIVITY_VOLUNTEER".equals(certType) ? "GY-VOL" : "GY-DL";
        String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
        return prefix + "-" + year + "-" + IdUtil.fastSimpleUUID().substring(0, 10).toUpperCase();
    }

    @Override
    public String generateVerifyCode() {
        return IdUtil.fastSimpleUUID().substring(0, 12).toUpperCase();
    }
}
