package com.example.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AccountSortOrderRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Valid
    @NotEmpty(message = "排序列表不能为空")
    private List<AccountSortOrderItem> items;

    @Data
    public static class AccountSortOrderItem {

        @NotNull(message = "账户ID不能为空")
        private Long id;

        @NotNull(message = "排序值不能为空")
        private Integer sortOrder;
    }
}
