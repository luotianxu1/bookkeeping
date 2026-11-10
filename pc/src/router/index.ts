import { createRouter, createWebHistory } from 'vue-router'
import { businessRoutes, publicBusinessRoutes } from './business'
import {
  can,
  clearSession,
  invalidatePermissions,
  loadSession,
  session,
} from '@/stores/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/account',
      component: () => import('@/layouts/BusinessAuthLayout.vue'),
      children: publicBusinessRoutes,
    },
    {
      path: '/login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/forbidden',
      component: () => import('@/views/StatusView.vue'),
      meta: { title: '无访问权限', public: true },
    },
    {
      path: '/unavailable',
      component: () => import('@/views/StatusView.vue'),
      meta: { title: '连接失败', public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/overview',
      children: [
        ...businessRoutes,
        {
          path: 'overview',
          component: () => import('@/views/OverviewView.vue'),
          meta: { title: '功能总览', permission: 'pc.access' },
        },
        {
          path: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { title: '用户管理', permission: 'user.read' },
        },
        {
          path: 'roles',
          component: () => import('@/views/RolesView.vue'),
          meta: { title: '角色管理', permission: 'role.read' },
        },
        {
          path: 'permissions',
          component: () => import('@/views/PermissionsView.vue'),
          meta: { title: '权限配置', permission: 'permission.read' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('@/views/StatusView.vue'),
      meta: { title: '页面不存在', public: true },
    },
  ],
})
router.beforeEach(async (to) => {
  document.title = `${to.meta.title || '管理工作台'} · Life Hub`
  if (to.meta.public) return true
  try {
    await loadSession(true)
  } catch {
    return { path: '/unavailable', query: { redirect: to.fullPath } }
  }
  if (session.denied) return '/forbidden'
  if (!session.data) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.permission && !can(String(to.meta.permission)))
    return '/forbidden'
})
window.addEventListener('session-expired', () => {
  clearSession()
  if (router.currentRoute.value.path !== '/login')
    void router.replace({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
})
window.addEventListener('permissions-changed', invalidatePermissions)
export default router
