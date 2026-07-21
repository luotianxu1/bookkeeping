package com.example.finance.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class StockScreenPageResponse {
    private StockScreenRunResponse run;
    private Long total = 0L;
    private Integer page = 1;
    private Integer pageSize = 20;
    private List<StockScreenItemResponse> items = Collections.emptyList();
}
