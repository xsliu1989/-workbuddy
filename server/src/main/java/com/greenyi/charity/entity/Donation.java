package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("donation")
public class Donation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String donationNo;
    private Long userId;
    private String category;
    private Integer quantity;
    private String description;
    private String photos;
    private String deliveryType;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
