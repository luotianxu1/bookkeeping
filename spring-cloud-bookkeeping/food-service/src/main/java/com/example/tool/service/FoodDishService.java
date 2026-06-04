package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.FoodDishRequest;
import com.example.tool.dto.FoodDishResponse;
import com.example.tool.entity.FoodCategoryEntity;
import com.example.tool.entity.FoodDishEntity;
import com.example.tool.entity.FoodDishIngredientEntity;
import com.example.tool.entity.FoodDishStepEntity;
import com.example.tool.mapper.FoodDishIngredientMapper;
import com.example.tool.mapper.FoodDishMapper;
import com.example.tool.mapper.FoodDishStepMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class FoodDishService {

    private final FoodDishMapper foodDishMapper;
    private final FoodDishIngredientMapper foodDishIngredientMapper;
    private final FoodDishStepMapper foodDishStepMapper;
    private final FoodDomainSupport foodDomainSupport;

    public FoodDishService(
        FoodDishMapper foodDishMapper,
        FoodDishIngredientMapper foodDishIngredientMapper,
        FoodDishStepMapper foodDishStepMapper,
        FoodDomainSupport foodDomainSupport
    ) {
        this.foodDishMapper = foodDishMapper;
        this.foodDishIngredientMapper = foodDishIngredientMapper;
        this.foodDishStepMapper = foodDishStepMapper;
        this.foodDomainSupport = foodDomainSupport;
    }

    public List<FoodDishResponse> listDishes(Long userId, Long categoryId, String status, String keyword) {
        String normalizedStatus = foodDomainSupport.normalizeOptionalDishStatus(status);

        LambdaQueryWrapper<FoodDishEntity> wrapper = new LambdaQueryWrapper<FoodDishEntity>()
            .eq(FoodDishEntity::getUserId, userId)
            .eq(categoryId != null, FoodDishEntity::getCategoryId, categoryId)
            .eq(StringUtils.hasText(normalizedStatus), FoodDishEntity::getStatus, normalizedStatus)
            .and(StringUtils.hasText(keyword), query -> query
                .like(FoodDishEntity::getName, keyword.trim())
                .or()
                .like(FoodDishEntity::getSubtitle, keyword.trim())
                .or()
                .like(FoodDishEntity::getTasteTags, keyword.trim()))
            .orderByAsc(FoodDishEntity::getSortOrder)
            .orderByAsc(FoodDishEntity::getId);

        List<FoodDishEntity> dishes = foodDishMapper.selectList(wrapper);
        Map<Long, FoodCategoryEntity> categoryMap = foodDomainSupport.getCategoryMap(userId);
        Map<Long, List<FoodDishIngredientEntity>> ingredientMap = foodDomainSupport.getDishIngredientMap(dishes.stream().map(FoodDishEntity::getId).toList());

        return dishes.stream()
            .map(dish -> foodDomainSupport.toDishResponse(dish, categoryMap, ingredientMap.getOrDefault(dish.getId(), List.of()), List.of()))
            .toList();
    }

    public Optional<FoodDishResponse> getDishById(Long id) {
        FoodDishEntity entity = foodDishMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }

        Map<Long, FoodCategoryEntity> categoryMap = foodDomainSupport.getCategoryMap(entity.getUserId());
        List<FoodDishIngredientEntity> ingredients = foodDishIngredientMapper.selectList(new LambdaQueryWrapper<FoodDishIngredientEntity>()
            .eq(FoodDishIngredientEntity::getDishId, id)
            .orderByAsc(FoodDishIngredientEntity::getSortOrder)
            .orderByAsc(FoodDishIngredientEntity::getId));
        List<FoodDishStepEntity> steps = foodDishStepMapper.selectList(new LambdaQueryWrapper<FoodDishStepEntity>()
            .eq(FoodDishStepEntity::getDishId, id)
            .orderByAsc(FoodDishStepEntity::getStepNo)
            .orderByAsc(FoodDishStepEntity::getId));

        return Optional.of(foodDomainSupport.toDishResponse(entity, categoryMap, ingredients, steps));
    }

    public FoodDishResponse createDish(FoodDishRequest request) {
        FoodDishEntity entity = new FoodDishEntity();
        foodDomainSupport.fillDishEntity(entity, request);
        foodDishMapper.insert(entity);
        foodDomainSupport.replaceDishChildren(entity.getId(), request);
        return getDishById(entity.getId()).orElseThrow(() -> new IllegalArgumentException("菜品保存失败"));
    }

    public Optional<FoodDishResponse> updateDish(Long id, FoodDishRequest request) {
        FoodDishEntity entity = foodDishMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getUserId(), request.getUserId())) {
            return Optional.empty();
        }

        foodDomainSupport.fillDishEntity(entity, request);
        foodDishMapper.updateById(entity);
        foodDomainSupport.replaceDishChildren(id, request);
        return getDishById(id);
    }
}
