<script setup lang="ts">
// 工具页：展示常用工具入口卡片。
type ToolItem = {
  key: string
  title: string
  short: string
  tone: 'blue' | 'purple' | 'sky' | 'green' | 'orange'
  featured?: boolean
}

const toolItems: ToolItem[] = [
  { key: 'todo', title: '代办事项', short: '办', tone: 'blue', featured: true },
  { key: 'anniversary', title: '纪念日', short: '纪', tone: 'purple' },
  { key: 'calendar', title: '日历', short: '历', tone: 'sky' },
  { key: 'checkin', title: '打卡', short: '打', tone: 'green' },
]
</script>

<template>
  <section class="tools-page" aria-label="工具页面">
    <section class="tools-grid" aria-label="工具入口">
      <button
        v-for="item in toolItems"
        :key="item.key"
        :class="['tool-card', `tone-${item.tone}`, { featured: item.featured }]"
        type="button"
      >
        <span :class="['tool-icon', `tone-${item.tone}`]">{{ item.short }}</span>
        <span class="tool-title">{{ item.title }}</span>
      </button>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
