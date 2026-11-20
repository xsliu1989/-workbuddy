package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("activity")
public class Activity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String coverUrl;
    private String description;
    private String location;
    private Date startTime;
    private Date endTime;
    private Integer quota;
    private Integer signedCount;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
