// 工具业务路由模块：承载工具首页、日历、纪念日和出行等页面。
import type { RouteRecordRaw } from 'vue-router'

export const toolsRoutes: RouteRecordRaw[] = [
  {
    path: '/tools',
    name: 'tools',
    component: () => import('@/pages/tools/ToolsPage/index.vue'),
    meta: {
      section: 'tools',
      title: '工具',
    },
  },
  {
    path: '/tools/calendar',
    name: 'tools-calendar',
    component: () => import('@/pages/tools/CalendarPage/index.vue'),
    meta: {
      section: 'tools',
      title: '日历',
    },
  },
  {
    path: '/tools/anniversaries',
    name: 'tools-anniversaries',
    component: () => import('@/pages/tools/AnniversaryPage/index.vue'),
    meta: {
      section: 'tools',
      title: '纪念日',
    },
  },
  {
    path: '/tools/photography-orders',
    name: 'tools-photography-orders',
    component: () => import('@/pages/tools/PhotographyOrdersPage/index.vue'),
    meta: {
      section: 'tools',
      title: '摄影订单',
    },
  },
  {
    path: '/tools/photography-orders/overview',
    name: 'tools-photography-orders-overview',
    component: () => import('@/pages/tools/PhotographyOrderOverviewPage/index.vue'),
    meta: {
      section: 'tools',
      title: '订单总览',
    },
  },
  {
    path: '/tools/todo-items',
    name: 'tools-todo-items',
    component: () => import('@/pages/tools/TodoPage/index.vue'),
    meta: {
      section: 'tools',
      title: '代办事项',
    },
  },
  {
    path: '/tools/contacts',
    name: 'tools-contacts',
    component: () => import('@/pages/tools/ContactsPage/index.vue'),
    meta: {
      section: 'tools',
      title: '联系人管理',
    },
  },
  {
    path: '/tools/health',
    name: 'tools-health',
    component: () => import('@/pages/tools/HealthPage/index.vue'),
    meta: {
      section: 'tools',
      title: '健康管理',
    },
  },
  {
    path: '/tools/health/records',
    name: 'tools-health-records',
    component: () => import('@/pages/tools/HealthRecordPage/index.vue'),
    meta: {
      section: 'tools',
      title: '体重打卡',
    },
  },
  {
    path: '/tools/travel-plans',
    name: 'tools-travel-plans',
    component: () => import('@/pages/tools/TravelPlansPage/index.vue'),
    meta: {
      section: 'tools',
      title: '旅行管理',
    },
  },
  {
    path: '/tools/travel-plans/new',
    name: 'tools-travel-plans-new',
    component: () => import('@/pages/tools/TravelPlanCreatePage/index.vue'),
    meta: {
      section: 'tools',
      title: '新增旅行',
    },
  },
  {
    path: '/tools/travel-plans/:planId/edit',
    name: 'tools-travel-plans-edit',
    component: () => import('@/pages/tools/TravelPlanCreatePage/index.vue'),
    meta: {
      section: 'tools',
      title: '修改旅行',
    },
  },
  {
    path: '/tools/travel-plans/:planId',
    name: 'tools-travel-plans-detail',
    component: () => import('@/pages/tools/TravelPlanDetailPage/index.vue'),
    meta: {
      section: 'tools',
      title: '旅行详情',
    },
  },
]
