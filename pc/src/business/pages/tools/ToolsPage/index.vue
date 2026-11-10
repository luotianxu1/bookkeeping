<script setup lang="ts">
import PageHeader from '@/business/components/common/PageHeader/index.vue'

// 工具页：展示常用工具入口卡片。
import { RouterLink } from 'vue-router'
type ToolItem = {
  key: string
  title: string
  short: string
  tone: 'blue' | 'purple' | 'sky' | 'green' | 'orange'
  featured?: boolean
  to?: string
}

const toolItems: ToolItem[] = [
  {
    key: 'todo',
    title: '代办事项',
    short: '办',
    tone: 'blue',
    featured: true,
    to: '/tools/todo-items',
  },
  {
    key: 'anniversary',
    title: '纪念日',
    short: '纪',
    tone: 'purple',
    featured: true,
    to: '/tools/anniversaries',
  },
  {
    key: 'calendar',
    title: '日历',
    short: '历',
    tone: 'sky',
    to: '/tools/calendar',
  },
  { key: 'checkin', title: '打卡', short: '打', tone: 'green' },
  {
    key: 'photography-orders',
    title: '摄影订单',
    short: '摄',
    tone: 'blue',
    featured: true,
    to: '/tools/photography-orders',
  },
  {
    key: 'contacts',
    title: '联系人',
    short: '联',
    tone: 'orange',
    featured: true,
    to: '/tools/contacts',
  },
  {
    key: 'health',
    title: '健康管理',
    short: '健',
    tone: 'green',
    featured: true,
    to: '/tools/health',
  },
  {
    key: 'travel-plans',
    title: '旅行管理',
    short: '旅',
    tone: 'green',
    featured: true,
    to: '/tools/travel-plans',
  },
]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="生活工具"><template #right></template></PageHeader>
    <div class="desktop-tool-catalog">
      <RouterLink
        v-for="item in toolItems.filter((item) => item.to)"
        :key="item.key"
        :to="item.to!"
        class="desktop-tool-entry"
      >
        <span class="desktop-tool-symbol">{{ item.short }}</span>
        <strong>{{ item.title }}</strong>
        <span aria-hidden="true">→</span>
      </RouterLink>
    </div>
  </section>
</template>
