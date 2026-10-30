package com.example.tool.dto;

import lombok.Data;

import java.util.List;

@Data
public class PhotographyOrderOverviewResponse {

    private String view;
    private String anchor;
    private String selectedValue;
    private String title;
    private String subtitle;
    private PhotographyOrderOverviewSummaryResponse summary;
    private List<PhotographyOrderOverviewTrendPointResponse> trendPoints;
    private List<PhotographyOrderOverviewTypeStatResponse> typeStats;
    private List<PhotographyOrderOverviewBucketResponse> buckets;
    private List<PhotographyOrderResponse> orders;
}
