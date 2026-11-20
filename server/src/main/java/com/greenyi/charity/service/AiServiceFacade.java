package com.greenyi.charity.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenyi.charity.entity.AiAuditLog;
import com.greenyi.charity.entity.Product;
import com.greenyi.charity.entity.Content;
import com.greenyi.charity.integration.AiService;
import com.greenyi.charity.integration.ConfigService;
import com.greenyi.charity.mapper.AiAuditLogMapper;
import com.greenyi.charity.mapper.ContentMapper;
import com.greenyi.charity.mapper.ProductMapper;
import com.greenyi.charity.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 服务门面：推荐（规则兜底可关闭）、客服（低置信转人工）、审计日志
 */
@Service
public class AiServiceFacade {
    @Autowired private AiService aiService;
    @Autowired private ConfigService config;
    @Autowired private AiAuditLogMapper auditMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private ContentMapper contentMapper;

    /** 智能推荐：mock 下按 id 倒序热门兜底；真实模式可接入排序模型。可关闭（enabled=false 返回热门） */
    public Map<String, Object> recommend(String scene, boolean enabled, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        if ("product".equals(scene)) {
            List<Product> products = productMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                    .eq(Product::getStatus, 1).orderByDesc(Product::getId).last("limit " + limit));
            for (Product p : products) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId()); m.put("name", p.getName()); m.put("coverUrl", p.getCoverUrl()); m.put("price", p.getPrice());
                m.put("kind", p.getKind()); m.put("reason", enabled ? "为你推荐" : "热门");
                items.add(m);
            }
        } else {
            List<Content> contents = contentMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Content>()
                    .eq(Content::getStatus, "PUBLISHED").orderByDesc(Content::getPublishedAt).last("limit " + limit));
            for (Content c : contents) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", c.getId()); m.put("title", c.getTitle()); m.put("coverUrl", c.getCoverUrl()); m.put("kind", c.getKind());
                items.add(m);
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("items", items);
        res.put("aiEnabled", enabled);
        return res;
    }

    /** AI 客服问答：低置信转人工，记审计 */
    public Map<String, Object> chat(String question) {
        List<Map<String, String>> faq = new ArrayList<>();
        Map<String, String> q1 = new HashMap<>(); q1.put("q", "如何捐赠"); q1.put("a", "进入小程序「捐赠」页填写物资信息与照片，提交后由评估员签收评估，贡献会计入你的荣誉档案并可能获得证书与积分。"); faq.add(q1);
        Map<String, String> q2 = new HashMap<>(); q2.put("q", "积分"); q2.put("a", "积分来自物资捐赠的评估值，购物结算时可抵扣现金（运费不可抵扣），具体规则以平台公示为准。"); faq.add(q2);
        Map<String, String> q3 = new HashMap<>(); q3.put("q", "免费申领"); q3.put("a", "有需要的朋友可在「免费申领」中选择物资，先到先得，仅需支付运费。"); faq.add(q3);
        Map<String, String> q4 = new HashMap<>(); q4.put("q", "证书"); q4.put("a", "参与活动并签到可获志愿服务证书，累计捐赠评估值达标可获捐赠等级证书，均可在荣誉档案查看。"); faq.add(q4);

        AiService.ChatAnswer ans = aiService.chat(question, faq);
        AiAuditLog log = new AiAuditLog();
        log.setUserId(UserContext.get() == null ? null : UserContext.get().getUserId());
        log.setFeature("CHAT"); log.setInputText(question); log.setOutputText(ans.getAnswer());
        log.setConfidence(java.math.BigDecimal.valueOf(ans.getConfidence()));
        log.setDecision(ans.isFallback() || ans.getConfidence() < config.chatConfidenceThreshold() ? "FALLBACK" : "ADOPTED");
        auditMapper.insert(log);

        Map<String, Object> res = new HashMap<>();
        res.put("answer", ans.getAnswer());
        res.put("confidence", ans.getConfidence());
        res.put("transferHuman", ans.isFallback() || ans.getConfidence() < config.chatConfidenceThreshold());
        return res;
    }

    /** 审计日志查询（后台） */
    public IPage<AiAuditLog> auditList(int current, int size, String feature) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiAuditLog> qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (feature != null && !feature.isEmpty()) qw.eq(AiAuditLog::getFeature, feature);
        qw.orderByDesc(AiAuditLog::getCreatedAt);
        return auditMapper.selectPage(new Page<>(current, size), qw);
    }
}
