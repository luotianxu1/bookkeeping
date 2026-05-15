package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvestmentProductResponse {
    private Long id;
    private String productType;
    private String market;
    private String exchangeCode;
    private String symbol;
    private String name;
    private String shortName;
    private String currencyCode;
    private String unitName;
    private Integer pricePrecision;
    private BigDecimal latestPrice;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
