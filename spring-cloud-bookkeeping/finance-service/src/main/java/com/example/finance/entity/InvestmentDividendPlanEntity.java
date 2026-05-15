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
@TableName("investment_dividend_plans")
public class InvestmentDividendPlanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Long productId;

    @TableField("dividend_year")
    private Integer dividendYear;

    @TableField("ex_dividend_date")
    private LocalDate exDividendDate;

    @TableField("record_date")
    private LocalDate recordDate;

    @TableField("pay_date")
    private LocalDate payDate;

    @TableField("dividend_per_unit")
    private BigDecimal dividendPerUnit;

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("currency_code")
    private String currencyCode;

    private String status;
    private String source;
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
