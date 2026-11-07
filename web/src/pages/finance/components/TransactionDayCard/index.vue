<script setup lang="ts">
// 单日流水卡片：展示日期汇总和该日期下的交易记录，可用于首页和收支列表页。
import { ref } from 'vue'
import type { DayGroup } from '@/types/finance'
import AmountText from '@/components/common/AmountText/index.vue'
import CategoryIcon from '@/components/common/CategoryIcon/index.vue'
import SwipeActionGroup from '@/components/common/SwipeActionGroup/index.vue'

const props = withDefaults(defineProps<{
  group: DayGroup
  summaryMode?: 'inline' | 'stacked'
  showDelete?: boolean
  deletingId?: number | null
}>(), {
  summaryMode: 'inline',
  showDelete: false,
  deletingId: null,
})

const emit = defineEmits<{
  edit: [transaction: DayGroup['transactions'][number]]
  delete: [transaction: DayGroup['transactions'][number]]
}>()

const openedKey = ref('')
const draggingKey = ref('')
const dragOffset = ref(0)
const dragStart = ref({
  pointerId: 0,
  x: 0,
  y: 0,
  originOffset: 0,
  actionWidth: 0,
  active: false,
  horizontal: false,
})

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
  if (transaction.type === 'expense') return (transaction.rawAmount ?? 0) < 0 ? 'positive' : 'negative'
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
  return amount === 0 ? value : `${amount > 0 ? '-' : '+'}¥${Math.abs(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function transactionKey(transaction: DayGroup['transactions'][number]) {
  return String(transaction.id ?? `${props.group.date}-${transaction.name}-${transaction.time}`)
}

function rowStyle(transaction: DayGroup['transactions'][number]) {
  const key = transactionKey(transaction)
  if (draggingKey.value === key) {
    return { transform: `translateX(${dragOffset.value}px)` }
  }
  return {
    transform: openedKey.value === key ? 'translateX(calc(-1 * var(--action-width)))' : 'translateX(0)',
  }
}

// 仅真实流水支持滑动删除/编辑：债务、人情等聚合展示记录没有流水 id，
// 后端已不再为其下发合成 id，误删会打到 DELETE /finance/transactions/{id}
function canSwipeDelete(transaction: DayGroup['transactions'][number]) {
  return props.showDelete
    && transaction.sourceType === 'transaction'
    && Boolean(transaction.id)
}

function canEditTransaction(transaction: DayGroup['transactions'][number]) {
  return canSwipeDelete(transaction)
    && transaction.sourceType === 'transaction'
    && Boolean(transaction.accountId)
    && !transaction.originalTransactionId
    && Boolean(transaction.categoryId)
    && Boolean(transaction.occurredAt)
}

function canRecoverTransaction(transaction: DayGroup['transactions'][number]) {
  return canSwipeDelete(transaction)
    && transaction.type === 'expense'
    && !transaction.originalTransactionId
}

function rowActionCount(transaction: DayGroup['transactions'][number]) {
  return 1 + Number(canEditTransaction(transaction)) + Number(canRecoverTransaction(transaction))
}

function handlePointerDown(event: PointerEvent, transaction: DayGroup['transactions'][number]) {
  if (!canSwipeDelete(transaction)) {
    return
  }

  const key = transactionKey(transaction)
  const target = event.currentTarget as HTMLElement
  // 操作按钮会随 rem 缩放；拖动距离必须使用实际渲染宽度。
  const actionWidth = target.parentElement?.querySelector('.transaction-actions')?.getBoundingClientRect().width ?? 0
  if (!actionWidth) return
  target.setPointerCapture(event.pointerId)
  draggingKey.value = key
  dragOffset.value = openedKey.value === key ? -actionWidth : 0
  dragStart.value = {
    pointerId: event.pointerId,
    x: event.clientX,
    y: event.clientY,
    originOffset: dragOffset.value,
    actionWidth,
    active: true,
    horizontal: false,
  }
}

function handlePointerMove(event: PointerEvent) {
  if (!dragStart.value.active || event.pointerId !== dragStart.value.pointerId) {
    return
  }

  const deltaX = event.clientX - dragStart.value.x
  const deltaY = event.clientY - dragStart.value.y
  if (!dragStart.value.horizontal && Math.abs(deltaX) > 8 && Math.abs(deltaX) > Math.abs(deltaY)) {
    dragStart.value.horizontal = true
  }
  if (!dragStart.value.horizontal) {
    return
  }

  event.preventDefault()
  dragOffset.value = Math.min(0, Math.max(-dragStart.value.actionWidth, dragStart.value.originOffset + deltaX))
}

function handlePointerUp(event: PointerEvent) {
  if (!dragStart.value.active || event.pointerId !== dragStart.value.pointerId) {
    return
  }

  openedKey.value = dragOffset.value <= -(dragStart.value.actionWidth / 2) ? draggingKey.value : ''
  draggingKey.value = ''
  dragOffset.value = 0
  dragStart.value.active = false
  dragStart.value.horizontal = false
}

function handleEdit(transaction: DayGroup['transactions'][number]) {
  openedKey.value = ''
  emit('edit', transaction)
}

function handleDelete(transaction: DayGroup['transactions'][number]) {
  openedKey.value = ''
  emit('delete', transaction)
}
</script>

<template>
  <article :class="['day-card', `day-card-${summaryMode}`]">
    <header class="day-header">
      <div class="day-title-row">
        <div class="day-title">
          <span class="day-title-dot" aria-hidden="true"></span>
          <strong>{{ group.date }}</strong>
        </div>
        <span>共 {{ group.transactions.length }} 笔</span>
      </div>

      <div class="day-summary-group">
        <div class="day-summary">
          <span>收入</span>
          <AmountText tag="strong" :value="group.income" :tone="summaryTone(group.income)" />
        </div>
        <div class="day-summary">
          <span>支出</span>
          <AmountText
            tag="strong"
            :value="expenseSummaryAmount(group.expense)"
            :tone="summaryTone(expenseSummaryAmount(group.expense))"
          />
        </div>
        <div v-if="summaryMode === 'stacked'" class="day-summary">
          <span>盈余</span>
          <AmountText tag="strong" :value="group.surplus" :tone="summaryTone(group.surplus)" />
        </div>
      </div>
    </header>

    <ul class="transaction-list">
      <li
        v-for="transaction in group.transactions"
        :key="transactionKey(transaction)"
        class="transaction-row"
        :class="{ 'transaction-row-swipeable': canSwipeDelete(transaction) }"
        :style="{ '--action-count': rowActionCount(transaction) }"
      >
        <div
          v-if="canSwipeDelete(transaction)"
          class="transaction-actions"
        >
          <RouterLink
            v-if="canRecoverTransaction(transaction)"
            class="recovery-action"
            :to="`/finance/expense-recoveries?transactionId=${transaction.id}`"
            :aria-label="`退款或报销${transaction.name}`"
            title="退款 / 报销"
            @click="openedKey = ''"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M3 10A9 9 0 1 1 5.6 18.4M3 4V10H9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M9 9L12 12L15 9M9 13H15M12 12V17" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </RouterLink>
          <SwipeActionGroup
            :show-edit="canEditTransaction(transaction)"
            :deleting="deletingId === transaction.id"
            :edit-label="`修改${transaction.name}`"
            :delete-label="`删除${transaction.name}`"
            @edit="handleEdit(transaction)"
            @delete="handleDelete(transaction)"
          />
        </div>
        <div
          class="transaction-item"
          :style="rowStyle(transaction)"
          @pointerdown="handlePointerDown($event, transaction)"
          @pointermove="handlePointerMove"
          @pointerup="handlePointerUp"
          @pointercancel="handlePointerUp"
        >
          <span class="transaction-copy">
            <CategoryIcon
              class="transaction-icon"
              :icon="transaction.categoryIcon"
              :color="transaction.categoryColor"
              :name="transaction.category || transaction.name"
              :size="36"
            />
            <span class="transaction-copy-main">
              <strong>{{ transaction.name }}</strong>
              <span>{{ transaction.time }}</span>
            </span>
          </span>
          <AmountText
            tag="strong"
            :value="transactionAmount(transaction)"
            :tone="transactionTone(transaction)"
            class="amount"
          />
        </div>
      </li>
    </ul>
  </article>
</template>

<style scoped lang="scss" src="./style.scss"></style>
