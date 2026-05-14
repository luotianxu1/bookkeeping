<script setup lang="ts">
// 单日流水卡片：展示日期汇总和该日期下的交易记录，可用于首页和收支列表页。
import { ref } from 'vue'
import type { DayGroup } from '@/types/finance'
import AmountText from '@/components/common/AmountText/index.vue'

const DELETE_WIDTH = 72
const OPEN_THRESHOLD = 36

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
  active: false,
  horizontal: false,
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
  return amountNumber(value) === 0 ? value : negativeAmount(value)
}

function transactionKey(transaction: DayGroup['transactions'][number]) {
  return String(transaction.id ?? `${props.group.date}-${transaction.name}-${transaction.time}`)
}

function rowOffset(transaction: DayGroup['transactions'][number]) {
  const key = transactionKey(transaction)
  if (draggingKey.value === key) {
    return dragOffset.value
  }
  return openedKey.value === key ? -DELETE_WIDTH : 0
}

function rowStyle(transaction: DayGroup['transactions'][number]) {
  return {
    transform: `translateX(${rowOffset(transaction)}px)`,
  }
}

function canSwipeDelete(transaction: DayGroup['transactions'][number]) {
  return props.showDelete && Boolean(transaction.id)
}

function handlePointerDown(event: PointerEvent, transaction: DayGroup['transactions'][number]) {
  if (!canSwipeDelete(transaction)) {
    return
  }

  const key = transactionKey(transaction)
  const target = event.currentTarget as HTMLElement
  target.setPointerCapture(event.pointerId)
  draggingKey.value = key
  dragOffset.value = openedKey.value === key ? -DELETE_WIDTH : 0
  dragStart.value = {
    pointerId: event.pointerId,
    x: event.clientX,
    y: event.clientY,
    originOffset: dragOffset.value,
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
  dragOffset.value = Math.min(0, Math.max(-DELETE_WIDTH, dragStart.value.originOffset + deltaX))
}

function handlePointerUp(event: PointerEvent) {
  if (!dragStart.value.active || event.pointerId !== dragStart.value.pointerId) {
    return
  }

  openedKey.value = dragOffset.value <= -OPEN_THRESHOLD ? draggingKey.value : ''
  draggingKey.value = ''
  dragOffset.value = 0
  dragStart.value.active = false
  dragStart.value.horizontal = false
}

function handleDelete(transaction: DayGroup['transactions'][number]) {
  openedKey.value = ''
  emit('delete', transaction)
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
      >
        <button
          v-if="canSwipeDelete(transaction)"
          class="transaction-delete"
          type="button"
          :disabled="deletingId === transaction.id"
          :aria-label="`删除${transaction.name}`"
          @click="handleDelete(transaction)"
        >
          {{ deletingId === transaction.id ? '...' : '删除' }}
        </button>
        <div
          class="transaction-item"
          :style="rowStyle(transaction)"
          @pointerdown="handlePointerDown($event, transaction)"
          @pointermove="handlePointerMove"
          @pointerup="handlePointerUp"
          @pointercancel="handlePointerUp"
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
        </div>
      </li>
    </ul>
  </article>
</template>

<style scoped lang="scss" src="./style.scss"></style>
