package com.greenyi.charity.integration;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 能力适配层
 * - mock 模式：返回确定性示例输出，保障链路可跑通、可关闭、有兜底
 * - 真实模式：替换为大模型 API（OQ-06 选型后接入）
 * 所有输出须经人工确认（BR-013），不自动发布/入账
 */
public interface AiService {

    /** AI 客服问答 -> 答复 + 置信度 */
    ChatAnswer chat(String question, List<Map<String, String>> faqContext);

    /** 文案生成 -> 草稿 */
    String generateCopy(String topic, String tone);

    /** 估值参考 -> 区间（仅参考，不自动入账） */
    ValuationRange estimateValue(String category, String description);

    @Data
    class ChatAnswer {
        private String answer;
        private double confidence;
        private boolean fallback;
    }

    @Data
    class ValuationRange {
        private double low;
        private double high;
        private String note;
    }
}
