<script setup lang="ts">
// 单日流水卡片：展示日期汇总和该日期下的交易记录，可用于首页和收支列表页。
import type { DayGroup } from '@/types/finance'
import AmountText from '@/components/common/AmountText/index.vue'

withDefaults(defineProps<{
  group: DayGroup
  summaryMode?: 'inline' | 'stacked'
}>(), {
  summaryMode: 'inline',
})

function negativeAmount(value: string) {
  const normalized = value.trim()
  return normalized.startsWith('-') ? normalized : `-${normalized}`
}

function transactionAmount(transaction: DayGroup['transactions'][number]) {
  if (transaction.type !== 'expense') {
    return transaction.amount
  }

  return negativeAmount(transaction.amount)
}

function transactionTone(transaction: DayGroup['transactions'][number]) {
  return transaction.type === 'expense' ? 'negative' : 'positive'
}
</script>

<template>
  <article :class="['day-card', `day-card-${summaryMode}`]">
    <header class="day-header">
      <div class="day-title">
        <strong>{{ group.date }}</strong>
        <span>共 {{ group.transactions.length }} 笔</span>
      </div>

      <div class="day-summary-group">
        <div class="day-summary">
          <span>收入</span>
          <AmountText tag="strong" :value="group.income" />
        </div>
        <div class="day-summary">
          <span>支出</span>
          <AmountText tag="strong" :value="negativeAmount(group.expense)" tone="negative" />
        </div>
        <div v-if="summaryMode === 'stacked'" class="day-summary">
          <span>盈余</span>
          <AmountText tag="strong" :value="group.surplus" />
        </div>
      </div>
    </header>

    <ul class="transaction-list">
      <li
        v-for="transaction in group.transactions"
        :key="`${group.date}-${transaction.name}`"
        class="transaction-item"
      >
        <span class="transaction-copy">
          <strong>{{ transaction.name }}</strong>
          <span>{{ transaction.time }}</span>
        </span>
        <AmountText
          tag="strong"
          :value="transactionAmount(transaction)"
          :tone="transactionTone(transaction)"
          class="amount"
        />
      </li>
    </ul>
  </article>
</template>

<style scoped lang="scss" src="./style.scss"></style>
