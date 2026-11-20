package com.greenyi.charity.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.entity.*;
import com.greenyi.charity.integration.ConfigService;
import com.greenyi.charity.integration.PaymentGateway;
import com.greenyi.charity.mapper.*;
import com.greenyi.charity.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 免费申领服务：0 元商品 + 运费支付
 * BR-009 先到先得无审核 + 限购防冒领；BR-010 运费按地区/重量计算，与捐赠分账
 */
@Service
public class ClaimService {
    @Autowired private ClaimOrderMapper claimMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private FreightTemplateMapper freightMapper;
    @Autowired private UserAddressMapper addressMapper;
    @Autowired private PaymentGateway paymentGateway;
    @Autowired private AppUserMapper userMapper;
    @Autowired private ConfigService config;

    /** 运费试算：按地区模板 + 重量（首重 + 续重每公斤） */
    public Map<String, Object> calcFreight(Long productId, String region) {
        Product p = productMapper.selectById(productId);
        if (p == null) throw new BusinessException(404, "申领品不存在");
        FreightTemplate t = freightMapper.selectOne(new LambdaQueryWrapper<FreightTemplate>()
                .eq(FreightTemplate::getRegion, region).eq(FreightTemplate::getStatus, 1));
        if (t == null) {
            t = freightMapper.selectOne(new LambdaQueryWrapper<FreightTemplate>()
                    .eq(FreightTemplate::getRegion, "全国").eq(FreightTemplate::getStatus, 1));
        }
        BigDecimal freight = BigDecimal.ZERO;
        if (t != null) {
            int weightKg = (p.getWeightGram() == null ? 0 : p.getWeightGram()) / 1000;
            int extraKg = Math.max(0, weightKg - 1);
            freight = t.getBaseFee().add(t.getPerKgFee().multiply(BigDecimal.valueOf(extraKg)));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("freightAmount", freight);
        res.put("region", region);
        res.put("weightGram", p.getWeightGram());
        res.put("weightKg", (p.getWeightGram() == null ? 0 : p.getWeightGram()) / 1000.0);
        res.put("template", t == null ? "无运费模板" : t.getRegion());
        return res;
    }

    /** 申领下单：校验限购 + 库存 -> 生成申领单 -> 拉起运费支付 */
    @Transactional
    public Map<String, Object> submit(Map<String, Object> body) {
        Long userId = UserContext.requireUserId();
        Long productId = Long.valueOf(body.get("productId").toString());
        Long addressId = Long.valueOf(body.get("addressId").toString());
        String region = body.get("region") == null ? "全国" : body.get("region").toString();

        Product p = productMapper.selectById(productId);
        if (p == null || !"CLAIM".equals(p.getKind()) || (p.getStatus() != null && p.getStatus() == 0)) {
            throw new BusinessException(400, "申领品不存在或不可申领");
        }
        // 限购校验（只统计已支付/已发货/完成的单）
        int claimed = claimMapper.selectCount(new LambdaQueryWrapper<ClaimOrder>()
                .eq(ClaimOrder::getUserId, userId).eq(ClaimOrder::getProductId, productId)
                .in(ClaimOrder::getStatus, Arrays.asList("PAID", "SHIPPED", "COMPLETED"))).intValue();
        if (claimed >= config.claimLimit()) throw new BusinessException(400, "已达限领上限");
        if (p.getStock() == null || p.getStock() <= 0) throw new BusinessException(400, "库存不足");

        UserAddress addr = addressMapper.selectById(addressId);
        if (addr == null || !addr.getUserId().equals(userId)) throw new BusinessException(403, "收货地址无效");

        Map<String, Object> fr = calcFreight(productId, region);
        BigDecimal freight = (BigDecimal) fr.get("freightAmount");

        ClaimOrder co = new ClaimOrder();
        co.setClaimNo("CL" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
        co.setUserId(userId); co.setProductId(productId); co.setAddressId(addressId);
        co.setRegion(region); co.setWeightGram(p.getWeightGram() == null ? 0 : p.getWeightGram());
        co.setFreightAmount(freight); co.setPayStatus("UNPAID"); co.setStatus("PENDING_PAY");
        claimMapper.insert(co);

        AppUser user = userMapper.selectById(userId);
        int amountCent = freight.multiply(BigDecimal.valueOf(100)).setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
        PaymentGateway.PayParams params = paymentGateway.createPayment(
                co.getClaimNo(), "绿益公益申领运费", amountCent, user == null ? "" : user.getOpenid());

        Map<String, Object> res = new HashMap<>();
        res.put("claimId", co.getId()); res.put("claimNo", co.getClaimNo());
        res.put("freightAmount", freight); res.put("payParams", params);
        return res;
    }

    /** 运费支付回调/确认：置为已支付并扣库存 */
    @Transactional
    public Map<String, Object> confirmPaid(String claimNo) {
        ClaimOrder co = claimMapper.selectOne(new LambdaQueryWrapper<ClaimOrder>().eq(ClaimOrder::getClaimNo, claimNo));
        if (co == null) throw new BusinessException(404, "申领单不存在");
        if ("PAID".equals(co.getPayStatus())) {
            Map<String, Object> dup = new HashMap<>(); dup.put("duplicate", true); return dup;
        }
        co.setPayStatus("PAID"); co.setStatus("PAID");
        co.setTransactionId("mock_" + claimNo); co.setPaidAt(new Date());
        claimMapper.updateById(co);
        Product p = productMapper.selectById(co.getProductId());
        if (p != null && p.getStock() != null && p.getStock() > 0) {
            p.setStock(p.getStock() - 1);
            productMapper.updateById(p);
        }
        Map<String, Object> res = new HashMap<>(); res.put("duplicate", false); res.put("status", "PAID"); return res;
    }

    public List<Map<String, Object>> myClaims() {
        Long userId = UserContext.requireUserId();
        List<ClaimOrder> list = claimMapper.selectList(new LambdaQueryWrapper<ClaimOrder>()
                .eq(ClaimOrder::getUserId, userId).orderByDesc(ClaimOrder::getCreatedAt));
        List<Map<String, Object>> res = new ArrayList<>();
        for (ClaimOrder co : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", co.getId()); m.put("claimNo", co.getClaimNo());
            m.put("freightAmount", co.getFreightAmount()); m.put("status", co.getStatus());
            m.put("statusLabel", statusLabel(co.getStatus())); m.put("createdAt", co.getCreatedAt());
            Product p = productMapper.selectById(co.getProductId());
            m.put("productName", p == null ? "已下架" : p.getName());
            res.add(m);
        }
        return res;
    }

    /** 后台申领记录：附带收货地址、商品名、买家信息，供发货核对 */
    public List<Map<String, Object>> adminList(String status) {
        LambdaQueryWrapper<ClaimOrder> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty() && !"ALL".equals(status)) qw.eq(ClaimOrder::getStatus, status);
        qw.orderByDesc(ClaimOrder::getCreatedAt);
        List<ClaimOrder> list = claimMapper.selectList(qw);
        List<Map<String, Object>> res = new ArrayList<>();
        for (ClaimOrder co : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", co.getId()); m.put("claimNo", co.getClaimNo());
            m.put("userId", co.getUserId()); m.put("productId", co.getProductId());
            m.put("addressId", co.getAddressId()); m.put("region", co.getRegion());
            m.put("weightGram", co.getWeightGram()); m.put("freightAmount", co.getFreightAmount());
            m.put("payStatus", co.getPayStatus()); m.put("status", co.getStatus());
            m.put("logisticsNo", co.getLogisticsNo()); m.put("createdAt", co.getCreatedAt());
            m.put("paidAt", co.getPaidAt()); m.put("transactionId", co.getTransactionId());
            Product p = productMapper.selectById(co.getProductId());
            m.put("productName", p == null ? "已下架" : p.getName());
            AppUser u = userMapper.selectById(co.getUserId());
            m.put("userName", u == null ? String.valueOf(co.getUserId())
                    : (u.getNickname() == null ? String.valueOf(co.getUserId()) : u.getNickname()));
            if (co.getAddressId() != null) {
                UserAddress a = addressMapper.selectById(co.getAddressId());
                if (a != null) {
                    Map<String, Object> addr = new HashMap<>();
                    addr.put("receiver", a.getReceiver());
                    addr.put("phone", a.getPhone());
                    addr.put("province", a.getProvince());
                    addr.put("city", a.getCity());
                    addr.put("district", a.getDistrict());
                    addr.put("detail", a.getDetail());
                    addr.put("fullAddress", (a.getProvince() == null ? "" : a.getProvince())
                            + (a.getCity() == null ? "" : a.getCity())
                            + (a.getDistrict() == null ? "" : a.getDistrict())
                            + (a.getDetail() == null ? "" : a.getDetail()));
                    m.put("address", addr);
                }
            }
            res.add(m);
        }
        return res;
    }

    public void ship(Long claimId, String logisticsNo) {
        ClaimOrder co = claimMapper.selectById(claimId);
        if (co == null) throw new BusinessException(404, "申领单不存在");
        co.setStatus("SHIPPED"); co.setLogisticsNo(logisticsNo);
        claimMapper.updateById(co);
    }

    private String statusLabel(String s) {
        Map<String, String> m = new HashMap<>();
        m.put("PENDING_PAY", "待付运费"); m.put("PAID", "待发货"); m.put("SHIPPED", "已发货"); m.put("COMPLETED", "已完成"); m.put("CLOSED", "已关闭");
        return m.getOrDefault(s, s);
    }
}
