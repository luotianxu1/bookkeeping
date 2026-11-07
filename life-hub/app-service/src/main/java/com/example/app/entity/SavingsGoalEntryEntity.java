package com.example.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("savings_goal_entries")
public class SavingsGoalEntryEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long goalId;
    private Long transactionId;
    private String type;
    private BigDecimal amount;
    private LocalDate occurredOn;
    private String remark;
    private LocalDateTime createdAt;
}
