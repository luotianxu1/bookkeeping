package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("food_order_items")
public class FoodOrderItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("dish_id")
    private Long dishId;

    @TableField("dish_name")
    private String dishName;

    @TableField("category_name")
    private String categoryName;

    @TableField("sort_order")
    private Integer sortOrder;
}
