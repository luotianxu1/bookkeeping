<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import { getInvestmentDividendIncome, type InvestmentDividendIncomePage } from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const isLoading = ref(false)
const pageError = ref('')
const pageData = ref<InvestmentDividendIncomePage | null>(null)

const summary = computed(() => pageData.value?.summary ?? {
  estimatedDividendAmount: 0,
  estimatedDividendRate: 0,
  actualDividendAmount: 0,
  actualDividendRate: 0,
  holdingCount: 0,
})

const holdings = computed(() => pageData.value?.items ?? [])
const updateText = computed(() => {
  const updatedAt = pageData.value?.updatedAt
  return updatedAt ? `数据更新于 ${formatDateTime(updatedAt)}` : ''
})
const estimatedMonthlyIncome = computed(() => summary.value.estimatedDividendAmount / 12)

onMounted(() => {
  void loadPageData()
})

async function loadPageData() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看攒股收息'
    return
  }

  isLoading.value = true
  pageError.value = ''
  try {
    pageData.value = await getInvestmentDividendIncome(currentUser.id)
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '攒股收息加载失败'
  } finally {
    isLoading.value = false
  }
}

function amountTone(value: number) {
  if (value > 0) return 'positive'
  if (value < 0) return 'negative'
  return 'neutral'
}

function formatAmount(value: number, digits = 2) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(value))
}

function formatSignedCurrency(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '¥0.00'
  }
  return `${value > 0 ? '+' : '-'} ¥${formatAmount(value)}`
}

function formatSignedPercent(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '0.00%'
  }
  return `${value > 0 ? '+' : '-'}${formatAmount(value)}%`
}

function formatHoldingAmountText(value: number, quantity: number, unitName?: string | null) {
  return `${formatMonthlyIncome(value)} · ${formatAmount(quantity)} ${unitName || '份'}`
}

function formatMonthlyIncome(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return '¥0.00'
  }
  return `¥${formatAmount(value)}`
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hour}:${minute}`
}
</script>

<template>
  <section class="dividend-income-page" aria-label="攒股收息">
    <PageHeader title="攒股收息" back-label="返回更多功能" />

    <p v-if="pageError" class="dividend-message dividend-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
    <section class="summary-card" aria-label="收息总览">
      <p class="summary-title">基于历史分红记录预测</p>
      <div class="summary-row">
        <AmountText
          tag="strong"
          :value="formatSignedCurrency(summary.estimatedDividendAmount)"
          :tone="amountTone(summary.estimatedDividendAmount)"
        />
        <AmountText
          tag="span"
          :value="`预计 ${formatSignedPercent(summary.estimatedDividendRate)}`"
          :tone="amountTone(summary.estimatedDividendRate)"
        />
      </div>
      <p class="summary-monthly">预估月薪 {{ formatMonthlyIncome(estimatedMonthlyIncome) }}</p>
      <p v-if="updateText" class="summary-footnote">仅展示历史稳定分红标的，{{ updateText }}</p>
      <p v-else class="summary-footnote">仅展示历史稳定分红标的</p>
    </section>

    <section class="holding-card" aria-label="持仓分红计划">
      <header class="holding-card-head">
        <strong>稳定分红持仓 {{ summary.holdingCount }} 项</strong>
      </header>
      <header class="holding-header">
        <span class="holding-spacer" />
        <div class="holding-header-right">
          <span>预估分红</span>
          <span>预估月薪</span>
        </div>
      </header>

      <p v-if="holdings.length === 0" class="dividend-message">
        暂无符合条件的稳定分红股票或基金持仓
      </p>

      <article v-for="item in holdings" :key="item.productId" class="holding-row">
        <div class="holding-left">
          <p>{{ item.productName }}</p>
          <small>{{ formatHoldingAmountText(item.marketValue, item.holdingQuantity, item.unitName) }}</small>
        </div>

        <div class="holding-right">
          <div class="value-column">
            <AmountText
              tag="strong"
              :value="formatSignedCurrency(item.estimatedDividendAmount)"
              :tone="amountTone(item.estimatedDividendAmount)"
            />
            <AmountText
              tag="small"
              :value="formatSignedPercent(item.estimatedDividendRate)"
              :tone="amountTone(item.estimatedDividendRate)"
            />
          </div>
          <div class="value-column">
            <AmountText
              tag="strong"
              :value="formatMonthlyIncome(item.estimatedDividendAmount / 12)"
              tone="inherit"
            />
          </div>
        </div>
      </article>
    </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
