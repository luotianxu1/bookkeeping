// 餐饮业务路由模块：承载餐饮首页、菜单、菜品和食材相关页面。
import type { RouteRecordRaw } from 'vue-router'
import FoodCategoryListPage from '@/pages/food/FoodCategoryListPage/index.vue'
import FoodDishCreatePage from '@/pages/food/FoodDishCreatePage/index.vue'
import FoodDishDetailPage from '@/pages/food/FoodDishDetailPage/index.vue'
import FoodDishListPage from '@/pages/food/FoodDishListPage/index.vue'
import FoodHomePage from '@/pages/food/FoodHomePage/index.vue'
import FoodIngredientCategoryPage from '@/pages/food/FoodIngredientCategoryPage/index.vue'
import FoodIngredientListPage from '@/pages/food/FoodIngredientListPage/index.vue'
import FoodMenuDetailPage from '@/pages/food/FoodMenuDetailPage/index.vue'
import FoodMenuListPage from '@/pages/food/FoodMenuListPage/index.vue'

export const foodRoutes: RouteRecordRaw[] = [
  {
    path: '/food',
    name: 'food',
    component: FoodHomePage,
    meta: {
      section: 'food',
      title: '餐饮',
    },
  },
  {
    path: '/food/menu',
    name: 'food-menu',
    component: FoodMenuListPage,
    meta: {
      section: 'food',
      title: '菜单列表',
    },
  },
  {
    path: '/food/menu/:orderId',
    name: 'food-menu-detail',
    component: FoodMenuDetailPage,
    meta: {
      section: 'food',
      title: '菜单详情',
    },
  },
  {
    path: '/food/dishes',
    name: 'food-dishes',
    component: FoodDishListPage,
    meta: {
      section: 'food',
      title: '菜品列表',
    },
  },
  {
    path: '/food/dishes/new',
    name: 'food-dishes-new',
    component: FoodDishCreatePage,
    meta: {
      section: 'food',
      title: '新增菜品',
    },
  },
  {
    path: '/food/dishes/:dishId',
    name: 'food-dishes-detail',
    component: FoodDishDetailPage,
    meta: {
      section: 'food',
      title: '菜品详情',
    },
  },
  {
    path: '/food/categories',
    name: 'food-categories',
    component: FoodCategoryListPage,
    meta: {
      section: 'food',
      title: '菜品分类',
    },
  },
  {
    path: '/food/categories/new',
    name: 'food-categories-new',
    component: FoodCategoryListPage,
    meta: {
      section: 'food',
      title: '新增菜品分类',
    },
  },
  {
    path: '/food/ingredients',
    name: 'food-ingredients',
    component: FoodIngredientListPage,
    meta: {
      section: 'food',
      title: '食材列表',
    },
  },
  {
    path: '/food/ingredient-categories',
    name: 'food-ingredient-categories',
    component: FoodIngredientCategoryPage,
    meta: {
      section: 'food',
      title: '食材分类',
    },
  },
  {
    path: '/food/ingredient-categories/new',
    name: 'food-ingredient-categories-new',
    component: FoodIngredientCategoryPage,
    meta: {
      section: 'food',
      title: '新增食材分类',
    },
  },
]
