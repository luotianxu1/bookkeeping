<script setup lang="ts">
import { formatAmount } from '@/utils/format'
// 投资账户详情页：展示单个投资账户的汇总、持仓列表和新增持仓。
// 持仓展示的纯函数见 ./holdingDisplay，新增资产弹窗逻辑见 ./useAddAsset。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AccountIcon from '@/components/common/AccountIcon/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import {
  getAccounts,
  getInvestmentAutoInvestPlans,
  getInvestmentPositions,
  getInvestmentSummary,
  getInvestmentTransactions,
  runInvestmentQuoteSyncTask,
  updateAccount,
  type Account,
  type InvestmentAutoInvestPlan,
  type InvestmentPosition,
  type InvestmentSummary,
  type InvestmentTransaction,
} from '@/api/modules/finance'
import {
  DEFAULT_INVESTMENT_ACCOUNT_ICON,
  allAccountIconOptions,
  resolveInvestmentAccountIcon,
} from '@/data/account-icons'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getErrorMessage, isCancelRequest } from '@/api/request'
import { useRequestAbort } from '@/composables/useRequestAbort'
import { useVisibilityPolling } from '@/composables/useVisibilityPolling'
import {
  compareHoldingsByProfitRate,
  formatCurrency,
  getAllocationPercent,
  getDividendReminderLabel,
  getDividendReminderText,
  getHoldingActionLabel,
  getHoldingActionRate,
  getHoldingActionTone,
  getHoldingActionValue,
  getHoldingBottomLabel,
  getHoldingBottomRate,
  getHoldingBottomValue,
  getHoldingDayProfitRateText,
  getHoldingDayProfitText,
  getHoldingDayProfitTone,
  getHoldingMarketValue,
  getHoldingMarketValueLabel,
  getHoldingPendingAmount,
  getHoldingPrimaryLabel,
  getHoldingPrimaryValue,
  getHoldingProfitLabel,
  getHoldingProfitRate,
  getHoldingProfitValue,
  getHoldingQuantityTag,
  getHoldingSecondaryLabel,
  getHoldingSecondaryValue,
  getHoldingSyncText,
  hasAutoInvestPlan,
  hasRecentDividendReminder,
  isAfterQuoteRefreshTime,
  isInitialFundSubscriptionTransaction,
  isPendingSubscription,
  shouldDisplayHolding,
} from './holdingDisplay'
import { useAddAsset } from './useAddAsset'

const router = useRouter()
const route = useRoute()
// 数据加载防竞态：新加载先 abort 旧请求，旧响应按代际号静默丢弃
const { signalFor } = useRequestAbort()
let loadSeq = 0

const investmentTabs = ['A股', '基金']
const HOLDING_VIEW_MODE_STORAGE_KEY = 'bookkeeping_investment_holding_view_mode'
type HoldingViewMode = 'card' | 'list'
const activeTab = ref('A股')
const holdingViewMode = ref<HoldingViewMode>('card')
const showAccountSettingsModal = ref(false)
const isLoading = ref(true)
const pageError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const currentTime = ref(new Date())
const investmentAccounts = ref<Account[]>([])
const fundingAccounts = ref<Account[]>([])
const summary = ref<InvestmentSummary>({
  userId: 0,
  totalMarketValue: 0,
  dayProfit: 0,
  dayProfitRate: 0,
  holdingProfit: 0,
  holdingProfitRate: 0,
  cumulativeProfit: 0,
  cumulativeProfitRate: 0,
  lastSyncedAt: null as string | null,
})
const positions = ref<InvestmentPosition[]>([])
const transactions = ref<InvestmentTransaction[]>([])
const autoInvestPlans = ref<InvestmentAutoInvestPlan[]>([])
const isRefreshingQuotes = ref(false)
const isSavingAccountSettings = ref(false)
const accountSettingsName = ref('')
const accountSettingsIcon = ref(DEFAULT_INVESTMENT_ACCOUNT_ICON)
const accountSettingsRemark = ref('')
const accountSettingsIncludeInNetWorth = ref(true)
const accountSettingsError = ref('')

