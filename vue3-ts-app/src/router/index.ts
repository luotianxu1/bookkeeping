// 应用路由入口：聚合各业务模块路由并创建路由实例。
import { createRouter, createWebHistory } from 'vue-router'
import type { AppSection } from '@/types/navigation'
import { getStoredToken } from '@/utils/auth-token'
import { financeRoutes } from './modules/finance'
import { foodRoutes } from './modules/food'
import { mainRoutes } from './modules/main'
import { toolsRoutes } from './modules/tools'

declare module 'vue-router' {
  interface RouteMeta {
    section: AppSection
    title: string
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: () => (getStoredToken() ? '/finance' : '/login'),
    },
    ...financeRoutes,
    ...foodRoutes,
    ...toolsRoutes,
    ...mainRoutes,
  ],
})

// 未登录一律重定向到登录页并携带回跳地址，受保护页面不渲染
router.beforeEach((to) => {
  if (to.name === 'login' || to.name === 'register' || getStoredToken()) {
    return true
  }

  return { path: '/login', query: { redirect: to.fullPath } }
})
