package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.FoodCategoryRequest;
import com.example.tool.dto.FoodCategoryResponse;
import com.example.tool.entity.FoodCategoryEntity;
import com.example.tool.mapper.FoodCategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class FoodCategoryService {

    private final FoodCategoryMapper foodCategoryMapper;
    private final FoodDomainSupport foodDomainSupport;

    public FoodCategoryService(FoodCategoryMapper foodCategoryMapper, FoodDomainSupport foodDomainSupport) {
        this.foodCategoryMapper = foodCategoryMapper;
        this.foodDomainSupport = foodDomainSupport;
    }

    public List<FoodCategoryResponse> listCategories(Long userId, String categoryType, String keyword, String status) {
        String normalizedCategoryType = foodDomainSupport.normalizeCategoryType(categoryType, false);
        String normalizedStatus = foodDomainSupport.normalizeCategoryStatus(status);

        LambdaQueryWrapper<FoodCategoryEntity> wrapper = new LambdaQueryWrapper<FoodCategoryEntity>()
            .eq(FoodCategoryEntity::getUserId, userId)
            .eq(StringUtils.hasText(normalizedCategoryType), FoodCategoryEntity::getCategoryType, normalizedCategoryType)
            .eq(StringUtils.hasText(normalizedStatus), FoodCategoryEntity::getStatus, normalizedStatus)
            .and(StringUtils.hasText(keyword), query -> query
                .like(FoodCategoryEntity::getName, keyword.trim())
                .or()
                .like(FoodCategoryEntity::getDescription, keyword.trim()))
            .orderByAsc(FoodCategoryEntity::getSortOrder)
            .orderByAsc(FoodCategoryEntity::getId);

        List<FoodCategoryEntity> categories = foodCategoryMapper.selectList(wrapper);
        Map<Long, Integer> itemCountMap = foodDomainSupport.buildCategoryCountMap(userId, normalizedCategoryType);

        return categories.stream()
            .map(category -> foodDomainSupport.toCategoryResponse(category, itemCountMap.getOrDefault(category.getId(), 0)))
            .toList();
    }

    public FoodCategoryResponse createCategory(FoodCategoryRequest request) {
        FoodCategoryEntity entity = new FoodCategoryEntity();
        foodDomainSupport.fillCategoryEntity(entity, request);
        foodCategoryMapper.insert(entity);
        FoodCategoryEntity saved = foodCategoryMapper.selectById(entity.getId());
        return foodDomainSupport.toCategoryResponse(saved, 0);
    }

    public Optional<FoodCategoryResponse> updateCategory(Long id, FoodCategoryRequest request) {
        FoodCategoryEntity entity = foodCategoryMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getUserId(), request.getUserId())) {
            return Optional.empty();
        }

        foodDomainSupport.fillCategoryEntity(entity, request);
        foodCategoryMapper.updateById(entity);

        FoodCategoryEntity saved = foodCategoryMapper.selectById(id);
        Integer itemCount = foodDomainSupport.buildCategoryCountMap(saved.getUserId(), saved.getCategoryType()).getOrDefault(saved.getId(), 0);
        return Optional.of(foodDomainSupport.toCategoryResponse(saved, itemCount));
    }

    public boolean deleteCategory(Long id) {
        FoodCategoryEntity entity = foodCategoryMapper.selectById(id);
        if (entity == null) {
            return false;
        }

        foodDomainSupport.validateCategoryCanDelete(entity);
        foodCategoryMapper.deleteById(id);
        return true;
    }
}
