// 首次加载和翻页共享取消、错误与序号保护，避免刷新后迟到的旧页覆盖新数据。
import { computed, ref, watch, type ComputedRef } from 'vue'
import {
  getInvestmentTransactionPage,
  type InvestmentTransaction,
  type InvestmentTransactionPage,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { useAsyncSection } from '@/business/composables/useAsyncSection'
import { TRANSACTION_PAGE_SIZE } from './utils'

export function useTransactionPagination(options: {
  positionId: ComputedRef<number | null>
}) {
  const { positionId } = options
  const currentTransactionPage = ref(1)
  const transactionPageItems = ref<InvestmentTransaction[]>([])
  const transactionTotal = ref(0)
  const transactionPageSize = ref(TRANSACTION_PAGE_SIZE)
  let requestedPage = 1

  const totalTransactionPages = computed(() =>
    Math.max(1, Math.ceil(transactionTotal.value / transactionPageSize.value)),
  )
  const transactionPaginationSummary = computed(
    () =>
      `第 ${currentTransactionPage.value} / ${totalTransactionPages.value} 页，共 ${transactionTotal.value} 条`,
  )
  const canGoPrevTransactionPage = computed(
    () => currentTransactionPage.value > 1,
  )
  const canGoNextTransactionPage = computed(
    () => currentTransactionPage.value < totalTransactionPages.value,
  )

  function applyTransactionPage(pageData: InvestmentTransactionPage) {
    transactionPageItems.value = pageData.items ?? []
    transactionTotal.value = Number(pageData.total ?? 0)
    transactionPageSize.value = Number(
      pageData.pageSize || TRANSACTION_PAGE_SIZE,
    )
    currentTransactionPage.value = Math.max(1, Number(pageData.page || 1))
  }

  const section = useAsyncSection(
    (signal) => {
      const currentUser = getStoredCurrentUser()
      if (!currentUser || !positionId.value)
        throw new Error('请先登录并选择投资资产')
      return getInvestmentTransactionPage(
        {
          userId: currentUser.id,
          positionId: positionId.value,
          page: requestedPage,
          pageSize: TRANSACTION_PAGE_SIZE,
        },
        { signal },
      )
    },
    applyTransactionPage,
    '交易记录加载失败',
  )

  function resetTransactionPage() {
    section.reset()
    requestedPage = 1
    applyTransactionPage({
      items: [],
      total: 0,
      page: 1,
      pageSize: TRANSACTION_PAGE_SIZE,
      totalPages: 1,
    })
  }
  watch(positionId, resetTransactionPage, { flush: 'sync' })

  async function loadTransactionPage(page = requestedPage) {
    if (section.loading.value) return
    requestedPage = page
    await section.run()
  }

  function goToPrevTransactionPage() {
    if (canGoPrevTransactionPage.value)
      void loadTransactionPage(currentTransactionPage.value - 1)
  }

  function goToNextTransactionPage() {
    if (canGoNextTransactionPage.value)
      void loadTransactionPage(currentTransactionPage.value + 1)
  }

  return {
    currentTransactionPage,
    transactionPageItems,
    transactionTotal,
    isLoadingTransactionPage: section.loading,
    transactionError: section.error,
    totalTransactionPages,
    transactionPaginationSummary,
    canGoPrevTransactionPage,
    canGoNextTransactionPage,
    resetTransactionPage,
    loadTransactionPage,
    goToPrevTransactionPage,
    goToNextTransactionPage,
  }
}
