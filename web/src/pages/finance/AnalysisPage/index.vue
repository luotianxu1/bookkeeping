<script setup lang="ts">
import { formatAmount } from '@/utils/format'
import { computed, onMounted, ref, watch } from 'vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CategoryIcon from '@/components/common/CategoryIcon/index.vue'
import MonthPicker from '@/components/common/MonthPicker/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import YearPicker from '@/components/common/YearPicker/index.vue'
import { useFinanceFamilyView } from '@/composables/useFinanceFamilyView'
import {
  getTransactionAnalysis,
  type TransactionAnalysis,
  type TransactionAnalysisCategoryBreakdownItem,
  type TransactionAnalysisPeriod,
  type TransactionAnalysisPeriodSummary,
} from '@/api/modules/finance'
import { getErrorMessage } from '@/api/request'
import {
  compareTransactionsDesc,
  mergeTransactionAnalysisResults,
  normalizeAnalysisForView,
} from './analysisMerge'
import {
  amountValue,
  breakdownDetailMetaText,
  buildMetricAmount,
  buildMetricTrend,
  buildMonthKey,
  detailAmountText,
  detailMetaText,
  findPreferredDayKey,
  findPreferredSummaryKey,
  formatIntegerAmount,
  formatMonthDayLabel,
  formatSignedAmount,
  formatSignedIntegerAmount,
  formatYearMonthLabel,
  getBreakdownItemKey,
  isTransactionInBreakdown,
  type SummaryTab,
  type TrendTone,
} from './analysisFormat'
import { useAnalysisCharts } from './useAnalysisCharts'

type PeriodLabel = '月' | '年'

type CalendarCell = {
  key: string
  day: string
  amount: string
  trend: TrendTone
  empty?: boolean
}

const period = ref<PeriodLabel>('月')
const periodOptions: PeriodLabel[] = ['月', '年']
const DETAIL_PAGE_SIZE = 10
const BREAKDOWN_DETAIL_PAGE_SIZE = 10
const summaryTab = ref<SummaryTab>('支出')
const activeMonth = ref(buildMonthKey(new Date()))
const activeYear = ref(new Date().getFullYear())
const selectedDayKey = ref('')
const selectedYearMonthKey = ref('')
const detailCurrentPage = ref(1)
const expandedBreakdownKey = ref('')
const breakdownDetailCurrentPage = ref(1)
const analysis = ref<TransactionAnalysis | null>(null)
const isLoading = ref(true)
const pageError = ref('')
const requestSerial = ref(0)

