package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InvestmentDividendResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productSymbol;
    private Integer dividendYear;
    private LocalDate payDate;
    private BigDecimal dividendPerUnit;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private String status;
    private LocalDateTime paidAt;
}
