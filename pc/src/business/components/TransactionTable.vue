<script setup lang="ts">
// 桌面流水明细：保留交易来源、定期存款及退款报销的操作限制。

import type { DayGroup } from '@/business/types/finance'
import DesktopTable from '@/business/components/DesktopTable.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'

const props = withDefaults(
  defineProps<{
    rows: DayGroup['transactions']
    loading?: boolean
    paginate?: boolean
    title?: string
    showDelete?: boolean
    deletingId?: number | null
  }>(),
  {
    loading: false,
    paginate: true,
    title: '收支明细',
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

function canDeleteTransaction(transaction: DayGroup['transactions'][number]) {
  return (
    props.showDelete &&
    !transaction.transactionNo?.startsWith('TD-') &&
    transaction.sourceType === 'transaction' &&
    Boolean(transaction.id)
  )
}

function canEditTransaction(transaction: DayGroup['transactions'][number]) {
  return (
    canDeleteTransaction(transaction) &&
    transaction.sourceType === 'transaction' &&
    Boolean(transaction.accountId) &&
    !transaction.originalTransactionId &&
    Boolean(transaction.categoryId) &&
    Boolean(transaction.occurredAt)
  )
}

function canRecoverTransaction(transaction: DayGroup['transactions'][number]) {
  return (
    canDeleteTransaction(transaction) &&
    transaction.type === 'expense' &&
    !transaction.originalTransactionId
  )
}

type Transaction = DayGroup['transactions'][number]
</script>
<template>
  <DesktopTable
    :rows="rows"
    :loading="loading"
    :paginate="paginate"
    :title="title"
  >
    <!-- @vue-generic {Transaction} -->
    <el-table-column
      label="时间"
      prop="occurredAt"
      min-width="175"
      show-overflow-tooltip
    >
      <template #default="{ row }: { row: Transaction }">
        {{ row.occurredAt?.replace('T', ' ').slice(0, 16) || '—' }}
      </template>
    </el-table-column>
    <!-- @vue-generic {Transaction} -->
    <el-table-column
      label="名称"
      prop="name"
      min-width="200"
      show-overflow-tooltip
    />
    <!-- @vue-generic {Transaction} -->
    <el-table-column
      label="账户"
      prop="accountName"
      min-width="140"
      show-overflow-tooltip
    />
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
    <el-table-column v-if="showDelete" label="操作" width="230" fixed="right">
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
            v-if="canDeleteTransaction(row)"
            :disabled="deletingId === row.id"
            @click="emit('delete', row)"
          >
            删除
          </el-button>
        </div>
      </template>
    </el-table-column>
  </DesktopTable>
</template>
