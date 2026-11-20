package com.greenyi.charity.integration;

import com.greenyi.charity.entity.SysConfig;
import com.greenyi.charity.mapper.SysConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 业务配置读取（集中管理草案值，后台可配置）
 */
@Service
public class ConfigService {
    @Autowired
    private SysConfigMapper configMapper;

    public String get(String key, String defaultValue) {
        SysConfig c = configMapper.selectById(key);
        return c == null ? defaultValue : c.getConfigValue();
    }

    public int getInt(String key, int def) {
        try { return Integer.parseInt(get(key, String.valueOf(def))); } catch (Exception e) { return def; }
    }

    public BigDecimal getDecimal(String key, double def) {
        try { return new BigDecimal(get(key, String.valueOf(def))); } catch (Exception e) { return BigDecimal.valueOf(def); }
    }

    // 常用配置快捷方法
    public int levelThreshold(int level) {
        return getInt("cert.level" + level + ".threshold", new int[]{0, 100, 500, 1000}[Math.max(1, Math.min(3, level))]);
    }
    public int pointPerYuan() { return getInt("point.per.yuan", 10); }
    public int pointExchangeRate() { return getInt("point.exchange.rate", 100); }
    public int pointMinUse() { return getInt("point.min.use", 100); }
    public BigDecimal pointMaxRatio() { return getDecimal("point.max.discount.ratio", 0.3); }
    public int claimLimit() { return getInt("claim.per.user.limit", 3); }
    public double chatConfidenceThreshold() { return getDecimal("ai.chat.confidence.threshold", 0.75).doubleValue(); }
}
