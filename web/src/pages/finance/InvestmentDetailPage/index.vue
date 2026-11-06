<script setup lang="ts">
// 投资详情页：负责数据加载与页面编排，行情/图表/弹窗/交易列表拆分至同目录子模块。
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  deleteInvestmentPosition,
  getAccounts,
  getInvestmentAutoInvestPlans,
  getInvestmentPositionDetail,
  getInvestmentFundRedeemFees,
  getInvestmentTransactions,
  updateInvestmentAutoInvestPlan,
  deleteInvestmentAutoInvestPlan,
  type Account,
  type InvestmentAssetDetail,
  type InvestmentAutoInvestPlan,
  type InvestmentDividendRecord,
  type InvestmentPosition,
  type InvestmentTransaction,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { useTheme } from '@/utils/theme'
import TradeModal from './TradeModal.vue'
import EditPositionModal from './EditPositionModal.vue'
import AutoInvestModal from './AutoInvestModal.vue'
import TransactionList from './TransactionList.vue'
import { useFundMarket } from './useFundMarket'
import { useTrendCharts } from './useTrendCharts'
import { useTransactionPagination } from './useTransactionPagination'
import { useFeedback } from '@/composables/useFeedback'
import { useAsyncSection } from '@/composables/useAsyncSection'
import {
  formatCurrency,
  formatDateTimeLabel,
  formatDividendDate,
  formatNumber,
  fundChartViewOptions,
  fundTrendRangeOptions,
  formatAutoInvestFrequency,
  formatAutoInvestStatus,
  getDividendRecordKey,
  getDividendStatusLabel,
  isInitialFundSubscriptionTransaction,
  isTodayMarketData,
  statClass,
  statTone,
  toneByNumber,
} from './utils'
import { getErrorMessage } from '@/api/request'

const route = useRoute()
const router = useRouter()
const { isDark } = useTheme()
const detail = ref<InvestmentAssetDetail | null>(null)
const transactions = ref<InvestmentTransaction[]>([])
const autoInvestPlans = ref<InvestmentAutoInvestPlan[]>([])
const fundingAccounts = ref<Account[]>([])
const { showFeedbackModal, feedbackMessage, feedbackType, showFeedback } = useFeedback()
const selectedFundChartView = ref<'netValue' | 'profit'>('netValue')
const showDeleteModal = ref(false)
const isDeletingPosition = ref(false)
const deleteError = ref('')
const isSavingAutoInvest = ref(false)

const tradeModalRef = ref<InstanceType<typeof TradeModal> | null>(null)
const editModalRef = ref<InstanceType<typeof EditPositionModal> | null>(null)
const autoInvestModalRef = ref<InstanceType<typeof AutoInvestModal> | null>(null)

