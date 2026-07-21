package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class LimitUpDownResponse {
    private Integer limitUpCount;
    private Integer limitDownCount;
    private Integer brokenLimitCount;
    private BigDecimal sealRate;
    private LocalDateTime updatedAt;
    private String source;
    private List<StockItem> limitUps = Collections.emptyList();
    private List<StockItem> limitDowns = Collections.emptyList();

    @Data
    public static class StockItem {
        private String code;
        private String name;
        private BigDecimal changePercent;
        private String industry;
    }
}
