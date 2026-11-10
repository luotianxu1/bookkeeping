<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { confirmAction } from '@/business/utils/confirm'
// 投资详情页：负责数据加载与页面编排，行情/图表/弹窗/交易列表拆分至同目录子模块。
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'
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
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { useTheme } from '@/business/utils/theme'
import TradeModal from './TradeModal.vue'
import EditPositionModal from './EditPositionModal.vue'
import AutoInvestModal from './AutoInvestModal.vue'
import TransactionList from './TransactionList.vue'
import { useFundMarket } from './useFundMarket'
import { useTrendCharts } from './useTrendCharts'
import { useTransactionPagination } from './useTransactionPagination'
import { useFeedback } from '@/business/composables/useFeedback'
import { useAsyncSection } from '@/business/composables/useAsyncSection'
import {
  formatCurrency,
  formatDateTimeLabel,
  formatDividendDate,
  formatNumber,
  fundChartViewOptions,
  fundTrendRangeOptions,
  formatAutoInvestFrequency,
  formatAutoInvestStatus,
  getDividendStatusLabel,
  isInitialFundSubscriptionTransaction,
  isTodayMarketData,
  statTone,
} from './utils'
import { getErrorMessage } from '@/business/api/request'

const route = useRoute()
const router = useRouter()
const { isDark } = useTheme()
const detail = ref<InvestmentAssetDetail | null>(null)
const transactions = ref<InvestmentTransaction[]>([])
const autoInvestPlans = ref<InvestmentAutoInvestPlan[]>([])
const fundingAccounts = ref<Account[]>([])
const { showFeedbackModal, feedbackMessage, feedbackType, showFeedback } =
  useFeedback()
const selectedFundChartView = ref<'netValue' | 'profit'>('netValue')
const showDeleteModal = ref(false)
const isDeletingPosition = ref(false)
const deleteError = ref('')
const isSavingAutoInvest = ref(false)

const tradeModalRef = ref<InstanceType<typeof TradeModal> | null>(null)
const editModalRef = ref<InstanceType<typeof EditPositionModal> | null>(null)
const autoInvestModalRef = ref<InstanceType<typeof AutoInvestModal> | null>(
  null,
)

