<script setup lang="ts">
// 首页快捷入口：按设计稿展示 4 个正方形入口。
import FeatureIcon, { type FeatureIconKey } from '@/components/common/FeatureIcon/index.vue'

const quickEntries = [
  { icon: 'accounts' as FeatureIconKey, label: '账户管理', to: '/finance/accounts' },
  { icon: 'gold-price' as FeatureIconKey, label: '金价', to: '/finance/gold-price' },
  { icon: 'profit-forecast' as FeatureIconKey, label: '收益预测', to: '/finance/profit-forecast' },
  { icon: 'analysis' as FeatureIconKey, label: '收支分析', to: '/finance/analysis' },
]
</script>

<template>
  <section class="stats-entry-grid" aria-label="首页快捷入口">
    <RouterLink v-for="entry in quickEntries" :key="entry.label" class="stats-entry-item" :to="entry.to">
      <FeatureIcon :icon="entry.icon" :label="entry.label" :size="32" />
      <strong>{{ entry.label }}</strong>
    </RouterLink>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
