package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("orders")
public class Orders {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long addressId;
    private BigDecimal goodsAmount;
    private BigDecimal freightAmount;
    private Integer pointsUsed;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private String payStatus;
    private String status;
    private String transactionId;
    private String logisticsNo;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    private Date paidAt;
}
