<script setup lang="ts">
import { computed, onErrorCaptured, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  LayoutGrid,
  UsersRound,
  Wallet,
  Landmark,
  ChartNoAxesCombined,
  CalendarDays,
  CookingPot,
  ShieldCheck,
  KeyRound,
  LogOut,
  Moon,
  PanelsTopLeft,
  PanelLeftClose,
  PanelLeftOpen,
  RotateCw,
  Sun,
  TriangleAlert,
} from '@lucide/vue'
import { ElMessageBox, type MenuInstance } from 'element-plus'
import { api } from '@/api/admin'
import { modules, findMenu } from '@/data/modules'
import { can, clearSession, session } from '@/stores/session'
import { clearPageTabs, openPageTab } from '@/stores/pageTabs'
import PageTabs from '@/layouts/PageTabs.vue'
import { showError } from '@/utils/feedback'
import { useTheme } from '@/business/utils/theme'
const route = useRoute()
const router = useRouter()
const { isDark, toggleTheme } = useTheme()
const collapsed = ref(false)
const compactScreen = window.matchMedia('(max-width: 960px)')
const narrow = ref(compactScreen.matches)
const isCollapsed = computed(() => collapsed.value || narrow.value)
const content = ref<{ $el: HTMLElement }>()
function onResize(event: MediaQueryListEvent) {
  narrow.value = event.matches
}
compactScreen.addEventListener('change', onResize)
onBeforeUnmount(() => compactScreen.removeEventListener('change', onResize))
watch(
  () => route.path,
  () => content.value?.$el.scrollTo({ top: 0 }),
  { flush: 'post' },
)
// 每进入一个页面就在顶栏下方登记一个标签；相同 fullPath 只保留一个。
watch(
  () => route.fullPath,
  (fullPath) => {
    openPageTab({
      key: fullPath,
      path: route.path,
      title: String(
        route.meta.title || findMenu(route.path)?.entry.title || '页面',
      ),
    })
  },
  { immediate: true },
)
const menu = ref<MenuInstance>()
const loggingOut = ref(false)
const moduleIcons = {
  shield: ShieldCheck,
  wallet: Wallet,
  landmark: Landmark,
  chart: ChartNoAxesCombined,
  calendar: CalendarDays,
  food: CookingPot,
  users: UsersRound,
}
const activeMenu = computed(() => findMenu(route.path))

// 左侧菜单对齐 H5 的层级：显示「模块 → 功能级入口」两级，
// 更深的从属页（工资设置/账户明细/行情子市场等）不在左侧平铺，
// 从模块首页或「功能总览」进入，避免侧栏被几十个三级项淹没。
// 判断规则：路径为 2 段及以内的是功能级入口；3 段及以上默认隐藏。
const PRIMARY_DEEP_ENTRIES = new Set([
  '/finance/entry/expense', // 记一笔
  '/finance/global-market/global', // 全球行情中心入口
])
function isPrimaryEntry(path: string) {
  const depth = path.split('/').filter(Boolean).length
  return depth <= 2 || PRIMARY_DEEP_ENTRIES.has(path)
}

