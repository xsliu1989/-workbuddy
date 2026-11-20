package com.greenyi.charity.integration;

/**
 * 证书验真适配层
 * - 官方承认资质依据与编号验真规则待确认（OQ-09a）
 * - 当前实现：平台内证书编号 + 验真码校验；真实模式可对接政府登记渠道
 */
public interface CertificateVerifier {
    /** 生成唯一证书编号 */
    String generateCertNo(String certType);
    /** 生成验真码 */
    String generateVerifyCode();
}
