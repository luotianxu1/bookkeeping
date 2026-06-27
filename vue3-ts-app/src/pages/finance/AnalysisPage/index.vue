<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import MonthPicker from '@/components/common/MonthPicker/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import YearPicker from '@/components/common/YearPicker/index.vue'
import { useFinanceFamilyView } from '@/composables/useFinanceFamilyView'
import { useTheme } from '@/utils/theme'
import {
  backfillAssetSnapshot,
  getCategories,
  getTransactionAnalysis,
  type Category,
  type Transaction,
  type TransactionAnalysis,
  type TransactionAnalysisCategoryBreakdownItem,
  type TransactionAnalysisPeriod,
  type TransactionAnalysisPeriodSummary,
  type TransactionAnalysisTrendPoint,
} from '@/api/modules/finance'

type PeriodLabel = '月' | '年'
type SummaryTab = '收入' | '支出' | '结余'
type TrendTone = 'income' | 'expense' | 'neutral'
type ExpenseBreakdownLevel = '一级分类' | '二级分类'

type CalendarCell = {
  key: string
  day: string
  amount: string
  trend: TrendTone
  empty?: boolean
}

const period = ref<PeriodLabel>('月')
const periodOptions: PeriodLabel[] = ['月', '年']
const summaryTab = ref<SummaryTab>('支出')
const expenseBreakdownLevel = ref<ExpenseBreakdownLevel>('一级分类')
const activeMonth = ref(buildMonthKey(new Date()))
const activeYear = ref(new Date().getFullYear())
const selectedDayKey = ref('')
const selectedYearMonthKey = ref('')
const analysis = ref<TransactionAnalysis | null>(null)
const expenseCategories = ref<Category[]>([])
const isLoading = ref(false)
const isBackfillingSnapshot = ref(false)
const snapshotMessage = ref('')
const pageError = ref('')
const requestSerial = ref(0)
const { isDark } = useTheme()

const {
  currentUser,
  familyView,
  familyViewOptions,
  selectedFamilyView,
  canSwitchFamilyView,
  selectedViewerUserIds,
  viewerNameByUserId,
  isReadOnlyFamilyView,
  loadFamilyMembers,
} = useFinanceFamilyView()

const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']
const summaryMetricMap: Record<SummaryTab, 'income' | 'expense' | 'surplus'> = {
  收入: 'income',
  支出: 'expense',
  结余: 'surplus',
}
const currencyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})
const integerCurrencyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
})
const toneColorMap: Record<'income' | 'expense' | 'surplus', string> = {
  income: '#F43F5E',
  expense: '#10B981',
  surplus: '#2563EB',
}
const expenseBreakdownLevelOptions: ExpenseBreakdownLevel[] = ['一级分类', '二级分类']
const emptySummary = {
  income: 0,
  expense: 0,
  surplus: 0,
  incomeCount: 0,
  expenseCount: 0,
  transactionCount: 0,
}

