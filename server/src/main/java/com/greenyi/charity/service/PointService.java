package com.greenyi.charity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.entity.PointAccount;
import com.greenyi.charity.entity.PointLedger;
import com.greenyi.charity.integration.ConfigService;
import com.greenyi.charity.mapper.PointAccountMapper;
import com.greenyi.charity.mapper.PointLedgerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分服务：账户、流水、发放、抵扣试算、核销、退款回退
 * 关键约束（BR-006/007/008）：
 * - 积分仅来自捐赠评估值（象征性比例）
 * - 兑换率 1000 积分=10 元（100 积分=1 元）
 * - 单笔抵扣设上限 + 门槛，运费不可用积分抵扣
 * - 退款回退积分，流水留痕
 */
@Service
public class PointService {
    @Autowired private PointAccountMapper accountMapper;
    @Autowired private PointLedgerMapper ledgerMapper;
    @Autowired private ConfigService config;

    public PointAccount getOrCreate(Long userId) {
        PointAccount pa = accountMapper.selectOne(new LambdaQueryWrapper<PointAccount>().eq(PointAccount::getUserId, userId));
        if (pa == null) {
            pa = new PointAccount();
            pa.setUserId(userId); pa.setBalance(0); pa.setTotalEarned(0); pa.setTotalUsed(0);
            accountMapper.insert(pa);
        }
        return pa;
    }

    public PointAccount getAccount(Long userId) { return getOrCreate(userId); }

    public List<PointLedger> listLedger(Long userId) {
        return ledgerMapper.selectList(new LambdaQueryWrapper<PointLedger>()
                .eq(PointLedger::getUserId, userId).orderByDesc(PointLedger::getCreatedAt));
    }

    /** 评估值确认时发放积分 */
    @Transactional
    public int earnFromAssessment(Long userId, BigDecimal valueAmount, String donationNo) {
        int points = valueAmount.multiply(BigDecimal.valueOf(config.pointPerYuan()))
                .setScale(0, RoundingMode.DOWN).intValue();
        if (points <= 0) return 0;
        PointAccount pa = getOrCreate(userId);
        int newBalance = pa.getBalance() + points;
        pa.setBalance(newBalance);
        pa.setTotalEarned(pa.getTotalEarned() + points);
        accountMapper.updateById(pa);
        addLedger(userId, "EARN", points, newBalance, "ASSESSMENT", donationNo, "物资评估值发放(象征性回馈)");
        return points;
    }

    /**
     * 抵扣试算（不扣减）：返回可用积分、抵扣额、拆分金额
     * @param goodsAmount 货款（不含运费，运费不可抵扣）
     */
    public Map<String, Object> calcDiscount(Long userId, BigDecimal goodsAmount, Integer requestPoints) {
        PointAccount pa = getOrCreate(userId);
        int rate = config.pointExchangeRate(); // 100 积分=1 元
        int minUse = config.pointMinUse();
        BigDecimal maxRatio = config.pointMaxRatio();

        int usable = pa.getBalance();
        // 单笔最多抵扣比例 -> 折算成最多可用积分
        BigDecimal maxDiscountByRatio = goodsAmount.multiply(maxRatio).setScale(2, RoundingMode.DOWN);
        int maxPointsByRatio = maxDiscountByRatio.multiply(BigDecimal.valueOf(rate)).setScale(0, RoundingMode.DOWN).intValue();
        int cap = Math.min(usable, maxPointsByRatio);

        int want = requestPoints == null ? cap : Math.min(requestPoints, cap);
        boolean belowThreshold = want > 0 && want < minUse;
        int applyPoints = belowThreshold ? 0 : want;
        BigDecimal discount = BigDecimal.valueOf(applyPoints).divide(BigDecimal.valueOf(rate), 2, RoundingMode.DOWN);
        BigDecimal payable = goodsAmount.subtract(discount);
        if (payable.compareTo(BigDecimal.ZERO) < 0) payable = BigDecimal.ZERO;

        Map<String, Object> res = new HashMap<>();
        res.put("balance", usable);
        res.put("usablePoints", cap);
        res.put("applyPoints", applyPoints);
        res.put("discountAmount", discount);
        res.put("goodsAmount", goodsAmount);
        res.put("payableGoods", payable);
        res.put("minUse", minUse);
        res.put("maxDiscountAmount", maxDiscountByRatio);
        res.put("belowThreshold", belowThreshold);
        return res;
    }

    /** 订单支付成功后核销积分 */
    @Transactional
    public void consume(Long userId, int points, String orderNo) {
        if (points <= 0) return;
        PointAccount pa = getOrCreate(userId);
        if (pa.getBalance() < points) throw new BusinessException(400, "积分余额不足");
        int newBalance = pa.getBalance() - points;
        pa.setBalance(newBalance);
        pa.setTotalUsed(pa.getTotalUsed() + points);
        accountMapper.updateById(pa);
        addLedger(userId, "USE", -points, newBalance, "ORDER", orderNo, "购物抵扣核销");
    }

    /** 退款回退积分 */
    @Transactional
    public void refund(Long userId, int points, String orderNo) {
        if (points <= 0) return;
        PointAccount pa = getOrCreate(userId);
        int newBalance = pa.getBalance() + points;
        pa.setBalance(newBalance);
        // 回退不增加 totalEarned（只是归还已核销的）
        accountMapper.updateById(pa);
        addLedger(userId, "REFUND", points, newBalance, "ORDER", orderNo, "退款回退积分");
    }

    private void addLedger(Long userId, String type, int amount, int balanceAfter, String source, String refNo, String remark) {
        PointLedger l = new PointLedger();
        l.setUserId(userId); l.setChangeType(type); l.setAmount(amount);
        l.setBalanceAfter(balanceAfter); l.setSource(source); l.setRefNo(refNo); l.setRemark(remark);
        ledgerMapper.insert(l);
    }
}
