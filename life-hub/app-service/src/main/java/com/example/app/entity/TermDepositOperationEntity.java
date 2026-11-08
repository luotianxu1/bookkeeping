package com.example.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.*;

@Data
@TableName("term_deposit_operations")
public class TermDepositOperationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long depositId;
    private String operation;
    private Long cashAccountId;
    private Long childDepositId;
    private Long principalTransactionId;
    private Long interestTransactionId;
    private Long renewalTransactionId;
    private BigDecimal principal;
    private BigDecimal interest;
    private BigDecimal cashDelta;
    private LocalDate occurredOn;
    private String detail;
    private LocalDateTime reversedAt;
    private LocalDateTime createdAt;
}
