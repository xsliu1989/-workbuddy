package com.greenyi.charity.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.common.PageResult;
import com.greenyi.charity.entity.*;
import com.greenyi.charity.integration.AiService;
import com.greenyi.charity.mapper.*;
import com.greenyi.charity.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 物资捐赠服务（MVP 主路径）
 * 发起 -> 签收 -> 评估 -> 累计评估值 -> 发放积分 -> 达到阈值发证书 -> 通知
 * 估值影响证书与积分，人工主导：AI 仅给参考区间，不自动入账（BR-002/013）
 */
@Service
public class DonationService {
    @Autowired private DonationMapper donationMapper;
    @Autowired private AssessmentMapper assessmentMapper;
    @Autowired private DisposalLedgerMapper disposalMapper;
    @Autowired private PointService pointService;
    @Autowired private HonorService honorService;
    @Autowired private CertificateService certificateService;
    @Autowired private AppUserMapper userMapper;
    @Autowired private AiService aiService;
    @Autowired private AiAuditLogMapper aiLogMapper;

    /** FA-001 发起捐赠 */
    public Donation submit(Map<String, Object> body) {
        Long userId = UserContext.requireUserId();
        Donation d = new Donation();
        d.setDonationNo("DN" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
        d.setUserId(userId);
        String category = str(body.get("category"));
        if (!org.springframework.util.StringUtils.hasText(category)) throw new BusinessException(400, "请选择物资类别");
        d.setCategory(category);
        d.setQuantity(body.get("quantity") == null ? 1 : Integer.parseInt(body.get("quantity").toString()));
        d.setDescription(str(body.get("description")));
        d.setPhotos(str(body.get("photos")));
        d.setDeliveryType(str(body.get("deliveryType")) == null ? "OFFLINE" : str(body.get("deliveryType")));
        if (body.get("deliveryAddressId") != null) {
            try { d.setDeliveryAddressId(Long.valueOf(body.get("deliveryAddressId").toString())); }
            catch (NumberFormatException ignored) { }
        }
        d.setStatus("PENDING_RECEIVE");
        donationMapper.insert(d);
        return d;
    }

    /** FA-002 评估员签收 */
    @Transactional
    public void receive(Long donationId) {
        Donation d = requireDonation(donationId);
        if (!"PENDING_RECEIVE".equals(d.getStatus())) throw new BusinessException(400, "当前状态不可签收");
        d.setStatus("RECEIVED");
        donationMapper.updateById(d);
    }

    /** FA-003/005 评估计价：核心入账逻辑，全部在一个事务内 */
    @Transactional
    public Map<String, Object> assess(Long donationId, Map<String, Object> body, Long assessorId) {
        Donation d = requireDonation(donationId);
        if (!"RECEIVED".equals(d.getStatus()) && !"REVIEWING".equals(d.getStatus())) {
            throw new BusinessException(400, "仅已接收/复核中的捐赠单可评估");
        }
        BigDecimal value = new BigDecimal(str(body.get("valueAmount")));
        if (value.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException(400, "评估值不能为负");
        String note = str(body.get("note"));
        boolean isReview = "REVIEWING".equals(d.getStatus());

        // 1. 记录评估（复核时保留原评估历史）
        Assessment a = new Assessment();
        a.setDonationId(donationId);
        a.setAssessorId(assessorId);
        a.setValueAmount(value);
        a.setNote(note);
        a.setStatus("ACTIVE");
        a.setIsReview(isReview ? 1 : 0);
        if (isReview) {
            Assessment prev = assessmentMapper.selectOne(new LambdaQueryWrapper<Assessment>()
                    .eq(Assessment::getDonationId, donationId).eq(Assessment::getStatus, "ACTIVE")
                    .orderByDesc(Assessment::getCreatedAt).last("limit 1"));
            if (prev != null) { prev.setStatus("REPLACED"); assessmentMapper.updateById(prev); a.setReviewOf(prev.getId()); }
        }
        assessmentMapper.insert(a);

        // 2. 更新捐赠单状态
        d.setStatus("ASSESSED");
        donationMapper.updateById(d);

        // 3. 累计评估值 -> 判定升级
        int newLevel = honorService.addAssessValue(d.getUserId(), value);

        // 4. 发放积分（象征性回馈）
        int points = pointService.earnFromAssessment(d.getUserId(), value, d.getDonationNo());

        // 5. 若升级则生成等级证书
        Certificate cert = null;
        if (newLevel > 0) {
            AppUser u = userMapper.selectById(d.getUserId());
            cert = certificateService.issueDonationLevelCert(d.getUserId(), newLevel, u == null ? null : u.getNickname());
        }

        Map<String, Object> res = new HashMap<>();
        res.put("donationId", donationId);
        res.put("valueAmount", value);
        res.put("pointsEarned", points);
        res.put("newLevel", newLevel);
        res.put("certNo", cert == null ? null : cert.getCertNo());
        return res;
    }

    /** FA-004 评估异议复核 */
    @Transactional
    public void dispute(Long donationId, String reason) {
        Donation d = requireDonation(donationId);
        if (!"ASSESSED".equals(d.getStatus()) && !"BOOKED".equals(d.getStatus())) {
            throw new BusinessException(400, "当前状态不可申请复核");
        }
        // 安全：仅本人或管理员
        Long uid = UserContext.requireUserId();
        if (!d.getUserId().equals(uid) && !UserContext.isAdmin()) throw new BusinessException(403, "无权操作");
        d.setStatus("REVIEWING");
        donationMapper.updateById(d);
    }

    /** FA-006 物资处置台账 */
    public void recordDisposal(Long donationId, Map<String, Object> body) {
        DisposalLedger l = new DisposalLedger();
        l.setDonationId(donationId);
        l.setMethod(str(body.get("method")));
        l.setNote(str(body.get("note")));
        Long operator = UserContext.get() == null ? null : UserContext.get().getUserId();
        l.setOperatorId(operator);
        disposalMapper.insert(l);
    }

    /** FA-007 我的捐赠记录 */
    public List<Map<String, Object>> myDonations() {
        Long userId = UserContext.requireUserId();
        List<Donation> list = donationMapper.selectList(new LambdaQueryWrapper<Donation>()
                .eq(Donation::getUserId, userId).orderByDesc(Donation::getCreatedAt));
        List<Map<String, Object>> res = new ArrayList<>();
        for (Donation d : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId()); m.put("donationNo", d.getDonationNo());
            m.put("category", d.getCategory()); m.put("quantity", d.getQuantity());
            m.put("status", d.getStatus()); m.put("statusLabel", statusLabel(d.getStatus()));
            m.put("createdAt", d.getCreatedAt());
            Assessment a = assessmentMapper.selectOne(new LambdaQueryWrapper<Assessment>()
                    .eq(Assessment::getDonationId, d.getId()).eq(Assessment::getStatus, "ACTIVE")
                    .orderByDesc(Assessment::getCreatedAt).last("limit 1"));
            m.put("valueAmount", a == null ? null : a.getValueAmount());
            res.add(m);
        }
        return res;
    }

    /** 评估工作台：默认展示全部（含刚提交待接收），可按状态筛选；TO_ASSESS=待评估 */
    public PageResult<Donation> workbench(int current, int size, String status) {
        LambdaQueryWrapper<Donation> qw = new LambdaQueryWrapper<>();
        if (!org.springframework.util.StringUtils.hasText(status) || "ALL".equals(status)) {
            // 不过滤状态，确保用户刚提交的 PENDING_RECEIVE 捐赠也能出现在工作台
        } else if ("TO_ASSESS".equals(status)) {
            qw.in(Donation::getStatus, Arrays.asList("RECEIVED", "REVIEWING"));
        } else {
            qw.eq(Donation::getStatus, status);
        }
        qw.orderByDesc(Donation::getCreatedAt);
        IPage<Donation> page = donationMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(page, d -> d);
    }

    /** AI 估值参考（仅参考，记审计，不自动入账） */
    public Map<String, Object> estimateValue(Map<String, Object> body) {
        AiService.ValuationRange range = aiService.estimateValue(str(body.get("category")), str(body.get("description")));
        // 审计留痕
        AiAuditLog log = new AiAuditLog();
        Long uid = UserContext.get() == null ? null : UserContext.get().getUserId();
        log.setUserId(uid); log.setFeature("VALUATION");
        log.setInputText(str(body.get("description")));
        log.setOutputText("[" + range.getLow() + "," + range.getHigh() + "]");
        log.setDecision("PENDING");
        aiLogMapper.insert(log);
        Map<String, Object> res = new HashMap<>();
        res.put("low", range.getLow()); res.put("high", range.getHigh());
        res.put("note", range.getNote());
        res.put("hint", "AI 估值仅供参考，最终以评估员裁定为准，不自动入账");
        return res;
    }

    private Donation requireDonation(Long id) {
        Donation d = donationMapper.selectById(id);
        if (d == null) throw new BusinessException(404, "捐赠单不存在");
        return d;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private String statusLabel(String s) {
        Map<String, String> m = new HashMap<>();
        m.put("PENDING_RECEIVE", "待接收"); m.put("RECEIVED", "已接收"); m.put("ASSESSED", "已评估");
        m.put("BOOKED", "已入账"); m.put("REVIEWING", "复核中"); m.put("CLOSED", "已关闭");
        return m.getOrDefault(s, s);
    }
}
