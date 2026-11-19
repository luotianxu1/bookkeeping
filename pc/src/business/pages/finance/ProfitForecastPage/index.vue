<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'

// 收益预测页：展示基金盘中预估收益，并支持按投资账户查看。
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'

import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'
import {
  getFundProfitForecast,
  type FundProfitForecast,
  type FundProfitForecastAccount,
  type FundProfitForecastHolding,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

type ForecastMetrics = Pick<
  FundProfitForecast,
  'holdingAmount' | 'estimateProfit' | 'estimateProfitRate' | 'estimatedAt'
>
type ProfitTone = 'positive' | 'negative' | 'neutral'

const selectedAccountId = ref('all')
const forecast = ref<FundProfitForecast | null>(null)
const isLoading = ref(true)
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
const accountSelectOptions = computed<CommonSelectOption[]>(() =>
  accountOptions.value.map((option) => ({
    label: option.label,
    value: option.value,
  })),
)
const selectedAccount = computed<FundProfitForecastAccount | null>(() => {
  if (selectedAccountId.value === 'all') {
    return null
  }

  return (
    forecast.value?.accounts.find(
      (account) => String(account.accountId) === selectedAccountId.value,
    ) ?? null
  )
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

  return items.filter(
    (item) => String(item.accountId) === selectedAccountId.value,
  )
})

const summaryHint = computed(() => {
  const timeText = formatDateTime(visibleSummary.value.estimatedAt)
  return timeText
    ? `按开盘时盘中估值实时预测（最近更新 ${timeText}）`
    : '按开盘时盘中估值实时预测'
})

onMounted(() => {
  void loadForecast()
})

async function loadForecast() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看收益预测'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const data = await getFundProfitForecast({ userId: currentUser.id })
    forecast.value = data

    const optionValues = new Set(
      data.accounts.map((account) => String(account.accountId)),
    )
    if (
      selectedAccountId.value !== 'all' &&
      !optionValues.has(selectedAccountId.value)
    ) {
      selectedAccountId.value = 'all'
    }
  } catch (error) {
    pageError.value = getErrorMessage(error, '收益预测加载失败')
  } finally {
    isLoading.value = false
  }
}

function formatNumber(value: number, digits = 2) {
  const safe = Number.isFinite(value) ? value : 0
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(safe))
}

function formatSignedCurrency(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}¥${formatNumber(value)}`
}

function formatSignedRate(value: number) {
  return formatPercentage(value, true)
}

function formatCurrency(value: number) {
  return `¥${formatNumber(value)}`
}

function formatHoldingMeta(item: FundProfitForecastHolding) {
  return formatCurrency(Number(item.holdingAmount ?? 0))
}

function formatHoldingUpdatedAt(item: FundProfitForecastHolding) {
  const timeText = formatDateTime(item.estimatedAt)
  return timeText ? `更新 ${timeText}` : '更新时间待确认'
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

type DesktopRow = (typeof visibleHoldings.value)[number]
</script>
<template>
  <section class="desktop-workspace stagger-in">
    <PageHeader title="收益预测">
      <template #right>
        <CommonSelect
          v-model="selectedAccountId"
          label="查看账户"
          :options="accountSelectOptions"
        />
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[
        {
          label: '预估收益',
          value: formatSignedCurrency(visibleSummary.estimateProfit),
        },
        {
          label: '预估收益率',
          value: formatSignedRate(visibleSummary.estimateProfitRate),
        },
        {
          label: '持仓市值',
          value: formatCurrency(visibleSummary.holdingAmount),
        },
      ]"
    />
    <DesktopRecordList
      :rows="visibleHoldings"
      :loading="isLoading"
      title="持仓收益预测"
      search-placeholder="搜索基金"
      searchable
      :search-keys="['productName']"
      empty-title="暂无持仓"
      empty-hint="添加基金持仓后可查看盘中预估收益"
    >
      <template #default="{ row }: { row: DesktopRow }">
        <article class="record-card fund-forecast-card">
          <div class="record-card-head">
            <div class="record-card-title">
              <span>{{ row.productName }}</span>
            </div>
          </div>
          <div class="fund-forecast-profit">
            <span class="fund-forecast-profit-label">预估收益</span>
            <AmountText
              class="fund-forecast-profit-value"
              :value="formatSignedCurrency(row.estimateProfit)"
              :tone="getProfitTone(row.estimateProfit)"
              font-size="24px"
            />
          </div>
          <dl class="record-card-body">
            <div>
              <dt>预估收益率</dt>
              <dd>{{ formatSignedRate(row.estimateProfitRate) }}</dd>
            </div>
            <div>
              <dt>持仓市值</dt>
              <dd>{{ formatHoldingMeta(row) }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatHoldingUpdatedAt(row) }}</dd>
            </div>
          </dl>
        </article>
      </template>
    </DesktopRecordList>
    <p class="desktop-note">{{ summaryHint }}</p>
  </section>
</template>
<style scoped>
/* 持仓预测卡：预估收益大数字（涨红跌绿）+ 持仓信息明细 */
.fund-forecast-profit {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.fund-forecast-profit-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.fund-forecast-profit-value {
  font-weight: 700;
  letter-spacing: -0.02em;
}
</style>
