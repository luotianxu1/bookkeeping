package com.example.app.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("transactions")
public class TransactionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("transaction_no")
    private String transactionNo;

    @TableField("user_id")
    private Long userId;

    private String type;
    private BigDecimal amount;

    @TableField("currency_code")
    private String currencyCode;

    @TableField(value = "account_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long accountId;

    @TableField(value = "category_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;

    @TableField(value = "from_account_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long fromAccountId;

    @TableField(value = "to_account_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long toAccountId;

    private String title;

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
