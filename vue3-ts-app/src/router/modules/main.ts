// 主导航路由模块：承载底部 Tab 的非财务入口页面。
import type { RouteRecordRaw } from 'vue-router'
import LoginPage from '@/pages/login/LoginPage/index.vue'
import PlaceholderPage from '@/pages/placeholder/PlaceholderPage/index.vue'
import ProfilePage from '@/pages/profile/ProfilePage/index.vue'
import ContactsPage from '@/pages/tools/ContactsPage/index.vue'
import PhotographyOrderOverviewPage from '@/pages/tools/PhotographyOrderOverviewPage/index.vue'
import PhotographyOrdersPage from '@/pages/tools/PhotographyOrdersPage/index.vue'
import TodoPage from '@/pages/tools/TodoPage/index.vue'
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
    path: '/profile',
    name: 'profile',
    component: ProfilePage,
    meta: {
      section: 'profile',
      title: '我的',
    },
  },
]
