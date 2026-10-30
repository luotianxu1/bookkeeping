<script setup lang="ts">
// 交易记录列表：纯展示组件，分页与待确认交易编辑由父页面驱动。
import AmountText from '@/components/common/AmountText/index.vue'
import type { InvestmentTransaction } from '@/api/modules/finance'
import {
  formatAmountLabel,
  formatTradeTime,
  getTradeQuantityClass,
  getTradeQuantityText,
  getTradeTypeLabel,
} from './utils'

defineProps<{
  items: InvestmentTransaction[]
  unitName: string
  total: number
  currentPage: number
  totalPages: number
  summary: string
  canPrev: boolean
  canNext: boolean
  isLoading: boolean
}>()

const emit = defineEmits<{
  (e: 'prev'): void
  (e: 'next'): void
  (e: 'openPending', entry: InvestmentTransaction): void
}>()
</script>

<template>
  <section class="investment-detail-transactions-wrap" aria-label="交易记录">
    <header class="investment-detail-transactions-head">
      <strong>交易记录</strong>
      <span>共 {{ total }} 条</span>
    </header>

    <section class="investment-detail-transactions-card">
      <article
        v-for="entry in items"
        :key="entry.id"
        :class="['investment-detail-transaction-item', { 'is-pending': entry.id > 0 && entry.settlementStatus === 'pending' }]"
        :tabindex="entry.id > 0 && entry.settlementStatus === 'pending' ? 0 : undefined"
        @click="emit('openPending', entry)"
        @keydown.enter="emit('openPending', entry)"
      >
        <div class="investment-detail-transaction-left">
          <strong>{{ getTradeTypeLabel(entry) }}</strong>
          <span>{{ formatTradeTime(entry.tradeAt) }}</span>
        </div>
        <div class="investment-detail-transaction-right">
          <span>{{ formatAmountLabel(entry) }}</span>
          <AmountText
            tag="strong"
            :class="getTradeQuantityClass(entry.tradeType)"
            tone="inherit"
            :value="getTradeQuantityText(entry, unitName)"
          />
        </div>
      </article>
      <p v-if="total === 0" class="investment-detail-empty">暂无交易记录</p>

      <div v-if="totalPages > 1" class="investment-detail-transactions-pagination" aria-label="交易记录分页">
        <button
          class="investment-detail-transactions-pagination-button"
          type="button"
          :disabled="!canPrev || isLoading"
          @click="emit('prev')"
        >
          上一页
        </button>
        <span class="investment-detail-transactions-pagination-summary">{{ summary }}</span>
        <button
          class="investment-detail-transactions-pagination-button"
          type="button"
          :disabled="!canNext || isLoading"
          @click="emit('next')"
        >
          下一页
        </button>
      </div>
    </section>
  </section>
</template>