const navigation = computed(() =>
  modules
    .map((module) => ({
      ...module,
      entries: module.entries.filter(
        (item) =>
          can(item.permission || 'pc.access') &&
          (!item.adminOnly ||
            ['admin', 'super_admin'].includes(
              session.data?.user.roleCode || '',
            )) &&
          isPrimaryEntry(item.path),
      ),
    }))
    .filter((module) => module.entries.length)
    .map((module) => ({ ...module, homePath: module.entries[0]!.path })),
)
// 高亮到当前可见项；若停留在被隐藏的深层页，则高亮其所属模块入口
const activeMenuPath = computed(() => {
  const current = activeMenu.value
  if (!current) return route.path
  const navModule = navigation.value.find((m) => m.id === current.module.id)
  if (!navModule) return route.path
  return navModule.entries.some((entry) => entry.path === current.entry.path)
    ? current.entry.path
    : navModule.homePath
})
// sub-menu 随当前路由自动展开
watch(
  () => [activeMenu.value?.module.id, isCollapsed.value],
  ([id]) => {
    if (id && !isCollapsed.value) menu.value?.open(String(id))
  },
  { flush: 'post' },
)
// 未匹配到具体模块（如停在功能总览）时默认展开权限管理，
// 保证 admin 二级入口（用户/角色/权限配置）始终可达
const defaultOpeneds = computed(() => [activeMenu.value?.module.id || 'access'])
async function logout() {
  try {
    await ElMessageBox.confirm('退出当前设备的管理端登录？', '退出登录', {
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  loggingOut.value = true
  try {
    await api.logout()
    clearSession()
    clearPageTabs()
    await router.replace('/login')
  } catch (error) {
    showError(error)
  } finally {
    loggingOut.value = false
  }
}

// 页面级错误边界：某个页面渲染/脚本崩溃时原地展示重试卡片，
// 而不是整个工作台白屏（上一轮审查发现部分页面缺数据形状防御）
const pageCrashed = ref(false)
const pageRenderKey = ref(0)
onErrorCaptured((error) => {
  console.error('[Life Hub] 页面渲染错误', error)
  pageCrashed.value = true
  return false
})
function retryPage() {
  pageCrashed.value = false
  pageRenderKey.value++
}
</script>

<template>
  <el-container class="admin-shell" :class="{ 'is-collapsed': isCollapsed }">
    <el-aside class="sidebar" width="var(--sidebar-width)">
      <RouterLink class="brand" to="/overview" aria-label="Life Hub 首页">
        <span class="brand-symbol"><PanelsTopLeft :size="22" /></span>
        <span v-if="!isCollapsed" class="brand-text">Life Hub</span>
      </RouterLink>
      <nav aria-label="主导航">
        <el-menu
          ref="menu"
          :default-active="activeMenuPath"
          :default-openeds="defaultOpeneds"
          :collapse="isCollapsed"
          :collapse-transition="false"
          unique-opened
          router
        >
          <el-menu-item index="/overview" aria-label="功能总览">
            <el-icon><LayoutGrid :size="18" /></el-icon>
            <template #title>功能总览</template>
          </el-menu-item>
          <el-sub-menu
            v-for="module in navigation"
            :key="module.id"
            :index="module.id"
            :aria-label="module.title"
          >
            <template #title>
              <el-icon>
                <component
                  :is="moduleIcons[module.icon as keyof typeof moduleIcons]"
                  :size="18"
                />
              </el-icon>
              <span>{{ module.title }}</span>
            </template>
            <el-menu-item
              v-for="item in module.entries"
              :key="item.path"
              :index="item.path"
              :aria-label="item.title"
            >
              {{ item.title }}
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </nav>
      <div class="identity">
        <el-avatar :size="32">
          {{ session.data?.user.displayName.slice(0, 1) }}
        </el-avatar>
        <div v-if="!isCollapsed" class="identity-text">
          <strong>{{ session.data?.user.displayName }}</strong>
          <span>{{ session.data?.user.roleName }}</span>
        </div>
        <el-tooltip content="退出登录">
          <el-button
            text
            class="icon-button"
            aria-label="退出登录"
            :disabled="loggingOut"
            @click="logout"
          >
            <LogOut :size="17" />
          </el-button>
        </el-tooltip>
      </div>
    </el-aside>
    <el-container class="workspace" direction="vertical">
      <el-header class="topbar" height="48px">
        <el-button
          text
          class="icon-button"
          :aria-label="isCollapsed ? '展开侧栏' : '收起侧栏'"
          :disabled="narrow"
          @click="collapsed = !collapsed"
        >
          <component
            :is="isCollapsed ? PanelLeftOpen : PanelLeftClose"
            :size="18"
          />
        </el-button>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/overview' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item v-if="activeMenu">
            {{ activeMenu.module.title }}
          </el-breadcrumb-item>
          <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="topbar-actions">
          <el-tooltip content="刷新当前页面">
            <el-button
              text
              class="icon-button"
              aria-label="刷新当前页面"
              @click="retryPage"
            >
              <RotateCw :size="18" />
            </el-button>
          </el-tooltip>
          <el-tooltip :content="isDark ? '切换到浅色模式' : '切换到深色模式'">
            <el-button
              text
              class="icon-button"
              :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
              @click="toggleTheme"
            >
              <component :is="isDark ? Sun : Moon" :size="18" />
            </el-button>
          </el-tooltip>
        </div>
      </el-header>
      <PageTabs />
      <el-main
        ref="content"
        class="page-container"
        :class="{ 'business-content': route.meta.business }"
        aria-label="功能区"
      >
        <div v-if="pageCrashed" class="page-crash-fallback">
          <TriangleAlert :size="34" />
          <h2>页面出了点问题</h2>
          <p>页面渲染时发生错误，其他功能不受影响。可以尝试重新加载这个页面。</p>
          <el-button type="primary" :icon="RotateCw" @click="retryPage">
            重新加载页面
          </el-button>
        </div>
        <RouterView v-else :key="pageRenderKey" />
      </el-main>
    </el-container>
  </el-container>
</template>
