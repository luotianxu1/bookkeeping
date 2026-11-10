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
    // 非 force：60s TTL 内用缓存会话，导航不被网络往返拖慢
    await loadSession()
  } catch {
    return { path: '/unavailable', query: { redirect: to.fullPath } }
  }
  if (session.denied) return '/forbidden'
  if (!session.data) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.permission && !can(String(to.meta.permission)))
    return '/forbidden'
})

// 发版后旧页面的懒加载 chunk 可能 404，导航会静默失败：
// 对这类错误整页刷新一次（带标记防循环），其余错误保持原行为
router.onError((error, to) => {
  const message = error?.message || ''
  const isChunkError =
    /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i.test(
      message,
    )
  if (isChunkError) {
    if (sessionStorage.getItem('pc-chunk-reloaded')) {
      sessionStorage.removeItem('pc-chunk-reloaded')
      return
    }
    sessionStorage.setItem('pc-chunk-reloaded', '1')
    window.location.replace(to?.fullPath || '/')
    return
  }
  // 组件 setup/加载失败会让导航静默回滚，用户点击后"没反应"：
  // 已有页面在渲染 → 轻提示；初始导航直接打开失败页 → 兜底到状态页，避免整站白屏
  console.error('[Life Hub] 路由导航失败', error)
  if (router.currentRoute.value.matched.length) {
    void import('element-plus').then(({ ElMessage }) => {
      ElMessage.error('页面加载失败，请稍后重试')
    })
  } else {
    void router.replace('/unavailable').catch(() => {})
  }
})
router.afterEach(() => sessionStorage.removeItem('pc-chunk-reloaded'))
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
