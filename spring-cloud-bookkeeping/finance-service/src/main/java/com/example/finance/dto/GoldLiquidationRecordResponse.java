package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GoldLiquidationRecordResponse {
    private Long id;
    private Long accountId;
    private String accountName;
    private Long positionId;
    private Long productId;
    private String productName;
    private String productSymbol;
    private LocalDateTime tradeAt;
    private BigDecimal profit;
    private BigDecimal weight;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private BigDecimal fee;
}
