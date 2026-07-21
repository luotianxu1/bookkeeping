package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class MarketStatusResponse {
    private String statusKey;
    private String statusLabel;
    private String tone;
    private String summary;
    private Integer advanceCount;
    private Integer declineCount;
    private Integer flatCount;
    private BigDecimal advanceRatio;
    private BigDecimal turnover;
    private LocalDateTime updatedAt;
    private String source;
    private List<IndexQuote> indices = Collections.emptyList();

    @Data
    public static class IndexQuote {
        private String code;
        private String name;
        private BigDecimal value;
        private BigDecimal change;
        private BigDecimal changePercent;
    }
}
