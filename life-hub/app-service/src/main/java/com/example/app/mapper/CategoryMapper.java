package com.example.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.app.entity.CategoryEntity;

public interface CategoryMapper extends BaseMapper<CategoryEntity> {
    /** 公共一级分类的私人子分类只影响所属用户的可记账层级。 */
    default long countActiveChildrenForUser(Long parentId, Long userId) {
        return selectCount(new LambdaQueryWrapper<CategoryEntity>()
            .eq(CategoryEntity::getParentId, parentId)
            .eq(CategoryEntity::getStatus, "active")
            .and(query -> query.isNull(CategoryEntity::getUserId)
                .or().eq(CategoryEntity::getUserId, userId)));
    }
}
