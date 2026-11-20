package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("point_ledger")
public class PointLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String changeType;
    private Integer amount;
    private Integer balanceAfter;
    private String source;
    private String refNo;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
