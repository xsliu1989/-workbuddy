package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("claim_order")
public class ClaimOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String claimNo;
    private Long userId;
    private Long productId;
    private Long addressId;
    private String region;
    private Integer weightGram;
    private BigDecimal freightAmount;
    private String payStatus;
    private String status;
    private String transactionId;
    private String logisticsNo;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    private Date paidAt;
}
