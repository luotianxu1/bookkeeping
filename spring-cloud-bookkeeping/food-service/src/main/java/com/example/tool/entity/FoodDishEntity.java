package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
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

    // 允许清空副标题/介绍/标签：默认 NOT_NULL 策略会把 null 字段从 UPDATE 的 SET 中剔除
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String subtitle;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    @TableField(value = "taste_tags", updateStrategy = FieldStrategy.ALWAYS)
    private String tasteTags;

    @TableField(value = "highlight_tags", updateStrategy = FieldStrategy.ALWAYS)
    private String highlightTags;

    @TableField("cook_minutes")
    private Integer cookMinutes;

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
