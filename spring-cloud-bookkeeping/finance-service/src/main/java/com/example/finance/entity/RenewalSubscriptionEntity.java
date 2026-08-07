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
@TableName("renewal_subscriptions")
public class RenewalSubscriptionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String name;

    @TableField("provider_name")
    private String providerName;

    private BigDecimal amount;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("funding_account_id")
    private Long fundingAccountId;

    @TableField("category_id")
    private Long categoryId;

    @TableField("billing_day")
    private Integer billingDay;

    @TableField("billing_cycle")
    private String billingCycle;

    @TableField("next_billing_date")
    private LocalDate nextBillingDate;

    @TableField("last_charged_at")
    private LocalDateTime lastChargedAt;

    @TableField("last_transaction_id")
    private Long lastTransactionId;

    @TableField("last_charge_status")
    private String lastChargeStatus;

    @TableField("last_charge_message")
    private String lastChargeMessage;

    private String status;
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