const positionId = computed(() => {
  const raw = Array.isArray(route.query.positionId) ? route.query.positionId[0] : route.query.positionId
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

const backTo = computed(() => {
  const accountId = detail.value?.position?.accountId
  return accountId ? `/finance/accounts/investment/${accountId}` : '/finance/accounts/investment'
})

const currentPosition = computed<InvestmentPosition | null>(() => detail.value?.position ?? null)
const isPendingSubscription = computed(() => currentPosition.value?.subscriptionStatus === 'pending')
const isFundPosition = computed(() => (detail.value?.productType || currentPosition.value?.productType) === 'fund')
const currentUnitName = computed(() => detail.value?.unitName || detail.value?.position.unitName || '份')

const pendingAdditionalPurchaseAmount = computed(() => transactions.value
  .filter((entry) => entry.tradeType === 'buy'
    && entry.settlementStatus === 'pending'
    && !isInitialFundSubscriptionTransaction(entry))
  .reduce((total, entry) => total + Number(entry.amount ?? 0) + Number(entry.feeAmount ?? 0) + Number(entry.taxAmount ?? 0), 0),
)
const summaryAmount = computed(() =>
  currentPosition.value?.subscriptionStatus === 'pending'
    ? (historyReady.value ? formatCurrency(Number(detail.value?.position?.costAmount ?? 0) + pendingAdditionalPurchaseAmount.value) : '--')
    : formatCurrency(Number(detail.value?.position?.marketValue ?? 0)),
)
const todayProfitValue = computed(() => {
  if (currentPosition.value?.subscriptionStatus === 'pending') {
    return '--'
  }
  if (detail.value?.position?.dayProfit === null || detail.value?.position?.dayProfit === undefined) {
    return '--'
  }
  return formatCurrency(Number(detail.value.position.dayProfit))
})
const todayProfitTone = computed<'positive' | 'negative' | 'neutral'>(() => {
  if (currentPosition.value?.subscriptionStatus === 'pending') {
    return 'neutral'
  }
  if (detail.value?.position?.dayProfit === null || detail.value?.position?.dayProfit === undefined) {
    return 'neutral'
  }
  return toneByNumber(Number(detail.value.position.dayProfit))
})
const todayValue = computed(() => {
  if (detail.value?.position?.dayProfitRate === null || detail.value?.position?.dayProfitRate === undefined) {
    return '--'
  }
  return formatNumber(Number(detail.value.position.dayProfitRate)) + '%'
})
const todayTone = computed<'positive' | 'negative' | 'neutral'>(() => {
  const fallbackRate = detail.value?.position?.dayProfitRate
  const resolvedValue = fallbackRate === null || fallbackRate === undefined ? null : Number(fallbackRate)
  if (resolvedValue === null || !Number.isFinite(resolvedValue) || resolvedValue === 0) {
    return 'neutral'
  }
  return resolvedValue > 0 ? 'positive' : 'negative'
})
const showTodayMetrics = computed(() => {
  if (currentPosition.value?.subscriptionStatus === 'pending') {
    return false
  }
  return isTodayMarketData(currentPosition.value?.lastSyncedAt)
})
const displayUpdatedAt = computed(() => {
  if (currentPosition.value?.subscriptionStatus === 'pending') {
    return '待确认'
  }
  const syncedAt = formatDateTimeLabel(currentPosition.value?.lastSyncedAt)
  return syncedAt === '--' ? '同步于 --' : `同步于 ${syncedAt}`
})
const showFundCumulativeProfitChart = computed(() => {
  const holdingQuantity = Number(currentPosition.value?.holdingQuantity ?? 0)
  return isFundPosition.value
    && !isPendingSubscription.value
    && Number.isFinite(holdingQuantity)
    && holdingQuantity > 0
})
const isFundProfitChartActive = computed(() =>
  showFundCumulativeProfitChart.value && selectedFundChartView.value === 'profit',
)
const currentTotalProfit = computed(() =>
  Number(currentPosition.value?.holdingProfit ?? 0) + Number(currentPosition.value?.cumulativeProfit ?? 0),
)

const {
  externalStatus,
  marketError,
  marketLoading,
  selectedFundTrendRange,
  selectedFundPeriodReturnTone,
  fundTrendPeriodReturnText,
  chartCostBaseline,
  cumulativeProfitPoints,
  resetFundMarketState,
  loadExternalMarketData,
} = useFundMarket({
  detail,
  transactions,
  currentPosition,
  isFundPosition,
  currentTotalProfit,
  showFundCumulativeProfitChart,
  mergeDetail,
})

const currentFundChartProfit = computed(() => {
  if (!historyReady.value) return currentTotalProfit.value
  const points = cumulativeProfitPoints.value
  const latestPoint = points[points.length - 1]
  const latestValue = Number(latestPoint?.value)
  return Number.isFinite(latestValue) ? latestValue : currentTotalProfit.value
})
const currentFundChartProfitRate = computed(() => {
  const costAmount = Number(currentPosition.value?.costAmount ?? 0)
  if (!Number.isFinite(costAmount) || costAmount <= 0) {
    return null
  }
  return currentFundChartProfit.value / costAmount * 100
})
const currentTotalProfitTone = computed<'positive' | 'negative' | 'neutral'>(() => toneByNumber(currentFundChartProfit.value))
const currentFundChartProfitRateText = computed(() => {
  const rate = currentFundChartProfitRate.value
  return rate === null ? '--' : `${rate > 0 ? '+' : ''}${formatNumber(rate)}%`
})
const currentFundChartProfitRateTone = computed<'positive' | 'negative' | 'neutral'>(() => {
  const rate = currentFundChartProfitRate.value
  return rate === null ? 'neutral' : toneByNumber(rate)
})
const cumulativeProfitChartAriaLabel = computed(() =>
  `${detail.value?.name || currentPosition.value?.productName || '基金'}累计收益折线图，当前累计收益${formatCurrency(currentFundChartProfit.value)}，累计收益率${currentFundChartProfitRateText.value}`,
)

const { chartRef, cumulativeProfitChartRef } = useTrendCharts({
  detail,
  transactions,
  currentPosition,
  chartCostBaseline,
  cumulativeProfitPoints,
  showFundCumulativeProfitChart,
  cumulativeProfitChartAriaLabel,
  selectedFundChartView,
})

const dividendRecords = computed<InvestmentDividendRecord[]>(() => detail.value?.dividendRecords ?? [])
const showDividendRecordsSection = computed(() => dividendRecords.value.length > 0)
const showDividendReminder = computed(() =>
  isFundPosition.value
  && Boolean(currentPosition.value?.hasRecentDividendPlan && currentPosition.value?.recentDividendDate),
)
const dividendReminderLabel = computed(() => {
  const status = currentPosition.value?.recentDividendStatus
  if (status === 'paid') return '最近已分红'
  if (status === 'planned') return '计划分红'
  if (status === 'cancelled') return '分红取消'
  return '已公告分红'
})
const dividendReminderText = computed(() => {
  const dateText = formatDividendDate(currentPosition.value?.recentDividendDate)
  const perUnit = Number(currentPosition.value?.recentDividendPerUnit ?? 0)
  if (!Number.isFinite(perUnit) || perUnit <= 0) {
    return `${dateText} 分红`
  }
  return `${dateText} · ${formatNumber(perUnit, 4)} / ${currentUnitName.value}`
})

function formatDividendPerUnit(value?: number | null) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? `${formatNumber(numeric, 4)} / ${currentUnitName.value}` : '--'
}

const showAutoInvestSection = computed(() => isFundPosition.value && !isPendingSubscription.value)
const showAutoInvestPlansSection = computed(() => showAutoInvestSection.value && autoInvestPlans.value.length > 0)

// ---------- 交易记录分页 ----------

const {
  currentTransactionPage,
  transactionPageItems,
  transactionTotal,
  isLoadingTransactionPage,
  totalTransactionPages,
  transactionPaginationSummary,
  canGoPrevTransactionPage,
  canGoNextTransactionPage,
  resetTransactionPage,
  loadTransactionPage,
  transactionError,
  goToPrevTransactionPage,
  goToNextTransactionPage,
} = useTransactionPagination({ positionId })

function openPendingTransactionModal(entry: InvestmentTransaction) {
  tradeModalRef.value?.openPending(entry)
}

// ---------- 数据加载 ----------

function mergeDetail(partial: Partial<InvestmentAssetDetail>) {
  if (!detail.value) {
    return
  }
  detail.value = {
    ...detail.value,
    ...partial,
  }
}

const detailSection = useAsyncSection(
  (signal) => {
    if (!positionId.value) throw new Error('投资资产不存在')
    return getInvestmentPositionDetail(positionId.value, { signal })
  },
  (value) => {
    detail.value = value
    void loadExternalMarketData(value)
    if (value.productType === 'fund') void feeSection.run()
  },
  '投资详情加载失败',
)
const { loading: isLoading, error: pageError } = detailSection
const historySection = useAsyncSection(
  (signal) => getInvestmentTransactions({ userId: getStoredCurrentUser()!.id, positionId: positionId.value! }, { signal }),
  (value) => { transactions.value = value },
  '完整交易历史加载失败',
)
const { loading: historyLoading, error: historyError, ready: historyReady } = historySection
const planSection = useAsyncSection(
  (signal) => getInvestmentAutoInvestPlans({ userId: getStoredCurrentUser()!.id, positionId: positionId.value! }, { signal }),
  (value) => { autoInvestPlans.value = value },
  '定投计划加载失败',
)
const { loading: plansLoading, error: plansError, ready: plansReady } = planSection
const accountSection = useAsyncSection(
  (signal) => getAccounts({ userId: getStoredCurrentUser()!.id, status: 'active' }, { signal }),
  (value) => { fundingAccounts.value = value.filter((account) => account.accountTypeCode === 'cash') },
  '资金账户加载失败',
)
const { loading: accountsLoading, error: accountsError, ready: accountsReady } = accountSection
const feeSection = useAsyncSection(
  (signal) => getInvestmentFundRedeemFees(detail.value?.symbol || currentPosition.value?.productSymbol || '', { signal }),
  (value) => mergeDetail({ fundRedeemFeeOptions: value }),
  '赎回费率加载失败',
)
const { loading: feesLoading, error: feesError } = feeSection

function loadDetail() {
  resetFundMarketState()
  for (const section of [detailSection, historySection, planSection, accountSection, feeSection]) section.reset()
  detail.value = null
  transactions.value = []
  autoInvestPlans.value = []
  fundingAccounts.value = []
  resetTransactionPage()
  const requests = [detailSection.run()]
  if (getStoredCurrentUser() && positionId.value) {
    requests.push(historySection.run(), loadTransactionPage(1), planSection.run(), accountSection.run())
  }
  // 各区域独立落地；首屏 loading 只跟随 detailSection。
  return Promise.all(requests)
}

// ---------- 定投计划操作 ----------

async function updateAutoInvestPlanStatus(plan: InvestmentAutoInvestPlan, status: 'active' | 'paused' | 'cancelled') {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || isSavingAutoInvest.value) {
    return
  }
  isSavingAutoInvest.value = true
  try {
    await updateInvestmentAutoInvestPlan(plan.id, {
      userId: currentUser.id,
      accountId: plan.accountId,
      positionId: plan.positionId,
      fundingAccountId: plan.fundingAccountId,
      frequency: plan.frequency === 'monthly'
        ? 'monthly'
        : plan.frequency === 'weekly'
          ? 'weekly'
          : 'daily',
      amount: plan.amount,
      currencyCode: plan.currencyCode || 'CNY',
      nextExecuteDate: plan.nextExecuteDate,
      status,
      remark: plan.remark || null,
    })
    showFeedback(status === 'active' ? '定投计划已恢复' : status === 'paused' ? '定投计划已暂停' : '定投计划已停用', 'success')
    await loadDetail()
  } catch (error) {
    showFeedback(getErrorMessage(error, '定投计划更新失败'), 'error')
  } finally {
    isSavingAutoInvest.value = false
  }
}