const {
  showAddModal,
  isSaving,
  formError,
  addAssetKeyword,
  addAssetFundingAccount,
  addAssetDividendType,
  addAssetQuantity,
  addAssetAmount,
  addAssetSubscriptionTimeSlot,
  isLookingUpProduct,
  addAssetSearchResults,
  fundingAccountOptions,
  subscriptionTimeSlotOptions,
  dividendTypeOptions,
  isFundSubscriptionDraft,
  isQuantityBasedDraft,
  saveAsset,
  lookupProductByKeyword,
  getAddAssetResultKey,
  selectAddAssetProduct,
  getAddAssetResultLabel,
  getAddAssetResultMeta,
  isAddAssetProductSelected,
  openAddModal,
  closeAddModal,
} = useAddAsset({
  fundingAccounts,
  getDefaultCategory: () => (activeTab.value === 'A股' ? 'stock' : 'fund'),
  getAccountId: () => selectedAccountId.value,
  onSaved: () => loadInvestmentData(),
  showFeedback,
})

const visiblePositions = computed(() =>
  positions.value.filter((item) => shouldDisplayHolding(item)),
)

const holdings = computed(() => {
  const targetType = activeTab.value === 'A股' ? 'stock' : 'fund'

  return visiblePositions.value
    .filter((item) => item.productType === targetType)
    .slice()
    .sort(compareHoldingsByProfitRate)
})
const showInvestmentTabSwitch = computed(() => {
  const hasStocks = visiblePositions.value.some((item) => item.productType === 'stock')
  const hasFunds = visiblePositions.value.some((item) => item.productType === 'fund')
  return hasStocks && hasFunds
})

const pendingAmountsByPositionId = computed(() => {
  const pendingMap = new Map<number, number>()
  for (const entry of transactions.value) {
    if (entry.settlementStatus !== 'pending' || !entry.positionId) {
      continue
    }
    const nextAmount = (pendingMap.get(entry.positionId) ?? 0) + Number(entry.amount || 0)
    pendingMap.set(entry.positionId, nextAmount)
  }
  return pendingMap
})
const pendingAdditionalAmountsByPositionId = computed(() => {
  const pendingMap = new Map<number, number>()
  for (const entry of transactions.value) {
    if (entry.settlementStatus !== 'pending' || entry.tradeType !== 'buy' || !entry.positionId || isInitialFundSubscriptionTransaction(entry)) {
      continue
    }
    const nextAmount = (pendingMap.get(entry.positionId) ?? 0) + Number(entry.amount || 0)
    pendingMap.set(entry.positionId, nextAmount)
  }
  return pendingMap
})
const autoInvestPositionIds = computed(() => new Set(
  autoInvestPlans.value
    .filter((plan) => plan.status !== 'cancelled')
    .map((plan) => plan.positionId),
))
const hasRefreshableHoldings = computed(() =>
  visiblePositions.value.some((item) => item.productType === 'fund' || item.productType === 'stock'),
)
const shouldShowQuoteRefreshButton = computed(() => hasRefreshableHoldings.value && isAfterQuoteRefreshTime(currentTime.value))

const totalSummaryProfit = computed(() => Number(summary.value.cumulativeProfit ?? 0) + Number(summary.value.holdingProfit ?? 0))
const totalSummaryProfitRate = computed(() => {
  const currentCostAmount = Number(summary.value.totalMarketValue ?? 0) - Number(summary.value.holdingProfit ?? 0)
  if (!Number.isFinite(currentCostAmount) || currentCostAmount <= 0) {
    return 0
  }
  return (totalSummaryProfit.value / currentCostAmount) * 100
})
const summaryDayProfitText = computed(() =>
  summary.value.dayProfit === null || summary.value.dayProfit === undefined ? '--' : formatAmount(summary.value.dayProfit),
)
const summaryDayProfitRateText = computed(() =>
  summary.value.dayProfitRate === null || summary.value.dayProfitRate === undefined ? '--' : `${formatAmount(summary.value.dayProfitRate)}%`,
)

