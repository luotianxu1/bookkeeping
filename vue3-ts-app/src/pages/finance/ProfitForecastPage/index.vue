<script setup lang="ts">
// 收益预测页：展示基金盘中预估收益，并支持按投资账户查看。
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import { getFundProfitForecast, type FundProfitForecast, type FundProfitForecastAccount, type FundProfitForecastHolding } from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

type ForecastMetrics = Pick<
  FundProfitForecast,
  'holdingAmount' | 'estimateProfit' | 'estimateProfitRate' | 'estimatedAt'
>
type ProfitTone = 'positive' | 'negative' | 'neutral'

const selectedAccountId = ref('all')
const forecast = ref<FundProfitForecast | null>(null)
const isLoading = ref(false)
const pageError = ref('')

const emptyMetrics: ForecastMetrics = {
  holdingAmount: 0,
  estimateProfit: 0,
  estimateProfitRate: 0,
  estimatedAt: null,
}

const accountOptions = computed(() => {
  const options = [{ label: '全部账户', value: 'all' }]
  for (const account of forecast.value?.accounts ?? []) {
    options.push({
      label: `${account.accountName}（${account.fundCount}只）`,
      value: String(account.accountId),
    })
  }
  return options
})
const selectedAccountLabel = computed(() =>
  accountOptions.value.find((option) => option.value === selectedAccountId.value)?.label ?? '全部账户',
)

const selectedAccount = computed<FundProfitForecastAccount | null>(() => {
  if (selectedAccountId.value === 'all') {
    return null
  }

  return forecast.value?.accounts.find((account) => String(account.accountId) === selectedAccountId.value) ?? null
})

const visibleSummary = computed<ForecastMetrics>(() => {
  if (!forecast.value) {
    return emptyMetrics
  }

  return selectedAccount.value ?? forecast.value
})

const visibleHoldings = computed<FundProfitForecastHolding[]>(() => {
  const items = forecast.value?.holdings ?? []
  if (selectedAccountId.value === 'all') {
    return items
  }

  return items.filter((item) => String(item.accountId) === selectedAccountId.value)
})

const hasInvestmentAccounts = computed(() => (forecast.value?.accounts.length ?? 0) > 0)
const summaryTitle = computed(() => (
  selectedAccount.value
    ? `${selectedAccount.value.accountName}当日收益预测`
    : '持仓基金当日收益预测'
))
const summaryHint = computed(() => {
  const timeText = formatDateTime(visibleSummary.value.estimatedAt)
  return timeText ? `按开盘时盘中估值实时预测（最近更新 ${timeText}）` : '按开盘时盘中估值实时预测'
})
const emptyMessage = computed(() => {
  if (!hasInvestmentAccounts.value) {
    return '暂无投资账户'
  }

  return selectedAccount.value ? '该账户暂无基金持仓' : '暂无基金持仓'
})

onMounted(() => {
  void loadForecast()
})

async function loadForecast() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看收益预测'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const data = await getFundProfitForecast({ userId: currentUser.id })
    forecast.value = data

    const optionValues = new Set(data.accounts.map((account) => String(account.accountId)))
    if (selectedAccountId.value !== 'all' && !optionValues.has(selectedAccountId.value)) {
      selectedAccountId.value = 'all'
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '收益预测加载失败'
  } finally {
    isLoading.value = false
  }
}

function formatNumber(value: number, digits = 2) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(value))
}

function formatSignedCurrency(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}¥${formatNumber(value)}`
}

function formatSignedRate(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}${formatNumber(value)}%`
}

function formatCurrency(value: number) {
  return `¥${formatNumber(value)}`
}

function formatHoldingMeta(item: FundProfitForecastHolding) {
  return formatCurrency(Number(item.holdingAmount ?? 0))
}

function getProfitTone(value: number): ProfitTone {
  if (value > 0) {
    return 'positive'
  }

  if (value < 0) {
    return 'negative'
  }

  return 'neutral'
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
</script>

<template>
  <section class="profit-forecast-page" aria-label="收益预测">
    <PageHeader title="收益预测" back-label="返回更多功能" />

    <p v-if="pageError" class="profit-forecast-message profit-forecast-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="summary-card" aria-label="收益预测汇总">
        <div class="summary-top">
          <p class="summary-title">{{ summaryTitle }}</p>
          <label class="summary-account-pill">
            <span class="summary-account-pill-text">{{ selectedAccountLabel }}</span>
            <select v-model="selectedAccountId" class="summary-account-pill-select" aria-label="查看账户">
              <option v-for="option in accountOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
        </div>
        <div class="summary-row">
          <AmountText
            tag="strong"
            class="summary-profit"
            :value="formatSignedCurrency(visibleSummary.estimateProfit)"
            :tone="getProfitTone(visibleSummary.estimateProfit)"
          />
        </div>
        <div class="summary-row summary-row--headline">
          <span class="summary-rate-group">
            <span class="summary-rate-label">预计</span>
            <AmountText
              tag="span"
              class="summary-rate"
              :value="formatSignedRate(visibleSummary.estimateProfitRate)"
              :tone="getProfitTone(visibleSummary.estimateProfitRate)"
            />
          </span>
          <span>持仓市值 {{ formatCurrency(visibleSummary.holdingAmount) }}</span>
        </div>
        <p class="summary-hint">{{ summaryHint }}</p>
      </section>

      <section class="holding-card" aria-label="持仓收益预测">
        <header class="holding-header">
          <span class="holding-header-title">
            {{ selectedAccount ? `${selectedAccount.accountName}基金明细` : '全部基金明细' }}
          </span>
          <div class="holding-header-right">
            <span>预估收益</span>
          </div>
        </header>

        <p v-if="visibleHoldings.length === 0" class="holding-empty">
          {{ emptyMessage }}
        </p>

        <article v-for="item in visibleHoldings" :key="item.positionId" v-else class="holding-row">
          <div class="holding-left">
            <p class="holding-name">{{ item.productName }}</p>
            <AmountText tag="p" class="holding-amount" :value="formatHoldingMeta(item)" tone="inherit" />
          </div>

          <div class="holding-right">
            <div class="value-column">
              <AmountText
                tag="strong"
                class="value-profit"
                :value="formatSignedCurrency(item.estimateProfit)"
                :tone="getProfitTone(item.estimateProfit)"
              />
              <AmountText
                tag="span"
                class="value-rate"
                :value="formatSignedRate(item.estimateProfitRate)"
                :tone="getProfitTone(item.estimateProfitRate)"
              />
            </div>
          </div>
        </article>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
