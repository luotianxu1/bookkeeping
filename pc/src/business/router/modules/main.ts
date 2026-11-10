// 主导航路由模块：承载底部 Tab 的非财务入口页面。
import type { RouteRecordRaw } from 'vue-router'

export const mainRoutes: RouteRecordRaw[] = [
  {
    path: '/profile/edit',
    name: 'profile-edit',
    component: () =>
      import('@/business/pages/profile/ProfileEditPage/index.vue'),
    meta: { section: 'profile', title: '个人资料' },
  },
  {
    path: '/profile/security',
    name: 'profile-security',
    component: () =>
      import('@/business/pages/profile/AccountSecurityPage/index.vue'),
    meta: { section: 'profile', title: '隐私与安全' },
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () =>
      import('@/business/pages/login/ForgotPasswordPage/index.vue'),
    meta: { section: 'profile', title: '找回密码' },
  },
  {
    path: '/profile/reminders',
    name: 'profile-reminders',
    component: () =>
      import('@/business/pages/profile/ReminderCenterPage/index.vue'),
    meta: { section: 'profile', title: '统一提醒' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/business/pages/login/RegisterPage/index.vue'),
    meta: {
      section: 'finance',
      title: '注册',
    },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/business/pages/profile/ProfilePage/index.vue'),
    meta: {
      section: 'profile',
      title: '我的',
    },
  },
  {
    path: '/profile/family-members',
    name: 'profile-family-members',
    component: () =>
      import('@/business/pages/profile/ProfileFamilyPage/index.vue'),
    meta: {
      section: 'profile',
      title: '绑定家庭成员',
    },
  },
  {
    path: '/profile/task-management',
    name: 'profile-task-management',
    component: () =>
      import('@/business/pages/profile/TaskManagementPage/index.vue'),
    meta: {
      section: 'profile',
      title: '任务管理',
    },
  },
  {
    path: '/profile/icon-management',
    name: 'profile-icon-management',
    component: () =>
      import('@/business/pages/profile/IconManagementPage/index.vue'),
    meta: {
      section: 'profile',
      title: '图标管理',
    },
  },
]
