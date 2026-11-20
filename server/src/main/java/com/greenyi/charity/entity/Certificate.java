package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("certificate")
public class Certificate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String certNo;
    private String verifyCode;
    private Long userId;
    private String certType;
    private String category;
    private String title;
    private Integer level;
    private Long activityId;
    private String holderName;
    @TableField(fill = FieldFill.INSERT)
    private Date issuedAt;
    private Integer status;
}
