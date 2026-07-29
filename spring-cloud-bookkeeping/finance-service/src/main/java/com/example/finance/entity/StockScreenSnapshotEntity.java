package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock_screen_snapshots")
public class StockScreenSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("run_id")
    private Long runId;
    @TableField("trade_date")
    private LocalDate tradeDate;
    @TableField("stock_code")
    private String stockCode;
    @TableField("stock_name")
    private String stockName;
    private String market;
    @TableField("bearish_count_6")
    private Integer bearishCount6;
    @TableField("last_three_bearish")
    private Boolean lastThreeBearish;
    @TableField("last_three_volume_up")
    private Boolean lastThreeVolumeUp;
    @TableField("three_day_decline_pct")
    private BigDecimal threeDayDeclinePct;
    @TableField("last_day_decline_pct")
    private BigDecimal lastDayDeclinePct;
    @TableField("bullish_engulfing")
    private Boolean bullishEngulfing;
    @TableField("no_lower_shadow")
    private Boolean noLowerShadow;
    @TableField("volume_shrinking")
    private Boolean volumeShrinking;
    @TableField("volume_ratio")
    private BigDecimal volumeRatio;
    @TableField("lower_shadow_pct")
    private BigDecimal lowerShadowPct;
    @TableField("signal_score")
    private Integer signalScore;
    @TableField("default_matched")
    private Boolean defaultMatched;
    @TableField("bearish_start_date")
    private LocalDate bearishStartDate;
    @TableField("previous_date")
    private LocalDate previousDate;
    @TableField("signal_date")
    private LocalDate signalDate;
    @TableField("previous_open")
    private BigDecimal previousOpen;
    @TableField("previous_close")
    private BigDecimal previousClose;
    @TableField("signal_open")
    private BigDecimal signalOpen;
    @TableField("signal_close")
    private BigDecimal signalClose;
    @TableField("signal_low")
    private BigDecimal signalLow;
    @TableField("previous_volume")
    private Long previousVolume;
    @TableField("signal_volume")
    private Long signalVolume;
    @TableField("yin_yang_double_bear_matched")
    private Boolean yinYangDoubleBearMatched;
    @TableField("yin_yang_penetration_pct")
    private BigDecimal yinYangPenetrationPct;
    @TableField("yin_yang_type")
    private String yinYangType;
    @TableField("yin_yang_score")
    private Integer yinYangScore;
    @TableField("first_board_high_bear_matched")
    private Boolean firstBoardHighBearMatched;
    @TableField("first_board_buy_point")
    private String firstBoardBuyPoint;
    @TableField("first_board_score")
    private Integer firstBoardScore;
    @TableField("first_board_date")
    private LocalDate firstBoardDate;
    @TableField("first_board_low")
    private BigDecimal firstBoardLow;
    @TableField("high_bear_date")
    private LocalDate highBearDate;
    @TableField("high_bear_high")
    private BigDecimal highBearHigh;
    @TableField("high_bear_volume_ratio")
    private BigDecimal highBearVolumeRatio;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
