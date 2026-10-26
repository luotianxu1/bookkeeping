package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GlobalMarketKLineItemResponse {
    private String date;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;
}
