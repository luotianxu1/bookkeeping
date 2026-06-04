package com.example.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.tool.dto.FoodOrderCreateRequest;
import com.example.tool.dto.FoodOrderResponse;
import com.example.tool.entity.FoodCategoryEntity;
import com.example.tool.entity.FoodDishEntity;
import com.example.tool.entity.FoodOrderEntity;
import com.example.tool.entity.FoodOrderItemEntity;
import com.example.tool.mapper.FoodDishMapper;
import com.example.tool.mapper.FoodOrderItemMapper;
import com.example.tool.mapper.FoodOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FoodOrderService {

    private final FoodDishMapper foodDishMapper;
    private final FoodOrderMapper foodOrderMapper;
    private final FoodOrderItemMapper foodOrderItemMapper;
    private final FoodDomainSupport foodDomainSupport;

    public FoodOrderService(
        FoodDishMapper foodDishMapper,
        FoodOrderMapper foodOrderMapper,
        FoodOrderItemMapper foodOrderItemMapper,
        FoodDomainSupport foodDomainSupport
    ) {
        this.foodDishMapper = foodDishMapper;
        this.foodOrderMapper = foodOrderMapper;
        this.foodOrderItemMapper = foodOrderItemMapper;
        this.foodDomainSupport = foodDomainSupport;
    }

    public List<FoodOrderResponse> listOrders(Long userId, String status, String keyword) {
        String normalizedStatus = foodDomainSupport.normalizeOptionalOrderStatus(status);

        LambdaQueryWrapper<FoodOrderEntity> wrapper = new LambdaQueryWrapper<FoodOrderEntity>()
            .eq(FoodOrderEntity::getUserId, userId)
            .eq(StringUtils.hasText(normalizedStatus), FoodOrderEntity::getStatus, normalizedStatus)
            .and(StringUtils.hasText(keyword), query -> query
                .like(FoodOrderEntity::getTitle, keyword.trim())
                .or()
                .like(FoodOrderEntity::getRemark, keyword.trim()))
            .orderByDesc(FoodOrderEntity::getPlannedFor)
            .orderByDesc(FoodOrderEntity::getId);

        List<FoodOrderEntity> orders = foodOrderMapper.selectList(wrapper);
        Map<Long, List<FoodOrderItemEntity>> itemMap = foodDomainSupport.getOrderItemMap(orders.stream().map(FoodOrderEntity::getId).toList());

        return orders.stream()
            .map(order -> foodDomainSupport.toOrderResponse(order, itemMap.getOrDefault(order.getId(), List.of())))
            .toList();
    }

    public FoodOrderResponse createOrder(FoodOrderCreateRequest request) {
        List<FoodDishEntity> dishes = foodDishMapper.selectBatchIds(request.getDishIds()).stream()
            .filter(dish -> Objects.equals(dish.getUserId(), request.getUserId()))
            .sorted(Comparator.comparing(FoodDishEntity::getSortOrder).thenComparing(FoodDishEntity::getId))
            .toList();
        if (dishes.isEmpty()) {
            throw new IllegalArgumentException("请选择有效的菜品");
        }

        LocalDate plannedFor = request.getPlannedFor() == null ? LocalDate.now() : request.getPlannedFor();
        FoodOrderEntity entity = new FoodOrderEntity();
        entity.setUserId(request.getUserId());
        entity.setTitle(foodDomainSupport.buildOrderTitle(request.getTitle(), plannedFor));
        entity.setPlannedFor(plannedFor);
        entity.setRemark(foodDomainSupport.normalizeNullable(request.getRemark()));
        entity.setTotalCookMinutes(dishes.stream().map(FoodDishEntity::getCookMinutes).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        entity.setServingCount(dishes.stream().map(FoodDishEntity::getServingCount).filter(Objects::nonNull).max(Integer::compareTo).orElse(1));
        entity.setStatus("planned");
        foodOrderMapper.insert(entity);

        Map<Long, FoodCategoryEntity> categoryMap = foodDomainSupport.getCategoryMap(request.getUserId());
        for (int index = 0; index < dishes.size(); index++) {
            FoodDishEntity dish = dishes.get(index);
            FoodOrderItemEntity item = new FoodOrderItemEntity();
            item.setOrderId(entity.getId());
            item.setDishId(dish.getId());
            item.setDishName(dish.getName());
            item.setCategoryName(categoryMap.containsKey(dish.getCategoryId()) ? categoryMap.get(dish.getCategoryId()).getName() : "");
            item.setSortOrder((index + 1) * 10);
            foodOrderItemMapper.insert(item);
        }

        return listOrders(request.getUserId(), "all", entity.getTitle()).stream()
            .filter(order -> Objects.equals(order.getId(), entity.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("菜单创建失败"));
    }
}
