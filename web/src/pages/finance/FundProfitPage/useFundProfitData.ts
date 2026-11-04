// 基金收益页数据加载与状态管理：请求防竞态、视图/锚点状态、日历与明细合并逻辑。
import { computed, onMounted, ref } from 'vue'
import {
  getFundProfitPage,
  runInvestmentQuoteSyncTask,
  type FundProfitCalendarCell,
  type FundProfitPage,
  type FundProfitView,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getErrorMessage, isCancelRequest } from '@/api/request'
import { useRequestAbort } from '@/composables/useRequestAbort'
import { useVisibilityPolling } from '@/composables/useVisibilityPolling'
import { useFeedback } from '@/composables/useFeedback'
import {
  formatMonthValue,
  isAfterQuoteRefreshTime,
  normalizeView,
  shiftMonthValue,
  sortSummaryShortcuts,
  wait,
} from './fundProfitFormat'

export type DetailFilter = 'all' | 'profit' | 'loss'

export function useFundProfitData() {
  // 数据加载防竞态：新加载先 abort 旧请求，旧响应按代际号静默丢弃
  const { signalFor } = useRequestAbort()
  let loadSeq = 0

  const selectedView = ref<FundProfitView>('day')
  const selectedAccountId = ref('all')
  const selectedSummaryShortcut = ref('')
  const selectedDetailFilter = ref<DetailFilter>('all')
  const selectedKey = ref('')
  const dayAnchor = ref(formatMonthValue(new Date()))
  const monthAnchor = ref(new Date().getFullYear())
  const yearAnchor = ref(new Date().getFullYear())
  const pageData = ref<FundProfitPage | null>(null)
  const isLoading = ref(true)
  const isDetailLoading = ref(false)
  const isCalendarLoading = ref(false)
  const isRefreshingPositions = ref(false)
  const { showFeedbackModal, feedbackMessage, feedbackType, showFeedback } = useFeedback()
  const pageError = ref('')
  const currentTime = ref(new Date())

  const shouldShowPositionRefreshButton = computed(() => isAfterQuoteRefreshTime(currentTime.value))

  const viewModel = computed({
    get: () => selectedView.value,
    set: (value: string) => {
      if (value === selectedView.value) {
        return
      }
      selectedView.value = value as FundProfitView
      selectedKey.value = ''
      void loadPage({ calendarOnly: true })
    },
  })

  onMounted(() => {
    void loadPage()
    currentTime.value = new Date()
  })

  // 时钟轮询感知页面可见性：后台标签页跳过，恢复可见立即校准
  useVisibilityPolling(() => {
    currentTime.value = new Date()
  }, 30 * 1000)

  async function loadPage(options?: { detailOnly?: boolean; calendarOnly?: boolean }) {
    const currentUser = getStoredCurrentUser()
    if (!currentUser) {
      pageError.value = '请先登录后查看基金收益'
      pageData.value = null
      isLoading.value = false
      return
    }

    const detailOnly = Boolean(options?.detailOnly && pageData.value)
    const calendarOnly = Boolean(options?.calendarOnly && pageData.value && !detailOnly)
    const seq = ++loadSeq
    const signal = signalFor()

    if (detailOnly) {
      isDetailLoading.value = true
    } else if (calendarOnly) {
      isCalendarLoading.value = true
    } else {
      isLoading.value = true
    }
    pageError.value = ''

    try {
      const data = await getFundProfitPage({
        userId: currentUser.id,
        accountId: selectedAccountId.value === 'all' ? undefined : Number(selectedAccountId.value),
        view: selectedView.value,
        anchor: resolveAnchorValue(),
        selected: selectedKey.value || undefined,
      }, { signal })

      if (seq !== loadSeq) {
        return
      }

      if (detailOnly && pageData.value) {
        mergeDetailSelection(data)
      } else if (calendarOnly && pageData.value) {
        mergeCalendarSelection(data)
      } else {
        pageData.value = data
        syncPageState(data)
      }
    } catch (error) {
      if (isCancelRequest(error) || seq !== loadSeq) {
        return
      }
      pageData.value = null
      pageError.value = getErrorMessage(error, '基金收益加载失败')
    } finally {
      if (seq !== loadSeq) {
        return
      }
      if (detailOnly) {
        isDetailLoading.value = false
      } else if (calendarOnly) {
        isCalendarLoading.value = false
      } else {
        isLoading.value = false
      }
    }
  }

  async function refreshPositionData() {
    if (!shouldShowPositionRefreshButton.value || isRefreshingPositions.value) {
      return
    }

    const currentUser = getStoredCurrentUser()
    if (!currentUser) {
      showFeedback('请先登录后刷新持仓数据', 'error')
      return
    }

    isRefreshingPositions.value = true
    try {
      await runInvestmentQuoteSyncTask({
        userId: currentUser.id,
        accountId: selectedAccountId.value === 'all' ? undefined : Number(selectedAccountId.value),
      })
      showFeedback('已提交刷新任务，正在更新基金收益', 'success')
      await wait(2000)
      await loadPage()
    } catch (error) {
      const message = getErrorMessage(error, '基金持仓刷新失败')
      showFeedback(message, 'error')
    } finally {
      isRefreshingPositions.value = false
    }
  }

  function syncPageState(data: FundProfitPage) {
    selectedView.value = normalizeView(data.view)
    selectedKey.value = data.selectedKey

    if (selectedView.value === 'day') {
      dayAnchor.value = data.anchor || dayAnchor.value
    } else if (selectedView.value === 'month') {
      monthAnchor.value = Number.parseInt(data.anchor, 10) || monthAnchor.value
    } else {
      yearAnchor.value = Number.parseInt(data.anchor, 10) || yearAnchor.value
    }

    const shortcutKeys = new Set(
      (data.summary.shortcuts ?? []).map((item) => item.key),
    )
    if (!shortcutKeys.has(selectedSummaryShortcut.value)) {
      const nextShortcut = sortSummaryShortcuts(data.summary.shortcuts ?? [])[0]
      selectedSummaryShortcut.value = nextShortcut?.key || data.summary.activeShortcut || 'cumulative'
    }

    const validAccountIds = new Set(['all', ...data.accounts.map((account) => String(account.accountId))])
    if (!validAccountIds.has(selectedAccountId.value)) {
      selectedAccountId.value = 'all'
    }
  }

  function mergeDetailSelection(data: FundProfitPage) {
    if (!pageData.value) {
      pageData.value = data
      syncPageState(data)
      return
    }

    selectedKey.value = data.selectedKey
    pageData.value = {
      ...pageData.value,
      selectedKey: data.selectedKey,
      selection: data.selection,
      details: data.details,
      calendarItems: pageData.value.calendarItems.map((item) => ({
        ...item,
        selected: item.key === data.selectedKey,
      })),
    }
  }

  function mergeCalendarSelection(data: FundProfitPage) {
    if (!pageData.value) {
      pageData.value = data
      syncPageState(data)
      return
    }

    selectedView.value = normalizeView(data.view)
    selectedKey.value = data.selectedKey

    if (selectedView.value === 'day') {
      dayAnchor.value = data.anchor || dayAnchor.value
    } else if (selectedView.value === 'month') {
      monthAnchor.value = Number.parseInt(data.anchor, 10) || monthAnchor.value
    } else {
      yearAnchor.value = Number.parseInt(data.anchor, 10) || yearAnchor.value
    }

    pageData.value = {
      ...pageData.value,
      view: data.view,
      anchor: data.anchor,
      selectedKey: data.selectedKey,
      calendarItems: data.calendarItems,
      selection: data.selection,
      details: data.details,
    }
  }

  function resolveAnchorValue() {
    if (selectedView.value === 'month') {
      return String(monthAnchor.value)
    }
    if (selectedView.value === 'year') {
      return String(yearAnchor.value)
    }
    return dayAnchor.value
  }

  function handleCalendarSelect(item: FundProfitCalendarCell) {
    if (item.profit === null || item.profit === undefined || item.key === selectedKey.value) {
      return
    }
    selectedKey.value = item.key
    void loadPage({ detailOnly: true })
  }

  function handlePeriodSelect(item: FundProfitCalendarCell) {
    if (item.profit === null || item.profit === undefined) {
      return
    }

    if (selectedView.value === 'month') {
      dayAnchor.value = item.startDate.slice(0, 7)
      selectedView.value = 'day'
      selectedKey.value = ''
      void loadPage({ calendarOnly: true })
      return
    }

    if (selectedView.value === 'year') {
      monthAnchor.value = Number.parseInt(item.startDate.slice(0, 4), 10) || monthAnchor.value
      selectedView.value = 'month'
      selectedKey.value = ''
      void loadPage({ calendarOnly: true })
      return
    }

    handleCalendarSelect(item)
  }

  function shiftCalendarAnchor(offset: number) {
    if (selectedView.value === 'day') {
      dayAnchor.value = shiftMonthValue(dayAnchor.value, offset)
    } else if (selectedView.value === 'month') {
      monthAnchor.value += offset
    } else {
      yearAnchor.value += offset
    }
    selectedKey.value = ''
    void loadPage({ calendarOnly: true })
  }

  return {
    selectedView,
    selectedAccountId,
    selectedSummaryShortcut,
    selectedDetailFilter,
    selectedKey,
    dayAnchor,
    monthAnchor,
    yearAnchor,
    pageData,
    isLoading,
    isDetailLoading,
    isCalendarLoading,
    isRefreshingPositions,
    pageError,
    shouldShowPositionRefreshButton,
    viewModel,
    refreshPositionData,
    handlePeriodSelect,
    shiftCalendarAnchor,
    showFeedbackModal,
    feedbackMessage,
    feedbackType,
  }
}
