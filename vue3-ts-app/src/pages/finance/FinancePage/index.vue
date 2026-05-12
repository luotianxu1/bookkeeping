<script setup lang="ts">
// 财务首页：组合资产总览、统计入口和收支明细列表。
import { useRouter } from 'vue-router'
import { financeDayGroups, financeOverview } from '@/data/finance'
import AssetOverviewCard from '../components/AssetOverviewCard/index.vue'
import StatsEntry from '../components/StatsEntry/index.vue'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'

const router = useRouter()

function openExpenseEntryPage() {
  router.push('/finance/entry/expense')
}
</script>

<template>
  <AssetOverviewCard :overview="financeOverview" />
  <section class="feature-block" aria-label="更多功能">
    <header class="feature-header">
      <strong>更多功能</strong>
      <RouterLink class="feature-link" to="/finance/more-features" aria-label="进入更多功能">
        >
      </RouterLink>
    </header>
    <StatsEntry />
  </section>

  <section class="records" aria-label="收支明细">
    <TransactionDayCard
      v-for="group in financeDayGroups"
      :key="group.date"
      :group="group"
      summary-mode="stacked"
    />
  </section>

  <FloatingAddButton aria-label="新增记账" @click="openExpenseEntryPage" />
</template>

<style scoped lang="scss" src="./style.scss"></style>
