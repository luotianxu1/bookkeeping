package com.example.app.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record CategoryBudgetRequest(@NotNull LocalDate budgetMonth, @NotNull Long categoryId,
    @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount) {}
