package com.greenyi.charity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenyi.charity.entity.*;
import com.greenyi.charity.integration.ConfigService;
import com.greenyi.charity.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 荣誉档案服务：聚合捐赠评估值 + 志愿时长 + 活动参与（FR-020/021）
 * 统一荣誉档案，证书分两类背书口径：
 * - HONOR 荣誉激励类：捐赠等级证书
 * - OFFICIAL 官方资质类：活动志愿者证书
 */
@Service
public class HonorService {
    @Autowired private HonorProfileMapper honorMapper;
    @Autowired private CertificateMapper certMapper;
    @Autowired private VolunteerSignupMapper signupMapper;
    @Autowired private DonationMapper donationMapper;
    @Autowired private ConfigService config;

    public HonorProfile getOrCreate(Long userId) {
        // honor_profile 主键为自增id，需按 userId 查
        HonorProfile hp = honorMapper.selectOne(new LambdaQueryWrapper<HonorProfile>().eq(HonorProfile::getUserId, userId));
        if (hp == null) {
            hp = new HonorProfile();
            hp.setUserId(userId);
            hp.setTotalAssessValue(BigDecimal.ZERO);
            hp.setVolunteerHours(0);
            hp.setDonationLevel(0);
            honorMapper.insert(hp);
        }
        return hp;
    }

    /** 根据累计评估值判定等级 1/2/3，0=未达标 */
    public int calcLevel(BigDecimal total) {
        if (total == null) return 0;
        if (total.compareTo(BigDecimal.valueOf(config.levelThreshold(3))) >= 0) return 3;
        if (total.compareTo(BigDecimal.valueOf(config.levelThreshold(2))) >= 0) return 2;
        if (total.compareTo(BigDecimal.valueOf(config.levelThreshold(1))) >= 0) return 1;
        return 0;
    }

    /** 累加评估值，返回升级后的等级（0 表示未变化到更高等级） */
    public int addAssessValue(Long userId, BigDecimal delta) {
        HonorProfile hp = getOrCreate(userId);
        int oldLevel = hp.getDonationLevel() == null ? 0 : hp.getDonationLevel();
        BigDecimal newTotal = (hp.getTotalAssessValue() == null ? BigDecimal.ZERO : hp.getTotalAssessValue()).add(delta);
        hp.setTotalAssessValue(newTotal);
        int newLevel = calcLevel(newTotal);
        hp.setDonationLevel(newLevel);
        honorMapper.updateById(hp);
        return newLevel > oldLevel ? newLevel : 0;
    }

    /** 活动结束时长并入荣誉档案 */
    public void addVolunteerHours(Long userId, int minutes) {
        HonorProfile hp = getOrCreate(userId);
        hp.setVolunteerHours((hp.getVolunteerHours() == null ? 0 : hp.getVolunteerHours()) + minutes);
        honorMapper.updateById(hp);
    }

    /** 荣誉档案聚合视图 */
    public Map<String, Object> getProfileView(Long userId) {
        HonorProfile hp = getOrCreate(userId);
        int donationCount = donationMapper.selectCount(new LambdaQueryWrapper<Donation>().eq(Donation::getUserId, userId)
                .in(Donation::getStatus, Arrays.asList("ASSESSED", "BOOKED"))).intValue();
        int activityCount = signupMapper.selectCount(new LambdaQueryWrapper<VolunteerSignup>()
                .eq(VolunteerSignup::getUserId, userId)).intValue();

        List<Certificate> certs = certMapper.selectList(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getUserId, userId).eq(Certificate::getStatus, 1)
                .orderByDesc(Certificate::getIssuedAt));

        Map<String, Object> res = new HashMap<>();
        res.put("totalAssessValue", hp.getTotalAssessValue());
        res.put("donationLevel", hp.getDonationLevel());
        res.put("volunteerHours", hp.getVolunteerHours());
        res.put("donationCount", donationCount);
        res.put("activityCount", activityCount);
        res.put("levelNextThreshold", nextThreshold(hp.getDonationLevel()));
        res.put("certificates", certs.stream().map(this::certView).collect(Collectors.toList()));
        // 分类展示
        res.put("honorCerts", certs.stream().filter(c -> "HONOR".equals(c.getCategory())).map(this::certView).collect(Collectors.toList()));
        res.put("officialCerts", certs.stream().filter(c -> "OFFICIAL".equals(c.getCategory())).map(this::certView).collect(Collectors.toList()));
        return res;
    }

    public Map<String, Object> certView(Certificate c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("certNo", c.getCertNo());
        m.put("title", c.getTitle());
        m.put("certType", c.getCertType());
        m.put("category", c.getCategory());
        m.put("categoryLabel", "OFFICIAL".equals(c.getCategory()) ? "官方资质类" : "荣誉激励类");
        m.put("level", c.getLevel());
        m.put("holderName", c.getHolderName());
        m.put("issuedAt", c.getIssuedAt());
        return m;
    }

    private int nextThreshold(int level) {
        if (level < 3) return config.levelThreshold(level + 1);
        return config.levelThreshold(3);
    }
}
