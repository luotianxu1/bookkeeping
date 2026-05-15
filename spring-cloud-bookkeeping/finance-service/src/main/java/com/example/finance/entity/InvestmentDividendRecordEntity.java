package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("investment_dividend_records")
public class InvestmentDividendRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("account_id")
    private Long accountId;

    @TableField("position_id")
    private Long positionId;

    @TableField("product_id")
    private Long productId;

    @TableField("plan_id")
    private Long planId;

    @TableField("dividend_type")
    private String dividendType;

    @TableField("holding_quantity")
    private BigDecimal holdingQuantity;

    @TableField("dividend_per_unit")
    private BigDecimal dividendPerUnit;

    @TableField("gross_amount")
    private BigDecimal grossAmount;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("reinvest_quantity")
    private BigDecimal reinvestQuantity;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    private String status;
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
