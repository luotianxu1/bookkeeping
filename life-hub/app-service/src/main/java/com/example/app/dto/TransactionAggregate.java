package com.example.app.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 与分页查询共享过滤条件的记录数及收支汇总。 */
@Data
public class TransactionAggregate {
    private long total;
    private BigDecimal incomeTotal;
    private BigDecimal expenseTotal;
}
