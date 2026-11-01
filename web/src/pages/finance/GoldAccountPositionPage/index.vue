<script setup lang="ts">
import { formatAmount } from '@/utils/format'
// 黄金账户持仓页：对接黄金聚合查询接口，并补充持仓新增、修改、删除能力。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AccountIcon from '@/components/common/AccountIcon/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  createInvestmentPosition,
  createInvestmentTransaction,
  deleteInvestmentPosition,
  getAccounts,
  getGoldAccountHoldings,
  getGoldLiquidations,
  getGoldAccountSummary,
  getInvestmentPosition,
  updateAccount,
  updateInvestmentPosition,
  type Account,
  type GoldAccountHolding,
  type GoldLiquidation,
  type GoldAccountSummary,
  type InvestmentPosition,
} from '@/api/modules/finance'
import { refreshGoldPriceCache, useGoldPriceCache } from '@/utils/gold-price-cache'
import { getStoredCurrentUser } from '@/utils/current-user'
import { allAccountIconOptions } from '@/data/account-icons'
import { getErrorMessage } from '@/api/request'
import { useFeedback } from '@/composables/useFeedback'
import {
  buildSummary,
  decorateHolding,
  formatAveragePriceTag,
  formatCompactWeight,
  formatCreatedDate,
  formatGoldUpdatedAt,
  formatHoldingMeta,
  formatRate,
  formatSummaryWeight,
  getProfitToneClass,
  parseAccountId,
  toApiDateTime,
  toFixedNumber,
} from './goldPositionFormat'

const route = useRoute()
const router = useRouter()

const isLoading = ref(true)
const isSavingPosition = ref(false)
const isDeletingPosition = ref(false)
const isLoadingPositionDetail = ref(false)
const isSellingPosition = ref(false)
const isSavingAccountSettings = ref(false)
const pageError = ref('')
const actionError = ref('')
const positionFormError = ref('')
const deleteError = ref('')
const sellError = ref('')
const isRefreshingGold = ref(false)
const { showFeedbackModal, feedbackMessage, feedbackType, showFeedback } = useFeedback()
const showPositionModal = ref(false)
const showDeleteConfirmModal = ref(false)
const showSellModal = ref(false)
const showAccountSettingsModal = ref(false)
const summary = ref<GoldAccountSummary>({
  totalWeight: 0,
  averagePrice: 0,
  purchaseTotal: 0,
  estimatedValue: 0,
  estimatedProfit: 0,
  profitRate: 0,
  cumulativeProfit: 0,
})
const liquidation = ref<GoldLiquidation>({
  cumulativeWeight: 0,
  cumulativeProfit: 0,
  records: [],
})
const holdings = ref<GoldAccountHolding[]>([])
const goldAccounts = ref<Account[]>([])
const fundingAccounts = ref<Account[]>([])
const goldPrice = useGoldPriceCache()
const editingPosition = ref<InvestmentPosition | null>(null)
const selectedHolding = ref<GoldAccountHolding | null>(null)
const deletingHolding = ref<GoldAccountHolding | null>(null)
const sellingPosition = ref<InvestmentPosition | null>(null)

const DEFAULT_GOLD_PRODUCT_NAME = '积存金'
const DEFAULT_GOLD_PRODUCT_SYMBOL = 'AU9999'

const formAccountId = ref('')
const formFundingAccountId = ref('')
const formWeight = ref('')
const formBuyPrice = ref('')
const formIncludeInNetWorth = ref(true)
const formRemark = ref('')
const sellFundingAccountId = ref('')
const sellWeight = ref('')
const sellPrice = ref('')
const sellFee = ref('0')
const sellRemark = ref('')
const accountSettingsName = ref('')
const accountSettingsIcon = ref('gold')
const accountSettingsRemark = ref('')
const accountSettingsIncludeInNetWorth = ref(true)
const accountSettingsError = ref('')

let requestVersion = 0

