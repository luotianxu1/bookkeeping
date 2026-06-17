package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GoldRealtimePriceResponse {

    private String name;
    private String unit;
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private LocalDateTime updatedAt;
    private String source;
}
