package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionResponse {

    private Long id;
    private Long sourceId;
    private String sourceType;
    private String transactionNo;
    private Long userId;
    private String type;
    private BigDecimal amount;
    private String currencyCode;
    private Long accountId;
    private String accountName;
    private Long categoryId;
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;
    // 转账流水专用：转出/转入账户
    private Long fromAccountId;
    private String fromAccountName;
    private Long toAccountId;
    private String toAccountName;
    private String title;
    private String remark;
    private LocalDateTime occurredAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
