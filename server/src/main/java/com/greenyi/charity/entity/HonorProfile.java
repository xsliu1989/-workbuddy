package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("honor_profile")
public class HonorProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private BigDecimal totalAssessValue;
    private Integer volunteerHours;
    private Integer donationLevel;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
