package com.example.tool.service;

import com.example.tool.dto.FoodCategoryResponse;
import com.example.tool.dto.FoodHomeResponse;
import com.example.tool.dto.FoodOrderResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class FoodHomeService {

    private final FoodCategoryService foodCategoryService;
    private final FoodIngredientService foodIngredientService;
    private final FoodOrderService foodOrderService;

    public FoodHomeService(
        FoodCategoryService foodCategoryService,
        FoodIngredientService foodIngredientService,
        FoodOrderService foodOrderService
    ) {
        this.foodCategoryService = foodCategoryService;
        this.foodIngredientService = foodIngredientService;
        this.foodOrderService = foodOrderService;
    }

    public FoodHomeResponse getHome(Long userId) {
        List<FoodCategoryResponse> dishCategories = foodCategoryService.listCategories(userId, "dish", null, "active");
        List<FoodCategoryResponse> ingredientCategories = foodCategoryService.listCategories(userId, "ingredient", null, "active");
        List<FoodOrderResponse> recentOrders = foodOrderService.listOrders(userId, null);

        FoodHomeResponse response = new FoodHomeResponse();
        response.setHeroTitle("今天吃什么");
        response.setManagementCards(List.of(
            buildManagementCard("dishes", "菜品列表", "查看全部菜品与详情", sumItemCount(dishCategories), "/food/menu"),
            buildManagementCard("dish-categories", "菜品分类", "按品类整理做饭灵感", dishCategories.size(), "/food/categories"),
            buildManagementCard("ingredients", "食材列表", "管理常用食材资料", foodIngredientService.listIngredients(userId, null, "all", null).size(), "/food/ingredients"),
            buildManagementCard("ingredient-categories", "食材分类", "统一食材收纳与归类", ingredientCategories.size(), "/food/ingredient-categories")
        ));
        response.setRecentMenus(recentOrders.stream().limit(3).map(this::toRecentMenu).toList());
        return response;
    }

    private FoodHomeResponse.ManagementCard buildManagementCard(
        String key,
        String title,
        String description,
        Integer count,
        String path
    ) {
        FoodHomeResponse.ManagementCard card = new FoodHomeResponse.ManagementCard();
        card.setKey(key);
        card.setTitle(title);
        card.setDescription(description);
        card.setCount(count);
        card.setPath(path);
        return card;
    }

    private FoodHomeResponse.RecentMenu toRecentMenu(FoodOrderResponse order) {
        FoodHomeResponse.RecentMenu item = new FoodHomeResponse.RecentMenu();
        item.setOrderId(order.getId());
        item.setTitle(order.getTitle());
        item.setSummary(String.join(" · ", order.getDishNames()));
        item.setActionLabel("查看");
        return item;
    }

    private int sumItemCount(List<FoodCategoryResponse> categories) {
        return categories.stream().map(FoodCategoryResponse::getItemCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }
}
