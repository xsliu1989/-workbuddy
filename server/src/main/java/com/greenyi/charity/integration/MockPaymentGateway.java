package com.greenyi.charity.integration;

import cn.hutool.core.util.IdUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟支付网关（greenyi.integration.wechat-pay-mock=true 时启用）
 */
@Component
@ConditionalOnProperty(name = "greenyi.integration.wechat-pay-mock", havingValue = "true", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PayParams createPayment(String outTradeNo, String subject, int amountCent, String openid) {
        PayParams p = new PayParams();
        p.setAppId("mock-appid");
        p.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000));
        p.setNonceStr(IdUtil.fastSimpleUUID());
        p.setPackageStr("prepay_id=mock_" + outTradeNo);
        p.setPaySign("mock-sign-" + outTradeNo);
        p.setMock(true);
        return p;
    }

    @Override
    public String verifyNotify(String body, String signature) {
        // 模拟：回调正文含 transaction_id 即视为成功
        if (body == null || !body.contains("transaction_id")) {
            throw new RuntimeException("回调校验失败");
        }
        int i = body.indexOf("transaction_id");
        String seg = body.substring(i);
        int s = seg.indexOf('"', seg.indexOf(':') + 1);
        int e = seg.indexOf('"', s + 1);
        return s >= 0 && e > s ? seg.substring(s + 1, e) : "mock_txn_" + IdUtil.fastSimpleUUID();
    }
}
