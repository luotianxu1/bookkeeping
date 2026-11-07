package com.example.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("savings_goals")
public class SavingsGoalEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long accountId;
    private String name;
    private BigDecimal targetAmount;
    private String currencyCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDate targetDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
