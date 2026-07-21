package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StockScreenItemResponse {
    private String stockCode;
    private String stockName;
    private String market;
    private Integer bearishCount6;
    private Boolean lastThreeBearish;
    private Boolean lastThreeVolumeUp;
    private BigDecimal threeDayDeclinePct;
    private BigDecimal lastDayDeclinePct;
    private Boolean bullishEngulfing;
    private Boolean noLowerShadow;
    private Boolean volumeShrinking;
    private BigDecimal volumeRatio;
    private BigDecimal lowerShadowPct;
    private Integer signalScore;
    private LocalDate bearishStartDate;
    private LocalDate previousDate;
    private LocalDate signalDate;
    private BigDecimal previousOpen;
    private BigDecimal previousClose;
    private BigDecimal signalOpen;
    private BigDecimal signalClose;
    private Long previousVolume;
    private Long signalVolume;
}
