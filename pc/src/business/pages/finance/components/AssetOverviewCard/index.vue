<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

// 资产总览卡片：展示总资产、月结余、收入支出和预算进度。
import type { FinanceOverview } from '@/business/types/finance'
import AmountText from '@/business/components/common/AmountText/index.vue'

defineProps<{
  overview: FinanceOverview
}>()
</script>

<template>
  <el-card shadow="never" class="pc-record-card asset-card">
    <div class="pc-toolbar">
      <div>
        <small>总资产</small>
        <AmountText tag="h1" :value="overview.totalAssets || '--'" />
      </div>
      <el-progress
        :format="formatPercentage"
        type="circle"
        :width="80"
        :stroke-width="7"
        :percentage="Math.min(100, overview.budgetUsagePercent)"
        :status="overview.isBudgetExceeded ? 'exception' : undefined"
      />
    </div>
    <div class="pc-statistics">
      <div
        v-for="(value, label) in {
          当月结余: overview.monthlyBalance,
          月预算: overview.budget,
          收入: overview.monthlyIncome,
          支出: overview.monthlyExpense,
        }"
        :key="label"
      >
        <small>{{ label }}</small>
        <strong>{{ value.replace(label + ' ', '') }}</strong>
      </div>
    </div>
  </el-card>
</template>
