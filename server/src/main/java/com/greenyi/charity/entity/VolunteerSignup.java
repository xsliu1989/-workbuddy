package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("volunteer_signup")
public class VolunteerSignup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long userId;
    /** 报名人姓名（可替他人报名） */
    private String signupName;
    /** 报名人手机号 */
    private String phone;
    private Integer guardianAuth;
    private String status;
    private Date checkinAt;
    private Integer hoursMinutes;
    private Long certId;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
