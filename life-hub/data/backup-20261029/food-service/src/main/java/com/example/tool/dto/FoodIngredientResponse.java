package com.example.tool.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FoodIngredientResponse {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private String name;
    private BigDecimal stockAmount;
    private String unit;
    private BigDecimal reorderLevel;
    private String storageLocation;
    private String status;
    private String note;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
