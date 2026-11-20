package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

/** 捐赠物资邮寄接收地址（管理端维护，小程序「快递邮寄」时展示供用户选择） */
@Data
@TableName("donation_address")
public class DonationAddress {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 收件人 */
    private String receiver;
    /** 联系电话 */
    private String phone;
    private String province;
    private String city;
    private String district;
    /** 详细地址 */
    private String detail;
    /** 邮政编码（可空） */
    private String postalCode;
    /** 说明，例如「仅接收衣物类」「工作日 9:00-17:00」 */
    private String remark;
    /** 是否启用：1 启用，0 停用 */
    private Integer enabled;
    /** 排序，越小越靠前 */
    private Integer sortNo;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
