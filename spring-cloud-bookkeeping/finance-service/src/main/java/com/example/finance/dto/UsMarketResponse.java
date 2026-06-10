package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UsMarketResponse {

    private List<UsMarketIndexQuote> indices;
    private LocalDateTime updatedAt;
    private Integer autoRefreshIntervalSeconds;
    private String source;

    @Data
    public static class UsMarketIndexQuote {
        private String code;
        private String name;
        private String alias;
        private BigDecimal price;
        private BigDecimal change;
        private BigDecimal changePercent;
        private BigDecimal previousClose;
        private BigDecimal openPrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private String marketTimeLabel;
        private List<UsMarketChartPoint> chartPoints;
        private LocalDateTime updatedAt;
        private String source;
        private boolean stale;
    }

    @Data
    public static class UsMarketChartPoint {
        private String label;
        private BigDecimal price;
    }
}
