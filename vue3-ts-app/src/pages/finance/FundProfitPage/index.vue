<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import {
  getFundProfitPage,
  type FundProfitCalendarCell,
  type FundProfitDetail,
  type FundProfitPage,
  type FundProfitPageSummaryMetric,
  type FundProfitTrendPoint,
  type FundProfitView,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

type DetailFilter = 'all' | 'profit' | 'loss'

const router = useRouter()

const viewOptions = [
  { label: '日视图', value: 'day' },
  { label: '月视图', value: 'month' },
  { label: '年视图', value: 'year' },
] as const
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']
const summaryShortcutOrder: Record<string, number> = {
  today: 0,
  '7d': 1,
  month: 2,
  cumulative: 3,
}

const selectedView = ref<FundProfitView>('day')
const selectedAccountId = ref('all')
const selectedSummaryShortcut = ref('cumulative')
const selectedDetailFilter = ref<DetailFilter>('all')
const selectedKey = ref('')
const dayAnchor = ref(formatMonthValue(new Date()))
const monthAnchor = ref(new Date().getFullYear())
const yearAnchor = ref(new Date().getFullYear())
const pageData = ref<FundProfitPage | null>(null)
const isLoading = ref(false)
const isDetailLoading = ref(false)
const isCalendarLoading = ref(false)
const pageError = ref('')

const isDayView = computed(() => selectedView.value === 'day')

const selectedAccountLabel = computed(() =>
  pageData.value?.accounts.find((account) => String(account.accountId) === selectedAccountId.value)?.accountName ?? '全部账户',
)

const summaryShortcuts = computed(() =>
  [...(pageData.value?.summary.shortcuts ?? [])].sort((left, right) => {
    const leftOrder = summaryShortcutOrder[left.key] ?? 99
    const rightOrder = summaryShortcutOrder[right.key] ?? 99
    return leftOrder - rightOrder
  }),
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
  const max = trendPoints.value.reduce((current, item) => Math.max(current, Math.abs(Number(item.profit ?? 0))), 0)
  return max > 0 ? max : 1
})

const trendStats = computed(() => {
  const items = trendPoints.value
  const positiveDays = items.filter((item) => Number(item.profit ?? 0) > 0).length
  const negativeDays = items.filter((item) => Number(item.profit ?? 0) < 0).length
  const sorted = [...items].sort((left, right) => Number(right.profit ?? 0) - Number(left.profit ?? 0))
  return {
    positiveDays,
    negativeDays,
    peak: sorted[0] ?? null,
    trough: sorted[sorted.length - 1] ?? null,
  }
})

const trendPositiveRate = computed(() => {
  if (!trendPoints.value.length) {
    return '--'
  }
  return `${Math.round((trendStats.value.positiveDays / trendPoints.value.length) * 100)}%`
})

const trendNegativeRate = computed(() => {
  if (!trendPoints.value.length) {
    return '--'
  }
  return `${Math.round((trendStats.value.negativeDays / trendPoints.value.length) * 100)}%`
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
})

