package com.greenyi.charity.integration;

import lombok.Data;

/**
 * 微信支付网关适配层
 * - mock 模式：生成模拟支付参数与交易号，用于本地联调
 * - 真实模式：替换为微信支付 V3 SDK 调用（OQ-01 商户开通后接入）
 */
public interface PaymentGateway {

    /** 创建支付单，返回小程序支付参数 */
    PayParams createPayment(String outTradeNo, String subject, int amountCent, String openid);

    /** 校验并解析支付回调，返回交易号；失败抛异常 */
    String verifyNotify(String body, String signature);

    @Data
    class PayParams {
        private String appId;
        private String timeStamp;
        private String nonceStr;
        private String packageStr;
        private String signType = "RSA";
        private String paySign;
        private boolean mock;
    }
}
