// 「添加资产」弹窗的全部状态与动作：搜索产品、选品回填、表单校验与保存。
// 通过 options 与页面解耦：账户 id、默认资产类别、保存后的刷新和全局反馈都由页面注入。
import { computed, ref, watch, type Ref } from 'vue'
import type { CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import {
  createInvestmentPosition,
  getInvestmentProducts,
  type Account,
  type InvestmentPosition,
  type InvestmentProduct,
  type InvestmentProductType,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getErrorMessage } from '@/api/request'
import { formatAmount } from '@/utils/format'
import { getFundPositionSubmitMessage, toApiDateTime } from './holdingDisplay'

interface UseAddAssetOptions {
  fundingAccounts: Ref<Account[]>
  getDefaultCategory: () => InvestmentProductType
  getAccountId: () => number | null
  onSaved: () => void | Promise<void>
  showFeedback: (message: string, type: 'success' | 'error') => void
}

export function useAddAsset(options: UseAddAssetOptions) {
  const showAddModal = ref(false)
  const isSaving = ref(false)
  const formError = ref('')

  const addAssetKeyword = ref('')
  const addAssetName = ref('')
  const addAssetSymbol = ref('')
  const addAssetMarket = ref('')
  const addAssetCategory = ref<InvestmentProductType>('fund')
  const addAssetFundingAccount = ref('')
  const addAssetDividendType = ref<'cash' | 'reinvest'>('cash')
  const addAssetQuantity = ref('')
  const addAssetAmount = ref('')
  const addAssetCurrentPrice = ref('')
  const addAssetSubscriptionTimeSlot = ref<'before_1500' | 'after_1500'>('before_1500')
  const isLookingUpProduct = ref(false)
  const addAssetSearchResults = ref<InvestmentProduct[]>([])
  const selectedAddAssetResultId = ref('')
  const productLookupMessage = ref('')
  let isFillingProduct = false

  const fundingAccountOptions = computed<CommonSelectOption[]>(() => [
    { label: '不选择', value: '' },
    ...options.fundingAccounts.value.map((account) => ({
      label: `${account.name}（余额 ${formatAmount(account.currentBalance)}）`,
      value: String(account.id),
    })),
  ])

  const subscriptionTimeSlotOptions = [
    { label: '15点前', value: 'before_1500' },
    { label: '15点后', value: 'after_1500' },
  ]
  const dividendTypeOptions = [
    { label: '现金分红', value: 'cash' },
    { label: '红利再投资', value: 'reinvest' },
  ]

  const isFundSubscriptionDraft = computed(() => addAssetCategory.value === 'fund')
  const isQuantityBasedDraft = computed(() => addAssetCategory.value !== 'fund')

  watch(options.fundingAccounts, (accounts) => {
    if (addAssetFundingAccount.value && !accounts.some((account) => String(account.id) === addAssetFundingAccount.value)) {
      addAssetFundingAccount.value = ''
    }
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

    addAssetSearchResults.value = []
    selectedAddAssetResultId.value = ''
    clearProductFields()
  })

  async function saveAsset() {
    if (isSaving.value) {
      return
    }

    const currentUser = getStoredCurrentUser()
    if (!currentUser) {
      formError.value = '请先登录后再新增投资'
      return
    }

    const accountId = options.getAccountId()
    const fundingAccountId = Number(addAssetFundingAccount.value)
    const normalizedFundingAccountId = Number.isFinite(fundingAccountId) && fundingAccountId > 0 ? fundingAccountId : undefined
    const currentPrice = Number(addAssetCurrentPrice.value)
    const holdingQuantity = Number(addAssetQuantity.value)
    const amountInput = Number(addAssetAmount.value)
    const isFundProduct = addAssetCategory.value === 'fund'
    let stockHoldingQuantity: number | undefined
    let costAmount = 0

    if (!addAssetName.value.trim() || !addAssetSymbol.value.trim()) {
      formError.value = '请先搜索并选择有效资产'
      return
    }
    if (!accountId) {
      formError.value = '当前投资账户不存在'
      return
    }
    if (isFundProduct) {
      if (!Number.isFinite(amountInput) || amountInput <= 0) {
        formError.value = '请输入买入金额'
        return
      }
      if (addAssetDividendType.value === 'cash' && !normalizedFundingAccountId) {
        formError.value = '现金分红必须选择到账资金账户'
        return
      }
      costAmount = amountInput
    } else {
      if (!Number.isFinite(holdingQuantity) || holdingQuantity <= 0) {
        formError.value = '请输入持仓份数'
        return
      }
      if (!Number.isFinite(amountInput) || amountInput <= 0) {
        formError.value = '请输入成本价'
        return
      }
      if (!Number.isFinite(currentPrice) || currentPrice <= 0) {
        formError.value = '未获取到当前价格，请重新搜索资产'
        return
      }
      const normalizedHoldingQuantity = Number(holdingQuantity.toFixed(6))
      if (!Number.isFinite(normalizedHoldingQuantity) || normalizedHoldingQuantity <= 0) {
        formError.value = '持仓份数或成本不正确'
        return
      }
      stockHoldingQuantity = normalizedHoldingQuantity
      costAmount = Number((normalizedHoldingQuantity * amountInput).toFixed(2))
    }

    isSaving.value = true
    formError.value = ''

    try {
      const savedPosition: InvestmentPosition = await createInvestmentPosition({
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
        holdingQuantity: stockHoldingQuantity,
        availableQuantity: stockHoldingQuantity,
        frozenQuantity: 0,
        costAmount: Number(costAmount.toFixed(2)),
        currentPrice: isFundProduct ? undefined : currentPrice,
        tradeAt: toApiDateTime(new Date()),
        subscriptionTimeSlot: isFundProduct ? addAssetSubscriptionTimeSlot.value : undefined,
        dividendType: isFundProduct ? addAssetDividendType.value : undefined,
        includeInNetWorth: true,
        status: 'active',
        remark: null,
      })
      closeAddModal()
      options.showFeedback(
        isFundProduct ? getFundPositionSubmitMessage(savedPosition) : '新增成功',
        'success',
      )
      await options.onSaved()
    } catch (error) {
      const message = getErrorMessage(error, '新增投资失败')
      formError.value = message
      options.showFeedback(message, 'error')
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
      let products = await getInvestmentProducts({
        keyword: searchKeyword,
        productType: addAssetCategory.value,
      })
      if (products.length === 0) {
        products = await getInvestmentProducts({
          keyword: searchKeyword,
        })
      }
      const normalizedKeyword = searchKeyword.toUpperCase()
      const matchedProducts = products.slice().sort((left, right) => {
        const leftScore = getProductMatchScore(left, searchKeyword, normalizedKeyword)
        const rightScore = getProductMatchScore(right, searchKeyword, normalizedKeyword)
        return rightScore - leftScore
      })

      if (matchedProducts.length === 0) {
        addAssetSearchResults.value = []
        selectedAddAssetResultId.value = ''
        clearProductFields()
        productLookupMessage.value = '未找到该资产，请确认代码或名称'
        return
      }

      addAssetSearchResults.value = matchedProducts
      selectedAddAssetResultId.value = ''
      clearProductFields()
      productLookupMessage.value = `找到 ${matchedProducts.length} 个资产，请选择`
    } catch (error) {
      addAssetSearchResults.value = []
      selectedAddAssetResultId.value = ''
      productLookupMessage.value = getErrorMessage(error, '代码识别失败')
    } finally {
      isLookingUpProduct.value = false
    }
  }

  function getProductMatchScore(product: InvestmentProduct, keyword: string, normalizedKeyword: string) {
    const symbol = product.symbol.toUpperCase()
    const name = product.name

    if (symbol === normalizedKeyword) {
      return 400
    }
    if (symbol.startsWith(normalizedKeyword)) {
      return 300
    }
    if (name === keyword) {
      return 200
    }
    if (name.includes(keyword)) {
      return 100
    }
    return 0
  }

  function getAddAssetResultKey(product: InvestmentProduct) {
    const fallbackId = Number(product.id)
    if (Number.isFinite(fallbackId) && fallbackId > 0) {
      return `id:${fallbackId}`
    }
    return [
      product.productType || 'unknown',
      product.market || '',
      product.exchangeCode || '',
      product.symbol || '',
      product.name || '',
    ].join('|')
  }

  function selectAddAssetProduct(productKey: string) {
    selectedAddAssetResultId.value = productKey
    const targetProduct = addAssetSearchResults.value.find((item) => getAddAssetResultKey(item) === productKey)
    if (!targetProduct) {
      clearProductFields()
      productLookupMessage.value = '未找到该资产，请重新选择'
      return
    }
    fillProductFields(targetProduct)
  }

  function getAddAssetResultLabel(product: InvestmentProduct) {
    return `${product.symbol} ${product.name}`
  }

  function getAddAssetResultMeta(product: InvestmentProduct) {
    const typeLabel = product.productType === 'stock'
      ? '股票'
      : product.productType === 'fund'
        ? '基金'
        : '投资资产'
    const marketLabel = product.market?.trim() ? ` · ${product.market}` : ''
    return `${typeLabel}${marketLabel}`
  }

  function isAddAssetProductSelected(product: InvestmentProduct) {
    return selectedAddAssetResultId.value === getAddAssetResultKey(product)
  }

  function fillProductFields(product: InvestmentProduct) {
    isFillingProduct = true
    addAssetName.value = product.name
    addAssetSymbol.value = product.symbol
    addAssetKeyword.value = `${product.symbol} ${product.name}`
    isFillingProduct = false
    selectedAddAssetResultId.value = getAddAssetResultKey(product)
    addAssetMarket.value = product.market || ''
    addAssetCategory.value = product.productType
    addAssetCurrentPrice.value = product.latestPrice && product.latestPrice > 0 ? String(product.latestPrice) : ''
    addAssetSearchResults.value = []
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
    addAssetCategory.value = options.getDefaultCategory()
    addAssetFundingAccount.value = ''
    addAssetDividendType.value = 'cash'
    addAssetQuantity.value = ''
    addAssetAmount.value = ''
    addAssetCurrentPrice.value = ''
    addAssetSubscriptionTimeSlot.value = 'before_1500'
    addAssetSearchResults.value = []
    selectedAddAssetResultId.value = ''
    productLookupMessage.value = ''
    formError.value = ''
  }

  return {
    showAddModal,
    isSaving,
    formError,
    addAssetKeyword,
    addAssetName,
    addAssetSymbol,
    addAssetMarket,
    addAssetCategory,
    addAssetFundingAccount,
    addAssetDividendType,
    addAssetQuantity,
    addAssetAmount,
    addAssetCurrentPrice,
    addAssetSubscriptionTimeSlot,
    isLookingUpProduct,
    addAssetSearchResults,
    selectedAddAssetResultId,
    productLookupMessage,
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
  }
}
