package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("food_dishes")
public class FoodDishEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("category_id")
    private Long categoryId;

    private String name;
    private String subtitle;
    private String description;

    @TableField("taste_tags")
    private String tasteTags;

    @TableField("highlight_tags")
    private String highlightTags;

    @TableField("cook_minutes")
    private Integer cookMinutes;

    @TableField("serving_count")
    private Integer servingCount;

    @TableField("cover_tone")
    private String coverTone;

    @TableField("cover_text")
    private String coverText;

    private String status;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
