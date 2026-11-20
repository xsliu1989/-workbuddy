package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("sys_config")
public class SysConfig {
    @TableId(type = IdType.INPUT)
    private String configKey;
    private String configValue;
    private String description;
    private Date updatedAt;
}