async function removeAutoInvestPlan(plan: InvestmentAutoInvestPlan) {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || isSavingAutoInvest.value) {
    return
  }
  if (!window.confirm('确认删除该定投计划吗？')) {
    return
  }
  isSavingAutoInvest.value = true
  try {
    await deleteInvestmentAutoInvestPlan(plan.id, currentUser.id)
    showFeedback('定投计划已删除', 'success')
    await loadDetail()
  } catch (error) {
    showFeedback(getErrorMessage(error, '删除定投计划失败'), 'error')
  } finally {
    isSavingAutoInvest.value = false
  }
}

// ---------- 删除持仓 ----------

function openDeleteModal() {
  if (!currentPosition.value || isDeletingPosition.value) {
    return
  }
  deleteError.value = ''
  showDeleteModal.value = true
}

function closeDeleteModal(force = false) {
  if (isDeletingPosition.value && !force) {
    return
  }
  showDeleteModal.value = false
  deleteError.value = ''
}

async function confirmDeletePosition() {
  if (!currentPosition.value || isDeletingPosition.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    deleteError.value = '请先登录后再删除投资资产'
    return
  }

  isDeletingPosition.value = true
  deleteError.value = ''

  try {
    await deleteInvestmentPosition(currentPosition.value.id, currentUser.id)
    closeDeleteModal(true)
    showFeedback('删除成功', 'success')
    await router.push(backTo.value)
  } catch (error) {
    const message = getErrorMessage(error, '投资资产删除失败')
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeletingPosition.value = false
  }
}

