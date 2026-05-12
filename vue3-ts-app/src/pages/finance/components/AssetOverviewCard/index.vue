<script setup lang="ts">
// 资产总览卡片：展示总资产、月结余、收入支出和预算进度。
import type { FinanceOverview } from '@/types/finance'
import AmountText from '@/components/common/AmountText/index.vue'

defineProps<{
  overview: FinanceOverview
}>()
</script>

<template>
  <section class="asset-card" aria-label="资产总览">
    <div class="asset-main">
      <div class="asset-main-left">
        <p class="asset-label">资产总数</p>
        <AmountText tag="h1" :value="overview.totalAssets" />
      </div>

      <div class="budget-ring-wrap" :aria-label="`预算使用 ${overview.budgetUsageLabel}`">
        <div
          class="budget-ring"
          :style="{ '--usage': `${overview.budgetUsagePercent}%` }"
        >
          <div class="budget-ring-inner">
            <span>已用</span>
            <strong>{{ overview.budgetUsageLabel.replace('已用 ', '') }}</strong>
          </div>
        </div>
      </div>
    </div>

    <div class="asset-metrics">
      <div class="asset-metric-item">
        <span>当月结余</span>
        <AmountText tag="strong" :value="overview.monthlyBalance.replace('当月结余 ', '')" />
      </div>
      <div class="asset-metric-item">
        <span>月预算</span>
        <AmountText tag="strong" :value="overview.budget.replace('月预算 ', '')" />
      </div>
      <div class="asset-metric-item">
        <span>收入</span>
        <AmountText tag="strong" :value="overview.monthlyIncome.replace('收入 ', '')" />
      </div>
      <div class="asset-metric-item">
        <span>支出</span>
        <AmountText tag="strong" :value="overview.monthlyExpense.replace('支出 ', '')" />
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
