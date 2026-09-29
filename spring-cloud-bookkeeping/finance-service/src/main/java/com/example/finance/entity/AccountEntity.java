package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
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

    @TableField(value = "contact_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long contactId;

    private String name;
    private String icon;
    private String color;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("current_balance")
    private BigDecimal currentBalance;

    @TableField(value = "loan_total_amount", updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal loanTotalAmount;

    @TableField(value = "loan_interest_amount", updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal loanInterestAmount;

    @TableField(value = "loan_interest_rate", updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal loanInterestRate;

    @TableField(value = "loan_total_periods", updateStrategy = FieldStrategy.ALWAYS)
    private Integer loanTotalPeriods;

    @TableField(value = "loan_repayment_day", updateStrategy = FieldStrategy.ALWAYS)
    private Integer loanRepaymentDay;

    @TableField(value = "loan_start_date", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate loanStartDate;

    @TableField(value = "loan_settled_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime loanSettledAt;

    @TableField("include_in_net_worth")
    private Boolean includeInNetWorth;

    @TableField("sort_order")
    private Integer sortOrder;

    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
