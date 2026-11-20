package com.greenyi.charity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.entity.Certificate;
import com.greenyi.charity.integration.CertificateVerifier;
import com.greenyi.charity.mapper.CertificateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 证书服务：捐赠等级证书（荣誉激励）+ 活动志愿者证书（官方资质）
 * 两类均由平台签发，背书口径分开标注（BR-004/005）
 */
@Service
public class CertificateService {
    @Autowired private CertificateMapper certMapper;
    @Autowired private CertificateVerifier verifier;

    private static final String[] LEVEL_TITLE = {"", "绿益公益·爱心志愿者", "绿益公益·公益之星", "绿益公益·公益大使"};

    /** 生成捐赠等级证书 */
    @Transactional
    public Certificate issueDonationLevelCert(Long userId, int level, String holderName) {
        Certificate c = new Certificate();
        c.setCertNo(verifier.generateCertNo("DONATION_LEVEL"));
        c.setVerifyCode(verifier.generateVerifyCode());
        c.setUserId(userId);
        c.setCertType("DONATION_LEVEL");
        c.setCategory("HONOR");
        c.setTitle(levelTitle(level));
        c.setLevel(level);
        c.setHolderName(holderName);
        c.setStatus(1);
        certMapper.insert(c);
        return c;
    }

    /** 生成活动志愿者证书（官方承认资质类） */
    @Transactional
    public Certificate issueVolunteerCert(Long userId, Long activityId, String holderName, String activityTitle) {
        Certificate c = new Certificate();
        c.setCertNo(verifier.generateCertNo("ACTIVITY_VOLUNTEER"));
        c.setVerifyCode(verifier.generateVerifyCode());
        c.setUserId(userId);
        c.setCertType("ACTIVITY_VOLUNTEER");
        c.setCategory("OFFICIAL");
        c.setTitle("志愿服务证书 · " + (activityTitle == null ? "公益活动" : activityTitle));
        c.setActivityId(activityId);
        c.setHolderName(holderName);
        c.setStatus(1);
        certMapper.insert(c);
        return c;
    }

    public List<Certificate> listMyCerts(Long userId) {
        return certMapper.selectList(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getUserId, userId).eq(Certificate::getStatus, 1)
                .orderByDesc(Certificate::getIssuedAt));
    }

    /** 验真：证书编号 + 验真码 */
    public Map<String, Object> verify(String certNo, String verifyCode) {
        Certificate c = certMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertNo, certNo)
                .eq(verifyCode != null, Certificate::getVerifyCode, verifyCode));
        Map<String, Object> res = new HashMap<>();
        if (c == null || (c.getStatus() != null && c.getStatus() == 0)) {
            res.put("valid", false);
            res.put("message", "证书不存在或已作废");
            return res;
        }
        res.put("valid", true);
        res.put("certNo", c.getCertNo());
        res.put("title", c.getTitle());
        res.put("holderName", c.getHolderName());
        res.put("categoryLabel", "OFFICIAL".equals(c.getCategory()) ? "官方资质类" : "荣誉激励类");
        res.put("issuedAt", c.getIssuedAt());
        return res;
    }

    private String levelTitle(int level) {
        return level >= 1 && level <= 3 ? LEVEL_TITLE[level] : "绿益公益·爱心志愿者";
    }
}
