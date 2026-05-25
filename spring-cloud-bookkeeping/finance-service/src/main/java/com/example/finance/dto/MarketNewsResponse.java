package com.example.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MarketNewsResponse {

    private String categoryKey;
    private String categoryLabel;
    private Integer count;
    private LocalDateTime updatedAt;
    private String source;
    private List<MarketNewsItem> items;

    @Data
    public static class MarketNewsItem {
        private String code;
        private String title;
        private String summary;
        private String url;
        private LocalDateTime publishedAt;
        private Integer commentCount;
        private Integer shareCount;
        private Integer relatedStockCount;
        private Boolean highlight;
    }
}