async function loadPage(options?: { detailOnly?: boolean; calendarOnly?: boolean }) {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看基金收益'
    pageData.value = null
    return
  }

  const detailOnly = Boolean(options?.detailOnly && pageData.value)
  const calendarOnly = Boolean(options?.calendarOnly && pageData.value && !detailOnly)
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
    })

    if (detailOnly && pageData.value) {
      mergeDetailSelection(data)
    } else if (calendarOnly && pageData.value) {
      mergeCalendarSelection(data)
    } else {
      pageData.value = data
      syncPageState(data)
    }
  } catch (error) {
    pageData.value = null
    pageError.value = error instanceof Error ? error.message : '基金收益加载失败'
  } finally {
    if (detailOnly) {
      isDetailLoading.value = false
    } else if (calendarOnly) {
      isCalendarLoading.value = false
    } else {
      isLoading.value = false
    }
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
    const nextShortcut = data.summary.shortcuts?.[0]
    selectedSummaryShortcut.value = shortcutKeys.has(data.summary.activeShortcut)
      ? data.summary.activeShortcut
      : nextShortcut?.key || 'cumulative'
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

function normalizeView(value: string) {
  if (value === 'month' || value === 'year') {
    return value
  }
  return 'day'
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

function openDetail(item: FundProfitDetail) {
  router.push({
    path: '/finance/accounts/investment/detail',
    query: {
      positionId: String(item.positionId),
    },
  })
}

function formatMonthValue(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}`
}

function formatMonthLabel(value: string) {
  const matched = /^(\d{4})-(\d{2})$/.exec(value)
  if (!matched) {
    return value
  }
  return `${matched[1]}年${matched[2]}月`
}

function shiftMonthValue(value: string, offset: number) {
  const [yearText, monthText] = value.split('-')
  const date = new Date(Number(yearText), Number(monthText) - 1 + offset, 1)
  return formatMonthValue(date)
}

function formatNumber(value: number, digits = 2) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(Number(value ?? 0)))
}

function formatCurrency(value: number, digits = 2) {
  return `¥${formatNumber(value, digits)}`
}

function formatSignedCurrency(value: number, digits = 2) {
  const normalized = Number(value ?? 0)
  const sign = normalized > 0 ? '+' : normalized < 0 ? '-' : ''
  return `${sign}${formatCurrency(normalized, digits)}`
}

function formatRate(value: number, digits = 2) {
  const normalized = Number(value ?? 0)
  const sign = normalized > 0 ? '+' : normalized < 0 ? '-' : ''
  return `${sign}${formatNumber(normalized, digits)}%`
}

function formatHoldingQuantity(value: number) {
  return `${formatNumber(value, 2)}份`
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function getTone(value: number) {
  if (value > 0) {
    return 'positive'
  }
  if (value < 0) {
    return 'negative'
  }
  return 'neutral'
}

function getTrendBarStyle(point: FundProfitTrendPoint) {
  const value = Math.abs(Number(point.profit ?? 0))
  const height = value === 0 ? 18 : Math.max(18, (value / trendMaxAbsProfit.value) * 132)
  return { height: `${height}px` }
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

function getDetailAccumulatedProfit(item: FundProfitDetail) {
  return item.holdingAmount - item.costAmount
}
</script>

<template>
  <section class="fund-profit-page" aria-label="基金收益">
    <PageHeader title="基金收益" back-to="/finance/more-features" back-label="返回更多功能" />

    <p v-if="pageError" class="fund-profit-message fund-profit-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" text="基金收益加载中..." />

    <template v-else-if="pageData">
      <SegmentedControl
        v-model="selectedSummaryShortcut"
        :options="summaryShortcutOptions"
        label="基金收益范围切换"
        class="fund-profit-range-seg"
      />

      <section class="fund-profit-summary-card" aria-label="基金收益汇总">
        <div class="fund-profit-summary-top">
          <div class="fund-profit-summary-main">
            <p>{{ summaryTitle }}</p>
            <div class="summary-amount-row">
              <AmountText
                tag="strong"
                class="summary-amount"
                :value="summaryMetricProfitText"
                :tone="summaryMetricProfitTone"
              />
              <AmountText
                tag="span"
                class="summary-rate-inline"
                :value="summaryMetricRateText"
                :tone="summaryMetricRateTone"
              />
            </div>
          </div>

          <div class="summary-account-pill">{{ summaryPillText }}</div>
        </div>

        <p class="summary-hint">{{ summaryHint }}</p>
      </section>

      <section class="fund-profit-card" aria-label="近7日收益波动">
        <header class="fund-profit-card-head trend-card-head">
          <strong>近7日收益波动</strong>
          <div class="trend-legend">
            <span class="trend-legend-item">
              <i class="positive"></i>
              <span>上涨</span>
            </span>
            <span class="trend-legend-item">
              <i class="negative"></i>
              <span>下跌</span>
            </span>
          </div>
        </header>

        <div class="trend-chart">
          <div v-for="point in trendPoints" :key="point.key" class="trend-column">
            <span class="trend-profit-text" :class="`is-${getTone(Number(point.profit ?? 0))}`">
              {{ formatSignedCurrency(Number(point.profit ?? 0), 0) }}
            </span>
            <span class="trend-bar-track">
              <span
                class="trend-bar-fill"
                :class="`is-${getTone(Number(point.profit ?? 0))}`"
                :style="getTrendBarStyle(point)"
              ></span>
            </span>
            <span class="trend-axis-label">{{ point.label }}</span>
          </div>
        </div>

        <p class="card-footnote">{{ trendFootnote }}</p>

        <div class="trend-stats">
          <article class="trend-stat up">
            <span>上涨 {{ trendStats.positiveDays }} 天</span>
            <strong>{{ trendPositiveRate }}</strong>
          </article>
          <article class="trend-stat down">
            <span>下跌 {{ trendStats.negativeDays }} 天</span>
            <strong>{{ trendNegativeRate }}</strong>
          </article>
          <article class="trend-stat neutral">
            <span>最大回撤</span>
            <strong>{{ trendStats.trough ? formatSignedCurrency(Number(trendStats.trough.profit ?? 0), 0) : '--' }}</strong>
          </article>
        </div>
      </section>

      <section class="fund-profit-card" aria-label="收益日历">
        <header class="fund-profit-card-head calendar-card-head">
          <strong>{{ calendarTitle }}</strong>
          <span class="card-side-text">{{ calendarHeadLabel }}</span>
        </header>

        <SegmentedControl
          v-model="viewModel"
          :options="viewOptions"
          label="基金收益视图切换"
          class="calendar-view-seg"
          variant="surface"
        />

        <div class="calendar-meta-row">
          <div class="calendar-meta-left">
            <button type="button" class="calendar-nav-button" @click="shiftCalendarAnchor(-1)">‹</button>
            <strong>{{ calendarAnchorLabel }}</strong>
            <button type="button" class="calendar-nav-button" @click="shiftCalendarAnchor(1)">›</button>
          </div>

          <div class="calendar-status-pill" :class="`is-${selectedView}`">
            <span>{{ calendarStatePillText }}</span>
          </div>
        </div>

        <div v-if="showCalendarLegend" class="calendar-legend-row">
          <span
            v-for="item in calendarLegendItems"
            :key="item.label"
            class="calendar-legend-item"
          >
            <i :class="`is-${item.tone}`"></i>
            <span>{{ item.label }}</span>
          </span>
        </div>

        <p v-if="isCalendarLoading" class="empty-text">日历加载中...</p>

        <template v-else-if="isDayView">
          <div class="calendar-box">
            <div class="weekday-row">
              <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
            </div>

            <div class="day-calendar-grid">
              <template v-for="(item, index) in dayCalendarCells" :key="item?.key ?? `blank-${index}`">
                <span v-if="!item" class="day-calendar-cell day-calendar-cell-blank"></span>
                <button
                  v-else
                  type="button"
                  :class="[
                    'day-calendar-cell',
                    `is-${getPeriodCardTone(item)}`,
                    { selected: item.selected, current: item.current, disabled: item.profit === null || item.profit === undefined },
                  ]"
                  @click="handlePeriodSelect(item)"
                >
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.profit === null || item.profit === undefined ? '--' : formatCurrency(item.profit, 0) }}</span>
                </button>
              </template>
            </div>
          </div>
        </template>

        <div v-else class="period-card-grid" :class="{ 'period-card-grid-year': selectedView === 'year' }">
          <template v-for="(item, index) in selectedView === 'year' ? yearPeriodCards : periodCards" :key="item?.key ?? `spacer-${index}`">
            <span v-if="!item" class="period-card period-card-spacer"></span>
            <button
              v-else
              type="button"
              :class="[
                'period-card',
                `is-${getPeriodCardTone(item)}`,
                { selected: item.selected, current: item.current, disabled: item.profit === null || item.profit === undefined },
              ]"
              @click="handlePeriodSelect(item)"
            >
              <span class="period-card-label">{{ item.label }}</span>
              <AmountText
                tag="strong"
                class="period-card-value"
                :value="item.profit === null || item.profit === undefined ? '--' : formatCurrency(item.profit)"
                :tone="getTone(Number(item.profit ?? 0))"
              />
              <span class="period-card-side">
                {{ item.profitRate === null || item.profitRate === undefined ? '--' : formatRate(item.profitRate) }}
              </span>
            </button>
          </template>
        </div>

        <p class="calendar-footnote">{{ calendarFootnote }}</p>
      </section>

      <section class="fund-profit-card" aria-label="基金收益明细">
        <header class="fund-profit-card-head detail-card-head">
          <strong>{{ detailTitle }}</strong>
          <span class="card-side-text">跟随 {{ selectionLabel }}</span>
        </header>

        <div class="detail-filter-row">
          <button
            v-for="item in detailFilterOptions"
            :key="item.value"
            type="button"
            :class="['detail-filter', { active: selectedDetailFilter === item.value }]"
            @click="selectedDetailFilter = item.value"
          >
            {{ item.label }}
          </button>
        </div>

        <p v-if="isDetailLoading" class="empty-text">明细加载中...</p>
        <p v-else-if="visibleDetails.length === 0" class="empty-text">当前筛选下暂无基金收益明细</p>

        <template v-else>
          <button
            v-for="item in visibleDetails"
            :key="item.positionId"
            type="button"
            class="detail-row"
            @click="openDetail(item)"
          >
            <div class="detail-main">
              <div class="fund-name-row">
                <strong>{{ item.productName }}</strong>
              </div>
              <div class="fund-detail-meta">
                <span>{{ formatHoldingQuantity(item.holdingQuantity) }} · 市值 {{ formatCurrency(item.holdingAmount) }}</span>
                <span v-if="item.productSymbol" class="fund-code-chip">{{ item.productSymbol }}</span>
              </div>
            </div>
            <div class="detail-side">
              <AmountText
                tag="strong"
                class="detail-profit-text"
                :value="formatSignedCurrency(item.periodProfit)"
                :tone="getTone(item.periodProfit)"
              />
              <AmountText
                tag="span"
                class="detail-accumulated-text"
                :value="`累计 ${formatSignedCurrency(getDetailAccumulatedProfit(item))}`"
                :tone="getTone(getDetailAccumulatedProfit(item))"
              />
            </div>
          </button>
        </template>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
