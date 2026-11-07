package com.example.app.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
@Data
@TableName("category_budgets")
public class CategoryBudgetEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private LocalDate budgetMonth;
    private Long categoryId;
    private BigDecimal amount;
    private String currencyCode;
}
