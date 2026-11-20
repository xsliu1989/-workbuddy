package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("disposal_ledger")
public class DisposalLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long donationId;
    private String method;
    private String note;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
