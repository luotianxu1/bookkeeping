// 主导航路由模块：承载底部 Tab 的非财务入口页面。
import type { RouteRecordRaw } from 'vue-router'
import LoginPage from '@/pages/login/LoginPage/index.vue'
import PlaceholderPage from '@/pages/placeholder/PlaceholderPage/index.vue'
import ProfilePage from '@/pages/profile/ProfilePage/index.vue'
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
    path: '/food',
    name: 'food',
    component: PlaceholderPage,
    meta: {
      section: 'food',
      title: '餐饮',
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
    path: '/profile',
    name: 'profile',
    component: ProfilePage,
    meta: {
      section: 'profile',
      title: '我的',
    },
  },
]
