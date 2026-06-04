<script setup lang="ts">
// 投资账户详情页：展示单个投资账户的汇总、持仓列表和新增持仓。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import {
  createInvestmentPosition,
  getAccounts,
  getInvestmentAutoInvestPlans,
  getInvestmentPositions,
  getInvestmentProducts,
  getInvestmentSummary,
  getInvestmentTransactions,
  type Account,
  type InvestmentAutoInvestPlan,
  type InvestmentPosition,
  type InvestmentProduct,
  type InvestmentProductType,
  type InvestmentSummary,
  type InvestmentTransaction,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const router = useRouter()
const route = useRoute()

const investmentTabs = ['A股', '基金']
const activeTab = ref('A股')
const showAddModal = ref(false)
const isLoading = ref(false)
const isSaving = ref(false)
const pageError = ref('')
const formError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
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

const addAssetKeyword = ref('')
const addAssetName = ref('')
const addAssetSymbol = ref('')
const addAssetMarket = ref('')
const addAssetCategory = ref<InvestmentProductType>('fund')
const addAssetFundingAccount = ref('')
const addAssetAmount = ref('')
const addAssetCurrentPrice = ref('')
const addAssetSubscriptionTimeSlot = ref<'before_1500' | 'after_1500'>('before_1500')
const isLookingUpProduct = ref(false)
const productLookupMessage = ref('')
let isFillingProduct = false

const fundingAccountOptions = computed(() =>
  fundingAccounts.value.map((account) => ({
    label: `${account.name}（余额 ${formatAmount(account.currentBalance)}）`,
    value: String(account.id),
  })),
)

const subscriptionTimeSlotOptions = [
  { label: '15点前', value: 'before_1500' },
  { label: '15点后', value: 'after_1500' },
]

const holdings = computed(() => {
  const targetType = activeTab.value === 'A股' ? 'stock' : 'fund'

  return positions.value
    .filter((item) => item.productType === targetType)
    .slice()
    .sort(compareHoldingsByProfitRate)
})
const showInvestmentTabSwitch = computed(() => {
  const hasStocks = positions.value.some((item) => item.productType === 'stock')
  const hasFunds = positions.value.some((item) => item.productType === 'fund')
  return hasStocks && hasFunds
})

const isFundSubscriptionDraft = computed(() => addAssetCategory.value === 'fund')
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
const autoInvestPositionIds = computed(() => new Set(
  autoInvestPlans.value
    .filter((plan) => plan.status !== 'cancelled')
    .map((plan) => plan.positionId),
))

const totalSummaryProfit = computed(() => Number(summary.value.cumulativeProfit ?? 0) + Number(summary.value.holdingProfit ?? 0))
const totalSummaryProfitRate = computed(() => {
  const currentCostAmount = Number(summary.value.totalMarketValue ?? 0) - Number(summary.value.holdingProfit ?? 0)
  if (!Number.isFinite(currentCostAmount) || currentCostAmount <= 0) {
    return 0
  }
  return (totalSummaryProfit.value / currentCostAmount) * 100
})

const summaryMetrics = computed(() => [
  { label: '持仓盈亏', value: summary.value.holdingProfit },
  { label: '持仓盈亏率', value: summary.value.holdingProfitRate, isRate: true },
  { label: '累计总收益', value: totalSummaryProfit.value },
  { label: '累计总收益率', value: totalSummaryProfitRate.value, isRate: true },
])

onMounted(() => {
  void loadInvestmentData()
})

watch(() => route.params.accountId, () => {
  void loadInvestmentData()
})

watch(addAssetKeyword, (nextKeyword) => {
  if (isFillingProduct) {
    return
  }

  const keyword = nextKeyword.trim()
  productLookupMessage.value = ''

  if (keyword === `${addAssetSymbol.value} ${addAssetName.value}`.trim()) {
    return
  }

  clearProductFields()
})

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
    return
  }

  if (!selectedAccountId.value) {
    pageError.value = '投资账户不存在'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }),
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
      getInvestmentSummary({ userId: currentUser.id, accountId: targetAccount.id }),
      getInvestmentPositions({ userId: currentUser.id, accountId: targetAccount.id }),
      getInvestmentTransactions({ userId: currentUser.id, accountId: targetAccount.id }),
      getInvestmentAutoInvestPlans({ userId: currentUser.id, accountId: targetAccount.id }),
    ])

    summary.value = summaryData
    positions.value = positionList
    transactions.value = transactionList
    autoInvestPlans.value = autoInvestPlanList
    syncActiveInvestmentTab()
    if (addAssetFundingAccount.value && !fundingAccounts.value.some((account) => String(account.id) === addAssetFundingAccount.value)) {
      addAssetFundingAccount.value = ''
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '投资账户加载失败'
  } finally {
    isLoading.value = false
  }
}

