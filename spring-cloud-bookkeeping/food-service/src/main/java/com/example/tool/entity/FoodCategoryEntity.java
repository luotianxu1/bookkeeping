package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("food_categories")
public class FoodCategoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("category_type")
    private String categoryType;

    private String name;

    @TableField("icon_text")
    private String iconText;

    @TableField("icon_tone")
    private String iconTone;

    private String description;

    @TableField("sort_order")
    private Integer sortOrder;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
