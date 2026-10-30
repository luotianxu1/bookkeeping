package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("food_dish_ingredients")
public class FoodDishIngredientEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("dish_id")
    private Long dishId;

    @TableField("ingredient_id")
    private Long ingredientId;

    @TableField("ingredient_name")
    private String ingredientName;

    private String amount;

    @TableField("sort_order")
    private Integer sortOrder;
}
