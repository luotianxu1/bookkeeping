<script setup lang="ts">
// 单日流水卡片：展示日期汇总和该日期下的交易记录，可用于首页和收支列表页。

import type { DayGroup } from '@/business/types/finance'
import AmountText from '@/business/components/common/AmountText/index.vue'
import CategoryIcon from '@/business/components/common/CategoryIcon/index.vue'
import SwipeActionGroup from '@/business/components/common/SwipeActionGroup/index.vue'

const props = withDefaults(
  defineProps<{
    group: DayGroup
    summaryMode?: 'inline' | 'stacked'
    showDelete?: boolean
    deletingId?: number | null
  }>(),
  {
    summaryMode: 'inline',
    showDelete: false,
    deletingId: null,
  },
)

const emit = defineEmits<{
  edit: [transaction: DayGroup['transactions'][number]]
  delete: [transaction: DayGroup['transactions'][number]]
}>()

function negativeAmount(value: string) {
  const normalized = value.trim()
  return normalized.startsWith('-') ? normalized : `-${normalized}`
}

function transactionAmount(transaction: DayGroup['transactions'][number]) {
  if (transaction.type !== 'expense' || (transaction.rawAmount ?? 0) < 0) {
    return transaction.amount
  }

  return negativeAmount(transaction.amount)
}

function transactionTone(transaction: DayGroup['transactions'][number]) {
  if (transaction.type === 'expense')
    return (transaction.rawAmount ?? 0) < 0 ? 'positive' : 'negative'
  if (transaction.type === 'transfer') return 'neutral'
  return 'positive'
}

function amountNumber(value: string) {
  const normalized = value.trim().replace(/[^\d.+-]/g, '')
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : 0
}

function summaryTone(value: string) {
  const parsed = amountNumber(value)
  if (parsed > 0) return 'positive'
  if (parsed < 0) return 'negative'
  return 'neutral'
}

function expenseSummaryAmount(value: string) {
  const amount = amountNumber(value)
  return amount === 0
    ? value
    : `${amount > 0 ? '-' : '+'}¥${Math.abs(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function canSwipeDelete(transaction: DayGroup['transactions'][number]) {
  return (
    props.showDelete &&
    !transaction.transactionNo?.startsWith('TD-') &&
    transaction.sourceType === 'transaction' &&
    Boolean(transaction.id)
  )
}

function canEditTransaction(transaction: DayGroup['transactions'][number]) {
  return (
    canSwipeDelete(transaction) &&
    transaction.sourceType === 'transaction' &&
    Boolean(transaction.accountId) &&
    !transaction.originalTransactionId &&
    Boolean(transaction.categoryId) &&
    Boolean(transaction.occurredAt)
  )
}

function canRecoverTransaction(transaction: DayGroup['transactions'][number]) {
  return (
    canSwipeDelete(transaction) &&
    transaction.type === 'expense' &&
    !transaction.originalTransactionId
  )
}

type Transaction = DayGroup['transactions'][number]
</script>
<template>
  <el-card shadow="never" class="pc-record-card">
    <div class="pc-toolbar">
      <h3>{{ group.date }}</h3>
      <span>收入 {{ group.income }}</span>
      <span>支出 {{ expenseSummaryAmount(group.expense) }}</span>
      <span>盈余 {{ group.surplus }}</span>
      <el-tag type="info">{{ group.transactions.length }} 笔</el-tag>
    </div>
    <el-table :data="group.transactions" stripe>
      <!-- @vue-generic {Transaction} -->
      <el-table-column
        label="时间"
        prop="time"
        min-width="100"
        show-overflow-tooltip
      />
      <!-- @vue-generic {Transaction} -->
      <el-table-column
        label="名称"
        prop="name"
        min-width="200"
        show-overflow-tooltip
      />
      <!-- @vue-generic {Transaction} -->
      <el-table-column
        label="分类"
        prop="category"
        min-width="140"
        show-overflow-tooltip
      />
      <!-- @vue-generic {Transaction} -->
      <el-table-column label="金额" min-width="160">
        <template #default="{ row }: { row: Transaction }">
          <AmountText
            :value="transactionAmount(row)"
            :tone="transactionTone(row)"
          />
        </template>
      </el-table-column>
      <!-- @vue-generic {Transaction} -->
      <el-table-column
        label="备注"
        prop="remark"
        min-width="200"
        show-overflow-tooltip
      />
      <!-- @vue-generic {Transaction} -->
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }: { row: Transaction }">
          <div class="pc-row-actions">
            <el-button
              link
              type="primary"
              v-if="canEditTransaction(row)"
              @click="emit('edit', row)"
            >
              编辑
            </el-button>
            <RouterLink
              v-if="canRecoverTransaction(row)"
              :to="'/finance/expense-recoveries?transactionId=' + row.id"
            >
              退款 / 报销
            </RouterLink>
            <el-button
              link
              type="danger"
              v-if="canSwipeDelete(row)"
              :disabled="deletingId === row.id"
              @click="emit('delete', row)"
            >
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