const {
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
  if (summaryTab.value === '支出') return analysis.value.expenseBreakdown
  return []
})
const periodSummaries = computed(() => analysis.value?.periodSummaries ?? [])
const periodTransactions = computed(() => (
  periodSummaries.value
    .flatMap((item) => item.transactions)
    .filter((transaction) => transaction.type === activeMetric.value)
    .sort(compareTransactionsDesc)
))
const expandedBreakdownItem = computed(() => (
  breakdownItems.value.find((item) => getBreakdownItemKey(item) === expandedBreakdownKey.value) ?? null
))
const expandedBreakdownTransactions = computed(() => {
  const item = expandedBreakdownItem.value
  if (!item) return []
  return periodTransactions.value.filter((transaction) => isTransactionInBreakdown(transaction, item))
})
const breakdownDetailTotalItems = computed(() => expandedBreakdownTransactions.value.length)
const breakdownDetailTotalPages = computed(() => Math.max(1, Math.ceil(breakdownDetailTotalItems.value / BREAKDOWN_DETAIL_PAGE_SIZE)))
const breakdownDetailPagedTransactions = computed(() => {
  const startIndex = (breakdownDetailCurrentPage.value - 1) * BREAKDOWN_DETAIL_PAGE_SIZE
  return expandedBreakdownTransactions.value.slice(startIndex, startIndex + BREAKDOWN_DETAIL_PAGE_SIZE)
})
const hasBreakdownDetailPagination = computed(() => breakdownDetailTotalPages.value > 1)
const breakdownDetailPaginationSummary = computed(() => (
  `第 ${breakdownDetailCurrentPage.value} / ${breakdownDetailTotalPages.value} 页，共 ${breakdownDetailTotalItems.value} 条`
))
const canGoPrevBreakdownDetailPage = computed(() => breakdownDetailCurrentPage.value > 1)
const canGoNextBreakdownDetailPage = computed(() => breakdownDetailCurrentPage.value < breakdownDetailTotalPages.value)
const familyViewHint = computed(() => {
  if (!isReadOnlyFamilyView.value) {
    return ''
  }

  return selectedFamilyView.value.kind === 'total'
    ? '当前为家庭总计视角，可查看全家收支分析。'
    : `当前查看 ${selectedFamilyView.value.label} 的收支分析。`
})
const familyViewSelectOptions = computed<CommonSelectOption[]>(() =>
  familyViewOptions.value.map((option) => ({
    label: option.label,
    value: option.value,
  })),
)
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
      amount: buildMetricAmount(item, analysisPeriod.value, summaryTab.value),
      trend: buildMetricTrend(item, summaryTab.value),
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
const detailTotalItems = computed(() => detailTransactions.value.length)
const detailTotalPages = computed(() => Math.max(1, Math.ceil(detailTotalItems.value / DETAIL_PAGE_SIZE)))
const detailPagedTransactions = computed(() => {
  const startIndex = (detailCurrentPage.value - 1) * DETAIL_PAGE_SIZE
  return detailTransactions.value.slice(startIndex, startIndex + DETAIL_PAGE_SIZE)
})
const hasDetailPagination = computed(() => detailTotalPages.value > 1)
const detailPaginationSummary = computed(() => `第 ${detailCurrentPage.value} / ${detailTotalPages.value} 页，共 ${detailTotalItems.value} 条`)
const canGoPrevDetailPage = computed(() => detailCurrentPage.value > 1)
const canGoNextDetailPage = computed(() => detailCurrentPage.value < detailTotalPages.value)
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

const { pieRef, lineRef, syncCharts } = useAnalysisCharts({
  breakdownItems,
  summaryTab,
  activeMetric,
  analysis,
})

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

watch([summaryTab, selectedDayKey, selectedYearMonthKey, analysisPeriod], () => {
  detailCurrentPage.value = 1
})

watch([summaryTab, requestKey], () => {
  expandedBreakdownKey.value = ''
  breakdownDetailCurrentPage.value = 1
})

watch(expandedBreakdownKey, () => {
  breakdownDetailCurrentPage.value = 1
})

watch(detailTotalPages, (totalPages) => {
  if (detailCurrentPage.value > totalPages) {
    detailCurrentPage.value = totalPages
  }
})

watch(breakdownItems, () => {
  if (expandedBreakdownKey.value && !expandedBreakdownItem.value) {
    expandedBreakdownKey.value = ''
  }
}, { deep: true })

watch(breakdownDetailTotalPages, (totalPages) => {
  if (breakdownDetailCurrentPage.value > totalPages) {
    breakdownDetailCurrentPage.value = totalPages
  }
})

onMounted(() => {
  void initializePage()
})

async function initializePage() {
  await loadFamilyMembers()
  await loadAnalysis()
  void syncCharts()
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

        return normalizeAnalysisForView(result, userId, {
          isReadOnlyFamilyView: isReadOnlyFamilyView.value,
          selectedFamilyViewKind: selectedFamilyView.value.kind,
          viewerNameByUserId: viewerNameByUserId.value,
        })
      }),
    )

    if (currentRequest !== requestSerial.value) {
      return
    }

    const merged = mergeTransactionAnalysisResults(results, {
      period: analysisPeriod.value,
      activeMonth: activeMonth.value,
      activeYear: activeYear.value,
    })
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
    pageError.value = getErrorMessage(error, '收支分析加载失败')
  } finally {
    if (currentRequest === requestSerial.value) {
      isLoading.value = false
    }
  }
}

