package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FoodDishResponse {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String subtitle;
    private String description;
    private List<String> tasteTags;
    private List<String> highlightTags;
    private Integer cookMinutes;
    private Integer servingCount;
    private String coverTone;
    private String coverText;
    private String status;
    private Integer sortOrder;
    private List<String> ingredientPreview;
    private List<IngredientItem> ingredients;
    private List<StepItem> steps;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class IngredientItem {
        private Long id;
        private Long ingredientId;
        private String ingredientName;
        private String amount;
        private Integer sortOrder;
    }

    @Data
    public static class StepItem {
        private Long id;
        private Integer stepNo;
        private String content;
    }
}
