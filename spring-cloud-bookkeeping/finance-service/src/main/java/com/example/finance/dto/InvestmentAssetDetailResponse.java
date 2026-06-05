package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class InvestmentAssetDetailResponse {
    private InvestmentPositionResponse position;
    private String productType;
    private String name;
    private String symbol;
    private String market;
    private String unitName;
    private BigDecimal latestPrice;
    private BigDecimal change;
    private BigDecimal changePercent;
    private String updatedAt;
    private List<InvestmentDetailStatResponse> marketStats;
    private List<InvestmentDetailStatResponse> holdingStats;
    private List<InvestmentFundRedeemFeeOptionResponse> fundRedeemFeeOptions;
    private List<InvestmentDividendResponse> dividendRecords;
    private List<InvestmentChartPointResponse> chartPoints;
    private String chartType;
    private String source;
    private String description;
}
