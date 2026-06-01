package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("liability_records")
public class LiabilityRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("account_id")
    private Long accountId;

    private BigDecimal amount;

    @TableField("installment_total_periods")
    private Integer installmentTotalPeriods;

    @TableField("installment_current_period")
    private Integer installmentCurrentPeriod;

    @TableField("repayment_status")
    private String repaymentStatus;

    @TableField("repayment_type")
    private String repaymentType;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("currency_code")
    private String currencyCode;

    private String remark;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
