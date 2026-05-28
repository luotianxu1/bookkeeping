package com.example.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.finance.dto.CategoryRequest;
import com.example.finance.dto.CategoryResponse;
import com.example.finance.entity.CategoryEntity;
import com.example.finance.mapper.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private static final String DEFAULT_STATUS = "active";
    private static final int ROOT_LEVEL = 1;
    private static final int CHILD_LEVEL = 2;

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
            .eq(StringUtils.hasText(status), CategoryEntity::getStatus, status);

        List<CategoryEntity> entities = categoryMapper.selectList(wrapper);
        Set<Long> rootIds = entities.stream()
            .filter(entity -> entity.getParentId() == null)
            .map(CategoryEntity::getId)
            .collect(Collectors.toSet());
        Comparator<CategoryEntity> comparator = Comparator
            .comparing(CategoryEntity::getType, Comparator.nullsLast(String::compareTo))
            .thenComparing(entity -> entity.getSortOrder() == null ? 0 : entity.getSortOrder())
            .thenComparing(entity -> entity.getId() == null ? 0L : entity.getId());

        List<CategoryEntity> roots = entities.stream()
            .filter(entity -> entity.getParentId() == null)
            .sorted(comparator)
            .toList();
        List<CategoryEntity> ordered = new ArrayList<>();
        for (CategoryEntity root : roots) {
            ordered.add(root);
            ordered.addAll(entities.stream()
                .filter(entity -> root.getId() != null && root.getId().equals(entity.getParentId()))
                .sorted(comparator)
                .toList());
        }
        ordered.addAll(entities.stream()
            .filter(entity -> entity.getParentId() != null && !rootIds.contains(entity.getParentId()))
            .sorted(comparator)
            .toList());

        return ordered.stream()
            .map(this::toResponse)
            .toList();
    }

    public Optional<CategoryResponse> getById(Long id) {
        return Optional.ofNullable(categoryMapper.selectById(id)).map(this::toResponse);
    }

    public CategoryResponse create(CategoryRequest request) {
        CategoryEntity parent = validateParent(request.getUserId(), request.getType(), request.getParentId(), null);
        validateNameUnique(request.getUserId(), request.getType(), request.getParentId(), request.getName(), null);

        CategoryEntity entity = new CategoryEntity();
        fillEntity(entity, request, parent);
        categoryMapper.insert(entity);

        return toResponse(categoryMapper.selectById(entity.getId()));
    }

    public Optional<CategoryResponse> update(Long id, CategoryRequest request) {
        CategoryEntity entity = categoryMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }

        if (request.getParentId() != null && hasChildren(id)) {
            throw new IllegalArgumentException("含有二级分类的一级分类不能调整为二级分类");
        }

        CategoryEntity parent = validateParent(request.getUserId(), request.getType(), request.getParentId(), id);
        validateNameUnique(request.getUserId(), request.getType(), request.getParentId(), request.getName(), id);
        fillEntity(entity, request, parent);
        categoryMapper.updateById(entity);

        return Optional.of(toResponse(categoryMapper.selectById(id)));
    }

    public boolean delete(Long id) {
        if (hasChildren(id)) {
            throw new IllegalArgumentException("请先删除二级分类");
        }
        return categoryMapper.deleteById(id) > 0;
    }

    private void validateNameUnique(Long userId, String type, Long parentId, String name, Long ignoredId) {
        LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<CategoryEntity>()
            .eq(CategoryEntity::getType, type)
            .eq(CategoryEntity::getName, name)
            .ne(ignoredId != null, CategoryEntity::getId, ignoredId)
            .last("LIMIT 1");

        if (parentId == null) {
            wrapper.isNull(CategoryEntity::getParentId);
        } else {
            wrapper.eq(CategoryEntity::getParentId, parentId);
        }

        if (userId == null) {
            wrapper.isNull(CategoryEntity::getUserId);
        } else {
            wrapper.eq(CategoryEntity::getUserId, userId);
        }

        if (categoryMapper.selectOne(wrapper) != null) {
            throw new IllegalArgumentException("分类名称已存在");
        }
    }

    private CategoryEntity validateParent(Long userId, String type, Long parentId, Long currentId) {
        if (parentId == null) {
            return null;
        }
        if (currentId != null && currentId.equals(parentId)) {
            throw new IllegalArgumentException("上级分类不能选择自己");
        }

        CategoryEntity parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("上级分类不存在");
        }
        if (!DEFAULT_STATUS.equals(parent.getStatus())) {
            throw new IllegalArgumentException("上级分类不可用");
        }
        if (!type.equals(parent.getType())) {
            throw new IllegalArgumentException("二级分类必须和上级分类保持同一类型");
        }
        if (parent.getParentId() != null || Integer.valueOf(CHILD_LEVEL).equals(parent.getLevel())) {
            throw new IllegalArgumentException("当前仅支持两级分类");
        }
        if (parent.getUserId() != null && !parent.getUserId().equals(userId)) {
            throw new IllegalArgumentException("上级分类不存在");
        }
        return parent;
    }

    private boolean hasChildren(Long id) {
        return categoryMapper.selectCount(new LambdaQueryWrapper<CategoryEntity>()
            .eq(CategoryEntity::getParentId, id)) > 0;
    }

    private void fillEntity(CategoryEntity entity, CategoryRequest request, CategoryEntity parent) {
        entity.setUserId(request.getUserId());
        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setIcon(request.getIcon());
        entity.setColor(request.getColor());
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setLevel(parent == null ? ROOT_LEVEL : CHILD_LEVEL);
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
        response.setParentId(entity.getParentId());
        response.setLevel(entity.getLevel() == null ? (entity.getParentId() == null ? ROOT_LEVEL : CHILD_LEVEL) : entity.getLevel());
        response.setSystem(entity.getSystem());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
