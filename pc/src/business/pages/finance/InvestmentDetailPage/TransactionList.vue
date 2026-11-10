<script setup lang="ts">
// 交易记录列表：纯展示组件，分页与待确认交易编辑由父页面驱动。
import AmountText from '@/business/components/common/AmountText/index.vue'
import type { InvestmentTransaction } from '@/business/api/modules/finance'
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
  <section class="pc-table-panel">
    <div class="pc-toolbar">
      <h2>交易记录</h2>
      <span>共 {{ total }} 条</span>
    </div>
    <el-skeleton v-if="isLoading" :rows="4" animated />
    <el-table v-else :data="items" stripe>
      <!-- @vue-generic {InvestmentTransaction} -->
      <el-table-column label="类型">
        <template #default="{ row }">{{ getTradeTypeLabel(row) }}</template>
      </el-table-column>
      <!-- @vue-generic {InvestmentTransaction} -->
      <el-table-column label="交易时间" min-width="180">
        <template #default="{ row }">
          {{ formatTradeTime(row.tradeAt) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {InvestmentTransaction} -->
      <el-table-column label="金额">
        <template #default="{ row }">{{ formatAmountLabel(row) }}</template>
      </el-table-column>
      <!-- @vue-generic {InvestmentTransaction} -->
      <el-table-column label="数量">
        <template #default="{ row }">
          {{ getTradeQuantityText(row, unitName) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {InvestmentTransaction} -->
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button
            v-if="row.id > 0 && row.settlementStatus === 'pending'"
            link
            type="primary"
            @click="emit('openPending', row)"
          >
            修改待确认交易
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="totalPages > 1" class="pc-pagination">
      <el-button :disabled="!canPrev || isLoading" @click="emit('prev')">
        上一页
      </el-button>
      <span>{{ summary }}</span>
      <el-button :disabled="!canNext || isLoading" @click="emit('next')">
        下一页
      </el-button>
    </div>
  </section>
</template>