watch(positionId, () => { void loadDetail() }, { immediate: true })
</script>

<template>
  <section :class="['investment-detail-page', { 'is-dark': isDark }]" aria-label="投资详情">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="投资详情" :back-to="backTo" back-label="返回投资账户" />

    <p v-if="pageError" class="investment-detail-message investment-detail-message-error">
      {{ pageError }} <button type="button" @click="detailSection.run()">重试</button>
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else-if="detail">
      <section class="investment-detail-summary-card" aria-label="投资详情总览">
        <div class="investment-detail-summary-title">
          <strong>
            {{ detail.name || detail.position.productName }}
            <span v-if="isFundPosition">{{ detail.symbol || detail.position.productSymbol }}</span>
          </strong>
          <span v-if="!isFundPosition">
            {{ detail.symbol || detail.position.productSymbol }} · {{ detail.productType === 'stock' ? '股票' : '投资资产' }}
          </span>
        </div>
        <div class="investment-detail-summary-head">
          <div class="investment-detail-summary-main">
            <AmountText tag="p" class="investment-detail-summary-amount" tone="inherit" :value="summaryAmount" />
            <p class="investment-detail-summary-updated">{{ displayUpdatedAt }}</p>
          </div>
          <div v-if="showTodayMetrics || showFundCumulativeProfitChart" class="investment-detail-summary-side">
            <div v-if="showFundCumulativeProfitChart" class="investment-detail-summary-metric-group">
              <div class="investment-detail-summary-metric">
                <span>累计收益</span>
                <AmountText tag="strong" :tone="currentTotalProfitTone" :value="formatCurrency(currentFundChartProfit)" />
              </div>
              <div class="investment-detail-summary-metric">
                <span>累计收益率</span>
                <AmountText tag="strong" :tone="currentFundChartProfitRateTone" :value="currentFundChartProfitRateText" />
              </div>
            </div>
            <div v-if="showTodayMetrics" class="investment-detail-summary-metric-group">
              <div class="investment-detail-summary-metric">
                <span>当日盈亏</span>
                <AmountText tag="strong" :tone="todayProfitTone" :value="todayProfitValue" />
              </div>
              <div class="investment-detail-summary-metric">
                <span>当日收益率</span>
                <AmountText tag="strong" :tone="todayTone" :value="todayValue" />
              </div>
            </div>
          </div>
        </div>
        <div v-if="showDividendReminder" class="investment-detail-dividend-reminder">
          <span class="investment-detail-dividend-reminder-badge">{{ dividendReminderLabel }}</span>
          <span class="investment-detail-dividend-reminder-text">{{ dividendReminderText }}</span>
        </div>
      </section>

      <p v-if="accountsLoading" class="investment-detail-description">资金账户加载中…</p>
      <p v-if="accountsError" class="investment-detail-message investment-detail-message-error">{{ accountsError }} <button type="button" @click="accountSection.run()">重试</button></p>
      <p v-if="feesLoading" class="investment-detail-description">赎回费率加载中…</p>
      <p v-if="feesError" class="investment-detail-message investment-detail-message-error">{{ feesError }} <button type="button" @click="feeSection.run()">重试</button></p>
      <section class="investment-detail-actions" aria-label="持仓操作">
        <button class="investment-detail-action-button buy" type="button" :disabled="!accountsReady" @click="tradeModalRef?.open('buy')">加仓</button>
        <button class="investment-detail-action-button sell" type="button" :disabled="isPendingSubscription || !accountsReady" @click="tradeModalRef?.open('sell')">减仓</button>
        <button
          class="investment-detail-action-button auto-invest"
          type="button"
          :disabled="!showAutoInvestSection || !accountsReady || !plansReady"
          @click="autoInvestModalRef?.open()"
        >
          定投
        </button>
        <button class="investment-detail-action-button edit" type="button" :disabled="!accountsReady" @click="editModalRef?.open()">修改</button>
        <button class="investment-detail-action-button delete" type="button" :disabled="isDeletingPosition" @click="openDeleteModal">删除</button>
      </section>

      <section class="investment-detail-card" :aria-label="detail.productType === 'fund' ? '基金走势' : '行情走势'">
        <header class="investment-detail-card-head">
          <h2>{{ detail.chartType === 'candlestick' ? '股票日K走势' : isFundProfitChartActive ? '累计收益走势' : '累计净值走势' }}</h2>
          <AmountText
            v-if="isFundPosition && !isFundProfitChartActive"
            tag="span"
            class="investment-detail-period-return"
            :tone="selectedFundPeriodReturnTone"
            :value="fundTrendPeriodReturnText"
          />
          <span v-else>{{ externalStatus || detail.source || '行情接口' }}</span>
        </header>
        <SegmentedControl
          v-if="showFundCumulativeProfitChart"
          v-model="selectedFundChartView"
          :options="fundChartViewOptions"
          label="基金走势图表切换"
          class="investment-detail-chart-tabs"
        />
        <SegmentedControl
          v-if="detail.productType === 'fund' && !isFundProfitChartActive"
          v-model="selectedFundTrendRange"
          :options="fundTrendRangeOptions"
          label="基金走势区间切换"
          variant="surface"
        />
        <p v-if="marketLoading" class="investment-detail-description">行情加载中…</p>
        <p v-if="marketError" class="investment-detail-message investment-detail-message-error">{{ marketError }} <button type="button" @click="loadExternalMarketData(detail)">重试</button></p>
        <p v-if="historyLoading" class="investment-detail-description">完整交易历史加载中…</p>
        <p v-if="historyError" class="investment-detail-message investment-detail-message-error">{{ historyError }} <button type="button" @click="historySection.run()">重试</button></p>
        <template v-if="historyReady && isFundProfitChartActive">
          <div
            v-if="cumulativeProfitPoints.length"
            ref="cumulativeProfitChartRef"
            class="investment-detail-chart investment-detail-profit-chart"
            role="img"
            :aria-label="cumulativeProfitChartAriaLabel"
          ></div>
          <p v-else class="investment-detail-empty">{{ externalStatus || '暂无累计收益走势数据' }}</p>
        </template>
        <template v-else-if="historyReady">
          <div v-if="detail.chartPoints.length" ref="chartRef" class="investment-detail-chart"></div>
          <p v-else class="investment-detail-empty">暂无走势数据</p>
        </template>
      </section>

      <section class="investment-detail-card" aria-label="资产详细数据">
        <h2>{{ detail.productType === 'stock' ? '股票详细数据' : detail.productType === 'fund' ? '基金详细数据' : '资产详细数据' }}</h2>
        <div class="investment-detail-grid">
          <div v-for="entry in detail.marketStats" :key="entry.label" class="investment-detail-grid-item">
            <span>{{ entry.label }}</span>
            <AmountText tag="strong" :class="statClass(entry)" :tone="statTone(entry)" :value="entry.value" />
          </div>
        </div>
      </section>

      <section class="investment-detail-card" aria-label="持仓分析">
        <h2>持仓分析</h2>
        <div class="investment-detail-grid">
          <div v-for="entry in detail.holdingStats" :key="entry.label" class="investment-detail-grid-item">
            <span>{{ entry.label }}</span>
            <AmountText tag="strong" :class="statClass(entry)" :tone="statTone(entry)" :value="entry.value" />
          </div>
        </div>
      </section>

      <section v-if="showDividendRecordsSection" class="investment-detail-card" aria-label="近一年分红与计划">
        <header class="investment-detail-card-head">
          <h2>近一年分红与计划</h2>
          <span>{{ dividendRecords.length }} 条</span>
        </header>
        <div class="investment-detail-dividend-list">
          <article
            v-for="entry in dividendRecords"
            :key="getDividendRecordKey(entry)"
            class="investment-detail-dividend-item"
          >
            <div class="investment-detail-dividend-top">
              <div class="investment-detail-dividend-title">
                <strong>{{ formatDividendDate(entry.payDate) }}</strong>
              </div>
              <em class="investment-detail-dividend-status">{{ getDividendStatusLabel(entry.status, entry.actualAmount) }}</em>
            </div>

            <div class="investment-detail-dividend-grid">
              <div>
                <span>每单位分红</span>
                <strong>{{ formatDividendPerUnit(entry.dividendPerUnit) }}</strong>
              </div>
              <div>
                <span>{{ entry.actualAmount === null || entry.actualAmount === undefined ? '预计分红' : '到账分红' }}</span>
                <strong>{{ formatCurrency(Number(entry.actualAmount ?? entry.expectedAmount ?? 0)) }}</strong>
              </div>
            </div>
          </article>
        </div>
      </section>

      <p v-if="plansLoading" class="investment-detail-description">定投计划加载中…</p>
      <p v-if="plansError" class="investment-detail-message investment-detail-message-error">{{ plansError }} <button type="button" @click="planSection.run()">重试</button></p>
      <section v-if="showAutoInvestPlansSection" class="investment-detail-card" aria-label="定投计划">
        <header class="investment-detail-card-head">
          <h2>定投计划</h2>
        </header>

        <div class="investment-auto-invest-list">
          <article
            v-for="plan in autoInvestPlans"
            :key="plan.id"
            class="investment-auto-invest-item"
          >
            <div class="investment-auto-invest-top">
              <div class="investment-auto-invest-main">
                <strong>{{ formatCurrency(Number(plan.amount)) }}</strong>
                <span>{{ formatAutoInvestFrequency(plan.frequency) }}</span>
              </div>
              <em :class="['investment-auto-invest-status', `is-${plan.status}`]">{{ formatAutoInvestStatus(plan.status) }}</em>
            </div>

            <div class="investment-auto-invest-grid">
              <div>
                <span>下次执行</span>
                <strong>{{ plan.nextExecuteDate }}</strong>
              </div>
              <div>
                <span>扣款账户</span>
                <strong>{{ plan.fundingAccountName || '--' }}</strong>
              </div>
              <div>
                <span>最近执行</span>
                <strong>{{ formatDateTimeLabel(plan.lastExecutedAt) }}</strong>
              </div>
              <div>
                <span>备注</span>
                <strong>{{ plan.remark?.trim() || '--' }}</strong>
              </div>
            </div>

            <div class="investment-auto-invest-actions">
              <button type="button" :disabled="isSavingAutoInvest || !accountsReady" @click="autoInvestModalRef?.open(plan)">修改</button>
              <button
                type="button"
                :disabled="isSavingAutoInvest"
                @click="updateAutoInvestPlanStatus(plan, plan.status === 'active' ? 'paused' : 'active')"
              >
                {{ plan.status === 'active' ? '暂停' : '恢复' }}
              </button>
              <button type="button" class="danger" :disabled="isSavingAutoInvest" @click="removeAutoInvestPlan(plan)">删除</button>
            </div>
          </article>
        </div>
      </section>

      <p v-if="transactionError" class="investment-detail-message investment-detail-message-error">{{ transactionError }} <button type="button" @click="loadTransactionPage()">重试</button></p>
      <TransactionList
        v-if="!transactionError"
        :items="transactionPageItems"
        :unit-name="detail.unitName || detail.position.unitName || ''"
        :total="transactionTotal"
        :current-page="currentTransactionPage"
        :total-pages="totalTransactionPages"
        :summary="transactionPaginationSummary"
        :can-prev="canGoPrevTransactionPage"
        :can-next="canGoNextTransactionPage"
        :is-loading="isLoadingTransactionPage"
        @prev="goToPrevTransactionPage"
        @next="goToNextTransactionPage"
        @open-pending="openPendingTransactionModal"
      />
    </template>

    <TradeModal
      v-if="detail"
      ref="tradeModalRef"
      :detail="detail"
      :funding-accounts="fundingAccounts"
      @saved="loadDetail"
      @feedback="showFeedback"
    />

    <EditPositionModal
      v-if="detail"
      ref="editModalRef"
      :detail="detail"
      :funding-accounts="fundingAccounts"
      @saved="loadDetail"
      @feedback="showFeedback"
    />

    <AutoInvestModal
      v-if="detail && currentPosition"
      ref="autoInvestModalRef"
      :position="currentPosition"
      :asset-name="detail?.name || detail?.position.productName || ''"
      :plans="autoInvestPlans"
      :funding-accounts="fundingAccounts"
      @saved="loadDetail"
      @feedback="showFeedback"
    />

    <CommonModal
      v-model="showDeleteModal"
      title="删除投资资产"
      size="compact"
      :close-on-overlay="!isDeletingPosition"
      @close="closeDeleteModal"
    >
      <div class="investment-detail-modal-form">
        <p class="investment-detail-description">
          删除后该资产的持仓与交易记录会一并移除，确认删除“{{ detail?.name || detail?.position.productName || '当前资产' }}”吗？
        </p>
        <p v-if="deleteError" class="investment-detail-modal-error">{{ deleteError }}</p>
      </div>

      <template #footer>
        <div class="investment-detail-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeletingPosition" @click="closeDeleteModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isDeletingPosition" @click="confirmDeletePosition">
            {{ isDeletingPosition ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style lang="scss" src="./style.scss"></style>
