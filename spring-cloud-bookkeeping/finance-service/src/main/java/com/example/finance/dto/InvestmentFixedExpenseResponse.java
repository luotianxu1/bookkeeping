package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvestmentFixedExpenseResponse {

    private Long id;
    private Long userId;
    private String name;
    private BigDecimal amount;
    private String currencyCode;
    private Integer sortOrder;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
