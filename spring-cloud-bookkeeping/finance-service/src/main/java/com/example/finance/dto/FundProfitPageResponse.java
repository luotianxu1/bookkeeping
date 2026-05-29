package com.example.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FundProfitPageResponse {
    private Long userId;
    private Long accountId;
    private String view;
    private String anchor;
    private String selectedKey;
    private LocalDateTime lastSyncedAt;
    private List<FundProfitPageAccountResponse> accounts;
    private FundProfitPageSummaryResponse summary;
    private String insight;
    private List<FundProfitTrendPointResponse> trendPoints;
    private List<FundProfitCalendarCellResponse> calendarItems;
    private FundProfitSelectionResponse selection;
    private List<FundProfitContributionResponse> contributors;
    private List<FundProfitDetailResponse> details;
}
