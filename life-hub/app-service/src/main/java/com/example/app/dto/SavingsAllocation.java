package com.example.app.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SavingsAllocation(@NotNull @Positive Long goalId,
    @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount) {}
