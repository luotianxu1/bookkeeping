// 应用路由入口：聚合各业务模块路由并创建路由实例。
import { createRouter, createWebHistory } from 'vue-router'
import type { AppSection } from '@/types/navigation'
import { getStoredToken } from '@/utils/auth-token'
import { showAuthPrompt } from '@/utils/auth-prompt'
import { financeRoutes } from './modules/finance'
import { mainRoutes } from './modules/main'

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
    ...mainRoutes,
  ],
})

router.beforeEach((to, from) => {
  if (to.name === 'login' || to.name === 'register' || getStoredToken()) {
    return true
  }

  showAuthPrompt(to.fullPath)
  if (from.name && from.name !== 'login') {
    return from.fullPath
  }

  return {
    path: '/login',
    query: {
      redirect: to.fullPath,
    },
  }
})
