// 主导航路由模块：承载底部 Tab 的非财务入口页面。
import type { RouteRecordRaw } from 'vue-router'

export const mainRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/login/LoginPage/index.vue'),
    meta: {
      section: 'finance',
      title: '登录',
    },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/pages/login/RegisterPage/index.vue'),
    meta: {
      section: 'finance',
      title: '注册',
    },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/pages/profile/ProfilePage/index.vue'),
    meta: {
      section: 'profile',
      title: '我的',
    },
  },
  {
    path: '/profile/family-members',
    name: 'profile-family-members',
    component: () => import('@/pages/profile/ProfileFamilyPage/index.vue'),
    meta: {
      section: 'profile',
      title: '绑定家庭成员',
    },
  },
  {
    path: '/profile/task-management',
    name: 'profile-task-management',
    component: () => import('@/pages/profile/TaskManagementPage/index.vue'),
    meta: {
      section: 'profile',
      title: '任务管理',
    },
  },
  {
    path: '/profile/icon-management',
    name: 'profile-icon-management',
    component: () => import('@/pages/profile/IconManagementPage/index.vue'),
    meta: {
      section: 'profile',
      title: '图标管理',
    },
  },
]
