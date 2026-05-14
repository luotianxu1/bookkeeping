package com.example.finance.entity;

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

    @TableField("account_id")
    private Long accountId;

    @TableField("category_id")
    private Long categoryId;

    @TableField("from_account_id")
    private Long fromAccountId;

    @TableField("to_account_id")
    private Long toAccountId;

    private String title;
    private String remark;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