const selectedAccountId = computed(() => parseAccountId(route.query.accountId))
const isScopedToAccount = computed(() => selectedAccountId.value !== null)
const scopedAccount = computed(() =>
  goldAccounts.value.find((account) => account.id === selectedAccountId.value) ?? null,
)
const realtimeGoldPrice = computed(() => Number(goldPrice.value?.price ?? 0))
const realtimeGoldUpdatedAt = computed(() => formatGoldUpdatedAt(goldPrice.value?.updatedAt))
const isCurrentAccountFixed = computed(() => isScopedToAccount.value && scopedAccount.value !== null)
const displayHoldings = computed(() => holdings.value.map((item) => decorateHolding(item, realtimeGoldPrice.value)))
const filteredHoldings = computed(() => {
  if (!selectedAccountId.value) {
    return displayHoldings.value
  }

  return displayHoldings.value.filter((item) => item.accountId === selectedAccountId.value)
})
const scopedCumulativeProfit = computed(() => {
  if (!selectedAccountId.value) {
    return Number(liquidation.value.cumulativeProfit ?? summary.value.cumulativeProfit ?? 0)
  }

  return Number(
    (liquidation.value.records ?? [])
      .filter((item) => item.accountId === selectedAccountId.value)
      .reduce((total, item) => total + Number(item.profit ?? 0), 0)
      .toFixed(2),
  )
})
const visibleSummary = computed(() => (
  realtimeGoldPrice.value > 0
    ? buildSummary(filteredHoldings.value, scopedCumulativeProfit.value)
    : isScopedToAccount.value
      ? buildSummary(filteredHoldings.value, scopedCumulativeProfit.value)
      : {
        ...summary.value,
        cumulativeProfit: scopedCumulativeProfit.value,
      }
))
const hasHoldings = computed(() => filteredHoldings.value.length > 0)
const positionModalTitle = computed(() => (
  editingPosition.value ? '修改黄金持仓' : '新增黄金持仓'
))
const fundingAccountOptions = computed(() => {
  return [
    { label: '不关联资金账户', value: '' },
    ...fundingAccounts.value.map((account) => ({
      label: `${account.name}（余额 ${formatAmount(account.currentBalance)}）`,
      value: String(account.id),
    })),
  ]
})
// 与 fundingAccountOptions 选项完全一致，保留独立名称以兼容模板绑定。
const sellFundingAccountOptions = fundingAccountOptions
const sellModalTitle = computed(() => (
  selectedHolding.value?.productName || selectedHolding.value?.productSymbol || '卖出黄金'
))
const sellAvailableQuantityText = computed(() => {
  const amount = Number(sellingPosition.value?.availableQuantity ?? 0)
  return `${formatCompactWeight(amount)}克`
})
const sellAmountPreview = computed(() => {
  const quantity = Number(sellWeight.value)
  const price = Number(sellPrice.value)
  const fee = Number(sellFee.value)
  const amount = quantity * price
  if (
    !Number.isFinite(quantity)
    || quantity <= 0
    || !Number.isFinite(price)
    || price <= 0
    || !Number.isFinite(fee)
    || fee < 0
    || fee > amount
  ) {
    return '--'
  }
  return `${formatAmount(amount - fee)}元`
})

onMounted(() => {
  void loadGoldPosition()
})

