package com.example.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InvestmentDividendIncomePageResponse {
    private Long userId;
    private InvestmentDividendIncomeSummaryResponse summary;
    private List<InvestmentDividendIncomeItemResponse> items;
    private LocalDateTime updatedAt;
}
