<script setup lang="ts">
// 财务更多功能页：按业务类型分组展示财务功能入口。
import PageHeader from '@/components/common/PageHeader/index.vue'
import FeatureIcon, { type FeatureIconKey } from '@/components/common/FeatureIcon/index.vue'

type FeatureItem = {
  icon: FeatureIconKey
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
      { icon: 'category-management', label: '分类管理', to: '/finance/categories' },
      { icon: 'transactions', label: '收支记录', to: '/finance/transactions' },
      { icon: 'analysis', label: '收支分析', to: '/finance/analysis' },
      { icon: 'trend', label: '资产趋势', to: '/finance/trend' },
      { icon: 'accounts', label: '账户管理', to: '/finance/accounts' },
      { icon: 'salary', label: '工资管理', to: '/finance/salary' },
      { icon: 'fund-profit', label: '基金收益', to: '/finance/fund-profit' },
      { icon: 'dividend-income', label: '攒股收息', to: '/finance/dividend-income' },
      { icon: 'budget', label: '预算管理', to: '/finance/budgets' },
      { icon: 'renewals', label: '固定支出', to: '/finance/renewals' },
    ],
  },
  {
    title: '行情工具',
    items: [
      { icon: 'stock-screener', label: 'A股选股', to: '/finance/stock-screener' },
      { icon: 'gold-price', label: '金价', to: '/finance/gold-price' },
      { icon: 'us-market', label: '美股行情', to: '/finance/us-market' },
      { icon: 'market-news', label: '市场快讯', to: '/finance/market-news' },
      { icon: 'exchange-rate', label: '汇率换算', to: '/finance/exchange-rate' },
      { icon: 'profit-forecast', label: '收益预测', to: '/finance/profit-forecast' },
      { icon: 'break-even', label: '回本预测', to: '/finance/break-even-forecast' },
      { icon: 'dividend-forecast', label: '收息预测', to: '/finance/dividend-forecast' },
      { icon: 'free-estimate', label: 'Free测算', to: '/finance/free-estimate' },
    ],
  },
]
</script>

<template>
  <section class="more-features-page" aria-label="财务更多功能">
    <PageHeader title="更多功能" back-to="/finance" back-label="返回财务首页" :prefer-back-to="true" />

    <section v-for="group in featureGroups" :key="group.title" class="feature-group">
      <h2>{{ group.title }}</h2>
      <div class="feature-grid">
        <RouterLink
          v-for="item in group.items"
          :key="item.label"
          class="feature-item"
          :to="item.to"
        >
          <FeatureIcon :icon="item.icon" :label="item.label" :size="32" />
          <strong>{{ item.label }}</strong>
        </RouterLink>
      </div>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
