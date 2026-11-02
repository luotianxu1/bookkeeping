package com.example.market.gold.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GoldPriceResponse {

    /**
     * ⚠️ 镜像类：与本文件逐字段对应的副本位于
     * app-service 的 com.example.app.dto.GoldPriceResponse（经 MarketGoldClient 传输）。
     * 两边字段必须同步修改，改前先 grep 对端类名。
     */
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
