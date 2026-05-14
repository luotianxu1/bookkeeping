package com.example.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AccountTypeSortOrderRequest {

    @Valid
    @NotEmpty(message = "排序列表不能为空")
    private List<AccountTypeSortOrderItem> items;

    @Data
    public static class AccountTypeSortOrderItem {

        @NotNull(message = "账户类型ID不能为空")
        private Long id;

        @NotNull(message = "排序值不能为空")
        private Integer sortOrder;
    }
}
