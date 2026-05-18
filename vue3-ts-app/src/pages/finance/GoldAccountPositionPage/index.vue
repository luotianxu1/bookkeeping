<script setup lang="ts">
// 黄金账户持仓页：通过后端接口展示黄金账户汇总和持仓列表。
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import {
  getGoldAccountHoldings,
  getGoldAccountSummary,
  type GoldAccountHolding,
  type GoldAccountSummary,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const isLoading = ref(false)
const pageError = ref('')
const summary = ref<GoldAccountSummary>({
  totalWeight: 0,
  averagePrice: 0,
  purchaseTotal: 0,
  estimatedValue: 0,
  estimatedProfit: 0,
  profitRate: 0,
  cumulativeProfit: 0,
})
const holdings = ref<GoldAccountHolding[]>([])
let requestVersion = 0

const hasHoldings = computed(() => holdings.value.length > 0)

onMounted(() => {
  void loadGoldPosition()
})

async function loadGoldPosition() {
  const currentRequestVersion = ++requestVersion
  const currentUser = getStoredCurrentUser()

  if (!currentUser) {
    pageError.value = '请先登录后查看黄金持仓'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [summaryData, holdingList] = await Promise.all([
      getGoldAccountSummary(currentUser.id),
      getGoldAccountHoldings(currentUser.id),
    ])

    if (currentRequestVersion !== requestVersion) {
      return
    }

    summary.value = summaryData
    holdings.value = holdingList
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = error instanceof Error ? error.message : '黄金持仓加载失败'
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
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })
}

function formatRate(value: number | null | undefined) {
  return `${Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}%`
}

function formatSignedAmount(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return `${amount >= 0 ? '+' : '-'}${formatAmount(Math.abs(amount))}`
}
</script>

<template>
  <section class="gold-position-page" aria-label="黄金账户持仓">
    <PageHeader title="黄金账户持仓" back-label="返回黄金账户" />

    <p v-if="pageError" class="gold-position-message gold-position-message-error">
      {{ pageError }}
    </p>
    <p v-else-if="isLoading" class="gold-position-message">
      加载中...
    </p>

    <template v-else>
      <section class="gold-position-summary">
        <div class="summary-head">
          <span>总重量(克)</span>
        </div>
        <div class="summary-main">
          <strong>{{ formatWeight(summary.totalWeight) }}</strong>
          <RouterLink class="liquidation-link" to="/finance/accounts/gold/liquidation">
            清仓记录
          </RouterLink>
        </div>

        <div class="summary-grid">
          <article>
            <span>平均克价(元)</span>
            <strong>{{ formatAmount(summary.averagePrice) }}</strong>
          </article>
          <article>
            <span>购入总价(元)</span>
            <strong>{{ formatAmount(summary.purchaseTotal) }}</strong>
          </article>
          <article>
            <span>预估价值(元)</span>
            <strong>{{ formatAmount(summary.estimatedValue) }}</strong>
          </article>
          <article>
            <span>预估收益(元)</span>
            <strong :class="{ up: summary.estimatedProfit >= 0 }">
              {{ formatSignedAmount(summary.estimatedProfit) }}
            </strong>
          </article>
          <article>
            <span>收益率(%)</span>
            <strong :class="{ up: summary.profitRate >= 0 }">
              {{ formatRate(summary.profitRate) }}
            </strong>
          </article>
          <article>
            <span>累计收益(元)</span>
            <strong :class="{ up: summary.cumulativeProfit >= 0 }">
              {{ formatSignedAmount(summary.cumulativeProfit) }}
            </strong>
          </article>
        </div>
      </section>

      <section class="position-list">
        <article v-for="item in holdings" :key="item.id" class="position-card">
          <span class="price-tag">{{ formatAmount(item.currentPrice) }}/克</span>
          <div class="position-top">
            <strong>{{ item.accountName }}</strong>
            <div class="profit" :class="{ negative: item.holdingProfit < 0 }">
              {{ formatSignedAmount(item.holdingProfit) }}
            </div>
          </div>
          <div class="position-bottom">
            <span>{{ formatWeight(item.weight) }}g</span>
            <span>{{ formatAmount(item.purchaseAmount) }}</span>
          </div>
        </article>

        <p v-if="!hasHoldings" class="gold-position-empty">暂无黄金持仓</p>
      </section>
    </template>

    <FloatingAddButton aria-label="新增黄金" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
