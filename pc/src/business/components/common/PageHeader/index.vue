<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft } from '@lucide/vue'
import {
  useSlots,
  computed,
  ref,
  onMounted,
  onBeforeUnmount,
  watch,
  nextTick,
} from 'vue'
import { hasRenderableSlotContent } from '@/business/utils/slotContent'
const props = defineProps<{
  title: string
  backTo?: string
  backLabel?: string
  preferBackTo?: boolean
}>()
const router = useRouter()
const route = useRoute()
const slots = useSlots()
// 插槽即使存在，内容也可能全为空（如空 <template #right> 或内部 v-if 均为 false，
// 后者会渲染出 children 为 "v-if" 的注释节点）。渲染一次插槽并检查有效节点，
// 确保空 header 真正被隐藏，不残留顶部留白。
const hasSlotContent = computed(() => {
  const renderSlot = slots.right || slots.default
  return renderSlot ? hasRenderableSlotContent(renderSlot()) : false
})
// 有返回按钮（详情页）或确实有操作内容时才显示整行
const showHeader = computed(
  () => Object.keys(route.params).length > 0 || hasSlotContent.value,
)

// 页面头部降级为浮动操作条：脱离 grid 流、浮到 workspace 右上角，不再占独立一行。
// 仅当本组件是 .desktop-workspace 的直接子元素时启用（.market-header 等特例保持原样）。
// 显示时给直接父级挂标记，由 CSS 提供顶部避让；用 class 而非 :has()，兼容性与可控性更好。
const rootEl = ref<HTMLElement | null>(null)
let workspaceEl: HTMLElement | null = null
let headerObserver: ResizeObserver | null = null

function resolveWorkspace() {
  const parent = rootEl.value?.parentElement
  workspaceEl =
    parent?.classList.contains('desktop-workspace') ? parent : null
}

// 浮动条高度随内容变化（多按钮换行、年份/家庭视角选择器等比按钮高）。
// 把实际高度写进 CSS 变量，让 workspace 顶部避让精确匹配，避免遮挡首块。
function syncHeaderHeight() {
  if (!workspaceEl || !rootEl.value) return
  const height = showHeader.value ? rootEl.value.getBoundingClientRect().height : 0
  workspaceEl.style.setProperty('--floating-header-h', `${Math.ceil(height)}px`)
}

function syncFloating() {
  if (!workspaceEl) return
  workspaceEl.classList.toggle('has-floating-actions', showHeader.value)
  void nextTick(syncHeaderHeight)
}

onMounted(async () => {
  await nextTick()
  resolveWorkspace()
  syncFloating()
  if (rootEl.value) {
    headerObserver = new ResizeObserver(syncHeaderHeight)
    headerObserver.observe(rootEl.value)
  }
})
watch(showHeader, syncFloating)
onBeforeUnmount(() => {
  headerObserver?.disconnect()
  headerObserver = null
  workspaceEl?.classList.remove('has-floating-actions')
  workspaceEl?.style.removeProperty('--floating-header-h')
  workspaceEl = null
})

function goBack() {
  if (props.backTo) return router.push(props.backTo)
  if (router.options.history.state.back) return router.back()
  const parent = route.path.slice(0, route.path.lastIndexOf('/'))
  return router.push(
    router.resolve(parent).matched.length ? parent : '/overview',
  )
}
</script>
<template>
  <!-- 没有返回按钮、也没有任何操作插槽内容时隐藏整行，避免顶部留白；
       保留 DOM 容器（v-show 而非 v-if），FloatingAddButton 的 Teleport 目标仍在。 -->
  <header
    ref="rootEl"
    v-show="showHeader"
    class="pc-page-header"
    :class="{ 'has-back': Object.keys(route.params).length > 0 }"
  >
    <el-button
      v-if="Object.keys(route.params).length"
      text
      :aria-label="backLabel || '返回'"
      @click="goBack"
    >
      <ArrowLeft :size="16" />
    </el-button>
    <div class="pc-page-actions">
      <slot name="right"><slot /></slot>
    </div>
  </header>
</template>
