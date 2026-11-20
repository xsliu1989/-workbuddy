package com.greenyi.charity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String kind;
    private BigDecimal price;
    private Integer weightGram;
    private Integer stock;
    private String coverUrl;
    private String images;
    /** 商品详情描述：支持 HTML（文字 + 图片 <img> 标签） */
    private String description;
    /** 售后描述：退换货/售后说明 */
    private String afterSaleDesc;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
