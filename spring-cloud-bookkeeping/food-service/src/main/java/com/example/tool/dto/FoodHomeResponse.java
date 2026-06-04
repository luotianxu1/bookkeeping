package com.example.tool.dto;

import lombok.Data;

import java.util.List;

@Data
public class FoodHomeResponse {

    private String heroTitle;
    private List<ManagementCard> managementCards;
    private List<RecentMenu> recentMenus;

    @Data
    public static class ManagementCard {
        private String key;
        private String title;
        private String description;
        private Integer count;
        private String path;
    }

    @Data
    public static class RecentMenu {
        private Long orderId;
        private String title;
        private String summary;
        private String actionLabel;
    }
}
