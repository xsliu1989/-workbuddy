package com.greenyi.charity.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 模拟 AI 服务（greenyi.integration.ai-mock=true 时启用）
 */
@Component
@ConditionalOnProperty(name = "greenyi.integration.ai-mock", havingValue = "true", matchIfMissing = true)
public class MockAiService implements AiService {

    @Override
    public ChatAnswer chat(String question, List<Map<String, String>> faqContext) {
        ChatAnswer a = new ChatAnswer();
        if (faqContext != null) {
            for (Map<String, String> kv : faqContext) {
                if (question != null && question.contains(kv.get("q"))) {
                    a.setAnswer(kv.get("a"));
                    a.setConfidence(0.9);
                    return a;
                }
            }
        }
        a.setAnswer("这个问题我暂时无法准确回答，已为你转接人工客服。");
        a.setConfidence(0.3);
        a.setFallback(true);
        return a;
    }

    @Override
    public String generateCopy(String topic, String tone) {
        return "【公益草稿】围绕「" + (topic == null ? "公益" : topic) + "」，"
                + "我们以" + (tone == null ? "温暖" : tone) + "的笔触记录每一份善意。"
                + "（AI 生成草稿，需人工确认后发布）";
    }

    @Override
    public ValuationRange estimateValue(String category, String description) {
        ValuationRange r = new ValuationRange();
        // 确定性示例：按类别给出粗略参考区间
        double base = 10;
        if (category != null && (category.contains("棉") || category.contains("冬"))) base = 30;
        else if (category != null && category.contains("书")) base = 8;
        r.setLow(base * 0.6);
        r.setHigh(base * 1.4);
        r.setNote("AI 估值仅供参考，最终以评估员裁定为准，不自动入账");
        return r;
    }
}
