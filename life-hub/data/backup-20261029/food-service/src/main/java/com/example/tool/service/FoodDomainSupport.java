package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.FoodCategoryRequest;
import com.example.tool.dto.FoodCategoryResponse;
import com.example.tool.dto.FoodDishRequest;
import com.example.tool.dto.FoodDishResponse;
import com.example.tool.dto.FoodIngredientRequest;
import com.example.tool.dto.FoodIngredientResponse;
import com.example.tool.dto.FoodOrderResponse;
import com.example.tool.entity.FoodCategoryEntity;
import com.example.tool.entity.FoodDishEntity;
import com.example.tool.entity.FoodDishIngredientEntity;
import com.example.tool.entity.FoodDishStepEntity;
import com.example.tool.entity.FoodIngredientEntity;
import com.example.tool.entity.FoodOrderEntity;
import com.example.tool.entity.FoodOrderItemEntity;
import com.example.tool.mapper.FoodCategoryMapper;
import com.example.tool.mapper.FoodDishIngredientMapper;
import com.example.tool.mapper.FoodDishMapper;
import com.example.tool.mapper.FoodDishStepMapper;
import com.example.tool.mapper.FoodIngredientMapper;
import com.example.tool.mapper.FoodOrderItemMapper;
import com.example.tool.mapper.FoodOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FoodDomainSupport {

    private static final String CATEGORY_TYPE_DISH = "dish";
    private static final String CATEGORY_TYPE_INGREDIENT = "ingredient";
    private static final String DEFAULT_INGREDIENT_UNIT = "项";
    private static final BigDecimal DEFAULT_INGREDIENT_AMOUNT = BigDecimal.ZERO;
    private static final String DEFAULT_INGREDIENT_STATUS = "enough";
    private static final Set<String> VALID_CATEGORY_TYPES = Set.of(CATEGORY_TYPE_DISH, CATEGORY_TYPE_INGREDIENT);
    private static final Set<String> VALID_CATEGORY_STATUSES = Set.of("active", "archived");
    private static final Set<String> VALID_DISH_STATUSES = Set.of("published", "pending", "draft");
    private static final Set<String> VALID_INGREDIENT_STATUSES = Set.of("enough", "low", "urgent");
    private final FoodCategoryMapper foodCategoryMapper;
    private final FoodIngredientMapper foodIngredientMapper;
    private final FoodDishMapper foodDishMapper;
    private final FoodDishIngredientMapper foodDishIngredientMapper;
    private final FoodDishStepMapper foodDishStepMapper;
    private final FoodOrderMapper foodOrderMapper;
    private final FoodOrderItemMapper foodOrderItemMapper;

    public FoodDomainSupport(
        FoodCategoryMapper foodCategoryMapper,
        FoodIngredientMapper foodIngredientMapper,
        FoodDishMapper foodDishMapper,
        FoodDishIngredientMapper foodDishIngredientMapper,
        FoodDishStepMapper foodDishStepMapper,
        FoodOrderMapper foodOrderMapper,
        FoodOrderItemMapper foodOrderItemMapper
    ) {
        this.foodCategoryMapper = foodCategoryMapper;
        this.foodIngredientMapper = foodIngredientMapper;
        this.foodDishMapper = foodDishMapper;
        this.foodDishIngredientMapper = foodDishIngredientMapper;
        this.foodDishStepMapper = foodDishStepMapper;
        this.foodOrderMapper = foodOrderMapper;
        this.foodOrderItemMapper = foodOrderItemMapper;
    }

    public String normalizeCategoryType(String categoryType, boolean required) {
        if (!StringUtils.hasText(categoryType)) {
            if (required) {
                throw new IllegalArgumentException("分类类型不能为空");
            }
            return null;
        }

        String normalized = categoryType.trim().toLowerCase(Locale.ROOT);
        if (!VALID_CATEGORY_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("分类类型不支持");
        }
        return normalized;
    }

    public String normalizeCategoryStatus(String status) {
        return normalizeStatus(status, VALID_CATEGORY_STATUSES, "分类状态不正确", "active");
    }

    public String normalizeIngredientStatus(String status) {
        return normalizeStatus(status, VALID_INGREDIENT_STATUSES, "食材状态不正确", DEFAULT_INGREDIENT_STATUS);
    }

    public String normalizeOptionalIngredientStatus(String status) {
        return normalizeOptionalStatus(status, VALID_INGREDIENT_STATUSES, "食材状态不正确");
    }

    public String normalizeDishStatus(String status) {
        return normalizeStatus(status, VALID_DISH_STATUSES, "菜品状态不正确", "published");
    }

    public String normalizeOptionalDishStatus(String status) {
        return normalizeOptionalStatus(status, VALID_DISH_STATUSES, "菜品状态不正确");
    }

    public Map<Long, Integer> buildCategoryCountMap(Long userId, String categoryType) {
        if (CATEGORY_TYPE_INGREDIENT.equals(categoryType)) {
            return foodIngredientMapper.selectList(new LambdaQueryWrapper<FoodIngredientEntity>()
                    .eq(FoodIngredientEntity::getUserId, userId))
                .stream()
                .collect(Collectors.groupingBy(FoodIngredientEntity::getCategoryId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
        }

        return foodDishMapper.selectList(new LambdaQueryWrapper<FoodDishEntity>()
                .eq(FoodDishEntity::getUserId, userId)
                .eq(FoodDishEntity::getStatus, "published"))
            .stream()
            .collect(Collectors.groupingBy(FoodDishEntity::getCategoryId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    public Map<Long, FoodCategoryEntity> getCategoryMap(Long userId) {
        return foodCategoryMapper.selectList(new LambdaQueryWrapper<FoodCategoryEntity>()
                .eq(FoodCategoryEntity::getUserId, userId))
            .stream()
            .collect(Collectors.toMap(FoodCategoryEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    public Map<Long, List<FoodDishIngredientEntity>> getDishIngredientMap(List<Long> dishIds) {
        if (dishIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return foodDishIngredientMapper.selectList(new LambdaQueryWrapper<FoodDishIngredientEntity>()
                .in(FoodDishIngredientEntity::getDishId, dishIds)
                .orderByAsc(FoodDishIngredientEntity::getSortOrder)
                .orderByAsc(FoodDishIngredientEntity::getId))
            .stream()
            .collect(Collectors.groupingBy(FoodDishIngredientEntity::getDishId, LinkedHashMap::new, Collectors.toList()));
    }

    public Map<Long, List<FoodOrderItemEntity>> getOrderItemMap(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return foodOrderItemMapper.selectList(new LambdaQueryWrapper<FoodOrderItemEntity>()
                .in(FoodOrderItemEntity::getOrderId, orderIds)
                .orderByAsc(FoodOrderItemEntity::getSortOrder)
                .orderByAsc(FoodOrderItemEntity::getId))
            .stream()
            .collect(Collectors.groupingBy(FoodOrderItemEntity::getOrderId, LinkedHashMap::new, Collectors.toList()));
    }

    public FoodCategoryResponse toCategoryResponse(FoodCategoryEntity entity, Integer itemCount) {
        FoodCategoryResponse response = new FoodCategoryResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setCategoryType(entity.getCategoryType());
        response.setName(entity.getName());
        response.setIconText(entity.getIconText());
        response.setIconTone(entity.getIconTone());
        response.setDescription(entity.getDescription());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setItemCount(itemCount);
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public FoodIngredientResponse toIngredientResponse(FoodIngredientEntity entity, Map<Long, FoodCategoryEntity> categoryMap) {
        FoodIngredientResponse response = new FoodIngredientResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setCategoryId(entity.getCategoryId());
        response.setCategoryName(Optional.ofNullable(categoryMap.get(entity.getCategoryId())).map(FoodCategoryEntity::getName).orElse(""));
        response.setName(entity.getName());
        response.setStockAmount(entity.getStockAmount());
        response.setUnit(entity.getUnit());
        response.setReorderLevel(entity.getReorderLevel());
        response.setStorageLocation(entity.getStorageLocation());
        response.setStatus(entity.getStatus());
        response.setNote(entity.getNote());
        response.setSortOrder(entity.getSortOrder());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public FoodDishResponse toDishResponse(
        FoodDishEntity entity,
        Map<Long, FoodCategoryEntity> categoryMap,
        List<FoodDishIngredientEntity> ingredients,
        List<FoodDishStepEntity> steps
    ) {
        FoodDishResponse response = new FoodDishResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setCategoryId(entity.getCategoryId());
        response.setCategoryName(Optional.ofNullable(categoryMap.get(entity.getCategoryId())).map(FoodCategoryEntity::getName).orElse(""));
        response.setName(entity.getName());
        response.setSubtitle(entity.getSubtitle());
        response.setDescription(entity.getDescription());
        response.setTasteTags(splitTags(entity.getTasteTags()));
        response.setHighlightTags(splitTags(entity.getHighlightTags()));
        response.setCookMinutes(entity.getCookMinutes());
        response.setCoverTone(entity.getCoverTone());
        response.setCoverText(entity.getCoverText());
        response.setStatus(entity.getStatus());
        response.setSortOrder(entity.getSortOrder());
        response.setIngredientPreview(ingredients.stream().map(FoodDishIngredientEntity::getIngredientName).limit(3).toList());
        response.setIngredients(ingredients.stream().map(ingredient -> {
            FoodDishResponse.IngredientItem item = new FoodDishResponse.IngredientItem();
            item.setId(ingredient.getId());
            item.setIngredientId(ingredient.getIngredientId());
            item.setIngredientName(ingredient.getIngredientName());
            item.setAmount(ingredient.getAmount());
            item.setSortOrder(ingredient.getSortOrder());
            return item;
        }).toList());
        response.setSteps(steps.stream().map(step -> {
            FoodDishResponse.StepItem item = new FoodDishResponse.StepItem();
            item.setId(step.getId());
            item.setStepNo(step.getStepNo());
            item.setContent(step.getContent());
            return item;
        }).toList());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public FoodOrderResponse toOrderResponse(FoodOrderEntity entity, List<FoodOrderItemEntity> items) {
        FoodOrderResponse response = new FoodOrderResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setTitle(entity.getTitle());
        response.setPlannedFor(entity.getPlannedFor());
        response.setRemark(entity.getRemark());
        response.setTotalCookMinutes(entity.getTotalCookMinutes());
        response.setDishCount(items.size());
        response.setDishNames(items.stream().map(FoodOrderItemEntity::getDishName).toList());
        response.setDishes(items.stream().map(item -> {
            FoodOrderResponse.DishItem dish = new FoodOrderResponse.DishItem();
            dish.setDishId(item.getDishId());
            dish.setDishName(item.getDishName());
            dish.setCategoryName(item.getCategoryName());
            return dish;
        }).toList());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public void fillCategoryEntity(FoodCategoryEntity entity, FoodCategoryRequest request) {
        entity.setUserId(request.getUserId());
        entity.setCategoryType(normalizeCategoryType(request.getCategoryType(), true));
        entity.setName(request.getName().trim());
        entity.setIconText(request.getIconText().trim());
        entity.setIconTone(request.getIconTone().trim());
        entity.setDescription(normalizeNullable(request.getDescription()));
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setStatus(normalizeCategoryStatus(request.getStatus()));
    }

    public void fillIngredientEntity(FoodIngredientEntity entity, FoodIngredientRequest request) {
        FoodCategoryEntity category = foodCategoryMapper.selectById(request.getCategoryId());
        if (category == null || !Objects.equals(category.getUserId(), request.getUserId()) || !CATEGORY_TYPE_INGREDIENT.equals(category.getCategoryType())) {
            throw new IllegalArgumentException("食材分类不存在");
        }

        entity.setUserId(request.getUserId());
        entity.setCategoryId(request.getCategoryId());
        entity.setName(request.getName().trim());
        entity.setStockAmount(request.getStockAmount() == null ? DEFAULT_INGREDIENT_AMOUNT : request.getStockAmount());
        entity.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : DEFAULT_INGREDIENT_UNIT);
        entity.setReorderLevel(request.getReorderLevel() == null ? DEFAULT_INGREDIENT_AMOUNT : request.getReorderLevel());
        entity.setStorageLocation(normalizeNullable(request.getStorageLocation()));
        entity.setStatus(normalizeIngredientStatus(request.getStatus()));
        entity.setNote(normalizeNullable(request.getNote()));
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    public void validateCategoryCanDelete(FoodCategoryEntity entity) {
        if (CATEGORY_TYPE_INGREDIENT.equals(entity.getCategoryType())) {
            Long ingredientCount = foodIngredientMapper.selectCount(new LambdaQueryWrapper<FoodIngredientEntity>()
                .eq(FoodIngredientEntity::getUserId, entity.getUserId())
                .eq(FoodIngredientEntity::getCategoryId, entity.getId()));
            if (ingredientCount != null && ingredientCount > 0) {
                throw new IllegalArgumentException("分类下还有食材，不能删除");
            }
            return;
        }

        Long dishCount = foodDishMapper.selectCount(new LambdaQueryWrapper<FoodDishEntity>()
            .eq(FoodDishEntity::getUserId, entity.getUserId())
            .eq(FoodDishEntity::getCategoryId, entity.getId()));
        if (dishCount != null && dishCount > 0) {
            throw new IllegalArgumentException("分类下还有菜品，不能删除");
        }
    }

    public void validateIngredientCanDelete(FoodIngredientEntity entity) {
        Long usageCount = foodDishIngredientMapper.selectCount(new LambdaQueryWrapper<FoodDishIngredientEntity>()
            .eq(FoodDishIngredientEntity::getIngredientId, entity.getId()));
        if (usageCount != null && usageCount > 0) {
            throw new IllegalArgumentException("食材已被菜品引用，不能删除");
        }
    }

    public void fillDishEntity(FoodDishEntity entity, FoodDishRequest request) {
        FoodCategoryEntity category = foodCategoryMapper.selectById(request.getCategoryId());
        if (category == null || !Objects.equals(category.getUserId(), request.getUserId()) || !CATEGORY_TYPE_DISH.equals(category.getCategoryType())) {
            throw new IllegalArgumentException("菜品分类不存在");
        }

        entity.setUserId(request.getUserId());
        entity.setCategoryId(request.getCategoryId());
        entity.setName(request.getName().trim());
        entity.setSubtitle(normalizeNullable(request.getSubtitle()));
        entity.setDescription(normalizeNullable(request.getDescription()));
        entity.setTasteTags(joinTags(request.getTasteTags()));
        entity.setHighlightTags(joinTags(request.getHighlightTags()));
        entity.setCookMinutes(request.getCookMinutes());
        entity.setCoverTone(request.getCoverTone().trim());
        entity.setCoverText(request.getCoverText().trim());
        entity.setStatus(normalizeDishStatus(request.getStatus()));
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    public void replaceDishChildren(Long dishId, FoodDishRequest request) {
        foodDishIngredientMapper.delete(new LambdaQueryWrapper<FoodDishIngredientEntity>().eq(FoodDishIngredientEntity::getDishId, dishId));
        foodDishStepMapper.delete(new LambdaQueryWrapper<FoodDishStepEntity>().eq(FoodDishStepEntity::getDishId, dishId));

        List<FoodCategoryEntity> ingredientCategories = foodCategoryMapper.selectList(new LambdaQueryWrapper<FoodCategoryEntity>()
            .eq(FoodCategoryEntity::getUserId, request.getUserId())
            .eq(FoodCategoryEntity::getCategoryType, CATEGORY_TYPE_INGREDIENT));

        for (int index = 0; index < request.getIngredients().size(); index++) {
            FoodDishRequest.IngredientItem ingredient = request.getIngredients().get(index);
            FoodDishIngredientEntity entity = new FoodDishIngredientEntity();
            entity.setDishId(dishId);
            entity.setIngredientId(resolveIngredientId(request.getUserId(), ingredient.getIngredientId(), ingredient.getIngredientName(), ingredientCategories));
            entity.setIngredientName(ingredient.getIngredientName().trim());
            entity.setAmount(ingredient.getAmount().trim());
            entity.setSortOrder((index + 1) * 10);
            foodDishIngredientMapper.insert(entity);
        }

        for (int index = 0; index < request.getSteps().size(); index++) {
            FoodDishRequest.StepItem step = request.getSteps().get(index);
            FoodDishStepEntity entity = new FoodDishStepEntity();
            entity.setDishId(dishId);
            entity.setStepNo(index + 1);
            entity.setContent(step.getContent().trim());
            foodDishStepMapper.insert(entity);
        }
    }

    public String buildOrderTitle(String title, LocalDate plannedFor) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }

        return switch (plannedFor.getDayOfWeek()) {
            case MONDAY -> "周一工作日晚餐";
            case TUESDAY -> "周二双人晚餐";
            case WEDNESDAY -> "周三轻食午餐";
            case THURSDAY -> "周四家常晚餐";
            case FRIDAY -> "周五放松晚餐";
            case SATURDAY -> "周末家庭晚餐";
            case SUNDAY -> "周日收心晚餐";
        };
    }

    public String resolveOrderTitle(Long userId, String title, LocalDate plannedFor) {
        String baseTitle = buildOrderTitle(title, plannedFor);
        String candidate = baseTitle;
        int suffix = 2;

        while (foodOrderMapper.selectCount(new LambdaQueryWrapper<FoodOrderEntity>()
            .eq(FoodOrderEntity::getUserId, userId)
            .eq(FoodOrderEntity::getTitle, candidate)
            .eq(FoodOrderEntity::getPlannedFor, plannedFor)) > 0) {
            candidate = baseTitle + " " + suffix;
            suffix++;
        }

        return candidate;
    }

    public String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public List<String> splitTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
    }

    public String joinTags(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .collect(Collectors.joining(","));
    }

    private Long resolveIngredientId(
        Long userId,
        Long ingredientId,
        String ingredientName,
        List<FoodCategoryEntity> ingredientCategories
    ) {
        if (ingredientId != null) {
            FoodIngredientEntity ingredient = foodIngredientMapper.selectById(ingredientId);
            return ingredient != null && Objects.equals(ingredient.getUserId(), userId) ? ingredientId : null;
        }

        if (!StringUtils.hasText(ingredientName)) {
            return null;
        }

        FoodIngredientEntity existing = foodIngredientMapper.selectOne(new LambdaQueryWrapper<FoodIngredientEntity>()
            .eq(FoodIngredientEntity::getUserId, userId)
            .eq(FoodIngredientEntity::getName, ingredientName.trim())
            .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }

        if (ingredientCategories.isEmpty()) {
            return null;
        }

        FoodIngredientEntity entity = new FoodIngredientEntity();
        entity.setUserId(userId);
        entity.setCategoryId(ingredientCategories.get(0).getId());
        entity.setName(ingredientName.trim());
        entity.setStockAmount(BigDecimal.ZERO);
        entity.setUnit("份");
        entity.setReorderLevel(BigDecimal.ZERO);
        entity.setStorageLocation("待分类");
        entity.setStatus("low");
        entity.setNote("由新增菜品自动补录");
        entity.setSortOrder(999);
        foodIngredientMapper.insert(entity);
        return entity.getId();
    }

    private String normalizeStatus(String status, Set<String> validValues, String message, String defaultValue) {
        if (!StringUtils.hasText(status)) {
            return defaultValue;
        }

        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!validValues.contains(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptionalStatus(String status, Set<String> validValues, String message) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status.trim())) {
            return null;
        }
        return normalizeStatus(status, validValues, message, null);
    }
}