function syncSelectedSummary() {
  if (analysisPeriod.value === 'month') {
    const available = new Set(periodSummaries.value.map((item) => item.key))
    if (!available.has(selectedDayKey.value)) {
      selectedDayKey.value = findPreferredDayKey(periodSummaries.value, analysis.value?.month)
    }
    return
  }

  const available = new Set(periodSummaries.value.map((item) => item.key))
  if (!available.has(selectedYearMonthKey.value)) {
    selectedYearMonthKey.value = findPreferredSummaryKey(periodSummaries.value)
  }
}

function selectCalendarDay(key: string) {
  if (!key) return
  selectedDayKey.value = key
}

function selectYearMonth(key: string) {
  if (!key) return
  selectedYearMonthKey.value = key
}

function goToPrevDetailPage() {
  if (!canGoPrevDetailPage.value) return
  detailCurrentPage.value -= 1
}

function goToNextDetailPage() {
  if (!canGoNextDetailPage.value) return
  detailCurrentPage.value += 1
}

function isBreakdownExpanded(item: TransactionAnalysisCategoryBreakdownItem) {
  return expandedBreakdownKey.value === getBreakdownItemKey(item)
}

function toggleBreakdownItem(item: TransactionAnalysisCategoryBreakdownItem) {
  const key = getBreakdownItemKey(item)
  expandedBreakdownKey.value = expandedBreakdownKey.value === key ? '' : key
}

function getBreakdownDetailRows(item: TransactionAnalysisCategoryBreakdownItem) {
  return isBreakdownExpanded(item) ? breakdownDetailPagedTransactions.value : []
}

function goToPrevBreakdownDetailPage() {
  if (!canGoPrevBreakdownDetailPage.value) return
  breakdownDetailCurrentPage.value -= 1
}

function goToNextBreakdownDetailPage() {
  if (!canGoNextBreakdownDetailPage.value) return
  breakdownDetailCurrentPage.value += 1
}
</script>

