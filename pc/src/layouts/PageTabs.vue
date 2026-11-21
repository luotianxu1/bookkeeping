<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Ellipsis, X } from '@lucide/vue'
import {
  pageTabs,
  closePageTab,
  closeOtherPageTabs,
  closeRightPageTabs,
  closeAllPageTabs,
  type PageTab,
} from '@/stores/pageTabs'

const route = useRoute()
const router = useRouter()
const scroller = ref<HTMLElement>()
const activeKey = computed(() => route.fullPath)
const activeTab = computed(() =>
  pageTabs.find((tab) => tab.key === activeKey.value),
)

function activate(tab: PageTab) {
  if (tab.key !== route.fullPath) void router.push(tab.key)
}

function remove(tab: PageTab) {
  const index = pageTabs.findIndex((item) => item.key === tab.key)
  const wasActive = tab.key === route.fullPath
  closePageTab(tab.key)
  if (!wasActive) return
  // 关闭当前标签后，优先落到右侧邻居；没有则落到左侧邻居。
  const next = pageTabs[Math.min(index, pageTabs.length - 1)]
  void router.push(next?.key ?? '/overview')
}

function onCommand(command: string, tab: PageTab) {
  switch (command) {
    case 'close':
      remove(tab)
      break
    case 'others':
      closeOtherPageTabs(tab.key)
      break
    case 'right':
      closeRightPageTabs(tab.key)
      break
    case 'all':
      closeAllPageTabs()
      if (route.fullPath !== '/overview') void router.push('/overview')
      break
  }
}

function onWheel(event: WheelEvent) {
  const element = scroller.value
  if (!element || Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return
  // 竖向滚轮在标签条上转为横向滚动，避免误触页面滚动。
  if (
    (event.deltaY > 0 && element.scrollLeft < element.scrollWidth - element.clientWidth) ||
    (event.deltaY < 0 && element.scrollLeft > 0)
  ) {
    event.preventDefault()
    element.scrollLeft += event.deltaY
  }
}

watch(
  activeKey,
  async () => {
    await nextTick()
    scroller.value
      ?.querySelector('.page-tab.is-active')
      ?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  },
  { immediate: true, flush: 'post' },
)
</script>

<template>
  <div class="page-tabs" role="tablist" aria-label="已打开页面">
    <div ref="scroller" class="page-tabs-scroll" @wheel="onWheel">
      <el-dropdown
        v-for="tab in pageTabs"
        :key="tab.key"
        trigger="contextmenu"
        @command="(command: string) => onCommand(command, tab)"
      >
        <div
          class="page-tab"
          :class="{ 'is-active': tab.key === activeKey, 'is-fixed': tab.fixed }"
          role="tab"
          :aria-selected="tab.key === activeKey"
          :title="tab.key"
          @click="activate(tab)"
          @auxclick.middle="remove(tab)"
        >
          <span class="page-tab-label">{{ tab.title }}</span>
          <el-button
            v-if="!tab.fixed"
            text
            class="page-tab-close"
            :aria-label="`关闭${tab.title}`"
            @click.stop="remove(tab)"
          >
            <X :size="12" />
          </el-button>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="close" :disabled="tab.fixed">
              关闭此页
            </el-dropdown-item>
            <el-dropdown-item command="others">关闭其他</el-dropdown-item>
            <el-dropdown-item command="right">关闭右侧</el-dropdown-item>
            <el-dropdown-item command="all">关闭全部</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <el-dropdown
      trigger="click"
      @command="(command: string) => activeTab && onCommand(command, activeTab)"
    >
      <button class="page-tabs-more" type="button" aria-label="标签页操作">
        <Ellipsis :size="16" />
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="others">关闭其他标签</el-dropdown-item>
          <el-dropdown-item command="right">关闭右侧标签</el-dropdown-item>
          <el-dropdown-item command="all">关闭全部标签</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>