const positionId = computed(() => {
  const raw = Array.isArray(route.query.positionId)
    ? route.query.positionId[0]
    : route.query.positionId
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

const backTo = computed(() => {
  const accountId = detail.value?.position?.accountId
  return accountId
    ? `/finance/accounts/investment/${accountId}`
    : '/finance/accounts/investment'
})

const currentPosition = computed<InvestmentPosition | null>(
  () => detail.value?.position ?? null,
)
const isPendingSubscription = computed(
  () => currentPosition.value?.subscriptionStatus === 'pending',
)
const isFundPosition = computed(
  () =>
    (detail.value?.productType || currentPosition.value?.productType) ===
    'fund',
)
const currentUnitName = computed(
  () => detail.value?.unitName || detail.value?.position.unitName || '份',
)

const pendingAdditionalPurchaseAmount = computed(() =>
  transactions.value
    .filter(
      (entry) =>
        entry.tradeType === 'buy' &&
        entry.settlementStatus === 'pending' &&
        !isInitialFundSubscriptionTransaction(entry),
    )
    .reduce(
      (total, entry) =>
        total +
        Number(entry.amount ?? 0) +
        Number(entry.feeAmount ?? 0) +
        Number(entry.taxAmount ?? 0),
      0,
    ),
)
const summaryAmount = computed(() =>
  currentPosition.value?.subscriptionStatus === 'pending'
    ? historyReady.value
      ? formatCurrency(
          Number(detail.value?.position?.costAmount ?? 0) +
            pendingAdditionalPurchaseAmount.value,
        )
      : '--'
    : formatCurrency(Number(detail.value?.position?.marketValue ?? 0)),
)
const todayProfitValue = computed(() => {
  if (currentPosition.value?.subscriptionStatus === 'pending') {
    return '--'
  }
  if (
    detail.value?.position?.dayProfit === null ||
    detail.value?.position?.dayProfit === undefined
  ) {
    return '--'
  }
  return formatCurrency(Number(detail.value.position.dayProfit))
})

const todayValue = computed(() => {
  if (
    detail.value?.position?.dayProfitRate === null ||
    detail.value?.position?.dayProfitRate === undefined
  ) {
    return '--'
  }
  return formatPercentage(detail.value.position.dayProfitRate)
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
  return (
    isFundPosition.value &&
    !isPendingSubscription.value &&
    Number.isFinite(holdingQuantity) &&
    holdingQuantity > 0
  )
})
const isFundProfitChartActive = computed(
  () =>
    showFundCumulativeProfitChart.value &&
    selectedFundChartView.value === 'profit',
)
const currentTotalProfit = computed(
  () =>
    Number(currentPosition.value?.holdingProfit ?? 0) +
    Number(currentPosition.value?.cumulativeProfit ?? 0),
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
  return (currentFundChartProfit.value / costAmount) * 100
})

const currentFundChartProfitRateText = computed(() => {
  const rate = currentFundChartProfitRate.value
  return rate === null ? '--' : formatPercentage(rate, true)
})

const cumulativeProfitChartAriaLabel = computed(
  () =>
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

const dividendRecords = computed<InvestmentDividendRecord[]>(
  () => detail.value?.dividendRecords ?? [],
)
const showDividendRecordsSection = computed(
  () => dividendRecords.value.length > 0,
)
const showDividendReminder = computed(
  () =>
    isFundPosition.value &&
    Boolean(
      currentPosition.value?.hasRecentDividendPlan &&
        currentPosition.value?.recentDividendDate,
    ),
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
  return Number.isFinite(numeric)
    ? `${formatNumber(numeric, 4)} / ${currentUnitName.value}`
    : '--'
}

const showAutoInvestSection = computed(
  () => isFundPosition.value && !isPendingSubscription.value,
)
const showAutoInvestPlansSection = computed(
  () => showAutoInvestSection.value && autoInvestPlans.value.length > 0,
)

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
  (signal) =>
    getInvestmentTransactions(
      { userId: getStoredCurrentUser()!.id, positionId: positionId.value! },
      { signal },
    ),
  (value) => {
    transactions.value = value
  },
  '完整交易历史加载失败',
)
const {
  loading: historyLoading,
  error: historyError,
  ready: historyReady,
} = historySection
const planSection = useAsyncSection(
  (signal) =>
    getInvestmentAutoInvestPlans(
      { userId: getStoredCurrentUser()!.id, positionId: positionId.value! },
      { signal },
    ),
  (value) => {
    autoInvestPlans.value = value
  },
  '定投计划加载失败',
)
const {
  loading: plansLoading,
  error: plansError,
  ready: plansReady,
} = planSection
const accountSection = useAsyncSection(
  (signal) =>
    getAccounts(
      { userId: getStoredCurrentUser()!.id, status: 'active' },
      { signal },
    ),
  (value) => {
    fundingAccounts.value = value.filter(
      (account) => account.accountTypeCode === 'cash',
    )
  },
  '资金账户加载失败',
)
const {
  loading: accountsLoading,
  error: accountsError,
  ready: accountsReady,
} = accountSection
const feeSection = useAsyncSection(
  (signal) =>
    getInvestmentFundRedeemFees(
      detail.value?.symbol || currentPosition.value?.productSymbol || '',
      { signal },
    ),
  (value) => mergeDetail({ fundRedeemFeeOptions: value }),
  '赎回费率加载失败',
)
const { loading: feesLoading, error: feesError } = feeSection

function loadDetail() {
  resetFundMarketState()
  for (const section of [
    detailSection,
    historySection,
    planSection,
    accountSection,
    feeSection,
  ])
    section.reset()
  detail.value = null
  transactions.value = []
  autoInvestPlans.value = []
  fundingAccounts.value = []
  resetTransactionPage()
  const requests = [detailSection.run()]
  if (getStoredCurrentUser() && positionId.value) {
    requests.push(
      historySection.run(),
      loadTransactionPage(1),
      planSection.run(),
      accountSection.run(),
    )
  }
  // 各区域独立落地；首屏 loading 只跟随 detailSection。
  return Promise.all(requests)
}

// ---------- 定投计划操作 ----------

async function updateAutoInvestPlanStatus(
  plan: InvestmentAutoInvestPlan,
  status: 'active' | 'paused' | 'cancelled',
) {
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
      frequency:
        plan.frequency === 'monthly'
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
    showFeedback(
      status === 'active'
        ? '定投计划已恢复'
        : status === 'paused'
          ? '定投计划已暂停'
          : '定投计划已停用',
      'success',
    )
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
  if (!(await confirmAction('确认删除该定投计划吗？'))) {
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

watch(
  positionId,
  () => {
    void loadDetail()
  },
  { immediate: true },
)

type DividendRow = (typeof dividendRecords.value)[number]
type PlanRow = (typeof autoInvestPlans.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader
      :title="detail?.name || detail?.position.productName || '投资详情'"
    >
      <template #right>
        <section
          class="pc-toolbar investment-detail-actions"
          aria-label="持仓操作"
        >
          <el-button
            class="investment-detail-action-button buy"
            native-type="button"
            :disabled="!accountsReady"
            @click="tradeModalRef?.open('buy')"
          >
            加仓
          </el-button>
          <el-button
            class="investment-detail-action-button sell"
            native-type="button"
            :disabled="isPendingSubscription || !accountsReady"
            @click="tradeModalRef?.open('sell')"
          >
            减仓
          </el-button>
          <el-button
            class="investment-detail-action-button auto-invest"
            native-type="button"
            :disabled="!showAutoInvestSection || !accountsReady || !plansReady"
            @click="autoInvestModalRef?.open()"
          >
            定投
          </el-button>
          <el-button
            class="investment-detail-action-button edit"
            native-type="button"
            :disabled="!accountsReady"
            @click="editModalRef?.open()"
          >
            修改
          </el-button>
          <el-button
            class="investment-detail-action-button delete"
            native-type="button"
            :disabled="isDeletingPosition"
            @click="openDeleteModal"
            type="danger"
            plain
          >
            删除
          </el-button>
        </section>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="loadDetail">重试</el-button>
    </el-alert>
    <CommonLoading v-if="isLoading" />
    <template v-if="detail">
      <MetricStrip
        :items="[
          { label: '持仓市值', value: summaryAmount },
          ...(showTodayMetrics
            ? [
                { label: '当日盈亏', value: todayProfitValue },
                { label: '当日收益率', value: todayValue },
              ]
            : []),
          ...(showFundCumulativeProfitChart
            ? [
                {
                  label: '累计收益',
                  value: formatCurrency(currentFundChartProfit),
                },
                { label: '累计收益率', value: currentFundChartProfitRateText },
              ]
            : []),
        ]"
      />
      <el-alert
        v-if="showDividendReminder"
        :title="dividendReminderLabel"
        :description="dividendReminderText"
        :closable="false"
        type="info"
      />
      <el-alert
        v-if="accountsError"
        :title="accountsError"
        type="error"
        :closable="false"
        show-icon
      >
        <el-button link type="primary" @click="accountSection.run()">
          重试
        </el-button>
      </el-alert>
      <el-alert
        v-if="feesError"
        :title="feesError"
        type="error"
        :closable="false"
        show-icon
      >
        <el-button link type="primary" @click="feeSection.run()">
          重试
        </el-button>
      </el-alert>
      <div class="desktop-main-aside">
        <PagePanel title="收益与走势">
          <header class="investment-detail-card-head">
            <h2>
              {{
                detail.chartType === 'candlestick'
                  ? '股票日K走势'
                  : isFundProfitChartActive
                    ? '累计收益走势'
                    : '累计净值走势'
              }}
            </h2>
            <AmountText
              v-if="isFundPosition && !isFundProfitChartActive"
              tag="span"
              class="investment-detail-period-return"
              :tone="selectedFundPeriodReturnTone"
              :value="fundTrendPeriodReturnText"
            />
            <span v-else>
              {{ externalStatus || detail.source || '行情接口' }}
            </span>
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
          <p v-if="marketLoading" class="investment-detail-description">
            行情加载中…
          </p>
          <p
            v-if="marketError"
            class="investment-detail-message investment-detail-message-error"
          >
            {{ marketError }}
            <el-button
              native-type="button"
              @click="loadExternalMarketData(detail)"
            >
              重试
            </el-button>
          </p>
          <p v-if="historyLoading" class="investment-detail-description">
            完整交易历史加载中…
          </p>
          <p
            v-if="historyError"
            class="investment-detail-message investment-detail-message-error"
          >
            {{ historyError }}
            <el-button native-type="button" @click="historySection.run()">
              重试
            </el-button>
          </p>
          <template v-if="historyReady && isFundProfitChartActive">
            <div
              v-if="cumulativeProfitPoints.length"
              ref="cumulativeProfitChartRef"
              class="pc-chart investment-detail-chart investment-detail-profit-chart"
              role="img"
              :aria-label="cumulativeProfitChartAriaLabel"
            ></div>
            <p v-else class="investment-detail-empty">
              {{ externalStatus || '暂无累计收益走势数据' }}
            </p>
          </template>
          <template v-else-if="historyReady">
            <div
              v-if="detail.chartPoints.length"
              ref="chartRef"
              class="pc-chart investment-detail-chart"
            ></div>
            <p v-else class="investment-detail-empty">暂无走势数据</p>
          </template>
        </PagePanel>
        <PagePanel title="持仓分析">
          <el-descriptions :column="1" border>
            <el-descriptions-item
              v-for="entry in detail.holdingStats"
              :key="entry.label"
              :label="entry.label"
            >
              <AmountText :tone="statTone(entry)" :value="entry.value" />
            </el-descriptions-item>
          </el-descriptions>
          <p class="desktop-note">{{ displayUpdatedAt }}</p>
        </PagePanel>
      </div>
      <el-tabs class="desktop-tabs" model-value="transactions">
        <el-tab-pane label="交易记录" name="transactions">
          <el-alert
            v-if="transactionError"
            :title="transactionError"
            type="error"
            :closable="false"
            show-icon
          >
            <el-button link type="primary" @click="loadTransactionPage()">
              重试
            </el-button>
          </el-alert>
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
        </el-tab-pane>
        <el-tab-pane label="行情数据" name="market">
          <PagePanel title="资产详细数据">
            <el-descriptions :column="2" border>
              <el-descriptions-item
                v-for="entry in detail.marketStats"
                :key="entry.label"
                :label="entry.label"
              >
                <AmountText :tone="statTone(entry)" :value="entry.value" />
              </el-descriptions-item>
            </el-descriptions>
          </PagePanel>
        </el-tab-pane>
        <el-tab-pane
          v-if="showDividendRecordsSection"
          label="分红计划"
          name="dividends"
        >
          <DesktopTable
            :rows="dividendRecords"
            :loading="false"
            title="近一年分红与计划"
          >
            <!-- @vue-generic {DividendRow} -->
            <el-table-column label="分红日期" min-width="150">
              <template #default="{ row }: { row: DividendRow }">
                {{ formatDividendDate(row.payDate) }}
              </template>
            </el-table-column>
            <!-- @vue-generic {DividendRow} -->
            <el-table-column label="状态" min-width="150">
              <template #default="{ row }: { row: DividendRow }">
                {{ getDividendStatusLabel(row.status, row.actualAmount) }}
              </template>
            </el-table-column>
            <!-- @vue-generic {DividendRow} -->
            <el-table-column label="每单位分红" min-width="150">
              <template #default="{ row }: { row: DividendRow }">
                {{ formatDividendPerUnit(row.dividendPerUnit) }}
              </template>
            </el-table-column>
            <!-- @vue-generic {DividendRow} -->
            <el-table-column label="预计 / 到账分红" min-width="150">
              <template #default="{ row }: { row: DividendRow }">
                {{
                  formatCurrency(
                    Number(row.actualAmount ?? row.expectedAmount ?? 0),
                  )
                }}
              </template>
            </el-table-column>
          </DesktopTable>
        </el-tab-pane>
        <el-tab-pane
          v-if="showAutoInvestPlansSection"
          label="定投计划"
          name="plans"
        >
          <el-alert
            v-if="plansError"
            :title="plansError"
            type="error"
            :closable="false"
            show-icon
          >
            <el-button link type="primary" @click="planSection.run()">
              重试
            </el-button>
          </el-alert>
          <DesktopTable
            :rows="autoInvestPlans"
            :loading="plansLoading"
            title="定投计划"
          >
            <!-- @vue-generic {PlanRow} -->
            <el-table-column label="金额" min-width="150">
              <template #default="{ row }: { row: PlanRow }">
                {{ formatCurrency(Number(row.amount)) }}
              </template>
            </el-table-column>
            <!-- @vue-generic {PlanRow} -->
            <el-table-column label="频率" min-width="150">
              <template #default="{ row }: { row: PlanRow }">
                {{ formatAutoInvestFrequency(row.frequency) }}
              </template>
            </el-table-column>
            <!-- @vue-generic {PlanRow} -->
            <el-table-column label="状态" min-width="150">
              <template #default="{ row }: { row: PlanRow }">
                {{ formatAutoInvestStatus(row.status) }}
              </template>
            </el-table-column>
            <el-table-column
              prop="nextExecuteDate"
              label="下次执行"
              min-width="160"
              sortable="custom"
              show-overflow-tooltip
            />
            <el-table-column
              prop="fundingAccountName"
              label="扣款账户"
              min-width="170"
              sortable="custom"
              show-overflow-tooltip
            />
            <!-- @vue-generic {PlanRow} -->
            <el-table-column fixed="right" label="操作" width="180">
              <template #default="{ row }: { row: PlanRow }">
                <el-button
                  link
                  type="primary"
                  :disabled="isSavingAutoInvest || !accountsReady"
                  @click="autoInvestModalRef?.open(row)"
                >
                  修改
                </el-button>
                <el-button
                  link
                  type="primary"
                  :disabled="isSavingAutoInvest"
                  @click="
                    updateAutoInvestPlanStatus(
                      row,
                      row.status === 'active' ? 'paused' : 'active',
                    )
                  "
                >
                  {{ row.status === 'active' ? '暂停' : '恢复' }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  :disabled="isSavingAutoInvest"
                  @click="removeAutoInvestPlan(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </DesktopTable>
        </el-tab-pane>
      </el-tabs>
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
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal
      v-model="showDeleteModal"
      title="删除投资资产"
      size="compact"
      :close-on-overlay="!isDeletingPosition"
      @close="closeDeleteModal"
    >
      <div class="pc-form-grid investment-detail-modal-form">
        <p class="investment-detail-description">
          删除后该资产的持仓与交易记录会一并移除，确认删除“{{
            detail?.name || detail?.position.productName || '当前资产'
          }}”吗？
        </p>
        <p v-if="deleteError" class="investment-detail-modal-error">
          {{ deleteError }}
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar investment-detail-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeletingPosition"
            @click="closeDeleteModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isDeletingPosition"
            @click="confirmDeletePosition"
          >
            {{ isDeletingPosition ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