const summaryMetrics = computed(() => [
  { label: '持仓盈亏', value: summary.value.holdingProfit },
  { label: '持仓盈亏率', value: summary.value.holdingProfitRate, isRate: true },
  { label: '累计总收益', value: totalSummaryProfit.value },
  { label: '累计总收益率', value: totalSummaryProfitRate.value, isRate: true },
])

onMounted(() => {
  restoreHoldingViewMode()
  void loadInvestmentData()
  currentTime.value = new Date()
})

// 时钟轮询感知页面可见性：后台标签页跳过，恢复可见立即校准
useVisibilityPolling(() => {
  currentTime.value = new Date()
}, 30 * 1000)

watch(() => route.params.accountId, () => {
  void loadInvestmentData()
})

watch(holdingViewMode, (nextMode) => {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(HOLDING_VIEW_MODE_STORAGE_KEY, nextMode)
})

function restoreHoldingViewMode() {
  if (typeof window === 'undefined') {
    return
  }
  const stored = window.localStorage.getItem(HOLDING_VIEW_MODE_STORAGE_KEY)
  if (stored === 'card' || stored === 'list') {
    holdingViewMode.value = stored
  }
}

function toggleHoldingViewMode() {
  holdingViewMode.value = holdingViewMode.value === 'list' ? 'card' : 'list'
}

function openAccountSettingsModal() {
  const account = selectedAccount.value
  if (!account) {
    return
  }

  accountSettingsName.value = account.name
  accountSettingsIcon.value = resolveInvestmentAccountIcon(account.icon)
  accountSettingsRemark.value = account.remark ?? ''
  accountSettingsIncludeInNetWorth.value = account.includeInNetWorth
  accountSettingsError.value = ''
  showAccountSettingsModal.value = true
}

function closeAccountSettingsModal(force = false) {
  if (isSavingAccountSettings.value && !force) {
    return
  }

  showAccountSettingsModal.value = false
  accountSettingsName.value = ''
  accountSettingsIcon.value = DEFAULT_INVESTMENT_ACCOUNT_ICON
  accountSettingsRemark.value = ''
  accountSettingsIncludeInNetWorth.value = true
  accountSettingsError.value = ''
}

