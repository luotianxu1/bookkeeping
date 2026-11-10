import type { RouteRecordRaw } from 'vue-router'
import { financeRoutes } from '@/business/router/modules/finance'
import { toolsRoutes } from '@/business/router/modules/tools'
import { foodRoutes } from '@/business/router/modules/food'
import { mainRoutes } from '@/business/router/modules/main'
import { findMenu } from '@/data/modules'

const publicPaths = new Set(['/register', '/forgot-password'])
function desktopRoute(route: RouteRecordRaw): RouteRecordRaw {
  return {
    ...route,
    meta: {
      ...route.meta,
      business: true,
      permission: 'pc.access',
      module: findMenu(route.path)?.module.title,
    },
    ...(route.children ? { children: route.children.map(desktopRoute) } : {}),
  } as RouteRecordRaw
}
export const businessRoutes = [
  ...financeRoutes,
  ...toolsRoutes,
  ...foodRoutes,
  ...mainRoutes,
]
  .filter((route) => route.path !== '/login' && !publicPaths.has(route.path))
  .map(desktopRoute)
export const publicBusinessRoutes = mainRoutes
  .filter((route) => publicPaths.has(route.path))
  .map((route) => ({
    ...route,
    meta: { ...route.meta, business: true, public: true },
  }))
