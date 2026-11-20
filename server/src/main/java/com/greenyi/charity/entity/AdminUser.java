package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("admin_user")
public class AdminUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String roleCode;
    private Integer status;
    private Date lastLoginAt;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
