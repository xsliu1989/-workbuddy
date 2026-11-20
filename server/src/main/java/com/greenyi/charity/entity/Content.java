package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("content")
public class Content {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String kind;
    private String coverUrl;
    private String body;
    private String videoUrl;
    private String status;
    private Integer shareCount;
    private Date publishedAt;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
