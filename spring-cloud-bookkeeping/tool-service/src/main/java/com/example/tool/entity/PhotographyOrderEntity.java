package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("photography_orders")
public class PhotographyOrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("user_id")
    private Long userId;

    @TableField("customer_name")
    private String customerName;

    @TableField("contact_info")
    private String contactInfo;

    @TableField("order_type")
    private String orderType;

    @TableField("shoot_at")
    private LocalDateTime shootAt;

    private String status;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("deposit_amount")
    private BigDecimal depositAmount;

    @TableField("final_amount")
    private BigDecimal finalAmount;

    @TableField("deposit_account_id")
    private Long depositAccountId;

    @TableField("deposit_transaction_id")
    private Long depositTransactionId;

    @TableField("deposit_received_at")
    private LocalDateTime depositReceivedAt;

    @TableField("final_account_id")
    private Long finalAccountId;

    @TableField("final_transaction_id")
    private Long finalTransactionId;

    @TableField("final_received_at")
    private LocalDateTime finalReceivedAt;

    private String address;
    private String remark;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
