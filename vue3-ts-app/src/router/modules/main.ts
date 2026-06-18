// 主导航路由模块：承载底部 Tab 的非财务入口页面。
import type { RouteRecordRaw } from 'vue-router'
import LoginPage from '@/pages/login/LoginPage/index.vue'
import RegisterPage from '@/pages/login/RegisterPage/index.vue'
import ProfileFamilyPage from '@/pages/profile/ProfileFamilyPage/index.vue'
import ProfilePage from '@/pages/profile/ProfilePage/index.vue'

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
