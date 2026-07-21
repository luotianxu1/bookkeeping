package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock_screen_runs")
public class StockScreenRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("trade_date")
    private LocalDate tradeDate;

    @TableField("trigger_name")
    private String triggerName;

    private String status;

    @TableField("total_stocks")
    private Integer totalStocks;

    @TableField("processed_stocks")
    private Integer processedStocks;

    @TableField("matched_stocks")
    private Integer matchedStocks;

    @TableField("failed_stocks")
    private Integer failedStocks;

    @TableField("data_source")
    private String dataSource;

    @TableField("rule_version")
    private String ruleVersion;

    @TableField("result_message")
    private String resultMessage;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
