package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.FoodIngredientRequest;
import com.example.tool.dto.FoodIngredientResponse;
import com.example.tool.entity.FoodCategoryEntity;
import com.example.tool.entity.FoodIngredientEntity;
import com.example.tool.mapper.FoodIngredientMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class FoodIngredientService {

    private final FoodIngredientMapper foodIngredientMapper;
    private final FoodDomainSupport foodDomainSupport;

    public FoodIngredientService(FoodIngredientMapper foodIngredientMapper, FoodDomainSupport foodDomainSupport) {
        this.foodIngredientMapper = foodIngredientMapper;
        this.foodDomainSupport = foodDomainSupport;
    }

    public List<FoodIngredientResponse> listIngredients(Long userId, Long categoryId, String status, String keyword) {
        String normalizedStatus = foodDomainSupport.normalizeOptionalIngredientStatus(status);

        LambdaQueryWrapper<FoodIngredientEntity> wrapper = new LambdaQueryWrapper<FoodIngredientEntity>()
            .eq(FoodIngredientEntity::getUserId, userId)
            .eq(categoryId != null, FoodIngredientEntity::getCategoryId, categoryId)
            .eq(StringUtils.hasText(normalizedStatus), FoodIngredientEntity::getStatus, normalizedStatus)
            .and(StringUtils.hasText(keyword), query -> query
                .like(FoodIngredientEntity::getName, keyword.trim())
                .or()
                .like(FoodIngredientEntity::getNote, keyword.trim())
                .or()
                .like(FoodIngredientEntity::getStorageLocation, keyword.trim()))
            .orderByAsc(FoodIngredientEntity::getSortOrder)
            .orderByAsc(FoodIngredientEntity::getId);

        List<FoodIngredientEntity> ingredients = foodIngredientMapper.selectList(wrapper);
        Map<Long, FoodCategoryEntity> categoryMap = foodDomainSupport.getCategoryMap(userId);

        return ingredients.stream()
            .map(ingredient -> foodDomainSupport.toIngredientResponse(ingredient, categoryMap))
            .toList();
    }

    public FoodIngredientResponse createIngredient(FoodIngredientRequest request) {
        FoodIngredientEntity entity = new FoodIngredientEntity();
        foodDomainSupport.fillIngredientEntity(entity, request);
        foodIngredientMapper.insert(entity);

        FoodIngredientEntity saved = foodIngredientMapper.selectById(entity.getId());
        Map<Long, FoodCategoryEntity> categoryMap = foodDomainSupport.getCategoryMap(saved.getUserId());
        return foodDomainSupport.toIngredientResponse(saved, categoryMap);
    }

    public Optional<FoodIngredientResponse> updateIngredient(Long id, FoodIngredientRequest request) {
        FoodIngredientEntity entity = foodIngredientMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getUserId(), request.getUserId())) {
            return Optional.empty();
        }

        foodDomainSupport.fillIngredientEntity(entity, request);
        foodIngredientMapper.updateById(entity);

        FoodIngredientEntity saved = foodIngredientMapper.selectById(id);
        Map<Long, FoodCategoryEntity> categoryMap = foodDomainSupport.getCategoryMap(saved.getUserId());
        return Optional.of(foodDomainSupport.toIngredientResponse(saved, categoryMap));
    }

    public boolean deleteIngredient(Long id) {
        FoodIngredientEntity entity = foodIngredientMapper.selectById(id);
        if (entity == null) {
            return false;
        }

        foodDomainSupport.validateIngredientCanDelete(entity);
        foodIngredientMapper.deleteById(id);
        return true;
    }
}
