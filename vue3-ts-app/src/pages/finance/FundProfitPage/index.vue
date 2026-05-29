<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import MonthPicker from '@/components/common/MonthPicker/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import YearPicker from '@/components/common/YearPicker/index.vue'
import {
  getFundProfitPage,
  type FundProfitCalendarCell,
  type FundProfitContribution,
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
  { label: '日视图', value: 'day', icon: 'calendar-day' },
  { label: '月视图', value: 'month', icon: 'calendar-month' },
  { label: '年视图', value: 'year', icon: 'calendar-year' },
] as const
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']
const detailFilters: Array<{ label: string; value: DetailFilter }> = [
  { label: '全部', value: 'all' },
  { label: '盈利', value: 'profit' },
  { label: '亏损', value: 'loss' },
]

const selectedView = ref<FundProfitView>('day')
const selectedAccountId = ref('all')
const selectedSummaryShortcut = ref('today')
const selectedDetailFilter = ref<DetailFilter>('all')
const selectedKey = ref('')
const dayAnchor = ref(formatMonthValue(new Date()))
const monthAnchor = ref(new Date().getFullYear())
const yearAnchor = ref(new Date().getFullYear())
const pageData = ref<FundProfitPage | null>(null)
const isLoading = ref(false)
const pageError = ref('')

const isDayView = computed(() => selectedView.value === 'day')
const isMonthView = computed(() => selectedView.value === 'month')

const accountOptions = computed(() => {
  const options = [{ label: '全部账户', value: 'all' }]
  for (const account of pageData.value?.accounts ?? []) {
    options.push({
      label: `${account.accountName}（${account.fundCount}只）`,
      value: String(account.accountId),
    })
  }
  return options
})

const selectedAccountLabel = computed(() =>
  accountOptions.value.find((option) => option.value === selectedAccountId.value)?.label ?? '全部账户',
)

const visibleSummaryShortcuts = computed(() =>
  (pageData.value?.summary.shortcuts ?? []).filter((item) => !isHiddenSummaryShortcut(item)),
)

const selectedSummaryMetric = computed<FundProfitPageSummaryMetric | null>(() => {
  const items = visibleSummaryShortcuts.value
  return items.find((item) => item.key === selectedSummaryShortcut.value) ?? items[0] ?? null
})

const summaryTitle = computed(() => {
  if (selectedAccountId.value === 'all') {
    return '基金收益总览'
  }
  return `${selectedAccountLabel.value}收益总览`
})

const summaryHint = computed(() => {
  const syncText = formatDateTime(pageData.value?.summary.lastSyncedAt ?? pageData.value?.lastSyncedAt)
  return syncText ? `含已确认基金持仓，数据更新时间 ${syncText}` : '含已确认基金持仓'
})

const summaryRateLabel = computed(() => {
  const label = selectedSummaryMetric.value?.label ?? '区间'
  return `${label}收益率`
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
const contributionTitle = computed(() => `${selectionLabel.value}收益贡献榜`)
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
  return [...Array.from({ length: offset }, () => null), ...items]
})

const periodCards = computed(() => (isDayView.value ? [] : pageData.value?.calendarItems ?? []))
const contributors = computed(() => pageData.value?.contributors ?? [])
const contributionMaxAbs = computed(() => {
  const max = contributors.value.reduce((current, item) => Math.max(current, Math.abs(Number(item.contributionAmount ?? 0))), 0)
  return max > 0 ? max : 1
})

const visibleDetails = computed(() => {
  const details = pageData.value?.details ?? []
  if (selectedDetailFilter.value === 'profit') {
    return details.filter((item) => Number(item.periodProfit ?? 0) > 0)
  }
  if (selectedDetailFilter.value === 'loss') {
    return details.filter((item) => Number(item.periodProfit ?? 0) < 0)
  }
  return details
})

const analysisLink = computed(() => (
  selectedAccountId.value === 'all'
    ? '/finance/trend'
    : `/finance/trend?accountId=${selectedAccountId.value}`
))