async function saveAccountSettings() {
  if (isSavingAccountSettings.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const account = selectedAccount.value
  const trimmedName = accountSettingsName.value.trim()
  const trimmedRemark = accountSettingsRemark.value.trim()

  if (!currentUser || !account) {
    accountSettingsError.value = '当前投资账户不存在'
    return
  }
  if (!trimmedName) {
    accountSettingsError.value = '请输入账户名称'
    return
  }

  isSavingAccountSettings.value = true
  accountSettingsError.value = ''

  try {
    await updateAccount(account.id, {
      userId: currentUser.id,
      accountTypeId: account.accountTypeId,
      contactId: account.contactId ?? null,
      name: trimmedName,
      icon: accountSettingsIcon.value,
      color: account.color ?? null,
      currencyCode: account.currencyCode || 'CNY',
      currentBalance: account.currentBalance,
      includeInNetWorth: accountSettingsIncludeInNetWorth.value,
      sortOrder: account.sortOrder,
      status: account.status || 'active',
      remark: trimmedRemark || null,
    })

    closeAccountSettingsModal(true)
    showFeedback('投资账户已更新', 'success')
    await loadInvestmentData()
  } catch (error) {
    const message = getErrorMessage(error, '投资账户保存失败')
    accountSettingsError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingAccountSettings.value = false
  }
}

function parseAccountId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

const selectedAccountId = computed(() => parseAccountId(route.params.accountId))
const selectedAccount = computed(() =>
  investmentAccounts.value.find((account) => account.id === selectedAccountId.value) ?? null,
)

async function loadInvestmentData() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看投资详情'
    isLoading.value = false
    return
  }

  if (!selectedAccountId.value) {
    pageError.value = '投资账户不存在'
    isLoading.value = false
    return
  }

  const seq = ++loadSeq
  const signal = signalFor()

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }, { signal }),
    ])
    investmentAccounts.value = accountList.filter((account) => account.accountTypeCode === 'investment')
    fundingAccounts.value = accountList.filter((account) => account.accountTypeCode === 'cash')

    const targetAccount = investmentAccounts.value.find((account) => account.id === selectedAccountId.value)
    if (!targetAccount) {
      positions.value = []
      summary.value = {
        userId: currentUser.id,
        totalMarketValue: 0,
        dayProfit: 0,
        dayProfitRate: 0,
        holdingProfit: 0,
        holdingProfitRate: 0,
        cumulativeProfit: 0,
        cumulativeProfitRate: 0,
        lastSyncedAt: null,
      }
      transactions.value = []
      autoInvestPlans.value = []
      pageError.value = '投资账户不存在'
      return
    }

    const [summaryData, positionList, transactionList, autoInvestPlanList] = await Promise.all([
      getInvestmentSummary({ userId: currentUser.id, accountId: targetAccount.id }, { signal }),
      getInvestmentPositions({ userId: currentUser.id, accountId: targetAccount.id }, { signal }),
      // 该列表仅用于汇总各持仓的待确认金额，服务端过滤 pending 避免全量拉取交易流水
      getInvestmentTransactions({ userId: currentUser.id, accountId: targetAccount.id, settlementStatus: 'pending' }, { signal }),
      getInvestmentAutoInvestPlans({ userId: currentUser.id, accountId: targetAccount.id }, { signal }),
    ])

    if (seq !== loadSeq) {
      return
    }

    summary.value = summaryData
    positions.value = positionList
    transactions.value = transactionList
    autoInvestPlans.value = autoInvestPlanList
    syncActiveInvestmentTab()
  } catch (error) {
    if (isCancelRequest(error) || seq !== loadSeq) {
      return
    }
    pageError.value = getErrorMessage(error, '投资账户加载失败')
  } finally {
    if (seq === loadSeq) {
      isLoading.value = false
    }
  }
}

async function refreshQuoteData() {
  if (!shouldShowQuoteRefreshButton.value || isRefreshingQuotes.value) {
    return
  }
  const currentUser = getStoredCurrentUser()
  if (!currentUser || !selectedAccountId.value) {
    showFeedback('当前投资账户不存在', 'error')
    return
  }

  isRefreshingQuotes.value = true
  try {
    await runInvestmentQuoteSyncTask({
      userId: currentUser.id,
      accountId: selectedAccountId.value,
    })
    showFeedback('已提交刷新任务，请稍后刷新页面', 'success')
  } catch (error) {
    const message = getErrorMessage(error, '投资持仓刷新失败')
    showFeedback(message, 'error')
  } finally {
    isRefreshingQuotes.value = false
  }
}

function syncActiveInvestmentTab() {
  const hasStocks = visiblePositions.value.some((item) => item.productType === 'stock')
  const hasFunds = visiblePositions.value.some((item) => item.productType === 'fund')

  if (!investmentTabs.includes(activeTab.value)) {
    activeTab.value = 'A股'
  }

  if (!hasStocks && hasFunds) {
    activeTab.value = '基金'
    return
  }

  if (hasStocks) {
    activeTab.value = 'A股'
    return
  }

  if (hasFunds) {
    activeTab.value = '基金'
  }
}

function openInvestmentDetail(positionId: number) {
  router.push({ path: '/finance/accounts/investment/detail', query: { positionId } })
}

