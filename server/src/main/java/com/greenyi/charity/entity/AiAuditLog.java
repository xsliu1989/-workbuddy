package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("ai_audit_log")
public class AiAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String feature;
    private String inputText;
    private String outputText;
    private BigDecimal confidence;
    private String decision;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
