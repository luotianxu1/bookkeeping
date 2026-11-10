// 黄金账户持仓页：核心数据加载、状态与展示计算逻辑。
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getAccounts,
  getGoldAccountHoldings,
  getGoldLiquidations,
  getGoldAccountSummary,
  type Account,
  type GoldAccountHolding,
  type GoldLiquidation,
  type GoldAccountSummary,
} from '@/business/api/modules/finance'
import {
  refreshGoldPriceCache,
  useGoldPriceCache,
} from '@/business/utils/gold-price-cache'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'
import type { FeedbackType } from '@/business/composables/useFeedback'
import { formatAmount } from '@/business/utils/format'
import {
  buildSummary,
  decorateHolding,
  formatGoldUpdatedAt,
  parseAccountId,
} from './goldPositionFormat'

export type UseGoldPositionDataOptions = {
  showFeedback: (message: string, type: FeedbackType) => void
}

export function useGoldPositionData(options: UseGoldPositionDataOptions) {
  const { showFeedback } = options

  const route = useRoute()
  const router = useRouter()

  const isLoading = ref(true)
  const pageError = ref('')
  const isRefreshingGold = ref(false)
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
  const formAccountId = ref('')
  const formFundingAccountId = ref('')

  let requestVersion = 0

  const selectedAccountId = computed(() =>
    parseAccountId(route.query.accountId),
  )
  const isScopedToAccount = computed(() => selectedAccountId.value !== null)
  const scopedAccount = computed(
    () =>
      goldAccounts.value.find(
        (account) => account.id === selectedAccountId.value,
      ) ?? null,
  )
  const realtimeGoldPrice = computed(() => Number(goldPrice.value?.price ?? 0))
  const realtimeGoldUpdatedAt = computed(() =>
    formatGoldUpdatedAt(goldPrice.value?.updatedAt),
  )
  const displayHoldings = computed(() =>
    holdings.value.map((item) =>
      decorateHolding(item, realtimeGoldPrice.value),
    ),
  )
  const filteredHoldings = computed(() => {
    if (!selectedAccountId.value) {
      return displayHoldings.value
    }

    return displayHoldings.value.filter(
      (item) => item.accountId === selectedAccountId.value,
    )
  })
  const scopedCumulativeProfit = computed(() => {
    if (!selectedAccountId.value) {
      return Number(
        liquidation.value.cumulativeProfit ??
          summary.value.cumulativeProfit ??
          0,
      )
    }

    return Number(
      (liquidation.value.records ?? [])
        .filter((item) => item.accountId === selectedAccountId.value)
        .reduce((total, item) => total + Number(item.profit ?? 0), 0)
        .toFixed(2),
    )
  })
  const visibleSummary = computed(() =>
    realtimeGoldPrice.value > 0
      ? buildSummary(filteredHoldings.value, scopedCumulativeProfit.value)
      : isScopedToAccount.value
        ? buildSummary(filteredHoldings.value, scopedCumulativeProfit.value)
        : {
            ...summary.value,
            cumulativeProfit: scopedCumulativeProfit.value,
          },
  )
  const hasHoldings = computed(() => filteredHoldings.value.length > 0)
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

  function syncFormSelections() {
    if (
      !goldAccounts.value.some(
        (account) => String(account.id) === formAccountId.value,
      )
    ) {
      const preferredAccountId =
        selectedAccountId.value &&
        goldAccounts.value.some(
          (account) => account.id === selectedAccountId.value,
        )
          ? selectedAccountId.value
          : goldAccounts.value[0]?.id
      formAccountId.value = preferredAccountId ? String(preferredAccountId) : ''
    }

    if (
      !fundingAccounts.value.some(
        (account) => String(account.id) === formFundingAccountId.value,
      )
    ) {
      formFundingAccountId.value = ''
    }
  }

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
      const [summaryData, holdingList, accountList, liquidationData] =
        await Promise.all([
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
      goldAccounts.value = accountList.filter(
        (account) => account.accountTypeCode === 'gold',
      )
      fundingAccounts.value = accountList.filter(
        (account) => account.accountTypeCode === 'cash',
      )
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

  return {
    isLoading,
    pageError,
    isRefreshingGold,
    summary,
    liquidation,
    holdings,
    goldAccounts,
    fundingAccounts,
    goldPrice,
    formAccountId,
    formFundingAccountId,
    selectedAccountId,
    isScopedToAccount,
    scopedAccount,
    realtimeGoldPrice,
    realtimeGoldUpdatedAt,
    displayHoldings,
    filteredHoldings,
    scopedCumulativeProfit,
    visibleSummary,
    hasHoldings,
    fundingAccountOptions,
    sellFundingAccountOptions,
    syncFormSelections,
    loadGoldPosition,
    refreshGoldData,
    openLiquidationList,
  }
}
