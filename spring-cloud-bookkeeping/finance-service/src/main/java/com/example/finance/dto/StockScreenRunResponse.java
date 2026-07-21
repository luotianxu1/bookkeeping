package com.example.finance.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockScreenRunResponse {
    private Long id;
    private LocalDate tradeDate;
    private String triggerName;
    private String status;
    private Integer totalStocks;
    private Integer processedStocks;
    private Integer matchedStocks;
    private Integer failedStocks;
    private String dataSource;
    private String ruleVersion;
    private String resultMessage;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
