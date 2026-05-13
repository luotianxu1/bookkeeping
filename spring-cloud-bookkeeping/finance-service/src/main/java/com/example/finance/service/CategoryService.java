package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.CategoryRequest;
import com.example.finance.dto.CategoryResponse;
import com.example.finance.entity.CategoryEntity;
import com.example.finance.mapper.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {

    private static final String DEFAULT_STATUS = "active";

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<CategoryResponse> list(Long userId, String type, String status) {
        LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<CategoryEntity>()
            .and(userId != null, query -> query
                .isNull(CategoryEntity::getUserId)
                .or()
                .eq(CategoryEntity::getUserId, userId)
            )
            .eq(StringUtils.hasText(type), CategoryEntity::getType, type)
            .eq(StringUtils.hasText(status), CategoryEntity::getStatus, status)
            .orderByAsc(CategoryEntity::getType)
            .orderByAsc(CategoryEntity::getSortOrder)
            .orderByAsc(CategoryEntity::getId);

        return categoryMapper.selectList(wrapper).stream()
            .map(this::toResponse)
            .toList();
    }

    public Optional<CategoryResponse> getById(Long id) {
        return Optional.ofNullable(categoryMapper.selectById(id)).map(this::toResponse);
    }

    public CategoryResponse create(CategoryRequest request) {
        validateNameUnique(request.getUserId(), request.getType(), request.getName(), null);

        CategoryEntity entity = new CategoryEntity();
        fillEntity(entity, request);
        categoryMapper.insert(entity);

        return toResponse(categoryMapper.selectById(entity.getId()));
    }

    public Optional<CategoryResponse> update(Long id, CategoryRequest request) {
        CategoryEntity entity = categoryMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }

        validateNameUnique(request.getUserId(), request.getType(), request.getName(), id);
        fillEntity(entity, request);
        categoryMapper.updateById(entity);

        return Optional.of(toResponse(categoryMapper.selectById(id)));
    }

    public boolean delete(Long id) {
        return categoryMapper.deleteById(id) > 0;
    }

    private void validateNameUnique(Long userId, String type, String name, Long ignoredId) {
        LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<CategoryEntity>()
            .eq(CategoryEntity::getType, type)
            .eq(CategoryEntity::getName, name)
            .ne(ignoredId != null, CategoryEntity::getId, ignoredId)
            .last("LIMIT 1");

        if (userId == null) {
            wrapper.isNull(CategoryEntity::getUserId);
        } else {
            wrapper.eq(CategoryEntity::getUserId, userId);
        }

        if (categoryMapper.selectOne(wrapper) != null) {
            throw new IllegalArgumentException("分类名称已存在");
        }
    }

    private void fillEntity(CategoryEntity entity, CategoryRequest request) {
        entity.setUserId(request.getUserId());
        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setIcon(request.getIcon());
        entity.setColor(request.getColor());
        entity.setSystem(request.getSystem() != null ? request.getSystem() : Boolean.FALSE);
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        entity.setRemark(request.getRemark());
    }

    private CategoryResponse toResponse(CategoryEntity entity) {
        CategoryResponse response = new CategoryResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setName(entity.getName());
        response.setType(entity.getType());
        response.setIcon(entity.getIcon());
        response.setColor(entity.getColor());
        response.setSystem(entity.getSystem());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
