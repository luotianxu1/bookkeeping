package com.example.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("investment_transactions")
public class InvestmentTransactionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("transaction_no")
    private String transactionNo;

    @TableField("user_id")
    private Long userId;

    @TableField("account_id")
    private Long accountId;

    @TableField("position_id")
    private Long positionId;

    @TableField("product_id")
    private Long productId;

    @TableField("trade_type")
    private String tradeType;

    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal amount;

    @TableField("fee_amount")
    private BigDecimal feeAmount;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @TableField("currency_code")
    private String currencyCode;

    @TableField("trade_at")
    private LocalDateTime tradeAt;

    private String status;
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
