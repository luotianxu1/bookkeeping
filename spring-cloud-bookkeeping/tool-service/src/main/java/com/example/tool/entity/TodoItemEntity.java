package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("todo_items")
public class TodoItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String title;

    @TableField("due_at")
    private LocalDateTime dueAt;

    // 允许清空备注/完成时间：默认 NOT_NULL 策略会把 null 字段从 UPDATE 的 SET 中剔除
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    @TableField("sort_order")
    private Integer sortOrder;

    private String status;

    @TableField(value = "completed_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