async function loadGoldPosition() {
  const currentRequestVersion = ++requestVersion
  const currentUser = getStoredCurrentUser()

  if (!currentUser) {
    pageError.value = '请先登录后查看黄金持仓'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [summaryData, holdingList, accountList, liquidationData] = await Promise.all([
      getGoldAccountSummary(currentUser.id),
      getGoldAccountHoldings(currentUser.id),
      getAccounts({ userId: currentUser.id, status: 'active' }),
      getGoldLiquidations(currentUser.id),
    ])

    if (currentRequestVersion !== requestVersion) {
      return
    }

    summary.value = summaryData
    holdings.value = holdingList
    goldAccounts.value = accountList.filter((account) => account.accountTypeCode === 'gold')
    fundingAccounts.value = accountList.filter((account) => account.accountTypeCode === 'cash')
    liquidation.value = liquidationData
    syncFormSelections()
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = getErrorMessage(error, '黄金持仓加载失败')
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

async function refreshGoldData() {
  if (isRefreshingGold.value) {
    return
  }

  isRefreshingGold.value = true

  try {
    await refreshGoldPriceCache({ forceRemote: true })
    showFeedback('黄金信息已刷新', 'success')
  } catch (error) {
    const message = getErrorMessage(error, '黄金信息刷新失败')
    showFeedback(message, 'error')
  } finally {
    isRefreshingGold.value = false
  }
}

function openLiquidationList() {
  if (selectedAccountId.value) {
    router.push({
      path: '/finance/accounts/gold/liquidation',
      query: { accountId: String(selectedAccountId.value) },
    })
    return
  }

  router.push('/finance/accounts/gold/liquidation')
}

function openAccountSettingsModal() {
  const account = scopedAccount.value
  if (!account) {
    return
  }

  accountSettingsName.value = account.name
  accountSettingsIcon.value = account.icon?.trim() || 'gold'
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
  accountSettingsIcon.value = 'gold'
  accountSettingsRemark.value = ''
  accountSettingsIncludeInNetWorth.value = true
  accountSettingsError.value = ''
}

async function saveAccountSettings() {
  if (isSavingAccountSettings.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const account = scopedAccount.value
  const trimmedName = accountSettingsName.value.trim()
  const trimmedRemark = accountSettingsRemark.value.trim()

  if (!currentUser || !account) {
    accountSettingsError.value = '当前黄金账户不存在'
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
    showFeedback('黄金账户已更新', 'success')
    await loadGoldPosition()
  } catch (error) {
    const message = getErrorMessage(error, '黄金账户保存失败')
    accountSettingsError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingAccountSettings.value = false
  }
}

function openCreateModal() {
  if (goldAccounts.value.length === 0) {
    showFeedback('请先新增黄金账户', 'error')
    return
  }

  editingPosition.value = null
  resetForm()
  syncFormSelections()
  showPositionModal.value = true
}

function toggleHoldingActions(holding: GoldAccountHolding) {
  if (isLoadingPositionDetail.value || isSavingPosition.value || isDeletingPosition.value || isSellingPosition.value) {
    return
  }

  actionError.value = ''
  if (selectedHolding.value?.positionId === holding.positionId) {
    selectedHolding.value = null
    return
  }

  selectedHolding.value = holding
}

function handleHoldingCardKeydown(event: KeyboardEvent, holding: GoldAccountHolding) {
  if (event.key !== 'Enter' && event.key !== ' ') {
    return
  }

  event.preventDefault()
  toggleHoldingActions(holding)
}

async function loadPositionDetail(positionId: number) {
  if (isLoadingPositionDetail.value) {
    return null
  }

  isLoadingPositionDetail.value = true

  try {
    return await getInvestmentPosition(positionId)
  } finally {
    isLoadingPositionDetail.value = false
  }
}

async function openEditModalFromAction() {
  const holding = selectedHolding.value
  if (!holding || isSavingPosition.value) {
    return
  }

  actionError.value = ''
  positionFormError.value = ''

  try {
    const position = await loadPositionDetail(holding.positionId)
    if (!position) {
      return
    }

    selectedHolding.value = null
    editingPosition.value = position
    formAccountId.value = String(position.accountId)
    formWeight.value = String(Number(position.holdingQuantity ?? 0))
    formBuyPrice.value = String(Number(position.avgCostPrice ?? 0))
    formIncludeInNetWorth.value = position.includeInNetWorth
    formRemark.value = position.remark ?? ''
    showPositionModal.value = true
  } catch (error) {
    const message = getErrorMessage(error, '持仓详情加载失败')
    actionError.value = message
    showFeedback(message, 'error')
  }
}

function closePositionModal(force = false) {
  if ((isSavingPosition.value || isLoadingPositionDetail.value) && !force) {
    return
  }

  showPositionModal.value = false
  editingPosition.value = null
  resetForm()
}

function resetSellForm() {
  sellFundingAccountId.value = ''
  sellWeight.value = ''
  sellPrice.value = realtimeGoldPrice.value > 0 ? String(realtimeGoldPrice.value) : ''
  sellFee.value = '0'
  sellRemark.value = ''
  sellError.value = ''
}

function resetForm() {
  formWeight.value = ''
  formBuyPrice.value = ''
  formIncludeInNetWorth.value = true
  formRemark.value = ''
  positionFormError.value = ''
  syncFormSelections()
}

function syncFormSelections() {
  if (!goldAccounts.value.some((account) => String(account.id) === formAccountId.value)) {
    const preferredAccountId = selectedAccountId.value && goldAccounts.value.some((account) => account.id === selectedAccountId.value)
      ? selectedAccountId.value
      : goldAccounts.value[0]?.id
    formAccountId.value = preferredAccountId ? String(preferredAccountId) : ''
  }

  if (!fundingAccounts.value.some((account) => String(account.id) === formFundingAccountId.value)) {
    formFundingAccountId.value = ''
  }
}

async function savePosition() {
  if (isSavingPosition.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    positionFormError.value = '请先登录后再保存持仓'
    return
  }

  const accountId = isCurrentAccountFixed.value
    ? Number(scopedAccount.value?.id ?? formAccountId.value)
    : Number(formAccountId.value)
  const fundingAccountId = Number(formFundingAccountId.value)
  const normalizedFundingAccountId = Number.isFinite(fundingAccountId) && fundingAccountId > 0
    ? fundingAccountId
    : undefined
  const weight = Number(formWeight.value)
  const buyPrice = Number(formBuyPrice.value)
  const remark = formRemark.value.trim()
  const purchaseAmount = weight * buyPrice

  if (!Number.isFinite(accountId) || accountId <= 0) {
    positionFormError.value = '请选择黄金账户'
    return
  }

  if (!Number.isFinite(weight) || weight <= 0) {
    positionFormError.value = '请输入有效的持仓重量'
    return
  }

  if (!Number.isFinite(buyPrice) || buyPrice <= 0) {
    positionFormError.value = '请输入有效的买入价格'
    return
  }

  if (!Number.isFinite(purchaseAmount) || purchaseAmount <= 0) {
    positionFormError.value = '持仓重量或买入价格不正确'
    return
  }

  isSavingPosition.value = true
  positionFormError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      accountId,
      holdingQuantity: toFixedNumber(weight, 6),
      availableQuantity: toFixedNumber(weight, 6),
      frozenQuantity: 0,
      costAmount: toFixedNumber(purchaseAmount, 2),
      currentPrice: toFixedNumber(buyPrice, 6),
      includeInNetWorth: formIncludeInNetWorth.value,
      status: editingPosition.value?.status ?? 'active',
      remark: remark || null,
    }

    if (editingPosition.value) {
      await updateInvestmentPosition(editingPosition.value.id, {
        ...payload,
        productId: editingPosition.value.productId,
      })
      showFeedback('修改成功', 'success')
    } else {
      await createInvestmentPosition({
        ...payload,
        fundingAccountId: normalizedFundingAccountId,
        product: {
          productType: 'gold',
          symbol: DEFAULT_GOLD_PRODUCT_SYMBOL,
          name: DEFAULT_GOLD_PRODUCT_NAME,
          currencyCode: 'CNY',
          unitName: '克',
          pricePrecision: 2,
          status: 'active',
          remark: null,
        },
      })
      showFeedback('新增成功', 'success')
    }

    closePositionModal(true)
    await loadGoldPosition()
  } catch (error) {
    const message = getErrorMessage(error, '黄金持仓保存失败')
    positionFormError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingPosition.value = false
  }
}

function openDeleteConfirmModal(holding: GoldAccountHolding) {
  deletingHolding.value = holding
  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function openDeleteConfirmModalFromAction() {
  if (!selectedHolding.value) {
    return
  }

  openDeleteConfirmModal(selectedHolding.value)
  selectedHolding.value = null
}

async function openSellModalFromAction() {
  const holding = selectedHolding.value
  if (!holding || isSellingPosition.value) {
    return
  }

  actionError.value = ''
  sellError.value = ''

  try {
    const position = await loadPositionDetail(holding.positionId)
    if (!position) {
      return
    }

    sellingPosition.value = position
    selectedHolding.value = null
    resetSellForm()
    showSellModal.value = true
  } catch (error) {
    const message = getErrorMessage(error, '持仓详情加载失败')
    actionError.value = message
    showFeedback(message, 'error')
  }
}

function closeDeleteConfirmModal(force = false) {
  if (isDeletingPosition.value && !force) {
    return
  }

  showDeleteConfirmModal.value = false
  deletingHolding.value = null
  deleteError.value = ''
}

function closeSellModal(force = false) {
  if (isSellingPosition.value && !force) {
    return
  }

  showSellModal.value = false
  sellingPosition.value = null
  sellError.value = ''
}

async function confirmDeletePosition() {
  if (!deletingHolding.value || isDeletingPosition.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    deleteError.value = '请先登录后再删除持仓'
    return
  }

  isDeletingPosition.value = true
  deleteError.value = ''

  try {
    await deleteInvestmentPosition(deletingHolding.value.positionId, currentUser.id)
    closeDeleteConfirmModal(true)
    showFeedback('删除成功', 'success')
    await loadGoldPosition()
  } catch (error) {
    const message = getErrorMessage(error, '黄金持仓删除失败')
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeletingPosition.value = false
  }
}

async function submitSellPosition() {
  if (isSellingPosition.value || !sellingPosition.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    sellError.value = '请先登录后再卖出持仓'
    return
  }

  const fundingAccountId = Number(sellFundingAccountId.value)
  const normalizedFundingAccountId = Number.isFinite(fundingAccountId) && fundingAccountId > 0
    ? fundingAccountId
    : undefined
  const quantity = Number(sellWeight.value)
  const price = Number(sellPrice.value)
  const fee = Number(sellFee.value)
  const amount = quantity * price
  const availableQuantity = Number(sellingPosition.value.availableQuantity ?? 0)

  if (!Number.isFinite(quantity) || quantity <= 0) {
    sellError.value = '请输入有效的卖出克数'
    return
  }
  if (quantity > availableQuantity) {
    sellError.value = '卖出克数不能超过可用持仓'
    return
  }
  if (!Number.isFinite(price) || price <= 0) {
    sellError.value = '请输入有效的卖出价格'
    return
  }
  if (!Number.isFinite(amount) || amount <= 0) {
    sellError.value = '卖出克数或价格不正确'
    return
  }
  if (!Number.isFinite(fee) || fee < 0) {
    sellError.value = '请输入有效的手续费'
    return
  }
  if (fee > amount) {
    sellError.value = '手续费不能超过卖出金额'
    return
  }

  isSellingPosition.value = true
  sellError.value = ''

  try {
    await createInvestmentTransaction({
      userId: currentUser.id,
      accountId: sellingPosition.value.accountId,
      positionId: sellingPosition.value.id,
      productId: sellingPosition.value.productId,
      tradeType: 'sell',
      quantity: toFixedNumber(quantity, 6),
      price: toFixedNumber(price, 6),
      amount: toFixedNumber(amount, 2),
      feeAmount: toFixedNumber(fee, 2),
      taxAmount: 0,
      currencyCode: sellingPosition.value.currencyCode || 'CNY',
      fundingAccountId: normalizedFundingAccountId,
      tradeAt: toApiDateTime(new Date()),
      remark: sellRemark.value.trim() || null,
    })
    closeSellModal(true)
    showFeedback('卖出成功', 'success')
    await loadGoldPosition()
  } catch (error) {
    const message = getErrorMessage(error, '卖出失败')
    sellError.value = message
    showFeedback(message, 'error')
  } finally {
    isSellingPosition.value = false
  }
}
</script>

<template>
  <section class="gold-position-page" aria-label="黄金账户持仓">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="黄金账户持仓" back-to="/finance/accounts/gold" back-label="返回黄金账户">
      <template #right>
        <div class="gold-position-header-actions">
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
            label="刷新黄金信息"
            :loading="isRefreshingGold"
            @click="refreshGoldData"
          />
          <CommonHeaderActionButton
            v-if="scopedAccount"
            label="修改黄金账户"
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

    <p v-if="pageError" class="gold-position-message gold-position-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="gold-position-summary">
        <div class="summary-head">
          <div class="summary-head-main">
            <div v-if="scopedAccount" class="summary-account-identity">
              <AccountIcon
                :icon="scopedAccount.icon"
                :account-type-code="scopedAccount.accountTypeCode"
                :color="scopedAccount.color"
                :name="scopedAccount.name"
                :size="28"
              />
              <div class="summary-account-copy">
                <strong>{{ scopedAccount.name }}</strong>
              </div>
            </div>
            <div class="summary-main">
              <strong class="summary-weight-value">{{ formatSummaryWeight(visibleSummary.totalWeight) }}</strong>
            </div>
          </div>
          <div class="gold-price-chip" aria-label="当前实时金价">
            <span class="gold-price-chip-label">实时金价</span>
            <strong class="gold-price-chip-value">
              {{ realtimeGoldPrice > 0 ? `${formatAmount(realtimeGoldPrice)} 元/克` : '--' }}
            </strong>
            <span class="gold-price-chip-time">{{ realtimeGoldUpdatedAt }}</span>
          </div>
        </div>

        <div class="summary-grid">
          <article>
            <span>平均克价(元)</span>
            <AmountText tag="strong" class="summary-static-amount" tone="inherit" :value="formatAmount(visibleSummary.averagePrice)" />
          </article>
          <article>
            <span>购入总价(元)</span>
            <AmountText tag="strong" class="summary-static-amount" tone="inherit" :value="formatAmount(visibleSummary.purchaseTotal)" />
          </article>
          <article>
            <span>预估价值(元)</span>
            <AmountText tag="strong" class="summary-static-amount" tone="inherit" :value="formatAmount(visibleSummary.estimatedValue)" />
          </article>
          <article>
            <span>预估收益(元)</span>
            <AmountText tag="strong" :value="formatAmount(visibleSummary.estimatedProfit)" />
          </article>
          <article>
            <span>收益率(%)</span>
            <AmountText tag="strong" :value="formatRate(visibleSummary.profitRate)" />
          </article>
          <article>
            <span>累计收益(元)</span>
            <AmountText tag="strong" :value="formatAmount(visibleSummary.cumulativeProfit)" />
          </article>
        </div>
      </section>

      <p class="position-section-title">持仓明细</p>

      <section class="position-list">
        <template v-if="hasHoldings">
          <article
            v-for="item in filteredHoldings"
            :key="item.id"
            class="position-card"
          >
            <div
              class="position-card-main"
              :class="{ 'is-disabled': isLoadingPositionDetail || isSavingPosition || isDeletingPosition || isSellingPosition }"
              role="button"
              tabindex="0"
              @click="toggleHoldingActions(item)"
              @keydown="handleHoldingCardKeydown($event, item)"
            >
              <span class="position-price-tag">{{ formatAveragePriceTag(item.avgCostPrice) }}</span>
              <div class="position-card-top">
                <strong class="position-card-title">{{ item.productName || item.productSymbol || '黄金持仓' }}</strong>
                <span :class="['position-profit-inline', getProfitToneClass(item.holdingProfit)]">
                  <span>收益:</span>
                  <AmountText tag="strong" tone="inherit" show-sign :value="formatAmount(item.holdingProfit)" />
                </span>
              </div>
              <div class="position-card-bottom">
                <span class="position-meta">{{ formatHoldingMeta(item.weight, item.purchaseAmount) }}</span>
                <span>{{ formatCreatedDate(item.createdAt) }}</span>
              </div>
            </div>

            <div
              v-if="selectedHolding?.positionId === item.positionId"
              class="gold-position-card-actions"
            >
              <CommonButton
                variant="secondary"
                type="button"
                :disabled="isLoadingPositionDetail || isSellingPosition"
                @click="void openEditModalFromAction()"
              >
                {{ isLoadingPositionDetail ? '读取中...' : '修改' }}
              </CommonButton>
              <CommonButton
                variant="secondary"
                class="gold-position-button-danger"
                type="button"
                :disabled="isLoadingPositionDetail || isSellingPosition"
                @click="openDeleteConfirmModalFromAction"
              >
                删除
              </CommonButton>
              <CommonButton
                variant="primary"
                type="button"
                :disabled="isLoadingPositionDetail || isSellingPosition"
                @click="void openSellModalFromAction()"
              >
                卖出
              </CommonButton>
            </div>
            <p
              v-if="selectedHolding?.positionId === item.positionId && actionError"
              class="gold-position-form-error gold-position-card-error"
            >
              {{ actionError }}
            </p>
          </article>
        </template>

        <p v-else class="gold-position-empty">
          {{ isScopedToAccount ? '该黄金账户暂无持仓' : '暂无黄金持仓' }}
        </p>
      </section>
    </template>

    <FloatingAddButton
      aria-label="新增黄金持仓"
      storage-key="gold-position"
      @click="openCreateModal"
    />

    <CommonModal
      v-model="showAccountSettingsModal"
      title="修改黄金账户"
      :close-on-overlay="!isSavingAccountSettings"
      @close="closeAccountSettingsModal()"
    >
      <form class="gold-position-form" @submit.prevent="saveAccountSettings">
        <CommonInput v-model="accountSettingsName" label="账户名称" placeholder="输入黄金账户名称" />
        <CommonSelect
          v-model="accountSettingsIcon"
          label="账户图标"
          :options="allAccountIconOptions"
          :disabled="isSavingAccountSettings"
        />
        <CommonInput v-model="accountSettingsRemark" label="备注" placeholder="输入账户说明" />
        <CommonSwitch v-model="accountSettingsIncludeInNetWorth" label="是否计入总资产" />
        <p v-if="accountSettingsError" class="gold-position-form-error">{{ accountSettingsError }}</p>
      </form>

      <template #footer>
        <div class="gold-position-actions">
          <CommonButton
            variant="secondary"
            type="button"
            :disabled="isSavingAccountSettings"
            @click="closeAccountSettingsModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            type="button"
            :disabled="isSavingAccountSettings"
            @click="saveAccountSettings"
          >
            {{ isSavingAccountSettings ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showPositionModal"
      :title="positionModalTitle"
      :close-on-overlay="!isSavingPosition"
      @close="closePositionModal"
    >
      <form class="gold-position-form" @submit.prevent="savePosition">
        <CommonSelect
          v-if="!editingPosition"
          v-model="formFundingAccountId"
          label="资金账户（选填）"
          :options="fundingAccountOptions"
          :disabled="false"
        />
        <p
          v-if="!editingPosition"
          class="gold-position-form-hint gold-position-form-hint-warning"
        >
          不选则只记录黄金持仓；如选择资金账户，保存时会同步扣减该现金账户余额
        </p>
        <CommonInput
          v-model="formWeight"
          label="持仓重量(克)"
          placeholder="请输入持仓重量"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="formBuyPrice"
          label="买入价格(元/克)"
          placeholder="请输入买入价格"
          input-type="number"
          input-mode="decimal"
        />
        <CommonSwitch v-model="formIncludeInNetWorth" label="是否计入总资产" />
        <CommonInput v-model="formRemark" label="备注" placeholder="例如：长期配置" />
        <p v-if="positionFormError" class="gold-position-form-error">{{ positionFormError }}</p>
      </form>

      <template #footer>
        <div class="gold-position-actions">
          <CommonButton variant="secondary" type="button" :disabled="isSavingPosition" @click="closePositionModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" type="button" :disabled="isSavingPosition" @click="savePosition">
            {{ isSavingPosition ? '保存中...' : '确认' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :close-on-overlay="!isDeletingPosition"
      @close="closeDeleteConfirmModal"
    >
      <p class="gold-delete-message">
        确认删除“{{ deletingHolding?.productName ?? deletingHolding?.productSymbol ?? '黄金持仓' }}”吗？
      </p>
      <p v-if="deleteError" class="gold-position-form-error">{{ deleteError }}</p>

      <template #footer>
        <div class="gold-position-actions">
          <CommonButton variant="secondary" type="button" :disabled="isDeletingPosition" @click="closeDeleteConfirmModal">
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            class="gold-position-button-danger"
            type="button"
            :disabled="isDeletingPosition"
            @click="confirmDeletePosition"
          >
            {{ isDeletingPosition ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showSellModal"
      :title="sellModalTitle"
      :close-on-overlay="!isSellingPosition"
      @close="closeSellModal"
    >
      <form class="gold-position-form" @submit.prevent="submitSellPosition">
        <CommonSelect
          v-model="sellFundingAccountId"
          label="到账资金账户"
          :options="sellFundingAccountOptions"
          :disabled="isSellingPosition"
        />
        <CommonInput
          v-model="sellWeight"
          label="卖出克数(克)"
          placeholder="请输入卖出克数"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="sellPrice"
          label="卖出价格(元/克)"
          placeholder="请输入卖出价格"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="sellFee"
          label="手续费(元)"
          placeholder="请输入手续费"
          input-type="number"
          input-mode="decimal"
        />
        <p class="gold-position-form-hint">
          可卖持仓：{{ sellAvailableQuantityText }}，预计到账：{{ sellAmountPreview }}
        </p>
        <CommonInput v-model="sellRemark" label="备注" placeholder="例如：止盈卖出" />
        <p v-if="sellError" class="gold-position-form-error">{{ sellError }}</p>
      </form>

      <template #footer>
        <div class="gold-position-actions">
          <CommonButton variant="secondary" type="button" :disabled="isSellingPosition" @click="closeSellModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" type="button" :disabled="isSellingPosition" @click="submitSellPosition">
            {{ isSellingPosition ? '卖出中...' : '确认卖出' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
