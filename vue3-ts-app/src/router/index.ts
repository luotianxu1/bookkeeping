// 应用路由入口：聚合各业务模块路由并创建路由实例。
import { createRouter, createWebHistory } from 'vue-router'
import type { AppSection } from '@/types/navigation'
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
      redirect: '/login',
    },
    ...financeRoutes,
    ...mainRoutes,
  ],
})
