package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FoodCategoryResponse {

    private Long id;
    private Long userId;
    private String categoryType;
    private String name;
    private String iconText;
    private String iconTone;
    private String description;
    private Integer sortOrder;
    private String status;
    private Integer itemCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