function syncActiveInvestmentTab() {
  const hasStocks = positions.value.some((item) => item.productType === 'stock')
  const hasFunds = positions.value.some((item) => item.productType === 'fund')

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

async function saveAsset() {
  if (isSaving.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    formError.value = '请先登录后再新增投资'
    return
  }

  const accountId = selectedAccountId.value
  const fundingAccountId = Number(addAssetFundingAccount.value)
  const normalizedFundingAccountId = Number.isFinite(fundingAccountId) && fundingAccountId > 0 ? fundingAccountId : undefined
  const currentPrice = Number(addAssetCurrentPrice.value)
  const costAmount = Number(addAssetAmount.value)
  const isFundProduct = addAssetCategory.value === 'fund'
  const quantity = !isFundProduct && currentPrice > 0 ? costAmount / currentPrice : 0

  if (!addAssetName.value.trim() || !addAssetSymbol.value.trim()) {
    formError.value = '请先搜索并选择有效资产'
    return
  }
  if (!accountId) {
    formError.value = '当前投资账户不存在'
    return
  }
  if (!Number.isFinite(costAmount) || costAmount <= 0) {
    formError.value = '请输入买入金额'
    return
  }
  if (!isFundProduct && (!Number.isFinite(currentPrice) || currentPrice <= 0)) {
    formError.value = '未获取到当前价格，请重新搜索资产'
    return
  }
  if (!isFundProduct && (!Number.isFinite(quantity) || quantity <= 0)) {
    formError.value = '买入金额或当前价格不正确'
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    const savedPosition = await createInvestmentPosition({
      userId: currentUser.id,
      accountId,
      fundingAccountId: normalizedFundingAccountId,
      product: {
        productType: addAssetCategory.value as InvestmentProductType,
        market: addAssetMarket.value.trim() || null,
        symbol: addAssetSymbol.value.trim(),
        name: addAssetName.value.trim(),
        currencyCode: 'CNY',
        unitName: addAssetCategory.value === 'stock' ? '股' : addAssetCategory.value === 'gold' ? '克' : '份',
        status: 'active',
        remark: null,
      },
      holdingQuantity: isFundProduct ? undefined : Number(quantity.toFixed(6)),
      availableQuantity: isFundProduct ? undefined : Number(quantity.toFixed(6)),
      frozenQuantity: 0,
      costAmount: Number(costAmount.toFixed(2)),
      currentPrice: isFundProduct ? undefined : currentPrice,
      tradeAt: toApiDateTime(new Date()),
      subscriptionTimeSlot: isFundProduct ? addAssetSubscriptionTimeSlot.value : undefined,
      includeInNetWorth: true,
      status: 'active',
      remark: null,
    })
    closeAddModal()
    showFeedback(
      isFundProduct ? getFundPositionSubmitMessage(savedPosition) : '新增成功',
      'success',
    )
    await loadInvestmentData()
  } catch (error) {
    const message = error instanceof Error ? error.message : '新增投资失败'
    formError.value = message
    showFeedback(message, 'error')
  } finally {
    isSaving.value = false
  }
}

async function lookupProductByKeyword(keyword = addAssetKeyword.value) {
  if (isLookingUpProduct.value) {
    return
  }

  const searchKeyword = keyword.trim()
  if (!searchKeyword) {
    formError.value = '请输入代码或名称'
    return
  }

  isLookingUpProduct.value = true
  productLookupMessage.value = '正在搜索资产...'
  formError.value = ''

  try {
    const products = await getInvestmentProducts({ keyword: searchKeyword })
    const normalizedKeyword = searchKeyword.toUpperCase()
    const matchedProduct = products.find((product) => product.symbol.toUpperCase() === normalizedKeyword)
      ?? products.find((product) => product.name.includes(searchKeyword))
      ?? products[0]

    if (!matchedProduct) {
      clearProductFields()
      productLookupMessage.value = '未找到该资产，请确认代码或名称'
      return
    }

    fillProductFields(matchedProduct)
  } catch (error) {
    productLookupMessage.value = error instanceof Error ? error.message : '代码识别失败'
  } finally {
    isLookingUpProduct.value = false
  }
}

function fillProductFields(product: InvestmentProduct) {
  isFillingProduct = true
  addAssetName.value = product.name
  addAssetSymbol.value = product.symbol
  addAssetKeyword.value = `${product.symbol} ${product.name}`
  isFillingProduct = false
  addAssetMarket.value = product.market || ''
  addAssetCategory.value = product.productType
  if (product.latestPrice && product.latestPrice > 0) {
    addAssetCurrentPrice.value = String(product.latestPrice)
  }
  productLookupMessage.value = product.productType === 'fund'
    ? `已识别：${product.name}，将按场外基金金额申购并在确认后生成份额`
    : `已识别：${product.name}`
}

function clearProductFields() {
  addAssetName.value = ''
  addAssetSymbol.value = ''
  addAssetMarket.value = ''
  addAssetCurrentPrice.value = ''
}

function openAddModal() {
  resetAddForm()
  showAddModal.value = true
}

function closeAddModal() {
  showAddModal.value = false
  formError.value = ''
}

function resetAddForm() {
  addAssetKeyword.value = ''
  addAssetName.value = ''
  addAssetSymbol.value = ''
  addAssetMarket.value = ''
  addAssetCategory.value = 'fund'
  addAssetFundingAccount.value = ''
  addAssetAmount.value = ''
  addAssetCurrentPrice.value = ''
  addAssetSubscriptionTimeSlot.value = 'before_1500'
  productLookupMessage.value = ''
  formError.value = ''
}

function openInvestmentDetail(positionId: number) {
  router.push({ path: '/finance/accounts/investment/detail', query: { positionId } })
}

function formatCurrency(value: number, digits = 2) {
  const numeric = Number(value)
  const sign = numeric < 0 ? '-' : ''
  return `${sign}¥${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(numeric))}`
}

function formatQuantity(value: number, unitName?: string | null) {
  return `${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value)} ${unitName || '份'}`
}

function formatPrice(value: number) {
  return formatCurrency(value, 4)
}

function formatMonthDay(value?: string | null) {
  if (!value) {
    return '--'
  }
  const normalized = /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    const matched = value.match(/^\d{4}-(\d{2})-(\d{2})/)
    return matched ? `${matched[1]}-${matched[2]}` : value
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day}`
}

function getAllocationPercent(position: InvestmentPosition) {
  const total = Number(summary.value.totalMarketValue)
  if (!Number.isFinite(total) || total <= 0) {
    return 0
  }
  const percent = (Number(position.marketValue) / total) * 100
  if (!Number.isFinite(percent)) {
    return 0
  }
  return Math.min(Math.max(percent, 0), 100)
}

function compareHoldingsByProfitRate(a: InvestmentPosition, b: InvestmentPosition) {
  const aPending = isPendingSubscription(a)
  const bPending = isPendingSubscription(b)
  if (aPending !== bPending) {
    return aPending ? 1 : -1
  }

  const profitDiff = getHoldingTotalProfit(b) - getHoldingTotalProfit(a)
  if (profitDiff !== 0) {
    return profitDiff
  }

  const rateDiff = getHoldingTotalProfitRate(b) - getHoldingTotalProfitRate(a)
  if (rateDiff !== 0) {
    return rateDiff
  }

  return Number(b.marketValue ?? 0) - Number(a.marketValue ?? 0)
}

function isPendingSubscription(position: InvestmentPosition) {
  return position.subscriptionStatus === 'pending'
}

function hasConfirmedHoldingQuantity(position: InvestmentPosition) {
  const quantity = Number(position.holdingQuantity ?? 0)
  return Number.isFinite(quantity) && quantity > 0
}

function getHoldingStatusText(position: InvestmentPosition) {
  if (!isPendingSubscription(position) || hasConfirmedHoldingQuantity(position)) {
    return formatQuantity(position.holdingQuantity, position.unitName)
  }

  return ''
}

function getHoldingQuantityTag(position: InvestmentPosition) {
  const quantityText = getHoldingStatusText(position)
  return quantityText || '待确认'
}

function getHoldingMarketValueLabel(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '申购金额' : '持仓市值'
}

function getHoldingMarketValue(position: InvestmentPosition) {
  return isPendingSubscription(position)
    ? formatCurrency(position.costAmount, 0)
    : formatCurrency(position.marketValue, 0)
}

function getHoldingActionLabel(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '申购处理中' : '今日盈亏'
}

function getHoldingActionValue(position: InvestmentPosition) {
  return isPendingSubscription(position) ? formatCurrency(position.costAmount, 0) : formatCurrency(position.dayProfit)
}

function getHoldingActionRate(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '' : `${formatAmount(position.dayProfitRate)}%`
}

function getHoldingActionTone(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return 'inherit' as const
  }
  const dayProfit = Number(position.dayProfit ?? 0)
  if (!Number.isFinite(dayProfit) || dayProfit === 0) {
    return 'neutral' as const
  }
  return dayProfit > 0 ? 'positive' as const : 'negative' as const
}

function getHoldingPrimaryLabel(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return '确认净值'
  }
  return position.productType === 'stock' ? '最新价' : '最新净值'
}

function getHoldingPrimaryValue(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return formatMonthDay(position.subscriptionAppliedDate)
  }
  return formatPrice(position.currentPrice)
}

function getHoldingSecondaryLabel(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '确认份额' : '成本价'
}

function getHoldingSecondaryValue(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return formatMonthDay(position.subscriptionExpectedConfirmDate || position.subscriptionAppliedDate)
  }
  return formatPrice(position.avgCostPrice)
}

function getHoldingBottomLabel(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '申购金额' : '累计总收益'
}

function getHoldingTotalProfit(position: InvestmentPosition) {
  return Number(position.cumulativeProfit ?? 0) + Number(position.holdingProfit ?? 0)
}

function getHoldingTotalProfitRate(position: InvestmentPosition) {
  const costAmount = Number(position.costAmount ?? 0)
  if (!Number.isFinite(costAmount) || costAmount <= 0) {
    return 0
  }
  return (getHoldingTotalProfit(position) / costAmount) * 100
}

function getHoldingBottomValue(position: InvestmentPosition) {
  return isPendingSubscription(position) ? formatCurrency(position.costAmount, 0) : formatCurrency(getHoldingTotalProfit(position))
}

function getHoldingBottomRate(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '待确认' : `${formatAmount(getHoldingTotalProfitRate(position))}%`
}

function getHoldingPendingAmount(position: InvestmentPosition) {
  return pendingAmountsByPositionId.value.get(position.id) ?? 0
}

function hasAutoInvestPlan(position: InvestmentPosition) {
  return position.productType === 'fund' && autoInvestPositionIds.value.has(position.id)
}

function formatAmount(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function toApiDateTime(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`
}