const analysisPeriod = computed<TransactionAnalysisPeriod>(() => (period.value === '年' ? 'year' : 'month'))
const activeMetric = computed(() => summaryMetricMap[summaryTab.value])
const requestKey = computed(() => (
  `${selectedFamilyView.value.value}:${analysisPeriod.value}:${analysisPeriod.value === 'month' ? activeMonth.value : activeYear.value}`
))
const summary = computed(() => analysis.value?.summary ?? emptySummary)
const summaryCards = computed(() => [
  { label: '收入' as const, amount: formatAmount(summary.value.income), tone: 'positive' as const },
  { label: '支出' as const, amount: formatAmount(summary.value.expense), tone: 'negative' as const },
  {
    label: '结余' as const,
    amount: formatSignedAmount(summary.value.surplus),
    tone: summary.value.surplus > 0 ? 'positive' as const : summary.value.surplus < 0 ? 'negative' as const : 'neutral' as const,
  },
])
const breakdownItems = computed<TransactionAnalysisCategoryBreakdownItem[]>(() => {
  if (!analysis.value) return []
  if (summaryTab.value === '收入') return analysis.value.incomeBreakdown
  if (summaryTab.value === '支出') {
    return expenseBreakdownLevel.value === '一级分类'
      ? buildExpenseParentBreakdown(analysis.value.expenseBreakdown)
      : analysis.value.expenseBreakdown
  }
  return []
})
const periodSummaries = computed(() => analysis.value?.periodSummaries ?? [])
const familyViewHint = computed(() => {
  if (!isReadOnlyFamilyView.value) {
    return ''
  }

  return selectedFamilyView.value.kind === 'total'
    ? '当前为家庭总计视角，可查看全家收支分析。'
    : `当前查看 ${selectedFamilyView.value.label} 的收支分析。`
})
const canBackfillSnapshot = computed(() => Boolean(currentUser.value) && !isReadOnlyFamilyView.value)
const calendarRows = computed<CalendarCell[][]>(() => {
  if (analysisPeriod.value !== 'month' || !analysis.value?.month) {
    return []
  }

  const [yearText, monthText] = analysis.value.month.split('-')
  const year = Number(yearText)
  const month = Number(monthText)
  const firstWeekday = new Date(year, month - 1, 1).getDay()
  const leadingEmptyCellCount = (firstWeekday + 6) % 7
  const cells: CalendarCell[] = Array.from({ length: leadingEmptyCellCount }, (_, index) => ({
    key: `empty-${index}`,
    day: '',
    amount: '',
    trend: 'neutral',
    empty: true,
  }))

  periodSummaries.value.forEach((item) => {
    cells.push({
      key: item.key,
      day: item.label,
      amount: buildMetricAmount(item),
      trend: buildMetricTrend(item),
    })
  })

  while (cells.length % 7 !== 0) {
    cells.push({
      key: `tail-${cells.length}`,
      day: '',
      amount: '',
      trend: 'neutral',
      empty: true,
    })
  }

  return Array.from({ length: Math.ceil(cells.length / 7) }, (_, index) => cells.slice(index * 7, index * 7 + 7))
})
const yearGridRows = computed<TransactionAnalysisPeriodSummary[][]>(() => {
  if (analysisPeriod.value !== 'year') {
    return []
  }

  return Array.from({ length: Math.ceil(periodSummaries.value.length / 3) }, (_, index) => (
    periodSummaries.value.slice(index * 3, index * 3 + 3)
  ))
})
const currentDetailSummary = computed(() => {
  if (analysisPeriod.value === 'month') {
    return periodSummaries.value.find((item) => item.key === selectedDayKey.value) ?? periodSummaries.value[0] ?? null
  }

  return periodSummaries.value.find((item) => item.key === selectedYearMonthKey.value) ?? periodSummaries.value[0] ?? null
})
const detailTransactions = computed(() => {
  const source = currentDetailSummary.value?.transactions ?? []
  if (summaryTab.value === '收入') {
    return source.filter((transaction) => transaction.type === 'income')
  }
  if (summaryTab.value === '支出') {
    return source.filter((transaction) => transaction.type === 'expense')
  }
  return source
})
const detailTitle = computed(() => {
  if (!currentDetailSummary.value) {
    return analysisPeriod.value === 'month' ? '暂无日期' : '暂无月份'
  }
  return analysisPeriod.value === 'month'
    ? formatMonthDayLabel(currentDetailSummary.value.key)
    : formatYearMonthLabel(currentDetailSummary.value.key)
})
const detailAmountLabel = computed(() => {
  if (summaryTab.value === '收入') return '总收入'
  if (summaryTab.value === '支出') return '总支出'
  return analysisPeriod.value === 'month' ? '当日结余' : '当月结余'
})
const detailAmountValue = computed(() => {
  const currentSummary = currentDetailSummary.value
  if (!currentSummary) {
    return analysisPeriod.value === 'month' ? '0' : '0.00'
  }

  if (summaryTab.value === '收入') {
    return analysisPeriod.value === 'month'
      ? formatIntegerAmount(currentSummary.income)
      : formatAmount(currentSummary.income)
  }
  if (summaryTab.value === '支出') {
    return analysisPeriod.value === 'month'
      ? formatIntegerAmount(currentSummary.expense)
      : formatAmount(currentSummary.expense)
  }
  return analysisPeriod.value === 'month'
    ? formatSignedIntegerAmount(currentSummary.surplus)
    : formatSignedAmount(currentSummary.surplus)
})
const detailAmountTone = computed<'positive' | 'negative' | 'neutral' | 'auto'>(() => {
  if (summaryTab.value === '收入') return 'positive'
  if (summaryTab.value === '支出') return 'negative'
  return amountValue(detailAmountValue.value) === 0 ? 'neutral' : 'auto'
})
const breakdownSectionTitle = computed(() => {
  if (summaryTab.value === '结余') return '分类占比'
  return `${summaryTab.value}分类占比`
})
const trendSectionTitle = computed(() => {
  const prefix = analysisPeriod.value === 'year' ? '年度' : '月度'
  if (summaryTab.value === '结余') return `${prefix}结余趋势`
  return `${prefix}${summaryTab.value}趋势`
})

