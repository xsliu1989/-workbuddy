package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("freight_template")
public class FreightTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String region;
    private BigDecimal baseFee;
    private BigDecimal perKgFee;
    private Integer status;
}
