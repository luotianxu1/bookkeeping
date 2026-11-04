// 基金收益页展示层派生状态：汇总卡片、近7日走势、收益日历、明细列表的计算逻辑。
import { computed, type Ref } from 'vue'
import type {
  FundProfitCalendarCell,
  FundProfitPage,
  FundProfitPageSummaryMetric,
  FundProfitTrendPoint,
  FundProfitView,
} from '@/api/modules/finance'
import {
  formatDateTime,
  formatMonthLabel,
  formatRate,
  formatSignedCurrency,
  getTone,
  sortSummaryShortcuts,
} from './fundProfitFormat'
import type { DetailFilter } from './useFundProfitData'

export type UseFundProfitViewOptions = {
  pageData: Ref<FundProfitPage | null>
  selectedView: Ref<FundProfitView>
  selectedAccountId: Ref<string>
  selectedSummaryShortcut: Ref<string>
  selectedDetailFilter: Ref<DetailFilter>
  dayAnchor: Ref<string>
  monthAnchor: Ref<number>
  yearAnchor: Ref<number>
}

export function useFundProfitView(options: UseFundProfitViewOptions) {
  const { pageData, selectedView, selectedAccountId, selectedSummaryShortcut, selectedDetailFilter, dayAnchor, monthAnchor, yearAnchor } = options

  const isDayView = computed(() => selectedView.value === 'day')

  const selectedAccountLabel = computed(() =>
    pageData.value?.accounts.find((account) => String(account.accountId) === selectedAccountId.value)?.accountName ?? '全部账户',
  )

  const summaryShortcuts = computed(() =>
    sortSummaryShortcuts(pageData.value?.summary.shortcuts ?? []),
  )

  const selectedSummaryMetric = computed<FundProfitPageSummaryMetric | null>(() => {
    const items = summaryShortcuts.value
    return items.find((item) => item.key === selectedSummaryShortcut.value) ?? items[0] ?? null
  })

  const summaryShortcutOptions = computed(() =>
    summaryShortcuts.value.map((item) => ({
      label: item.label,
      value: item.key,
    })),
  )

  const summaryTitle = computed(() => {
    if (selectedAccountId.value === 'all') {
      return '基金收益总览'
    }
    return `${selectedAccountLabel.value}收益总览`
  })

  const summaryPillText = computed(() => `${pageData.value?.summary.fundCount ?? 0}只持仓基金`)
  const summaryMetricProfitText = computed(() =>
    selectedSummaryMetric.value?.profit === null || selectedSummaryMetric.value?.profit === undefined
      ? '--'
      : formatSignedCurrency(selectedSummaryMetric.value.profit),
  )
  const summaryMetricProfitTone = computed(() =>
    selectedSummaryMetric.value?.profit === null || selectedSummaryMetric.value?.profit === undefined
      ? 'neutral'
      : getTone(Number(selectedSummaryMetric.value.profit)),
  )
  const summaryMetricRateText = computed(() =>
    selectedSummaryMetric.value?.profitRate === null || selectedSummaryMetric.value?.profitRate === undefined
      ? '--'
      : formatRate(selectedSummaryMetric.value.profitRate),
  )
  const summaryMetricRateTone = computed(() =>
    selectedSummaryMetric.value?.profitRate === null || selectedSummaryMetric.value?.profitRate === undefined
      ? 'neutral'
      : getTone(Number(selectedSummaryMetric.value.profitRate)),
  )

  const summaryHint = computed(() => {
    const syncText = formatDateTime(pageData.value?.summary.lastSyncedAt ?? pageData.value?.lastSyncedAt)
    return syncText ? `含分红再投资，数据更新时间 ${syncText}` : '含分红再投资'
  })

  const trendPoints = computed(() => pageData.value?.trendPoints ?? [])
  const trendMaxAbsProfit = computed(() => {
    const max = trendPoints.value.reduce((current, item) => {
      if (item.profit === null || item.profit === undefined) {
        return current
      }
      return Math.max(current, Math.abs(Number(item.profit)))
    }, 0)
    return max > 0 ? max : 1
  })

  const trendStats = computed(() => {
    const items = trendPoints.value.filter((item) => item.profit !== null && item.profit !== undefined)
    const positiveDays = items.filter((item) => Number(item.profit) > 0).length
    const negativeDays = items.filter((item) => Number(item.profit) < 0).length
    const sorted = [...items].sort((left, right) => Number(right.profit) - Number(left.profit))
    return {
      positiveDays,
      negativeDays,
      peak: sorted[0] ?? null,
      trough: sorted[sorted.length - 1] ?? null,
    }
  })

  const trendPositiveRate = computed(() => {
    const availableCount = trendPoints.value.filter((item) => item.profit !== null && item.profit !== undefined).length
    if (!availableCount) {
      return '--'
    }
    return `${Math.round((trendStats.value.positiveDays / availableCount) * 100)}%`
  })

  const trendNegativeRate = computed(() => {
    const availableCount = trendPoints.value.filter((item) => item.profit !== null && item.profit !== undefined).length
    if (!availableCount) {
      return '--'
    }
    return `${Math.round((trendStats.value.negativeDays / availableCount) * 100)}%`
  })

  const trendFootnote = computed(() => {
    if (!trendStats.value.peak || !trendStats.value.trough) {
      return '近 7 日收益变化将按上方所选账户持续更新。'
    }
    return `${trendStats.value.peak.label} 录得最高 ${formatSignedCurrency(Number(trendStats.value.peak.profit ?? 0))}，${trendStats.value.trough.label} 最低 ${formatSignedCurrency(Number(trendStats.value.trough.profit ?? 0))}。`
  })

  const calendarTitle = computed(() => {
    if (selectedView.value === 'month') {
      return '收益月历'
    }
    if (selectedView.value === 'year') {
      return '收益年历'
    }
    return '收益日历'
  })

  const selection = computed(() => pageData.value?.selection ?? null)
  const selectionLabel = computed(() => selection.value?.label ?? '--')
  const calendarHeadLabel = computed(() => {
    if (selectedView.value === 'month') {
      return '月视图'
    }
    if (selectedView.value === 'year') {
      return '年视图'
    }
    return '日视图'
  })
  const detailTitle = computed(() => `${selectionLabel.value}收益明细`)

  const dayCalendarCells = computed<Array<FundProfitCalendarCell | null>>(() => {
    if (!isDayView.value) {
      return []
    }

    const items = pageData.value?.calendarItems ?? []
    if (!items.length) {
      return []
    }

    const firstDate = new Date(`${items[0].startDate}T00:00:00`)
    const offset = (firstDate.getDay() + 6) % 7
    const cells = [...Array.from({ length: offset }, () => null), ...items]
    const remainder = cells.length % 7
    if (remainder === 0) {
      return cells
    }
    return [...cells, ...Array.from({ length: 7 - remainder }, () => null)]
  })

  const periodCards = computed(() => (isDayView.value ? [] : pageData.value?.calendarItems ?? []))
  const yearPeriodCards = computed<Array<FundProfitCalendarCell | null>>(() => {
    if (selectedView.value !== 'year') {
      return []
    }
    const items = [...periodCards.value]
    const remainder = items.length % 3
    if (remainder === 0) {
      return items
    }
    return [...items, ...Array.from({ length: 3 - remainder }, () => null)]
  })

  const visibleDetails = computed(() => {
    const details = pageData.value?.details ?? []
    const sortedDetails = [...details].sort((left, right) => Number(right.periodProfit ?? 0) - Number(left.periodProfit ?? 0))

    if (selectedDetailFilter.value === 'profit') {
      return sortedDetails.filter((item) => Number(item.periodProfit ?? 0) > 0)
    }
    if (selectedDetailFilter.value === 'loss') {
      return sortedDetails.filter((item) => Number(item.periodProfit ?? 0) < 0)
    }
    return sortedDetails
  })

  const detailFilterOptions = computed(() => {
    const details = pageData.value?.details ?? []
    const profitCount = details.filter((item) => Number(item.periodProfit ?? 0) > 0).length
    const lossCount = details.filter((item) => Number(item.periodProfit ?? 0) < 0).length
    return [
      { label: `全部 ${details.length}`, value: 'all' as const },
      { label: `盈利 ${profitCount}`, value: 'profit' as const },
      { label: `亏损 ${lossCount}`, value: 'loss' as const },
    ]
  })

  const calendarAnchorLabel = computed(() => {
    if (selectedView.value === 'month') {
      return `${monthAnchor.value}年`
    }
    if (selectedView.value === 'year') {
      return '近5年'
    }
    return formatMonthLabel(dayAnchor.value)
  })

  const calendarStatePillText = computed(() => {
    const items = pageData.value?.calendarItems ?? []
    if (selectedView.value === 'month') {
      const positiveMonths = items.filter((item) => Number(item.profit ?? 0) > 0).length
      return `正收益月 ${positiveMonths} / ${items.length || 0}`
    }
    if (selectedView.value === 'year') {
      return `${yearAnchor.value} 为当前年`
    }
    const positiveDays = items.filter((item) => Number(item.profit ?? 0) > 0).length
    return `正收益 ${positiveDays} 天`
  })

  const calendarFootnote = computed(() => {
    if (selectedView.value === 'month') {
      return '点击月份可切换到日视图查看当月每日收益表现。'
    }
    if (selectedView.value === 'year') {
      return '点击年份卡片可切换到月视图查看当年各月收益表现。'
    }
    return '点击日期可下钻查看当日基金收益构成，红色为上涨、绿色为回撤。'
  })

  const showCalendarLegend = computed(() => selectedView.value !== 'year')

  const calendarLegendItems = computed(() => {
    if (selectedView.value === 'month') {
      return [
        { label: '盈利月', tone: 'positive' },
        { label: '回撤月', tone: 'negative' },
        { label: '当前月', tone: 'selected' },
      ]
    }
    return [
      { label: '上涨', tone: 'positive' },
      { label: '下跌', tone: 'negative' },
      { label: '选中', tone: 'selected' },
    ]
  })

  function getTrendBarStyle(point: FundProfitTrendPoint) {
    if (point.profit === null || point.profit === undefined) {
      return { height: '18px' }
    }
    const value = Math.abs(Number(point.profit ?? 0))
    const height = value === 0 ? 18 : Math.max(18, (value / trendMaxAbsProfit.value) * 132)
    return { height: `${height}px` }
  }

  function getTrendTone(point: FundProfitTrendPoint) {
    if (point.profit === null || point.profit === undefined) {
      return 'muted'
    }
    return getTone(Number(point.profit))
  }

  function getPeriodCardTone(item: FundProfitCalendarCell) {
    const value = Number(item.profit ?? 0)
    if (value > 0) {
      return 'positive'
    }
    if (value < 0) {
      return 'negative'
    }
    return 'neutral'
  }

  return {
    isDayView,
    summaryShortcutOptions,
    summaryTitle,
    summaryPillText,
    summaryMetricProfitText,
    summaryMetricProfitTone,
    summaryMetricRateText,
    summaryMetricRateTone,
    summaryHint,
    trendPoints,
    trendStats,
    trendPositiveRate,
    trendNegativeRate,
    trendFootnote,
    calendarTitle,
    selectionLabel,
    calendarHeadLabel,
    detailTitle,
    dayCalendarCells,
    periodCards,
    yearPeriodCards,
    visibleDetails,
    detailFilterOptions,
    calendarAnchorLabel,
    calendarStatePillText,
    calendarFootnote,
    showCalendarLegend,
    calendarLegendItems,
    getTrendBarStyle,
    getTrendTone,
    getPeriodCardTone,
  }
}
