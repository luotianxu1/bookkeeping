<script setup lang="ts">
// 收支列表页：还原 Pencil「收支列表页」画板中的筛选与日期分组流水。
import { ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import { financeDayGroups } from '@/data/finance'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'

const filterOptions = ['全部', '收入', '支出']
const activeFilter = ref(filterOptions[0])
</script>

<template>
  <section class="transaction-list-page" aria-label="收支列表">
    <PageHeader title="收支列表" back-to="/finance" back-label="返回财务首页" />

    <SegmentedControl
      v-model="activeFilter"
      :options="filterOptions"
      label="收支类型筛选"
    />

    <section class="transaction-list-groups" aria-label="收支明细">
      <TransactionDayCard
        v-for="group in financeDayGroups"
        :key="group.date"
        :group="group"
        summary-mode="stacked"
      />
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