function openLiquidationList() {
  if (!selectedAccountId.value) {
    return
  }
  router.push(`/finance/accounts/investment/${selectedAccountId.value}/liquidations`)
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

</script>

<template>
  <section class="investment-account-page" aria-label="投资账户">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="投资详情" back-to="/finance/accounts/investment" back-label="返回投资账户">
      <template #right>
        <div class="investment-header-actions">
          <CommonHeaderActionButton
            class="investment-view-toggle"
            :class="{ 'is-active': holdingViewMode === 'list' }"
            :label="holdingViewMode === 'list' ? '切换为卡片样式' : '切换为列表样式'"
            :aria-pressed="holdingViewMode === 'list'"
            @click="toggleHoldingViewMode"
          >
            <svg v-if="holdingViewMode === 'list'" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <rect x="3.5" y="4.5" width="17" height="15" rx="2" stroke="currentColor" stroke-width="1.8" />
              <path d="M3.5 9.5H20.5" stroke="currentColor" stroke-width="1.8" />
              <path d="M3.5 14.5H20.5" stroke="currentColor" stroke-width="1.8" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <rect x="3.5" y="4.5" width="17" height="6" rx="2" stroke="currentColor" stroke-width="1.8" />
              <rect x="3.5" y="13.5" width="17" height="6" rx="2" stroke="currentColor" stroke-width="1.8" />
            </svg>
          </CommonHeaderActionButton>
          <CommonHeaderActionButton label="清仓明细" @click="openLiquidationList">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M8 6H21" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <path d="M8 12H21" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <path d="M8 18H21" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <path d="M3.5 6H4.5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
              <path d="M3.5 12H4.5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
              <path d="M3.5 18H4.5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
            </svg>
          </CommonHeaderActionButton>
          <CommonHeaderRefreshButton
            v-if="shouldShowQuoteRefreshButton"
            label="刷新投资持仓数据"
            :loading="isRefreshingQuotes"
            @click="refreshQuoteData"
          />
          <CommonHeaderActionButton
            v-if="selectedAccount"
            label="修改投资账户"
            @click="openAccountSettingsModal"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 15.2A3.2 3.2 0 1 0 12 8.8A3.2 3.2 0 0 0 12 15.2Z" stroke="currentColor" stroke-width="1.8" />
              <path d="M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 1 1 16.96 19.71L16.9 19.65A1.65 1.65 0 0 0 15.08 19.32A1.65 1.65 0 0 0 14.08 20.83V21A2 2 0 1 1 10.08 21V20.91A1.65 1.65 0 0 0 9 19.4A1.65 1.65 0 0 0 7.18 19.73L7.12 19.79A2 2 0 1 1 4.29 16.96L4.35 16.9A1.65 1.65 0 0 0 4.68 15.08A1.65 1.65 0 0 0 3.17 14.08H3A2 2 0 1 1 3 10.08H3.09A1.65 1.65 0 0 0 4.6 9A1.65 1.65 0 0 0 4.27 7.18L4.21 7.12A2 2 0 1 1 7.04 4.29L7.1 4.35A1.65 1.65 0 0 0 8.92 4.68H9A1.65 1.65 0 0 0 10 3.17V3A2 2 0 1 1 14 3V3.09A1.65 1.65 0 0 0 15 4.6A1.65 1.65 0 0 0 16.82 4.27L16.88 4.21A2 2 0 1 1 19.71 7.04L19.65 7.1A1.65 1.65 0 0 0 19.32 8.92V9A1.65 1.65 0 0 0 20.83 10H21A2 2 0 1 1 21 14H20.91A1.65 1.65 0 0 0 19.4 15Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </CommonHeaderActionButton>
        </div>
      </template>
    </PageHeader>

    <p v-if="pageError" class="investment-message investment-message-error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="investment-summary-card" aria-label="投资总览">
        <div class="investment-summary-top">
          <div class="investment-summary-main">
            <div class="investment-summary-account">
              <AccountIcon
                v-if="selectedAccount"
                :icon="selectedAccount.icon"
                :account-type-code="selectedAccount.accountTypeCode"
                :color="selectedAccount.color"
                :name="selectedAccount.name"
                :size="28"
              />
              <p>{{ selectedAccount?.name || '投资账户' }}</p>
            </div>
            <AmountText tag="strong" :value="formatAmount(summary.totalMarketValue)" />
            <span>{{ selectedAccount?.remark?.trim() || `同步于 ${summary.lastSyncedAt ? new Date(summary.lastSyncedAt).toLocaleString('zh-CN') : '暂无'}` }}</span>
          </div>
          <div class="investment-summary-side">
            <span>今日盈亏</span>
            <AmountText tag="strong" class="investment-summary-profit" font-size="18px" :value="summaryDayProfitText" />
            <AmountText tag="span" class="investment-summary-rate" :value="summaryDayProfitRateText" />
          </div>
        </div>

        <div class="investment-metrics">
          <div v-for="metric in summaryMetrics" :key="metric.label" class="investment-metric-item">
            <span>{{ metric.label }}</span>
            <AmountText
              tag="strong"
              class="investment-metric-value"
              font-size="12px"
              :value="metric.isRate ? `${formatAmount(metric.value)}%` : formatAmount(metric.value)"
            />
          </div>
        </div>
      </section>

      <SegmentedControl
        v-if="showInvestmentTabSwitch"
        v-model="activeTab"
        :options="investmentTabs"
        label="投资分类筛选"
      />

      <section v-if="holdings.length > 0 && holdingViewMode === 'card'" class="investment-holdings" aria-label="持仓列表">
        <article
          v-for="holding in holdings"
          :key="holding.id"
          class="investment-holding-card"
          @click="openInvestmentDetail(holding.id)"
        >
          <div class="holding-card-top">
            <div class="holding-card-main">
              <div class="holding-card-left-top">
                <div class="holding-title">
                  <strong>{{ holding.productName }}</strong>
                </div>
                <div class="holding-value-line">
                  <span>{{ getHoldingMarketValueLabel(holding) }}</span>
                  <AmountText tag="strong" class="holding-market-value" tone="inherit" :value="getHoldingMarketValue(holding, pendingAdditionalAmountsByPositionId.get(holding.id) ?? 0)" />
                  <span v-if="hasAutoInvestPlan(holding, autoInvestPositionIds)" class="holding-auto-invest-tag">定投</span>
                </div>
              </div>

              <div v-if="!isPendingSubscription(holding)" class="holding-card-right-top">
                <span>{{ getHoldingActionLabel(holding) }}</span>
                <div class="holding-action-group">
                  <AmountText
                    tag="strong"
                    class="holding-action-value"
                    :tone="getHoldingActionTone(holding)"
                    :value="getHoldingActionValue(holding)"
                  />
                  <AmountText
                    v-if="!isPendingSubscription(holding)"
                    tag="span"
                    class="holding-action-rate"
                    :tone="getHoldingActionTone(holding)"
                    :value="getHoldingActionRate(holding)"
                  />
                </div>
              </div>
            </div>
          </div>

          <div class="holding-panel">
            <div class="holding-panel-row">
              <div class="holding-panel-field">
                <span>{{ getHoldingPrimaryLabel(holding) }}</span>
                <div class="holding-primary-line">
                  <AmountText tag="strong" class="holding-panel-value" tone="inherit" :value="getHoldingPrimaryValue(holding)" />
                  <span class="holding-quantity-tag">{{ getHoldingQuantityTag(holding) }}</span>
                  <span v-if="getHoldingSyncText(holding)" class="holding-sync-tag">{{ getHoldingSyncText(holding) }}</span>
                </div>
              </div>
              <div class="holding-panel-field is-end">
                <span>{{ getHoldingSecondaryLabel(holding) }}</span>
                <AmountText tag="strong" class="holding-panel-value is-secondary" tone="inherit" :value="getHoldingSecondaryValue(holding)" />
              </div>
            </div>

            <p v-if="getHoldingPendingAmount(holding, pendingAmountsByPositionId) > 0" class="holding-pending-amount">
              待确认金额
              <AmountText
                tag="span"
                class="holding-pending-amount-value"
                :value="formatCurrency(getHoldingPendingAmount(holding, pendingAmountsByPositionId))"
              />
            </p>

            <div v-if="hasRecentDividendReminder(holding)" class="holding-dividend-notice">
              <span class="holding-dividend-badge">{{ getDividendReminderLabel(holding) }}</span>
              <span class="holding-dividend-text">{{ getDividendReminderText(holding) }}</span>
            </div>

            <div class="holding-divider"></div>

            <div class="holding-summary-row">
              <div class="holding-summary-metrics">
                <div class="holding-summary-item">
                  <span>{{ getHoldingBottomLabel(holding) }}</span>
                  <div class="holding-pnl-line">
                    <AmountText tag="strong" class="holding-pnl-value" :value="getHoldingBottomValue(holding)" />
                    <AmountText tag="span" class="holding-pnl-rate" :value="getHoldingBottomRate(holding)" />
                  </div>
                </div>

                <div class="holding-summary-item">
                  <span>{{ getHoldingProfitLabel(holding) }}</span>
                  <div class="holding-pnl-line">
                    <AmountText tag="strong" class="holding-pnl-value" :value="getHoldingProfitValue(holding)" />
                    <AmountText tag="span" class="holding-pnl-rate" :value="getHoldingProfitRate(holding)" />
                  </div>
                </div>
              </div>

              <div class="holding-summary-allocation">
                <div class="holding-allocation">
                  <div class="holding-allocation-track">
                    <span :style="{ width: `${getAllocationPercent(holding, summary.totalMarketValue)}%` }"></span>
                  </div>
                  <AmountText tag="strong" tone="inherit" :value="`${formatAmount(getAllocationPercent(holding, summary.totalMarketValue))}%`" />
                </div>
              </div>
            </div>
          </div>
        </article>
      </section>

      <section v-else-if="holdings.length > 0" class="investment-holding-list" aria-label="持仓列表">
        <header class="holding-list-header">
          <span>名称</span>
          <span>当日收益</span>
          <span>持有收益</span>
        </header>

        <article
          v-for="holding in holdings"
          :key="holding.id"
          class="holding-list-row"
          @click="openInvestmentDetail(holding.id)"
        >
          <div class="holding-list-main">
            <strong class="holding-list-name">{{ holding.productName }}</strong>
            <div class="holding-list-amount">
              <span>{{ getHoldingMarketValueLabel(holding) }}</span>
              <AmountText tag="strong" tone="inherit" :value="getHoldingMarketValue(holding, pendingAdditionalAmountsByPositionId.get(holding.id) ?? 0)" />
            </div>
          </div>

          <div class="holding-list-metric">
            <AmountText
              tag="strong"
              class="holding-pnl-value"
              :tone="getHoldingDayProfitTone(holding)"
              :value="getHoldingDayProfitText(holding)"
            />
            <AmountText
              tag="span"
              class="holding-pnl-rate"
              :tone="getHoldingDayProfitTone(holding)"
              :value="getHoldingDayProfitRateText(holding)"
            />
          </div>

          <div class="holding-list-metric">
            <AmountText tag="strong" class="holding-pnl-value" :value="getHoldingProfitValue(holding)" />
            <AmountText tag="span" class="holding-pnl-rate" :value="getHoldingProfitRate(holding)" />
          </div>
        </article>
      </section>

      <p v-if="holdings.length === 0" class="investment-message">
        暂无持仓
      </p>
    </template>

    <FloatingAddButton aria-label="新增投资资产" storage-key="investment-account" @click="openAddModal" />

    <CommonModal
      v-model="showAccountSettingsModal"
      title="修改投资账户"
      :close-on-overlay="!isSavingAccountSettings"
      @close="closeAccountSettingsModal()"
    >
      <div class="investment-add-modal-form investment-account-settings-form">
        <CommonInput v-model="accountSettingsName" label="账户名称" placeholder="输入投资账户名称" />
        <CommonSelect
          v-model="accountSettingsIcon"
          label="账户图标"
          :options="allAccountIconOptions"
        />
        <CommonInput v-model="accountSettingsRemark" label="备注" placeholder="输入账户说明" />
        <CommonSwitch v-model="accountSettingsIncludeInNetWorth" label="是否计入总资产" />
        <p v-if="accountSettingsError" class="investment-add-error">{{ accountSettingsError }}</p>
      </div>

      <template #footer>
        <div class="investment-add-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSavingAccountSettings"
            @click="closeAccountSettingsModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSavingAccountSettings"
            @click="saveAccountSettings"
          >
            {{ isSavingAccountSettings ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal v-model="showAddModal" title="添加资产" size="compact" :show-close="false">
      <div class="investment-add-modal-form">
        <div class="investment-search-dropdown">
          <label class="investment-search-field" aria-label="输入代码或名称">
            <input
              v-model="addAssetKeyword"
              type="search"
              inputmode="search"
              placeholder="输入代码或名称"
              @keydown.enter.prevent="lookupProductByKeyword()"
            />
            <button type="button" :disabled="isLookingUpProduct" @click="lookupProductByKeyword()">
              <span aria-hidden="true">⌕</span>
              {{ isLookingUpProduct ? '搜索中' : '搜索' }}
            </button>
          </label>

          <div v-if="addAssetSearchResults.length > 0" class="investment-search-results" aria-label="搜索结果">
            <button
              v-for="product in addAssetSearchResults"
              :key="getAddAssetResultKey(product)"
              type="button"
              :class="['investment-search-result-item', { active: isAddAssetProductSelected(product) }]"
              @click="selectAddAssetProduct(getAddAssetResultKey(product))"
            >
              <strong>{{ getAddAssetResultLabel(product) }}</strong>
              <span>{{ getAddAssetResultMeta(product) }}</span>
            </button>
          </div>
        </div>

        <label v-if="isFundSubscriptionDraft" class="investment-add-modal-field">
          <span>申购时点</span>
          <SegmentedControl
            v-model="addAssetSubscriptionTimeSlot"
            :options="subscriptionTimeSlotOptions"
            label="基金申购时点"
          />
        </label>

        <label v-if="isFundSubscriptionDraft" class="investment-add-modal-field">
          <span>分红方式</span>
          <SegmentedControl
            v-model="addAssetDividendType"
            :options="dividendTypeOptions"
            label="基金分红方式"
          />
        </label>

        <CommonSelect
          v-model="addAssetFundingAccount"
          :label="isFundSubscriptionDraft && addAssetDividendType === 'cash' ? '现金分红到账及申购账户' : '申购资金账户（选填）'"
          :options="fundingAccountOptions"
        />

        <label v-if="isQuantityBasedDraft" class="investment-add-modal-field">
          <span>持仓份数</span>
          <input
            v-model="addAssetQuantity"
            class="investment-field-control"
            type="number"
            min="0"
            step="0.01"
            inputmode="decimal"
            placeholder="0.00"
            aria-label="持仓份数"
          />
        </label>

        <label class="investment-add-modal-field">
          <span>{{ isFundSubscriptionDraft ? '买入金额' : '成本价' }}</span>
          <input
            v-model="addAssetAmount"
            class="investment-field-control"
            type="number"
            min="0"
            step="0.01"
            inputmode="decimal"
            placeholder="0.00"
            :aria-label="isFundSubscriptionDraft ? '买入金额' : '成本价'"
          />
        </label>
        <p v-if="formError" class="investment-add-error">{{ formError }}</p>
      </div>

      <template #footer>
        <div class="investment-add-modal-actions">
          <button class="investment-modal-button secondary" type="button" :disabled="isSaving" @click="closeAddModal">取消</button>
          <button class="investment-modal-button primary" type="button" :disabled="isSaving" @click="saveAsset">
            {{ isSaving ? '保存中...' : '确认添加' }}
          </button>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
