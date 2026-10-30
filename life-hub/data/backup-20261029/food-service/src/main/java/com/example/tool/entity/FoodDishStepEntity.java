package com.example.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("food_dish_steps")
public class FoodDishStepEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("dish_id")
    private Long dishId;

    @TableField("step_no")
    private Integer stepNo;

    private String content;
}
