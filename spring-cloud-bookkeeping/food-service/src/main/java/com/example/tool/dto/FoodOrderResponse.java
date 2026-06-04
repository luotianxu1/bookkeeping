package com.example.tool.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FoodOrderResponse {

    private Long id;
    private Long userId;
    private String title;
    private LocalDate plannedFor;
    private String remark;
    private Integer totalCookMinutes;
    private Integer dishCount;
    private List<String> dishNames;
    private List<DishItem> dishes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class DishItem {
        private Long dishId;
        private String dishName;
        private String categoryName;
    }
}
