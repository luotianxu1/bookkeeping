// 主导航路由模块：承载底部 Tab 的非财务入口页面。
import type { RouteRecordRaw } from 'vue-router'
import LoginPage from '@/pages/login/LoginPage/index.vue'
import RegisterPage from '@/pages/login/RegisterPage/index.vue'
import ProfileFamilyPage from '@/pages/profile/ProfileFamilyPage/index.vue'
import ProfilePage from '@/pages/profile/ProfilePage/index.vue'
import FoodCategoryListPage from '@/pages/food/FoodCategoryListPage/index.vue'
import FoodDishCreatePage from '@/pages/food/FoodDishCreatePage/index.vue'
import FoodDishDetailPage from '@/pages/food/FoodDishDetailPage/index.vue'
import FoodDishListPage from '@/pages/food/FoodDishListPage/index.vue'
import FoodHomePage from '@/pages/food/FoodHomePage/index.vue'
import FoodIngredientCategoryPage from '@/pages/food/FoodIngredientCategoryPage/index.vue'
import FoodIngredientListPage from '@/pages/food/FoodIngredientListPage/index.vue'
import FoodMenuDetailPage from '@/pages/food/FoodMenuDetailPage/index.vue'
import FoodMenuListPage from '@/pages/food/FoodMenuListPage/index.vue'
import AnniversaryPage from '@/pages/tools/AnniversaryPage/index.vue'
import CalendarPage from '@/pages/tools/CalendarPage/index.vue'
import ContactsPage from '@/pages/tools/ContactsPage/index.vue'
import PhotographyOrderOverviewPage from '@/pages/tools/PhotographyOrderOverviewPage/index.vue'
import PhotographyOrdersPage from '@/pages/tools/PhotographyOrdersPage/index.vue'
import TodoPage from '@/pages/tools/TodoPage/index.vue'
import TravelPlanCreatePage from '@/pages/tools/TravelPlanCreatePage/index.vue'
import TravelPlanDetailPage from '@/pages/tools/TravelPlanDetailPage/index.vue'
import TravelPlansPage from '@/pages/tools/TravelPlansPage/index.vue'
import ToolsPage from '@/pages/tools/ToolsPage/index.vue'

export const mainRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginPage,
    meta: {
      section: 'finance',
      title: '登录',
    },
  },
  {
    path: '/register',
    name: 'register',
    component: RegisterPage,
    meta: {
      section: 'finance',
      title: '注册',
    },
  },
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
  {
    path: '/tools',
    name: 'tools',
    component: ToolsPage,
    meta: {
      section: 'tools',
      title: '工具',
    },
  },
  {
    path: '/tools/calendar',
    name: 'tools-calendar',
    component: CalendarPage,
    meta: {
      section: 'tools',
      title: '日历',
    },
  },
  {
    path: '/tools/anniversaries',
    name: 'tools-anniversaries',
    component: AnniversaryPage,
    meta: {
      section: 'tools',
      title: '纪念日',
    },
  },
  {
    path: '/tools/photography-orders',
    name: 'tools-photography-orders',
    component: PhotographyOrdersPage,
    meta: {
      section: 'tools',
      title: '摄影订单',
    },
  },
  {
    path: '/tools/photography-orders/overview',
    name: 'tools-photography-orders-overview',
    component: PhotographyOrderOverviewPage,
    meta: {
      section: 'tools',
      title: '订单总览',
    },
  },
  {
    path: '/tools/todo-items',
    name: 'tools-todo-items',
    component: TodoPage,
    meta: {
      section: 'tools',
      title: '代办事项',
    },
  },
  {
    path: '/tools/contacts',
    name: 'tools-contacts',
    component: ContactsPage,
    meta: {
      section: 'tools',
      title: '联系人管理',
    },
  },
  {
    path: '/tools/travel-plans',
    name: 'tools-travel-plans',
    component: TravelPlansPage,
    meta: {
      section: 'tools',
      title: '旅行管理',
    },
  },
  {
    path: '/tools/travel-plans/new',
    name: 'tools-travel-plans-new',
    component: TravelPlanCreatePage,
    meta: {
      section: 'tools',
      title: '新增旅行',
    },
  },
  {
    path: '/tools/travel-plans/:planId/edit',
    name: 'tools-travel-plans-edit',
    component: TravelPlanCreatePage,
    meta: {
      section: 'tools',
      title: '修改旅行',
    },
  },
  {
    path: '/tools/travel-plans/:planId',
    name: 'tools-travel-plans-detail',
    component: TravelPlanDetailPage,
    meta: {
      section: 'tools',
      title: '旅行详情',
    },
  },
  {
    path: '/profile',
    name: 'profile',
    component: ProfilePage,
    meta: {
      section: 'profile',
      title: '我的',
    },
  },
  {
    path: '/profile/family-members',
    name: 'profile-family-members',
    component: ProfileFamilyPage,
    meta: {
      section: 'profile',
      title: '绑定家庭成员',
    },
  },
]
