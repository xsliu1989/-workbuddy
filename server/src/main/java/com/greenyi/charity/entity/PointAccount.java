package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("point_account")
public class PointAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer balance;
    private Integer totalEarned;
    private Integer totalUsed;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
