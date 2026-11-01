// 投资详情页的交易记录分页逻辑（状态 + 翻页加载）。
import { computed, ref, watch, type ComputedRef } from 'vue'
import {
  getInvestmentTransactionPage,
  type InvestmentTransaction,
  type InvestmentTransactionPage,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getErrorMessage } from '@/api/request'
import { TRANSACTION_PAGE_SIZE } from './utils'

export function useTransactionPagination(options: {
  positionId: ComputedRef<number | null>
  showFeedback: (message: string, type: 'success' | 'error') => void
}) {
  const { positionId, showFeedback } = options

  const currentTransactionPage = ref(1)
  const transactionPageItems = ref<InvestmentTransaction[]>([])
  const transactionTotal = ref(0)
  const transactionPageSize = ref(TRANSACTION_PAGE_SIZE)
  const isLoadingTransactionPage = ref(false)

  const totalTransactionPages = computed(() => Math.max(1, Math.ceil(transactionTotal.value / transactionPageSize.value)))
  const transactionPaginationSummary = computed(() =>
    `第 ${currentTransactionPage.value} / ${totalTransactionPages.value} 页，共 ${transactionTotal.value} 条`,
  )
  const canGoPrevTransactionPage = computed(() => currentTransactionPage.value > 1)
  const canGoNextTransactionPage = computed(() => currentTransactionPage.value < totalTransactionPages.value)

  watch(totalTransactionPages, (totalPages) => {
    if (currentTransactionPage.value > totalPages) {
      currentTransactionPage.value = totalPages
    }
  })

  function applyTransactionPage(pageData: InvestmentTransactionPage) {
    transactionPageItems.value = pageData.items ?? []
    transactionTotal.value = Number(pageData.total ?? 0)
    transactionPageSize.value = Number(pageData.pageSize || TRANSACTION_PAGE_SIZE)
    currentTransactionPage.value = Math.max(1, Number(pageData.page || 1))
  }

  async function loadTransactionPage(page: number) {
    if (isLoadingTransactionPage.value || !positionId.value) {
      return
    }
    const currentUser = getStoredCurrentUser()
    if (!currentUser) {
      applyTransactionPage({ items: [], total: 0, page: 1, pageSize: TRANSACTION_PAGE_SIZE, totalPages: 1 })
      return
    }

    isLoadingTransactionPage.value = true
    try {
      const pageData = await getInvestmentTransactionPage({
        userId: currentUser.id,
        positionId: positionId.value,
        page,
        pageSize: TRANSACTION_PAGE_SIZE,
      })
      applyTransactionPage(pageData)
    } catch (error) {
      showFeedback(getErrorMessage(error, '交易记录加载失败'), 'error')
    } finally {
      isLoadingTransactionPage.value = false
    }
  }

  function goToPrevTransactionPage() {
    if (!canGoPrevTransactionPage.value || isLoadingTransactionPage.value) {
      return
    }
    void loadTransactionPage(currentTransactionPage.value - 1)
  }

  function goToNextTransactionPage() {
    if (!canGoNextTransactionPage.value || isLoadingTransactionPage.value) {
      return
    }
    void loadTransactionPage(currentTransactionPage.value + 1)
  }

  return {
    currentTransactionPage,
    transactionPageItems,
    transactionTotal,
    transactionPageSize,
    isLoadingTransactionPage,
    totalTransactionPages,
    transactionPaginationSummary,
    canGoPrevTransactionPage,
    canGoNextTransactionPage,
    applyTransactionPage,
    loadTransactionPage,
    goToPrevTransactionPage,
    goToNextTransactionPage,
  }
}
