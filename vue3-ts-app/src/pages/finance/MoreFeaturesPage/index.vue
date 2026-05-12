<script setup lang="ts">
// 财务更多功能页：按业务类型分组展示财务功能入口。
import PageHeader from '@/components/common/PageHeader/index.vue'

type FeatureItem = {
  icon: string
  label: string
  to: string
}

type FeatureGroup = {
  title: string
  items: FeatureItem[]
}

const featureGroups: FeatureGroup[] = [
  {
    title: '资金管理',
    items: [
      { icon: '🗂', label: '分类管理', to: '/finance/categories' },
      { icon: '📒', label: '收支记录', to: '/finance/transactions' },
      { icon: '📉', label: '收支分析', to: '/finance/analysis' },
      { icon: '🏦', label: '账户管理', to: '/finance/accounts' },
      { icon: '📈', label: '收益预测', to: '/finance/profit-forecast' },
      { icon: '🪙', label: '攒股收息', to: '/finance/dividend-income' },
    ],
  },
  {
    title: '行情工具',
    items: [
      { icon: '🥇', label: '金价', to: '/finance/gold-price' },
      { icon: '📰', label: '市场快讯', to: '/finance/gold-price' },
      { icon: '💱', label: '汇率换算', to: '/finance/exchange-rate' },
    ],
  },
]
</script>

<template>
  <section class="more-features-page" aria-label="财务更多功能">
    <PageHeader title="更多功能" back-to="/finance" back-label="返回财务首页" />

    <section v-for="group in featureGroups" :key="group.title" class="feature-group">
      <h2>{{ group.title }}</h2>
      <div class="feature-grid">
        <RouterLink
          v-for="item in group.items"
          :key="item.label"
          class="feature-item"
          :to="item.to"
        >
          <span class="feature-icon">{{ item.icon }}</span>
          <strong>{{ item.label }}</strong>
        </RouterLink>
      </div>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
