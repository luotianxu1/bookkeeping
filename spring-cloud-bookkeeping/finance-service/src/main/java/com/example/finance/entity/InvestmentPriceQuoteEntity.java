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
@TableName("investment_price_quotes")
public class InvestmentPriceQuoteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Long productId;

    @TableField("quote_date")
    private LocalDate quoteDate;

    @TableField("quote_time")
    private LocalDateTime quoteTime;

    @TableField("open_price")
    private BigDecimal openPrice;

    @TableField("high_price")
    private BigDecimal highPrice;

    @TableField("low_price")
    private BigDecimal lowPrice;

    @TableField("close_price")
    private BigDecimal closePrice;

    @TableField("latest_price")
    private BigDecimal latestPrice;

    @TableField("pre_close_price")
    private BigDecimal preClosePrice;

    @TableField("change_amount")
    private BigDecimal changeAmount;

    @TableField("change_rate")
    private BigDecimal changeRate;

    private String source;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
