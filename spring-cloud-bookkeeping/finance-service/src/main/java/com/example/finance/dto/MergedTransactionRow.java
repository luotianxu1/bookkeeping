package com.example.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 流水聚合查询（transactions + debt_records + human_relation_records UNION ALL）的宽行。
 * 仅用于分页列表：排序、过滤、分页均在数据库完成，账户/分类名称由服务层对当页数据批量补齐。
 */
@Data
public class MergedTransactionRow {

    /** 来源表主键 */
    private Long sourceId;
    /** transaction / debt_record / human_relation_record */
    private String sourceType;
    private Long userId;
    /** 已在 SQL 中映射为统一的 expense/income/transfer */
    private String type;
    private BigDecimal amount;
    private String currencyCode;
    /** 展示账户：transaction = account_id；debt/human = COALESCE(funding_account_id, account_id) */
    private Long displayAccountId;
    /** 原始业务账户：transaction = account_id；debt/human = account_id（债务/人情账户，用于构建标题） */
    private Long originAccountId;
    /** 资金账户（仅 debt/human） */
    private Long fundingAccountId;
    private Long categoryId;
    /** 仅 transaction 分支有值 */
    private String transactionNo;
    /** 仅 transaction 分支有值；debt/human 的标题由服务层构建 */
    private String title;
    private String remark;
    /** 仅 debt/human 分支有值 */
    private String direction;
    /** 仅 debt 分支有值：borrow / repayment */
    private String recordType;
    /** 仅 transfer 流水有值 */
    private Long fromAccountId;
    private Long toAccountId;
    private LocalDateTime occurredAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