const pieRef = ref<HTMLDivElement | null>(null)
const lineRef = ref<HTMLDivElement | null>(null)
let echartsLib: (typeof import('echarts')) | null = null
let pieChart: ECharts | null = null
let lineChart: ECharts | null = null

watch(requestKey, () => {
  void loadAnalysis()
})

watch([breakdownItems, () => analysis.value?.summary, activeMetric], () => {
  void syncCharts()
}, { deep: true, flush: 'post' })

watch(periodSummaries, () => {
  syncSelectedSummary()
  void syncCharts()
}, { deep: true, flush: 'post' })

watch(isLoading, (loading) => {
  if (!loading && !pageError.value) {
    void syncCharts()
  }
}, { flush: 'post' })

watch(isDark, () => {
  void syncCharts()
})

onMounted(() => {
  window.addEventListener('resize', handleResize)
  void initializePage()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose()
  lineChart?.dispose()
})

async function initializePage() {
  await loadFamilyMembers()
  await loadExpenseCategories()
  await loadAnalysis()
  void syncCharts()
}

async function runSnapshotBackfill() {
  const user = currentUser.value
  if (!user) {
    snapshotMessage.value = '请先登录'
    return
  }

  isBackfillingSnapshot.value = true
  snapshotMessage.value = ''

  try {
    const yesterday = new Date()
    yesterday.setDate(yesterday.getDate() - 1)
    const snapshotDate = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, '0')}-${String(yesterday.getDate()).padStart(2, '0')}`
    const savedCount = await backfillAssetSnapshot({
      userId: user.id,
      snapshotDate,
    })
    snapshotMessage.value = `已补跑昨天快照，生成 ${savedCount} 条记录`
    await loadAnalysis()
  } catch (error) {
    snapshotMessage.value = error instanceof Error ? error.message : '补跑失败'
  } finally {
    isBackfillingSnapshot.value = false
  }
}

async function loadExpenseCategories() {
  try {
    expenseCategories.value = await getCategories({
      type: 'expense',
      status: 'active',
    })
  } catch {
    expenseCategories.value = []
  }
}

async function loadAnalysis() {
  if (selectedViewerUserIds.value.length === 0) {
    analysis.value = null
    isLoading.value = false
    pageError.value = '请先登录后查看收支分析'
    return
  }

  const currentRequest = requestSerial.value + 1
  requestSerial.value = currentRequest
  isLoading.value = true
  pageError.value = ''

  try {
    const results = await Promise.all(
      selectedViewerUserIds.value.map(async (userId) => {
        const result = await getTransactionAnalysis({
          userId,
          period: analysisPeriod.value,
          month: analysisPeriod.value === 'month' ? activeMonth.value : undefined,
          year: analysisPeriod.value === 'year' ? activeYear.value : undefined,
        })

        return normalizeAnalysisForView(result, userId)
      }),
    )

    if (currentRequest !== requestSerial.value) {
      return
    }

    const merged = mergeTransactionAnalysisResults(results)
    analysis.value = merged
    if (merged.month && merged.month !== activeMonth.value) {
      activeMonth.value = merged.month
    }
    if (typeof merged.year === 'number' && merged.year !== activeYear.value) {
      activeYear.value = merged.year
    }
    syncSelectedSummary()
  } catch (error) {
    if (currentRequest !== requestSerial.value) {
      return
    }
    analysis.value = null
    pageError.value = error instanceof Error ? error.message : '收支分析加载失败'
  } finally {
    if (currentRequest === requestSerial.value) {
      isLoading.value = false
    }
  }
}

function normalizeAnalysisForView(source: TransactionAnalysis, userId: number) {
  if (!isReadOnlyFamilyView.value || selectedFamilyView.value.kind !== 'total') {
    return source
  }

  const viewerName = viewerNameByUserId.value.get(userId)
  if (!viewerName) {
    return source
  }

  return {
    ...source,
    periodSummaries: source.periodSummaries.map((item) => ({
      ...item,
      transactions: item.transactions.map((transaction) => ({
        ...transaction,
        accountName: `${viewerName} · ${transaction.accountName ?? '现金账户'}`,
      })),
    })),
  }
}

function mergeTransactionAnalysisResults(results: TransactionAnalysis[]) {
  const summary = {
    income: 0,
    expense: 0,
    surplus: 0,
    incomeCount: 0,
    expenseCount: 0,
    transactionCount: 0,
  }

  const incomeBreakdownMap = new Map<string, TransactionAnalysisCategoryBreakdownItem>()
  const expenseBreakdownMap = new Map<string, TransactionAnalysisCategoryBreakdownItem>()
  const trendMap = new Map<string, TransactionAnalysisTrendPoint>()
  const periodSummaryMap = new Map<string, TransactionAnalysisPeriodSummary>()

  results.forEach((result) => {
    summary.income += Number(result.summary.income ?? 0)
    summary.expense += Number(result.summary.expense ?? 0)
    summary.surplus += Number(result.summary.surplus ?? 0)
    summary.incomeCount += Number(result.summary.incomeCount ?? 0)
    summary.expenseCount += Number(result.summary.expenseCount ?? 0)
    summary.transactionCount += Number(result.summary.transactionCount ?? 0)

    mergeBreakdownItems(incomeBreakdownMap, result.incomeBreakdown)
    mergeBreakdownItems(expenseBreakdownMap, result.expenseBreakdown)

    result.trendPoints.forEach((item) => {
      const current = trendMap.get(item.key) ?? {
        key: item.key,
        label: item.label,
        income: 0,
        expense: 0,
        surplus: 0,
      }
      current.income += Number(item.income ?? 0)
      current.expense += Number(item.expense ?? 0)
      current.surplus += Number(item.surplus ?? 0)
      trendMap.set(item.key, current)
    })

    result.periodSummaries.forEach((item) => {
      const current = periodSummaryMap.get(item.key) ?? {
        key: item.key,
        label: item.label,
        income: 0,
        expense: 0,
        surplus: 0,
        transactionCount: 0,
        transactions: [],
      }
      current.income += Number(item.income ?? 0)
      current.expense += Number(item.expense ?? 0)
      current.surplus += Number(item.surplus ?? 0)
      current.transactionCount += Number(item.transactionCount ?? 0)
      current.transactions = [...current.transactions, ...item.transactions]
      periodSummaryMap.set(item.key, current)
    })
  })

  const incomeBreakdown = finalizeBreakdownItems(incomeBreakdownMap, summary.income)
  const expenseBreakdown = finalizeBreakdownItems(expenseBreakdownMap, summary.expense)
  const trendPoints = Array.from(trendMap.values()).sort((left, right) => left.key.localeCompare(right.key))
  const periodSummaries = Array.from(periodSummaryMap.values())
    .sort((left, right) => left.key.localeCompare(right.key))
    .map((item) => ({
      ...item,
      transactions: [...item.transactions].sort(compareTransactionsDesc),
    }))

  const first = results[0]
  return {
    userId: first?.userId ?? 0,
    period: analysisPeriod.value,
    month: analysisPeriod.value === 'month' ? (first?.month ?? activeMonth.value) : null,
    year: analysisPeriod.value === 'year' ? (first?.year ?? activeYear.value) : null,
    summary,
    incomeBreakdown,
    expenseBreakdown,
    trendPoints,
    periodSummaries,
  } satisfies TransactionAnalysis
}

function mergeBreakdownItems(
  target: Map<string, TransactionAnalysisCategoryBreakdownItem>,
  source: TransactionAnalysisCategoryBreakdownItem[],
) {
  source.forEach((item) => {
    const key = `${item.categoryId ?? item.categoryName}`
    const current = target.get(key) ?? {
      categoryId: item.categoryId,
      categoryName: item.categoryName,
      categoryIcon: item.categoryIcon,
      categoryColor: item.categoryColor,
      amount: 0,
      percent: 0,
      transactionCount: 0,
    }
    current.amount += Number(item.amount ?? 0)
    current.transactionCount += Number(item.transactionCount ?? 0)
    target.set(key, current)
  })
}

function buildExpenseParentBreakdown(source: TransactionAnalysisCategoryBreakdownItem[]) {
  if (source.length === 0) {
    return []
  }

  const categoriesById = new Map<number, Category>()
  expenseCategories.value.forEach((item) => {
    categoriesById.set(item.id, item)
  })

  const totals = new Map<string, TransactionAnalysisCategoryBreakdownItem>()
  const totalAmount = source.reduce((sum, item) => sum + Number(item.amount ?? 0), 0)

  source.forEach((item) => {
    const currentCategory = item.categoryId != null ? categoriesById.get(item.categoryId) : null
    const parentCategory = currentCategory?.parentId != null
      ? categoriesById.get(currentCategory.parentId)
      : currentCategory

    const categoryId = parentCategory?.id ?? item.categoryId ?? null
    const categoryName = parentCategory?.name ?? item.categoryName
    const categoryIcon = parentCategory?.icon ?? item.categoryIcon
    const categoryColor = parentCategory?.color ?? item.categoryColor
    const key = categoryId != null ? `category:${categoryId}` : `name:${categoryName}`

    const current = totals.get(key) ?? {
      categoryId,
      categoryName,
      categoryIcon,
      categoryColor,
      amount: 0,
      percent: 0,
      transactionCount: 0,
    }
    current.amount += Number(item.amount ?? 0)
    current.transactionCount += Number(item.transactionCount ?? 0)
    totals.set(key, current)
  })

  return Array.from(totals.values())
    .map((item) => ({
      ...item,
      percent: totalAmount > 0 ? (Number(item.amount ?? 0) / totalAmount) * 100 : 0,
    }))
    .sort((left, right) => Number(right.amount ?? 0) - Number(left.amount ?? 0))
}

function finalizeBreakdownItems(
  source: Map<string, TransactionAnalysisCategoryBreakdownItem>,
  totalAmount: number,
) {
  return Array.from(source.values())
    .map((item) => ({
      ...item,
      percent: totalAmount > 0 ? (Number(item.amount ?? 0) / totalAmount) * 100 : 0,
    }))
    .sort((left, right) => Number(right.amount ?? 0) - Number(left.amount ?? 0))
}

function compareTransactionsDesc(left: Transaction, right: Transaction) {
  const leftTime = parseTime(left.occurredAt)
  const rightTime = parseTime(right.occurredAt)
  if (leftTime !== rightTime) {
    return rightTime - leftTime
  }
  return Number(right.id ?? 0) - Number(left.id ?? 0)
}

function parseTime(value?: string | null) {
  if (!value) {
    return 0
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

function syncSelectedSummary() {
  if (analysisPeriod.value === 'month') {
    const available = new Set(periodSummaries.value.map((item) => item.key))
    if (!available.has(selectedDayKey.value)) {
      selectedDayKey.value = findPreferredDayKey(periodSummaries.value)
    }
    return
  }

  const available = new Set(periodSummaries.value.map((item) => item.key))
  if (!available.has(selectedYearMonthKey.value)) {
    selectedYearMonthKey.value = findPreferredSummaryKey(periodSummaries.value)
  }
}

function findPreferredSummaryKey(items: TransactionAnalysisPeriodSummary[]) {
  return items.find((item) => item.transactionCount > 0)?.key ?? items[0]?.key ?? ''
}

function findPreferredDayKey(items: TransactionAnalysisPeriodSummary[]) {
  if (analysis.value?.month === buildMonthKey(new Date())) {
    const todayKey = buildDateKey(new Date())
    if (items.some((item) => item.key === todayKey)) {
      return todayKey
    }
  }

  return findPreferredSummaryKey(items)
}

function selectCalendarDay(key: string) {
  if (!key) return
  selectedDayKey.value = key
}

function selectYearMonth(key: string) {
  if (!key) return
  selectedYearMonthKey.value = key
}

function buildMetricAmount(summaryItem: TransactionAnalysisPeriodSummary) {
  const amountFormatter = analysisPeriod.value === 'month' ? formatIntegerAmount : formatAmount
  const signedAmountFormatter = analysisPeriod.value === 'month' ? formatSignedIntegerAmount : formatSignedAmount

  if (summaryTab.value === '收入') {
    return summaryItem.income > 0 ? `+${amountFormatter(summaryItem.income)}` : amountFormatter(0)
  }
  if (summaryTab.value === '支出') {
    return summaryItem.expense > 0 ? `-${amountFormatter(summaryItem.expense)}` : amountFormatter(0)
  }
  return signedAmountFormatter(summaryItem.surplus)
}

function buildMetricTrend(summaryItem: TransactionAnalysisPeriodSummary): TrendTone {
  if (summaryTab.value === '收入') {
    return summaryItem.income > 0 ? 'income' : 'neutral'
  }
  if (summaryTab.value === '支出') {
    return summaryItem.expense > 0 ? 'expense' : 'neutral'
  }
  if (summaryItem.surplus > 0) return 'income'
  if (summaryItem.surplus < 0) return 'expense'
  return 'neutral'
}

function handleResize() {
  pieChart?.resize()
  lineChart?.resize()
}

async function ensureEcharts() {
  if (!echartsLib) {
    echartsLib = await import('echarts')
  }
  return echartsLib
}

async function syncCharts() {
  await ensureEcharts()
  await nextTick()

  if (!pieRef.value && !lineRef.value) {
    return
  }

  if (!pieRef.value && pieChart) {
    pieChart.dispose()
    pieChart = null
  }

  if (pieRef.value) {
    if (pieChart && pieChart.getDom() !== pieRef.value) {
      pieChart.dispose()
      pieChart = null
    }
    pieChart = pieChart ?? echartsLib!.init(pieRef.value)
    pieChart.setOption(buildPieOption(), true)
  }

  if (lineRef.value) {
    if (lineChart && lineChart.getDom() !== lineRef.value) {
      lineChart.dispose()
      lineChart = null
    }
    lineChart = lineChart ?? echartsLib!.init(lineRef.value)
    lineChart.setOption(buildLineOption(), true)
  }
}

function buildPieOption(): EChartsCoreOption {
  const rootStyle = getComputedStyle(document.documentElement)
  const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
  const splitLine = rootStyle.getPropertyValue('--color-chart-split').trim()
  const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
  const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
  const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()

  if (breakdownItems.value.length === 0) {
    return {
      animation: false,
      graphic: [
        {
          type: 'text',
          left: 'center',
          top: 'middle',
          style: {
            text: summaryTab.value === '结余' ? '结余不按分类拆分' : '暂无数据',
            fill: axisText,
            fontSize: 12,
            fontWeight: 500,
          },
        },
      ],
      series: [
        {
          type: 'pie',
          radius: ['46%', '72%'],
          center: ['50%', '50%'],
          silent: true,
          label: { show: false },
          data: [{ value: 1, itemStyle: { color: splitLine } }],
        },
      ],
    }
  }

  return {
    animation: false,
    tooltip: {
      trigger: 'item',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipText },
      valueFormatter: (value: unknown) => formatAmount(Number(value ?? 0)),
    },
    series: [
      {
        type: 'pie',
        radius: ['46%', '72%'],
        center: ['50%', '50%'],
        label: { show: false },
        data: breakdownItems.value.map((item) => ({
          value: item.amount,
          name: item.categoryName,
          itemStyle: {
            color: item.categoryColor || '#CBD5E1',
          },
        })),
      },
    ],
  }
}

function buildLineOption(): EChartsCoreOption {
  const rootStyle = getComputedStyle(document.documentElement)
  const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
  const axisLine = rootStyle.getPropertyValue('--color-chart-axis-strong').trim()
  const splitLine = rootStyle.getPropertyValue('--color-chart-split').trim()
  const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
  const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
  const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()
  const metric = activeMetric.value
  const color = toneColorMap[metric]
  const data = (analysis.value?.trendPoints ?? []).map((point) => Number(point[metric] ?? 0))
  const seriesData = metric === 'surplus'
    ? data.map((value) => ({
      value,
      itemStyle: {
        color: value >= 0 ? toneColorMap.income : toneColorMap.expense,
        borderRadius: value >= 0 ? [6, 6, 0, 0] : [0, 0, 6, 6],
      },
    }))
    : data

  return {
    animation: false,
    grid: { left: 44, right: 12, top: 14, bottom: 22 },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow',
      },
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipText },
      valueFormatter: (value: unknown) => {
        const numericValue = Number(value ?? 0)
        return metric === 'surplus' ? formatSignedAmount(numericValue) : formatAmount(numericValue)
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: (analysis.value?.trendPoints ?? []).map((point) => point.label),
      axisLine: { lineStyle: { color: axisLine } },
      axisTick: { show: false },
      axisLabel: { color: axisText, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      axisLine: {
        show: true,
        lineStyle: { color: axisLine },
      },
      axisTick: { show: false },
      axisLabel: {
        show: true,
        color: axisText,
        fontSize: 11,
        formatter: (value: number) => formatAxisValue(value),
      },
      splitLine: { lineStyle: { color: splitLine } },
    },
    series: [
      {
        type: 'bar',
        data: seriesData,
        barMaxWidth: 18,
        itemStyle: {
          color,
          borderRadius: [6, 6, 0, 0],
        },
      },
    ],
  }
}

function formatAmount(value: number) {
  return currencyFormatter.format(Number(value ?? 0))
}

function formatIntegerAmount(value: number) {
  return integerCurrencyFormatter.format(Number(value ?? 0))
}

function formatSignedAmount(value: number) {
  const numericValue = Number(value ?? 0)
  if (numericValue > 0) return `+${formatAmount(numericValue)}`
  if (numericValue < 0) return `-${formatAmount(Math.abs(numericValue))}`
  return formatAmount(0)
}

function formatSignedIntegerAmount(value: number) {
  const numericValue = Number(value ?? 0)
  if (numericValue > 0) return `+${formatIntegerAmount(numericValue)}`
  if (numericValue < 0) return `-${formatIntegerAmount(Math.abs(numericValue))}`
  return formatIntegerAmount(0)
}

function amountValue(value: string) {
  const normalized = value.replace(/[^\d.+-]/g, '')
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : 0
}

function formatAxisValue(value: number) {
  const numericValue = Number(value ?? 0)
  const absoluteValue = Math.abs(numericValue)

  if (absoluteValue >= 10000) {
    return `${(numericValue / 10000).toFixed(1)}w`
  }

  return `${Math.round(numericValue)}`
}

function buildMonthKey(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${date.getFullYear()}-${month}`
}

