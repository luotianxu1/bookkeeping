<script setup lang="ts">
// 黄金清仓记录页：通过后端接口展示累计收益和清仓明细。
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  getGoldLiquidations,
  type GoldLiquidation,
  type GoldLiquidationRecord,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const isLoading = ref(false)
const pageError = ref('')
const liquidation = ref<GoldLiquidation>({
  cumulativeWeight: 0,
  cumulativeProfit: 0,
  records: [],
})
let requestVersion = 0

const records = computed<GoldLiquidationRecord[]>(() => liquidation.value.records ?? [])

onMounted(() => {
  void loadLiquidations()
})

async function loadLiquidations() {
  const currentRequestVersion = ++requestVersion
  const currentUser = getStoredCurrentUser()

  if (!currentUser) {
    pageError.value = '请先登录后查看清仓记录'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const response = await getGoldLiquidations(currentUser.id)

    if (currentRequestVersion !== requestVersion) {
      return
    }

    liquidation.value = response
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = error instanceof Error ? error.message : '清仓记录加载失败'
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

function formatAmount(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatWeight(value: number | null | undefined) {
  return `${Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })}g`
}

function formatSignedAmount(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return `${amount >= 0 ? '+' : '-'}${formatAmount(Math.abs(amount))}`
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <section class="gold-liquidation-page" aria-label="清仓记录">
    <PageHeader title="清仓记录" back-label="返回黄金账户持仓" />

    <p v-if="pageError" class="gold-liquidation-message gold-liquidation-message-error">
      {{ pageError }}
    </p>
    <p v-else-if="isLoading" class="gold-liquidation-message">
      加载中...
    </p>

    <template v-else>
      <section class="liquidation-summary">
        <div class="summary-row">
          <span>累计克重</span>
          <strong class="weight">{{ formatWeight(liquidation.cumulativeWeight) }}</strong>
        </div>
        <div class="summary-row">
          <span>累计收益</span>
          <strong class="profit" :class="{ negative: liquidation.cumulativeProfit < 0 }">
            {{ formatSignedAmount(liquidation.cumulativeProfit) }}
          </strong>
        </div>
      </section>

      <section class="liquidation-list">
        <article v-for="record in records" :key="record.id" class="liquidation-card">
          <div class="card-top">
            <strong>{{ formatDateTime(record.tradeAt) }}</strong>
            <em :class="{ negative: record.profit < 0 }">收益 {{ formatSignedAmount(record.profit) }}</em>
          </div>

          <div class="detail-grid">
            <div>
              <span>克重</span>
              <strong>{{ formatWeight(record.weight) }}</strong>
            </div>
            <div>
              <span>买入价</span>
              <strong>{{ formatAmount(record.buyPrice) }}</strong>
            </div>
            <div>
              <span>卖出价</span>
              <strong>{{ formatAmount(record.sellPrice) }}</strong>
            </div>
            <div>
              <span>手续费</span>
              <strong>{{ formatAmount(record.fee) }}</strong>
            </div>
          </div>
        </article>

        <p v-if="records.length === 0" class="gold-liquidation-empty">暂无清仓记录</p>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
