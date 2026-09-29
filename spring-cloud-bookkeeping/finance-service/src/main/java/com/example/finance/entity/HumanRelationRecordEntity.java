package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("human_relation_records")
public class HumanRelationRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("account_id")
    private Long accountId;

    @TableField(value = "funding_account_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long fundingAccountId;

    private String direction;
    private BigDecimal amount;

    @TableField("currency_code")
    private String currencyCode;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
