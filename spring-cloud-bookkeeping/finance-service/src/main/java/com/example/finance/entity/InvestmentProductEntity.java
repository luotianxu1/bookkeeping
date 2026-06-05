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
@TableName("investment_products")
public class InvestmentProductEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("product_type")
    private String productType;

    private String market;

    @TableField("exchange_code")
    private String exchangeCode;

    private String symbol;
    private String name;

    @TableField("short_name")
    private String shortName;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("unit_name")
    private String unitName;

    @TableField("price_precision")
    private Integer pricePrecision;

    @TableField("is_stable_dividend")
    private Boolean stableDividend;

    @TableField("predicted_annual_dividend_per_unit")
    private BigDecimal predictedAnnualDividendPerUnit;

    @TableField("dividend_stable_years")
    private Integer dividendStableYears;

    @TableField("dividend_last_paid_date")
    private LocalDate dividendLastPaidDate;

    @TableField("dividend_data_source")
    private String dividendDataSource;

    @TableField("dividend_evaluated_at")
    private LocalDateTime dividendEvaluatedAt;

    private String status;
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
