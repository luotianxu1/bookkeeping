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
@TableName("accounts")
public class AccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("account_type_id")
    private Long accountTypeId;

    @TableField("contact_id")
    private Long contactId;

    private String name;
    private String icon;
    private String color;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("current_balance")
    private BigDecimal currentBalance;

    @TableField("loan_total_amount")
    private BigDecimal loanTotalAmount;

    @TableField("loan_interest_amount")
    private BigDecimal loanInterestAmount;

    @TableField("loan_interest_rate")
    private BigDecimal loanInterestRate;

    @TableField("loan_total_periods")
    private Integer loanTotalPeriods;

    @TableField("loan_repayment_day")
    private Integer loanRepaymentDay;

    @TableField("loan_start_date")
    private LocalDate loanStartDate;

    @TableField("loan_settled_at")
    private LocalDateTime loanSettledAt;

    @TableField("include_in_net_worth")
    private Boolean includeInNetWorth;

    @TableField("sort_order")
    private Integer sortOrder;

    private String status;
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
