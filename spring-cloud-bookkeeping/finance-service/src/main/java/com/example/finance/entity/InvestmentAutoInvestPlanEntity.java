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
@TableName("investment_auto_invest_plans")
public class InvestmentAutoInvestPlanEntity {

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

    @TableField("funding_account_id")
    private Long fundingAccountId;

    private String frequency;
    private BigDecimal amount;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("next_execute_date")
    private LocalDate nextExecuteDate;

    @TableField("last_executed_at")
    private LocalDateTime lastExecutedAt;

    private String status;
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
