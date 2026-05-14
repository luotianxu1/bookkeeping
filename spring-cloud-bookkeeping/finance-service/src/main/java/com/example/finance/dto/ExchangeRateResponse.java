package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExchangeRateResponse {

    private String fromCurrency;
    private String toCurrency;
    private BigDecimal rate;
    private LocalDateTime updatedAt;
    private String source;
}
