// 黄金账户持仓页：持仓新增/修改/删除/卖出与账户设置等交互动作。
import { computed, ref, type ComputedRef, type Ref } from 'vue'
import {
  createInvestmentPosition,
  createInvestmentTransaction,
  deleteInvestmentPosition,
  getInvestmentPosition,
  updateAccount,
  updateInvestmentPosition,
  type Account,
  type GoldAccountHolding,
  type InvestmentPosition,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { formatAmount } from '@/utils/format'
import { getErrorMessage } from '@/api/request'
import type { FeedbackType } from '@/composables/useFeedback'
import { formatCompactWeight, toApiDateTime, toFixedNumber } from './goldPositionFormat'

export type UseGoldPositionActionsOptions = {
  goldAccounts: Ref<Account[]>
  isScopedToAccount: ComputedRef<boolean>
  scopedAccount: ComputedRef<Account | null>
  realtimeGoldPrice: ComputedRef<number>
  formAccountId: Ref<string>
  formFundingAccountId: Ref<string>
  syncFormSelections: () => void
  loadGoldPosition: () => Promise<void>
  showFeedback: (message: string, type: FeedbackType) => void
}

export function useGoldPositionActions(options: UseGoldPositionActionsOptions) {
  const {
    goldAccounts,
    isScopedToAccount,
    scopedAccount,
    realtimeGoldPrice,
    formAccountId,
    formFundingAccountId,
    syncFormSelections,
    loadGoldPosition,
    showFeedback,
  } = options

  const isSavingPosition = ref(false)
  const isDeletingPosition = ref(false)
  const isLoadingPositionDetail = ref(false)
  const isSellingPosition = ref(false)
  const isSavingAccountSettings = ref(false)
  const actionError = ref('')
  const positionFormError = ref('')
  const deleteError = ref('')
  const sellError = ref('')
  const showPositionModal = ref(false)
  const showDeleteConfirmModal = ref(false)
  const showSellModal = ref(false)
  const showAccountSettingsModal = ref(false)
  const editingPosition = ref<InvestmentPosition | null>(null)
  const selectedHolding = ref<GoldAccountHolding | null>(null)
  const deletingHolding = ref<GoldAccountHolding | null>(null)
  const sellingPosition = ref<InvestmentPosition | null>(null)

  const DEFAULT_GOLD_PRODUCT_NAME = '积存金'
  const DEFAULT_GOLD_PRODUCT_SYMBOL = 'AU9999'

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

  const isCurrentAccountFixed = computed(() => isScopedToAccount.value && scopedAccount.value !== null)
  const positionModalTitle = computed(() => (
    editingPosition.value ? '修改黄金持仓' : '新增黄金持仓'
  ))
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

  return {
    isSavingPosition,
    isDeletingPosition,
    isLoadingPositionDetail,
    isSellingPosition,
    isSavingAccountSettings,
    actionError,
    positionFormError,
    deleteError,
    sellError,
    showPositionModal,
    showDeleteConfirmModal,
    showSellModal,
    showAccountSettingsModal,
    editingPosition,
    selectedHolding,
    deletingHolding,
    sellingPosition,
    formWeight,
    formBuyPrice,
    formIncludeInNetWorth,
    formRemark,
    sellFundingAccountId,
    sellWeight,
    sellPrice,
    sellFee,
    sellRemark,
    accountSettingsName,
    accountSettingsIcon,
    accountSettingsRemark,
    accountSettingsIncludeInNetWorth,
    accountSettingsError,
    isCurrentAccountFixed,
    positionModalTitle,
    sellModalTitle,
    sellAvailableQuantityText,
    sellAmountPreview,
    openAccountSettingsModal,
    closeAccountSettingsModal,
    saveAccountSettings,
    openCreateModal,
    toggleHoldingActions,
    handleHoldingCardKeydown,
    loadPositionDetail,
    openEditModalFromAction,
    closePositionModal,
    resetSellForm,
    resetForm,
    savePosition,
    openDeleteConfirmModal,
    openDeleteConfirmModalFromAction,
    openSellModalFromAction,
    closeDeleteConfirmModal,
    closeSellModal,
    confirmDeletePosition,
    submitSellPosition,
  }
}
