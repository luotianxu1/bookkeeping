// 餐饮业务路由模块：承载餐饮首页、菜单、菜品和食材相关页面。
import type { RouteRecordRaw } from 'vue-router'

export const foodRoutes: RouteRecordRaw[] = [
  {
    path: '/food',
    name: 'food',
    component: () => import('@/pages/food/FoodHomePage/index.vue'),
    meta: {
      section: 'food',
      title: '餐饮',
    },
  },
  {
    path: '/food/menu',
    name: 'food-menu',
    component: () => import('@/pages/food/FoodMenuListPage/index.vue'),
    meta: {
      section: 'food',
      title: '菜单列表',
    },
  },
  {
    path: '/food/menu/:orderId',
    name: 'food-menu-detail',
    component: () => import('@/pages/food/FoodMenuDetailPage/index.vue'),
    meta: {
      section: 'food',
      title: '菜单详情',
    },
  },
  {
    path: '/food/dishes',
    name: 'food-dishes',
    component: () => import('@/pages/food/FoodDishListPage/index.vue'),
    meta: {
      section: 'food',
      title: '菜品列表',
    },
  },
  {
    path: '/food/dishes/new',
    name: 'food-dishes-new',
    component: () => import('@/pages/food/FoodDishCreatePage/index.vue'),
    meta: {
      section: 'food',
      title: '新增菜品',
    },
  },
  {
    path: '/food/dishes/:dishId',
    name: 'food-dishes-detail',
    component: () => import('@/pages/food/FoodDishDetailPage/index.vue'),
    meta: {
      section: 'food',
      title: '菜品详情',
    },
  },
  {
    path: '/food/categories',
    name: 'food-categories',
    component: () => import('@/pages/food/FoodCategoryListPage/index.vue'),
    meta: {
      section: 'food',
      title: '菜品分类',
    },
  },
  {
    path: '/food/categories/new',
    name: 'food-categories-new',
    component: () => import('@/pages/food/FoodCategoryListPage/index.vue'),
    meta: {
      section: 'food',
      title: '新增菜品分类',
    },
  },
  {
    path: '/food/ingredients',
    name: 'food-ingredients',
    component: () => import('@/pages/food/FoodIngredientListPage/index.vue'),
    meta: {
      section: 'food',
      title: '食材列表',
    },
  },
  {
    path: '/food/ingredient-categories',
    name: 'food-ingredient-categories',
    component: () => import('@/pages/food/FoodIngredientCategoryPage/index.vue'),
    meta: {
      section: 'food',
      title: '食材分类',
    },
  },
  {
    path: '/food/ingredient-categories/new',
    name: 'food-ingredient-categories-new',
    component: () => import('@/pages/food/FoodIngredientCategoryPage/index.vue'),
    meta: {
      section: 'food',
      title: '新增食材分类',
    },
  },
]
