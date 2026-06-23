package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("debt_records")
public class DebtRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("account_id")
    private Long accountId;

    @TableField("funding_account_id")
    private Long fundingAccountId;

    private String direction;

    @TableField("record_type")
    private String recordType;

    private BigDecimal amount;

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
