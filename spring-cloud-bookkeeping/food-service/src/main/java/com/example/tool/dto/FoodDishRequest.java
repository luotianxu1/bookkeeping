package com.example.tool.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FoodDishRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "菜品分类不能为空")
    private Long categoryId;

    @NotBlank(message = "菜品名称不能为空")
    @Size(max = 120, message = "菜品名称不能超过120个字符")
    private String name;

    @Size(max = 160, message = "副标题不能超过160个字符")
    private String subtitle;

    @Size(max = 800, message = "菜品介绍不能超过800个字符")
    private String description;

    private List<@Size(max = 32, message = "口味标签不能超过32个字符") String> tasteTags;
    private List<@Size(max = 32, message = "亮点标签不能超过32个字符") String> highlightTags;

    @NotNull(message = "预计时间不能为空")
    @Min(value = 1, message = "预计时间至少为1分钟")
    private Integer cookMinutes;

    @NotNull(message = "适合份量不能为空")
    @Min(value = 1, message = "适合份量至少为1人")
    private Integer servingCount;

    @NotBlank(message = "封面色系不能为空")
    @Size(max = 32, message = "封面色系不能超过32个字符")
    private String coverTone;

    @NotBlank(message = "封面文案不能为空")
    @Size(max = 32, message = "封面文案不能超过32个字符")
    private String coverText;

    private String status;
    private Integer sortOrder;

    @Valid
    @NotEmpty(message = "请至少填写一项食材")
    private List<IngredientItem> ingredients;

    @Valid
    @NotEmpty(message = "请至少填写一个步骤")
    private List<StepItem> steps;

    @Data
    public static class IngredientItem {
        private Long ingredientId;

        @NotBlank(message = "食材名称不能为空")
        @Size(max = 80, message = "食材名称不能超过80个字符")
        private String ingredientName;

        @NotBlank(message = "食材用量不能为空")
        @Size(max = 40, message = "食材用量不能超过40个字符")
        private String amount;
    }

    @Data
    public static class StepItem {
        @NotBlank(message = "步骤内容不能为空")
        @Size(max = 500, message = "步骤内容不能超过500个字符")
        private String content;
    }
}
