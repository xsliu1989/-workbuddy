package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("assessment")
public class Assessment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long donationId;
    private Long assessorId;
    private BigDecimal valueAmount;
    private String note;
    private String status;
    private Integer isReview;
    private Long reviewOf;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