function buildDateKey(date: Date) {
  const day = String(date.getDate()).padStart(2, '0')
  return `${buildMonthKey(date)}-${day}`
}

function formatMonthDayLabel(key: string) {
  const [, monthText, dayText] = key.split('-')
  if (!monthText || !dayText) {
    return key
  }
  return `${Number(monthText)}月${Number(dayText)}日`
}

function formatYearMonthLabel(key: string) {
  const [yearText, monthText] = key.split('-')
  if (!yearText || !monthText) {
    return key
  }
  return `${yearText}年${Number(monthText)}月`
}

function detailMetaText(transaction: Transaction) {
  const category = transaction.categoryName || (transaction.type === 'income' ? '收入' : '支出')
  return [transaction.accountName, category, formatTime(transaction.occurredAt)]
    .filter(Boolean)
    .join(' · ')
}

function detailAmountText(transaction: Transaction) {
  return transaction.type === 'income'
    ? `+${formatAmount(transaction.amount)}`
    : `-${formatAmount(transaction.amount)}`
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<template>
  <section class="analysis-page" aria-label="收支分析">
    <PageHeader title="收支分析" back-to="/finance" back-label="返回财务首页">
      <label v-if="canSwitchFamilyView" class="analysis-family-switch">
        <select v-model="familyView" class="analysis-family-switch-select" aria-label="切换家庭成员收支分析视角">
          <option
            v-for="option in familyViewOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </option>
        </select>
      </label>
      <CommonButton
        v-if="canBackfillSnapshot"
        variant="secondary"
        size="sm"
        :disabled="isBackfillingSnapshot"
        @click="runSnapshotBackfill"
      >
        {{ isBackfillingSnapshot ? '补跑中' : '补跑昨天快照' }}
      </CommonButton>
    </PageHeader>

    <p v-if="familyViewHint" class="analysis-view-hint">
      {{ familyViewHint }}
    </p>
    <p v-if="snapshotMessage" class="analysis-status">{{ snapshotMessage }}</p>

    <SegmentedControl v-model="period" :options="periodOptions" label="月年切换" />

    <MonthPicker v-if="period === '月'" v-model="activeMonth" />
    <YearPicker v-else v-model="activeYear" />

    <p v-if="pageError" class="analysis-status analysis-status-error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="summary-switch" aria-label="收支概览">
        <button
          v-for="item in summaryCards"
          :key="item.label"
          type="button"
          :class="['summary-item', { active: summaryTab === item.label }]"
          @click="summaryTab = item.label"
        >
          <strong>{{ item.label }}</strong>
          <AmountText tag="span" :value="item.amount" :tone="item.tone" />
        </button>
      </section>

      <section v-if="summaryTab !== '结余'" class="card">
        <header class="card-head split">
          <strong>{{ breakdownSectionTitle }}</strong>
          <SegmentedControl
            v-if="summaryTab === '支出'"
            v-model="expenseBreakdownLevel"
            :options="expenseBreakdownLevelOptions"
            label="支出分类层级切换"
            variant="surface"
            size="small"
          />
        </header>
        <div ref="pieRef" class="pie-chart"></div>
        <div class="breakdown-list">
          <article v-for="item in breakdownItems" :key="`${item.categoryId ?? item.categoryName}`" class="breakdown-item">
            <div class="breakdown-head">
              <div class="breakdown-left" :style="{ color: item.categoryColor || '#475569' }">
                <strong>{{ item.categoryName }}</strong>
              </div>
              <span class="breakdown-right" :style="{ color: item.categoryColor || '#475569' }">
                {{ item.percent.toFixed(2) }}%&nbsp;
                <AmountText tag="span" :value="formatAmount(item.amount)" />
                &nbsp;{{ item.transactionCount }}笔
              </span>
            </div>
            <div class="breakdown-track">
              <span :style="{ width: `${item.percent}%`, background: item.categoryColor || '#CBD5E1' }"></span>
            </div>
          </article>
          <p v-if="breakdownItems.length === 0" class="card-empty">当前周期暂无{{ summaryTab }}记录</p>
        </div>
      </section>

      <section class="card">
        <header class="card-head">
          <strong>{{ trendSectionTitle }}</strong>
        </header>
        <div ref="lineRef" class="line-chart"></div>
      </section>

      <section v-if="period === '月'" class="card">
        <header class="card-head split">
          <strong>月度汇总</strong>
          <span>{{ analysis?.month ? formatYearMonthLabel(analysis.month) : '' }}</span>
        </header>

        <div class="calendar-week">
          <span v-for="day in weekdayLabels" :key="day">{{ day }}</span>
        </div>

        <div class="calendar-grid">
          <div v-for="(week, weekIndex) in calendarRows" :key="`w-${weekIndex}`" class="calendar-row">
            <button
              v-for="cell in week"
              :key="cell.key"
              type="button"
              :class="[
                'calendar-cell',
                `trend-${cell.trend}`,
                { active: cell.key === selectedDayKey, empty: cell.empty },
              ]"
              @click="selectCalendarDay(cell.key)"
            >
              <span class="day">{{ cell.day }}</span>
              <AmountText tag="span" class="amount" :value="cell.amount" />
            </button>
          </div>
        </div>

        <div class="day-detail">
          <header>
            <strong class="detail-header-title">{{ detailTitle }}</strong>
            <div class="detail-header-total">
              <span>{{ detailAmountLabel }}</span>
              <AmountText tag="strong" class="detail-header-amount" :value="detailAmountValue" :tone="detailAmountTone" show-sign />
            </div>
          </header>
          <p v-if="detailTransactions.length === 0" class="card-empty">这一天暂无对应记录</p>
          <article v-for="row in detailTransactions" :key="row.id" class="day-detail-row">
            <div>
              <strong>{{ row.title }}</strong>
              <span>{{ detailMetaText(row) }}</span>
            </div>
            <AmountText tag="strong" class="expense" :value="detailAmountText(row)" />
          </article>
        </div>
      </section>

      <section v-else class="card">
        <header class="card-head split">
          <strong>年度汇总</strong>
          <span>{{ activeYear }}年</span>
        </header>

        <div class="year-grid">
          <div v-for="(row, rowIndex) in yearGridRows" :key="`yr-${rowIndex}`" class="year-grid-row">
            <button
              v-for="item in row"
              :key="item.key"
              type="button"
              :class="['year-grid-item', { active: selectedYearMonthKey === item.key }]"
              @click="selectYearMonth(item.key)"
            >
              <strong>{{ item.label }}</strong>
              <AmountText tag="span" :value="buildMetricAmount(item)" />
            </button>
          </div>
        </div>

        <div class="day-detail">
          <header>
            <strong class="detail-header-title">{{ detailTitle }}</strong>
            <div class="detail-header-total">
              <span>{{ detailAmountLabel }}</span>
              <AmountText tag="strong" class="detail-header-amount" :value="detailAmountValue" :tone="detailAmountTone" show-sign />
            </div>
          </header>
          <p v-if="detailTransactions.length === 0" class="card-empty">这个月份暂无对应记录</p>
          <article v-for="row in detailTransactions" :key="row.id" class="day-detail-row">
            <div>
              <strong>{{ row.title }}</strong>
              <span>{{ detailMetaText(row) }}</span>
            </div>
            <AmountText tag="strong" class="expense" :value="detailAmountText(row)" />
          </article>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
