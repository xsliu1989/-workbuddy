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
    /** 用户选择的邮寄地址ID（donation_address.id），仅 MAIL 时有值 */
    private Long deliveryAddressId;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
