// 工具业务路由模块：承载工具首页、日历、纪念日和出行等页面。
import type { RouteRecordRaw } from 'vue-router'
import AnniversaryPage from '@/pages/tools/AnniversaryPage/index.vue'
import CalendarPage from '@/pages/tools/CalendarPage/index.vue'
import ContactsPage from '@/pages/tools/ContactsPage/index.vue'
import PhotographyOrderOverviewPage from '@/pages/tools/PhotographyOrderOverviewPage/index.vue'
import PhotographyOrdersPage from '@/pages/tools/PhotographyOrdersPage/index.vue'
import TodoPage from '@/pages/tools/TodoPage/index.vue'
import TravelPlanCreatePage from '@/pages/tools/TravelPlanCreatePage/index.vue'
import TravelPlanDetailPage from '@/pages/tools/TravelPlanDetailPage/index.vue'
import TravelPlansPage from '@/pages/tools/TravelPlansPage/index.vue'
import ToolsPage from '@/pages/tools/ToolsPage/index.vue'

export const toolsRoutes: RouteRecordRaw[] = [
  {
    path: '/tools',
    name: 'tools',
    component: ToolsPage,
    meta: {
      section: 'tools',
      title: '工具',
    },
  },
  {
    path: '/tools/calendar',
    name: 'tools-calendar',
    component: CalendarPage,
    meta: {
      section: 'tools',
      title: '日历',
    },
  },
  {
    path: '/tools/anniversaries',
    name: 'tools-anniversaries',
    component: AnniversaryPage,
    meta: {
      section: 'tools',
      title: '纪念日',
    },
  },
  {
    path: '/tools/photography-orders',
    name: 'tools-photography-orders',
    component: PhotographyOrdersPage,
    meta: {
      section: 'tools',
      title: '摄影订单',
    },
  },
  {
    path: '/tools/photography-orders/overview',
    name: 'tools-photography-orders-overview',
    component: PhotographyOrderOverviewPage,
    meta: {
      section: 'tools',
      title: '订单总览',
    },
  },
  {
    path: '/tools/todo-items',
    name: 'tools-todo-items',
    component: TodoPage,
    meta: {
      section: 'tools',
      title: '代办事项',
    },
  },
  {
    path: '/tools/contacts',
    name: 'tools-contacts',
    component: ContactsPage,
    meta: {
      section: 'tools',
      title: '联系人管理',
    },
  },
  {
    path: '/tools/travel-plans',
    name: 'tools-travel-plans',
    component: TravelPlansPage,
    meta: {
      section: 'tools',
      title: '旅行管理',
    },
  },
  {
    path: '/tools/travel-plans/new',
    name: 'tools-travel-plans-new',
    component: TravelPlanCreatePage,
    meta: {
      section: 'tools',
      title: '新增旅行',
    },
  },
  {
    path: '/tools/travel-plans/:planId/edit',
    name: 'tools-travel-plans-edit',
    component: TravelPlanCreatePage,
    meta: {
      section: 'tools',
      title: '修改旅行',
    },
  },
  {
    path: '/tools/travel-plans/:planId',
    name: 'tools-travel-plans-detail',
    component: TravelPlanDetailPage,
    meta: {
      section: 'tools',
      title: '旅行详情',
    },
  },
]
