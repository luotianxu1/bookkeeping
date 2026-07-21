package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class UsPremarketResponse {
    private String sessionStatus;
    private String sessionLabel;
    private LocalDateTime updatedAt;
    private String source;
    private List<IndexQuote> indices = Collections.emptyList();

    @Data
    public static class IndexQuote {
        private String indexCode;
        private String indexName;
        private String proxySymbol;
        private String proxyName;
        private BigDecimal price;
        private BigDecimal change;
        private BigDecimal changePercent;
        private Long volume;
        private BigDecimal bidPrice;
        private BigDecimal askPrice;
        private String lastTradeTime;
    }
}
