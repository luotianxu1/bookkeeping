package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("investment_positions")
public class InvestmentPositionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("account_id")
    private Long accountId;

    @TableField("product_id")
    private Long productId;

    @TableField("holding_quantity")
    private BigDecimal holdingQuantity;

    @TableField("available_quantity")
    private BigDecimal availableQuantity;

    @TableField("frozen_quantity")
    private BigDecimal frozenQuantity;

    @TableField("cost_amount")
    private BigDecimal costAmount;

    @TableField("avg_cost_price")
    private BigDecimal avgCostPrice;

    @TableField("current_price")
    private BigDecimal currentPrice;

    @TableField("market_value")
    private BigDecimal marketValue;

    @TableField("day_profit")
    private BigDecimal dayProfit;

    @TableField("day_profit_rate")
    private BigDecimal dayProfitRate;

    @TableField("holding_profit")
    private BigDecimal holdingProfit;

    @TableField("holding_profit_rate")
    private BigDecimal holdingProfitRate;

    @TableField("cumulative_profit")
    private BigDecimal cumulativeProfit;

    @TableField("cumulative_profit_rate")
    private BigDecimal cumulativeProfitRate;

    @TableField("include_in_net_worth")
    private Boolean includeInNetWorth;

    private String status;

    @TableField("last_synced_at")
    private LocalDateTime lastSyncedAt;

    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