function getFundPositionSubmitMessage(position: InvestmentPosition) {
  if (position.subscriptionStatus === 'confirmed') {
    return '基金申购已按最新净值确认，份额已生成'
  }
  const appliedDate = position.subscriptionAppliedDate || '--'
  const expectedDate = position.subscriptionExpectedConfirmDate || appliedDate
  if (expectedDate === appliedDate) {
    return `基金申购已提交，将按 ${appliedDate} 净值确认份额`
  }
  return `基金申购已提交，将按 ${appliedDate} 净值确认，预计 ${expectedDate} 完成`
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

    <PageHeader title="投资详情" back-to="/finance/accounts/investment" back-label="返回投资账户" />

    <p v-if="pageError" class="investment-message investment-message-error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="investment-summary-card" aria-label="投资总览">
        <div class="investment-summary-top">
          <div class="investment-summary-main">
            <p>{{ selectedAccount?.name || '投资账户' }}</p>
            <AmountText tag="strong" :value="formatAmount(summary.totalMarketValue)" />
            <span>{{ selectedAccount?.remark?.trim() || `同步于 ${summary.lastSyncedAt ? new Date(summary.lastSyncedAt).toLocaleString('zh-CN') : '暂无'}` }}</span>
          </div>
          <div class="investment-summary-side">
            <span>今日盈亏</span>
            <AmountText tag="strong" class="investment-summary-profit" :value="formatAmount(summary.dayProfit)" />
            <AmountText tag="span" class="investment-summary-rate" :value="`${formatAmount(summary.dayProfitRate)}%`" />
          </div>
        </div>

        <div class="investment-metrics">
          <template v-for="(metric, index) in summaryMetrics" :key="metric.label">
            <div class="investment-metric-item">
              <span>{{ metric.label }}</span>
              <AmountText
                tag="strong"
                class="investment-metric-value"
                :value="metric.isRate ? `${formatAmount(metric.value)}%` : formatAmount(metric.value)"
              />
            </div>
            <div v-if="index !== summaryMetrics.length - 1" class="investment-metric-divider"></div>
          </template>
        </div>
      </section>

      <SegmentedControl
        v-if="showInvestmentTabSwitch"
        v-model="activeTab"
        :options="investmentTabs"
        label="投资分类筛选"
      />

      <section class="investment-holdings" aria-label="持仓列表">
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
                  <AmountText tag="strong" class="holding-market-value" tone="inherit" :value="getHoldingMarketValue(holding)" />
                  <span v-if="hasAutoInvestPlan(holding)" class="holding-auto-invest-tag">定投</span>
                </div>
              </div>

              <div class="holding-card-right-top">
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
                </div>
              </div>
              <div class="holding-panel-field is-end">
                <span>{{ getHoldingSecondaryLabel(holding) }}</span>
                <AmountText tag="strong" class="holding-panel-value is-secondary" tone="inherit" :value="getHoldingSecondaryValue(holding)" />
              </div>
            </div>

            <p v-if="getHoldingPendingAmount(holding) > 0" class="holding-pending-amount">
              待确认金额
              <AmountText
                tag="span"
                class="holding-pending-amount-value"
                :value="formatCurrency(getHoldingPendingAmount(holding))"
              />
            </p>

            <div class="holding-divider"></div>

            <div class="holding-panel-row">
              <div class="holding-panel-field">
                <span>{{ getHoldingBottomLabel(holding) }}</span>
                <div class="holding-pnl-line">
                  <AmountText tag="strong" class="holding-pnl-value" :value="getHoldingBottomValue(holding)" />
                  <AmountText tag="span" class="holding-pnl-rate" :value="getHoldingBottomRate(holding)" />
                </div>
              </div>
              <div class="holding-panel-field is-end">
                <span>仓位占比</span>
                <div class="holding-allocation">
                  <div class="holding-allocation-track">
                    <span :style="{ width: `${getAllocationPercent(holding)}%` }"></span>
                  </div>
                  <AmountText tag="strong" tone="inherit" :value="`${formatAmount(getAllocationPercent(holding))}%`" />
                </div>
              </div>
            </div>
          </div>
        </article>

        <p v-if="holdings.length === 0" class="investment-message">
          暂无持仓
        </p>
      </section>
    </template>

    <FloatingAddButton aria-label="新增投资资产" storage-key="investment-account" @click="openAddModal" />

    <CommonModal v-model="showAddModal" title="添加资产" size="compact" :show-close="false">
      <div class="investment-add-modal-form">
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
        <p v-if="productLookupMessage" class="investment-lookup-message">
          {{ productLookupMessage }}
        </p>

        <p v-if="isFundSubscriptionDraft" class="investment-lookup-message">
          场外基金按金额申购处理；若目标申购日净值已同步，系统会直接确认份额，否则会在净值同步后自动确认。QDII 基金净值通常更新更晚。
        </p>

        <label v-if="isFundSubscriptionDraft" class="investment-add-modal-field">
          <span>申购时点</span>
          <SegmentedControl
            v-model="addAssetSubscriptionTimeSlot"
            :options="subscriptionTimeSlotOptions"
            label="基金申购时点"
          />
        </label>

        <label class="investment-add-modal-field">
          <span>资金账户（选填）</span>
          <select v-model="addAssetFundingAccount" class="investment-field-control">
            <option value="">不选择</option>
            <option v-for="option in fundingAccountOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>

        <label class="investment-add-modal-field">
          <span>买入金额</span>
          <input
            v-model="addAssetAmount"
            class="investment-field-control"
            type="number"
            min="0"
            step="0.01"
            inputmode="decimal"
            placeholder="0.00"
            aria-label="买入金额"
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