<template>
  <section class="analysis-page" aria-label="收支分析">
    <div class="analysis-top">
      <PageHeader title="收支分析" back-to="/finance" back-label="返回财务首页">
        <template #right>
          <div v-if="canSwitchFamilyView" class="analysis-family-switch">
            <CommonSelect
              v-model="familyView"
              label="切换家庭成员收支分析视角"
              :options="familyViewSelectOptions"
            />
          </div>
        </template>
      </PageHeader>

      <p v-if="familyViewHint" class="analysis-view-hint">
        {{ familyViewHint }}
      </p>

      <SegmentedControl v-model="period" :options="periodOptions" label="月年切换" />

      <MonthPicker v-if="period === '月'" v-model="activeMonth" />
      <YearPicker v-else v-model="activeYear" />

      <p v-if="pageError" class="analysis-status analysis-status-error">{{ pageError }}</p>
      <CommonLoading v-else-if="isLoading" />

      <section v-else class="summary-switch" aria-label="收支概览">
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
    </div>

    <div v-if="!pageError && !isLoading" class="analysis-scroll">
      <section v-if="summaryTab !== '结余'" class="card">
        <header class="card-head">
          <strong>{{ breakdownSectionTitle }}</strong>
        </header>
        <div ref="pieRef" class="pie-chart"></div>
        <p v-if="breakdownItems.some(item => item.amount < 0)" class="card-empty">图表占比仅含净支出为正的分类；支出冲减计入下方明细和收支合计。</p>
        <div class="breakdown-list">
          <article
            v-for="item in breakdownItems"
            :key="getBreakdownItemKey(item)"
            :class="['breakdown-item', { expanded: isBreakdownExpanded(item) }]"
          >
            <button
              type="button"
              class="breakdown-head"
              :aria-expanded="isBreakdownExpanded(item)"
              @click="toggleBreakdownItem(item)"
            >
              <span class="breakdown-left" :style="{ color: item.categoryColor || 'var(--color-text-tertiary)' }">
                <CategoryIcon
                  :icon="item.categoryIcon"
                  :color="item.categoryColor"
                  :name="item.categoryName"
                  :size="24"
                />
                <strong>{{ item.categoryName }}</strong>
              </span>
              <span class="breakdown-right" :style="{ color: item.categoryColor || 'var(--color-text-tertiary)' }">
                <span class="breakdown-summary">
                  {{ item.amount < 0 ? '支出冲减' : `${item.percent.toFixed(2)}%` }}&nbsp;
                  <AmountText tag="span" :value="formatAmount(item.amount)" />
                  &nbsp;{{ item.transactionCount }}笔
                </span>
                <span class="breakdown-toggle" aria-hidden="true">{{ isBreakdownExpanded(item) ? '收起' : '展开' }}</span>
              </span>
            </button>
            <div class="breakdown-track">
              <span :style="{ width: `${item.percent}%`, background: item.categoryColor || 'var(--color-chart-axis-strong)' }"></span>
            </div>
            <div v-if="isBreakdownExpanded(item)" class="breakdown-detail" aria-label="分类收支明细">
              <p v-if="expandedBreakdownTransactions.length === 0" class="card-empty">当前分类暂无对应记录</p>
              <article v-for="row in getBreakdownDetailRows(item)" :key="row.id" class="day-detail-row breakdown-detail-row">
                <div>
                  <strong>{{ row.title }}</strong>
                  <span>{{ breakdownDetailMetaText(row) }}</span>
                </div>
                <AmountText tag="strong" class="expense" :value="detailAmountText(row)" />
              </article>
              <div v-if="hasBreakdownDetailPagination" class="detail-pagination breakdown-pagination" aria-label="分类明细分页">
                <button
                  class="detail-pagination-button prev"
                  type="button"
                  aria-label="上一页"
                  :disabled="!canGoPrevBreakdownDetailPage"
                  @click="goToPrevBreakdownDetailPage"
                >
                  <span aria-hidden="true"></span>
                </button>
                <span class="detail-pagination-summary">{{ breakdownDetailPaginationSummary }}</span>
                <button
                  class="detail-pagination-button next"
                  type="button"
                  aria-label="下一页"
                  :disabled="!canGoNextBreakdownDetailPage"
                  @click="goToNextBreakdownDetailPage"
                >
                  <span aria-hidden="true"></span>
                </button>
              </div>
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
          <article v-for="row in detailPagedTransactions" :key="row.id" class="day-detail-row">
            <div>
              <strong>{{ row.title }}</strong>
              <span>{{ detailMetaText(row) }}</span>
            </div>
            <AmountText tag="strong" class="expense" :value="detailAmountText(row)" />
          </article>
          <div v-if="hasDetailPagination" class="detail-pagination" aria-label="明细分页">
            <button
              class="detail-pagination-button prev"
              type="button"
              aria-label="上一页"
              :disabled="!canGoPrevDetailPage"
              @click="goToPrevDetailPage"
            >
              <span aria-hidden="true"></span>
            </button>
            <span class="detail-pagination-summary">{{ detailPaginationSummary }}</span>
            <button
              class="detail-pagination-button next"
              type="button"
              aria-label="下一页"
              :disabled="!canGoNextDetailPage"
              @click="goToNextDetailPage"
            >
              <span aria-hidden="true"></span>
            </button>
          </div>
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
              <AmountText tag="span" :value="buildMetricAmount(item, analysisPeriod, summaryTab)" />
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
          <article v-for="row in detailPagedTransactions" :key="row.id" class="day-detail-row">
            <div>
              <strong>{{ row.title }}</strong>
              <span>{{ detailMetaText(row) }}</span>
            </div>
            <AmountText tag="strong" class="expense" :value="detailAmountText(row)" />
          </article>
          <div v-if="hasDetailPagination" class="detail-pagination" aria-label="明细分页">
            <button
              class="detail-pagination-button prev"
              type="button"
              aria-label="上一页"
              :disabled="!canGoPrevDetailPage"
              @click="goToPrevDetailPage"
            >
              <span aria-hidden="true"></span>
            </button>
            <span class="detail-pagination-summary">{{ detailPaginationSummary }}</span>
            <button
              class="detail-pagination-button next"
              type="button"
              aria-label="下一页"
              :disabled="!canGoNextDetailPage"
              @click="goToNextDetailPage"
            >
              <span aria-hidden="true"></span>
            </button>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
