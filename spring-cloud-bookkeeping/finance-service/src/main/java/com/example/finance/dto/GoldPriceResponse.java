package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GoldPriceResponse {

    private GoldMarketQuote spotGold;
    private GoldMarketQuote londonGold;
    private GoldMarketStats stats;
    private List<JewelryGoldPrice> jewelryPrices;
    private List<GoldChartPoint> chartPoints;
    private LocalDateTime updatedAt;
    private String source;

    @Data
    public static class GoldMarketQuote {
        private String name;
        private String unit;
        private BigDecimal price;
        private BigDecimal change;
        private BigDecimal changePercent;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class GoldMarketStats {
        private BigDecimal openPrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private String unit;
    }

    @Data
    public static class JewelryGoldPrice {
        private String brandName;
        private BigDecimal price;
        private String unit;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class GoldChartPoint {
        private String label;
        private BigDecimal price;
    }
}