const viewModel = computed({
  get: () => selectedView.value,
  set: (value: string) => {
    if (value === selectedView.value) {
      return
    }
    selectedView.value = value as FundProfitView
    selectedKey.value = ''
    void loadPage()
  },
})

const accountModel = computed({
  get: () => selectedAccountId.value,
  set: (value: string) => {
    if (value === selectedAccountId.value) {
      return
    }
    selectedAccountId.value = value
    selectedKey.value = ''
    void loadPage()
  },
})

const dayAnchorModel = computed({
  get: () => dayAnchor.value,
  set: (value: string) => {
    if (value === dayAnchor.value) {
      return
    }
    dayAnchor.value = value
    selectedKey.value = ''
    void loadPage()
  },
})

const monthAnchorModel = computed({
  get: () => monthAnchor.value,
  set: (value: number) => {
    if (value === monthAnchor.value) {
      return
    }
    monthAnchor.value = value
    selectedKey.value = ''
    void loadPage()
  },
})

const yearAnchorModel = computed({
  get: () => yearAnchor.value,
  set: (value: number) => {
    if (value === yearAnchor.value) {
      return
    }
    yearAnchor.value = value
    selectedKey.value = ''
    void loadPage()
  },
})

onMounted(() => {
  void loadPage()
})

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看基金收益'
    pageData.value = null
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const data = await getFundProfitPage({
      userId: currentUser.id,
      accountId: selectedAccountId.value === 'all' ? undefined : Number(selectedAccountId.value),
      view: selectedView.value,
      anchor: resolveAnchorValue(),
      selected: selectedKey.value || undefined,
    })

    pageData.value = data
    syncPageState(data)
  } catch (error) {
    pageData.value = null
    pageError.value = error instanceof Error ? error.message : '基金收益加载失败'
  } finally {
    isLoading.value = false
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
    (data.summary.shortcuts ?? [])
      .filter((item) => !isHiddenSummaryShortcut(item))
      .map((item) => item.key),
  )
  if (!shortcutKeys.has(selectedSummaryShortcut.value)) {
    const nextShortcut = (data.summary.shortcuts ?? []).find((item) => !isHiddenSummaryShortcut(item))
    selectedSummaryShortcut.value = shortcutKeys.has(data.summary.activeShortcut) ? data.summary.activeShortcut : nextShortcut?.key || 'today'
  }

  const validAccountIds = new Set(['all', ...data.accounts.map((account) => String(account.accountId))])
  if (!validAccountIds.has(selectedAccountId.value)) {
    selectedAccountId.value = 'all'
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
  void loadPage()
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

function isHiddenSummaryShortcut(item: FundProfitPageSummaryMetric) {
  return item.key === 'cumulative' || item.label === '累计'
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

function getContributionBarStyle(item: FundProfitContribution) {
  const width = Math.max(10, (Math.abs(Number(item.contributionAmount ?? 0)) / contributionMaxAbs.value) * 100)
  return { width: `${Math.min(width, 100)}%` }
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
</script>

<template>
  <section class="fund-profit-page" aria-label="基金收益">
    <PageHeader title="基金收益" back-to="/finance/more-features" back-label="返回更多功能" />

    <p v-if="pageError" class="fund-profit-message fund-profit-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" text="基金收益加载中..." />

    <template v-else-if="pageData">
      <section class="fund-profit-summary-card" aria-label="基金收益汇总">
        <div class="fund-profit-summary-top">
          <div class="fund-profit-summary-main">
            <p>{{ summaryTitle }}</p>
            <AmountText
              tag="strong"
              class="summary-amount"
              :value="selectedSummaryMetric?.profit ?? 0"
              :tone="getTone(Number(selectedSummaryMetric?.profit ?? 0))"
              show-sign
              show-unit
            />
            <span>{{ summaryHint }}</span>
          </div>

          <div class="fund-profit-summary-side">
            <label class="summary-account-pill">
              <span class="summary-account-pill-text">{{ selectedAccountLabel }}</span>
              <select v-model="accountModel" class="summary-account-pill-select" aria-label="查看账户">
                <option v-for="option in accountOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>
            <div class="summary-rate-card">
              <span>{{ summaryRateLabel }}</span>
              <AmountText
                tag="strong"
                :value="selectedSummaryMetric ? formatRate(selectedSummaryMetric.profitRate) : '--'"
                :tone="getTone(Number(selectedSummaryMetric?.profitRate ?? 0))"
              />
            </div>
          </div>
        </div>

        <div class="fund-profit-summary-stats">
          <article class="summary-stat-card">
            <span>持仓市值</span>
            <strong>{{ formatCurrency(pageData.summary.holdingAmount) }}</strong>
          </article>
          <article class="summary-stat-card">
            <span>持仓成本</span>
            <strong>{{ formatCurrency(pageData.summary.investedAmount) }}</strong>
          </article>
          <article class="summary-stat-card">
            <span>总收益</span>
            <AmountText
              tag="strong"
              :value="pageData.summary.totalProfit"
              :tone="getTone(pageData.summary.totalProfit)"
              show-sign
              show-unit
            />
          </article>
        </div>

        <div class="summary-shortcuts" aria-label="收益范围快捷切换">
          <button
            v-for="shortcut in visibleSummaryShortcuts"
            :key="shortcut.key"
            :class="['summary-shortcut', { active: shortcut.key === selectedSummaryShortcut }]"
            type="button"
            @click="selectedSummaryShortcut = shortcut.key"
          >
            <span>{{ shortcut.label }}</span>
            <AmountText
              tag="strong"
              :value="formatSignedCurrency(shortcut.profit)"
              :tone="getTone(shortcut.profit)"
            />
          </button>
        </div>
      </section>

      <section class="fund-profit-card fund-profit-insight-banner" aria-label="收益洞察">
        <div class="insight-dot"></div>
        <p>{{ pageData.insight }}</p>
      </section>

      <section class="fund-profit-card" aria-label="近7日收益波动">
        <header class="fund-profit-card-head">
          <div>
            <strong>近7日收益波动</strong>
            <p>按所选账户展示最近 7 天基金收益变化</p>
          </div>
          <span class="card-side-text">单位：元</span>
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
            <span>正收益天数</span>
            <strong>{{ trendStats.positiveDays }}</strong>
          </article>
          <article class="trend-stat down">
            <span>回撤天数</span>
            <strong>{{ trendStats.negativeDays }}</strong>
          </article>
        </div>
      </section>

      <section class="fund-profit-card" aria-label="收益日历">
        <header class="fund-profit-card-head">
          <div>
            <strong>{{ calendarTitle }}</strong>
            <p>点击任意日、月、年，下方贡献榜和明细会同步切换</p>
          </div>
          <span class="card-side-text">{{ selectionLabel }}已选</span>
        </header>

        <SegmentedControl
          v-model="viewModel"
          :options="viewOptions"
          label="基金收益视图切换"
          variant="surface"
        />

        <div class="calendar-toolbar">
          <MonthPicker v-if="isDayView" v-model="dayAnchorModel" />
          <YearPicker v-else-if="isMonthView" v-model="monthAnchorModel" />
          <YearPicker v-else v-model="yearAnchorModel" />

          <div class="calendar-status-pill">
            <span>正收益 {{ selection?.positiveFundCount ?? 0 }}</span>
            <span>回撤 {{ selection?.negativeFundCount ?? 0 }}</span>
          </div>
        </div>

        <template v-if="isDayView">
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
                @click="handleCalendarSelect(item)"
              >
                <strong>{{ item.label }}</strong>
                <span>{{ item.profit === null || item.profit === undefined ? '--' : formatSignedCurrency(item.profit, 0) }}</span>
              </button>
            </template>
          </div>
        </template>

        <div v-else class="period-card-grid" :class="{ 'period-card-grid-year': !isMonthView }">
          <button
            v-for="item in periodCards"
            :key="item.key"
            type="button"
            :class="[
              'period-card',
              `is-${getPeriodCardTone(item)}`,
              { selected: item.selected, current: item.current, disabled: item.profit === null || item.profit === undefined },
            ]"
            @click="handleCalendarSelect(item)"
          >
            <span class="period-card-label">{{ item.label }}</span>
            <AmountText
              tag="strong"
              :value="item.profit === null || item.profit === undefined ? '--' : formatSignedCurrency(item.profit)"
              :tone="getTone(Number(item.profit ?? 0))"
            />
            <span class="period-card-side">
              {{ item.profitRate === null || item.profitRate === undefined ? '--' : formatRate(item.profitRate) }}
            </span>
          </button>
        </div>
      </section>

      <section class="fund-profit-card" aria-label="收益贡献榜">
        <header class="fund-profit-card-head">
          <div>
            <strong>{{ contributionTitle }}</strong>
            <p>按当前选中范围收益排序</p>
          </div>
          <span class="card-side-text">Top {{ contributors.length }}</span>
        </header>

        <p v-if="contributors.length === 0" class="empty-text">当前范围暂无收益贡献数据</p>

        <template v-else>
          <article v-for="item in contributors" :key="item.positionId" class="contribution-item">
            <div class="contribution-row">
              <div class="contribution-title">
                <strong>{{ item.productName }}</strong>
                <span>{{ item.accountName }} · {{ formatHoldingQuantity(item.holdingQuantity) }}</span>
              </div>
              <div class="contribution-value">
                <AmountText
                  tag="strong"
                  :value="formatSignedCurrency(item.contributionAmount)"
                  :tone="getTone(item.contributionAmount)"
                />
                <AmountText
                  tag="span"
                  :value="formatRate(item.contributionRate)"
                  :tone="getTone(item.contributionRate)"
                />
              </div>
            </div>
            <div class="contribution-track">
              <span
                class="contribution-track-fill"
                :class="`is-${getTone(Number(item.contributionAmount ?? 0))}`"
                :style="getContributionBarStyle(item)"
              ></span>
            </div>
          </article>
        </template>
      </section>

      <section class="fund-profit-card" aria-label="基金收益明细">
        <header class="fund-profit-card-head">
          <div>
            <strong>{{ detailTitle }}</strong>
            <p>点击基金可进入资产详情页查看持仓与交易</p>
          </div>
          <span class="card-side-text">跟随 {{ selectionLabel }}</span>
        </header>

        <div class="detail-filter-row">
          <button
            v-for="item in detailFilters"
            :key="item.value"
            type="button"
            :class="['detail-filter', { active: selectedDetailFilter === item.value }]"
            @click="selectedDetailFilter = item.value"
          >
            {{ item.label }}
          </button>
        </div>

        <p v-if="visibleDetails.length === 0" class="empty-text">当前筛选下暂无基金收益明细</p>

        <template v-else>
          <button
            v-for="item in visibleDetails"
            :key="item.positionId"
            type="button"
            class="detail-row"
            @click="openDetail(item)"
          >
            <div class="detail-main">
              <strong>{{ item.productName }}</strong>
              <span>{{ item.accountName }} · {{ formatHoldingQuantity(item.holdingQuantity) }} · 净值 {{ formatNumber(item.netValue, 4) }}</span>
            </div>
            <div class="detail-side">
              <AmountText
                tag="strong"
                :value="formatSignedCurrency(item.periodProfit)"
                :tone="getTone(item.periodProfit)"
              />
              <span>{{ formatCurrency(item.holdingAmount) }}</span>
            </div>
          </button>
        </template>
      </section>

      <RouterLink class="analysis-link" :to="analysisLink">
        查看完整收益分析
      </RouterLink>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
