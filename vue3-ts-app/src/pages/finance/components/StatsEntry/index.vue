<script setup lang="ts">
// 首页快捷入口：按设计稿展示 4 个正方形入口。
const quickEntries = [
  { icon: '🏦', label: '账户管理', to: '/finance/accounts' },
  { icon: '🥇', label: '金价', to: '/finance/gold-price' },
  { icon: '📈', label: '收益预测', to: '/finance/profit-forecast' },
  { icon: '📉', label: '收支分析', to: '/finance/analysis' },
]
</script>

<template>
  <section class="stats-entry-grid" aria-label="首页快捷入口">
    <RouterLink v-for="entry in quickEntries" :key="entry.label" class="stats-entry-item" :to="entry.to">
      <span class="entry-icon">{{ entry.icon }}</span>
      <strong>{{ entry.label }}</strong>
    </RouterLink>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
