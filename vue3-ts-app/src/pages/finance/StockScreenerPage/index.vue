<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { ECharts } from 'echarts'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  getLimitUpDownStatistics,
  getMarketStatus,
  getStockScreenResults,
  getStockScreenStatus,
  stopStockScreenRun,
  triggerStockScreenRun,
  type LimitUpDownStatistics,
  type MarketStatus,
  type MarketStatusIndex,
  type StockScreenItem,
  type StockScreenPage,
  type StockScreenRun,
} from '@/api/modules/finance'

type ScreeningRule = {
  key: string
  label: string
  title: string
  description: string
  points: string[]
}

// 选股规则目录：后续新增规则时，在此追加一项即可自动出现在下拉框中。
const screeningRules: ScreeningRule[] = [
  {
    key: 'sunrise-rise',
    label: '旭日东升',
    title: '旭日东升',
    description: '连续下跌末端出现缩量阳线实体反包，捕捉超跌反转的启动信号。',
    points: [
      '近6日至少 4 根阴线，处于连续下跌通道',
      '最后3日累计跌幅不低于 9%，第3根阴线单日跌幅不低于 3%',
      '最后3日连续收阴，次日阳线实体反包且缩量',
    ],
  },
]

const activeRuleKey = ref<string>(screeningRules[0].key)
const criteria = reactive({
  minBearishCount: 4,
  minThreeDayDecline: 9,
  minLastDayDecline: 3,
  requireVolumeUp: false,
  requireNoLowerShadow: false,
  includeChiNext: false,
  includeStar: false,
})
const screenPage = ref<StockScreenPage | null>(null)
const latestRun = ref<StockScreenRun | null>(null)
const limitStatistics = ref<LimitUpDownStatistics | null>(null)
const marketStatus = ref<MarketStatus | null>(null)
const activeLimitTab = ref<'up' | 'down'>('up')
const results = ref<StockScreenItem[]>([])
const isLoading = ref(false)
const isLimitStatisticsLoading = ref(false)
const isMarketStatusLoading = ref(false)
const isLoadingMore = ref(false)
const isSubmittingScan = ref(false)
const isStoppingScan = ref(false)
const pageError = ref('')
const limitStatisticsError = ref('')
const marketStatusError = ref('')
const actionMessage = ref('')
let statusTimer: number | undefined
let waitingForSubmittedScan = false
let submittedAfterRunId = 0
let statusRequestInFlight = false

type KlinePoint = {
  label: string
  open: number
  close: number
  high: number
  low: number
  volume: number
}

// 展开的股票 K 线：一次只展开一只，数据前端直连腾讯行情，按 key 缓存已拉取的点位。
const expandedKey = ref('')
const klineLoadingKey = ref('')
const klineErrorKey = ref('')
const klineChartError = ref('')
const klineCache = new Map<string, KlinePoint[]>()
const klineCharts = new Map<string, ECharts>()
const klineContainers = new Map<string, HTMLElement>()

const STATUS_POLL_INTERVAL = 2000

const cardKey = (item: StockScreenItem) => `${item.signalDate}-${item.stockCode}`

const scanIsRunning = computed(() => latestRun.value?.status === 'running')
const resultTotal = computed(() => screenPage.value?.total || 0)
const activeLimitStocks = computed(() => (
  activeLimitTab.value === 'up'
    ? limitStatistics.value?.limitUps || []
    : limitStatistics.value?.limitDowns || []
))
const primaryMarketIndices = computed<MarketStatusIndex[]>(() => {
  const primaryCodes = new Set(['000001', '399001', '399006', '899050'])
  return (marketStatus.value?.indices || []).filter((item) => primaryCodes.has(item.code))
})
const activeRule = computed(() => (
  screeningRules.find((rule) => rule.key === activeRuleKey.value) || screeningRules[0]
))
const ruleOptions = computed(() => (
  screeningRules.map((rule) => ({ label: rule.label, value: rule.key }))
))
const hasMore = computed(() => results.value.length < resultTotal.value)
const dataTradeDate = computed(() => screenPage.value?.run?.tradeDate || latestRun.value?.tradeDate || '')

onMounted(() => {
  window.addEventListener('focus', handlePageFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  void loadInitialData()
  void loadLimitStatistics()
  void loadMarketStatus()
})

onBeforeUnmount(() => {
  stopStatusPolling()
  window.removeEventListener('focus', handlePageFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  disposeAllKlineCharts()
})

async function loadInitialData() {
  isLoading.value = true
  pageError.value = ''
  try {
    const [status, page] = await Promise.all([
      getStockScreenStatus(),
      getStockScreenResults(buildQuery(1)),
    ])
    latestRun.value = status
    screenPage.value = page
    results.value = page.items || []
    if (status?.status === 'running') {
      scheduleStatusPolling(0)
    }
  } catch (error) {
    pageError.value = toErrorMessage(error, '选股数据加载失败')
  } finally {
    isLoading.value = false
  }
}

async function loadLimitStatistics() {
  if (isLimitStatisticsLoading.value) {
    return
  }
  isLimitStatisticsLoading.value = true
  limitStatisticsError.value = ''
  try {
    limitStatistics.value = await getLimitUpDownStatistics()
  } catch (error) {
    if (!limitStatistics.value) {
      limitStatisticsError.value = toErrorMessage(error, '涨跌停数据加载失败')
    }
  } finally {
    isLimitStatisticsLoading.value = false
  }
}

async function loadMarketStatus() {
  if (isMarketStatusLoading.value) {
    return
  }
  isMarketStatusLoading.value = true
  marketStatusError.value = ''
  try {
    marketStatus.value = await getMarketStatus()
  } catch (error) {
    if (!marketStatus.value) {
      marketStatusError.value = toErrorMessage(error, '大盘状态加载失败')
    }
  } finally {
    isMarketStatusLoading.value = false
  }
}

async function applyRules(options: { preserveMessage?: boolean } = {}) {
  isLoading.value = true
  pageError.value = ''
  if (!options.preserveMessage) {
    actionMessage.value = ''
  }
  try {
    const page = await getStockScreenResults(buildQuery(1))
    screenPage.value = page
    results.value = page.items || []
  } catch (error) {
    pageError.value = toErrorMessage(error, '规则筛选失败')
  } finally {
    isLoading.value = false
  }
}

async function loadMore() {
  if (!screenPage.value || !hasMore.value || isLoadingMore.value) {
    return
  }
  isLoadingMore.value = true
  pageError.value = ''
  try {
    const nextPage = screenPage.value.page + 1
    const page = await getStockScreenResults(buildQuery(nextPage))
    screenPage.value = page
    results.value = [...results.value, ...(page.items || [])]
  } catch (error) {
    pageError.value = toErrorMessage(error, '加载更多结果失败')
  } finally {
    isLoadingMore.value = false
  }
}

async function startFullMarketScan() {
  if (isSubmittingScan.value || scanIsRunning.value) {
    return
  }
  isSubmittingScan.value = true
  pageError.value = ''
  actionMessage.value = ''
  try {
    submittedAfterRunId = latestRun.value?.id || 0
    const submission = await triggerStockScreenRun()
    actionMessage.value = submission.message
    void loadLimitStatistics()
    void loadMarketStatus()
    if (submission.status === 'reused') {
      waitingForSubmittedScan = false
      latestRun.value = await getStockScreenStatus()
      await applyRules({ preserveMessage: true })
      return
    }
    waitingForSubmittedScan = true
    latestRun.value = createOptimisticRunningState(latestRun.value)
    scheduleStatusPolling(0)
  } catch (error) {
    pageError.value = toErrorMessage(error, '全市场扫描提交失败')
  } finally {
    isSubmittingScan.value = false
  }
}

async function stopFullMarketScan() {
  if (!scanIsRunning.value || isStoppingScan.value) {
    return
  }
  isStoppingScan.value = true
  pageError.value = ''
  actionMessage.value = ''
  try {
    const submission = await stopStockScreenRun()
    actionMessage.value = submission.message
    if (submission.status === 'accepted') {
      scheduleStatusPolling(0)
      return
    }
    isStoppingScan.value = false
    latestRun.value = await getStockScreenStatus()
  } catch (error) {
    isStoppingScan.value = false
    pageError.value = toErrorMessage(error, '停止扫描失败，请稍后重试')
  }
}

function handleHeaderScanAction() {
  if (scanIsRunning.value) {
    void stopFullMarketScan()
    return
  }
  void startFullMarketScan()
}

function scheduleStatusPolling(delay = STATUS_POLL_INTERVAL) {
  stopStatusPolling()
  statusTimer = window.setTimeout(() => void pollStatus(), delay)
}

async function pollStatus() {
  statusTimer = undefined
  if (statusRequestInFlight) {
    scheduleStatusPolling()
    return
  }
  statusRequestInFlight = true
  try {
    const wasTrackingScan = waitingForSubmittedScan || latestRun.value?.status === 'running'
    const status = await getStockScreenStatus()
    const waitingForNewRun = waitingForSubmittedScan
      && (!status || status.id <= submittedAfterRunId)
    if (waitingForNewRun) {
      scheduleStatusPolling()
      return
    }

    latestRun.value = status
    if (!status) {
      if (waitingForSubmittedScan) {
        scheduleStatusPolling()
      }
      return
    }
    if (status.status === 'running') {
      scheduleStatusPolling()
      return
    }

    isStoppingScan.value = false
    const hasNewCompletedResult = status.status === 'success'
      && status.id !== screenPage.value?.run?.id
    if (wasTrackingScan || hasNewCompletedResult) {
      waitingForSubmittedScan = false
      actionMessage.value = status.status === 'success'
        ? '全市场扫描完成，已刷新最新结果'
        : status.status === 'canceled'
          ? '全市场扫描已停止，继续显示上一次成功结果'
          : status.errorMessage || '全市场扫描失败，请稍后重试'
      await applyRules({ preserveMessage: true })
      void loadLimitStatistics()
      void loadMarketStatus()
    }
  } catch {
    if (waitingForSubmittedScan || latestRun.value?.status === 'running') {
      scheduleStatusPolling()
    }
  } finally {
    statusRequestInFlight = false
  }
}

function stopStatusPolling() {
  if (statusTimer !== undefined) {
    window.clearTimeout(statusTimer)
    statusTimer = undefined
  }
}

function handlePageFocus() {
  scheduleStatusPolling(0)
  void loadLimitStatistics()
  void loadMarketStatus()
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    scheduleStatusPolling(0)
    void loadLimitStatistics()
    void loadMarketStatus()
  }
}

function createOptimisticRunningState(current: StockScreenRun | null): StockScreenRun {
  return {
    id: current?.id || submittedAfterRunId,
    tradeDate: current?.tradeDate || null,
    triggerName: 'manual-api',
    status: 'running',
    totalStocks: 0,
    processedStocks: 0,
    matchedStocks: 0,
    failedStocks: 0,
    dataSource: current?.dataSource || '公开行情',
    resultMessage: null,
    errorMessage: null,
    startedAt: new Date().toISOString(),
    finishedAt: null,
  }
}

function buildQuery(page: number) {
  return {
    minBearishCount: clampNumber(criteria.minBearishCount, 1, 6, 4),
    minThreeDayDecline: clampNumber(criteria.minThreeDayDecline, 0, 50, 9),
    minLastDayDecline: clampNumber(criteria.minLastDayDecline, 0, 50, 3),
    requireVolumeUp: criteria.requireVolumeUp,
    requireNoLowerShadow: criteria.requireNoLowerShadow,
    includeChiNext: criteria.includeChiNext,
    includeStar: criteria.includeStar,
    page,
    pageSize: 20,
  }
}

function clampNumber(value: number, min: number, max: number, fallback: number) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? Math.min(max, Math.max(min, normalized)) : fallback
}

function formatDate(value?: string | null) {
  if (!value) return '--'
  const parts = value.split('-')
  return parts.length === 3 ? `${parts[0]}.${parts[1]}.${parts[2]}` : value
}

function formatPrice(value?: number | null) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized.toFixed(2) : '--'
}

function formatSignedPercent(value?: number | null) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized)) return '--'
  return `${normalized > 0 ? '+' : ''}${normalized.toFixed(2)}%`
}

function formatIndexValue(value?: number | null) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized.toFixed(2) : '--'
}

function formatTurnover(value?: number | null) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized) || normalized <= 0) return '--'
  if (normalized >= 1_000_000_000_000) return `${(normalized / 1_000_000_000_000).toFixed(2)}万亿`
  return `${(normalized / 100_000_000).toFixed(0)}亿`
}

function formatMarketTime(value?: string | null) {
  if (!value) return '--:--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--:--'
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function marketChangeClass(value?: number | null) {
  const normalized = Number(value)
  return normalized > 0 ? 'positive' : normalized < 0 ? 'negative' : 'neutral'
}

function marketLabel(market: string) {
  return market === 'SH' ? '沪市' : market === 'SZ' ? '深市' : market === 'BJ' ? '北交所' : market
}

// 今日涨幅：信号日收盘相对前一日收盘的百分比。
function itemChangePercent(item: StockScreenItem) {
  const prev = Number(item.previousClose)
  const close = Number(item.signalClose)
  if (!Number.isFinite(prev) || !Number.isFinite(close) || prev <= 0) {
    return null
  }
  return ((close - prev) / prev) * 100
}

async function toggleCard(item: StockScreenItem) {
  const key = cardKey(item)
  if (expandedKey.value === key) {
    disposeKlineChart(key)
    expandedKey.value = ''
    return
  }
  // 一次只展开一只：先销毁上一只的图表实例。
  if (expandedKey.value) {
    disposeKlineChart(expandedKey.value)
  }
  expandedKey.value = key
  await loadKline(item)
}

async function loadKline(item: StockScreenItem) {
  const key = cardKey(item)
  klineErrorKey.value = ''
  if (klineCache.has(key)) {
    await renderKline(key)
    return
  }
  klineLoadingKey.value = key
  try {
    const points = await fetchStockKline(toScreenerSymbol(item.stockCode, item.market))
    if (points.length === 0) {
      throw new Error('未获取到日K数据')
    }
    klineCache.set(key, points)
    // 拉取期间用户可能已收起或切换到别的卡片。
    if (expandedKey.value === key) {
      await renderKline(key)
    }
  } catch (error) {
    if (expandedKey.value === key) {
      klineErrorKey.value = key
      pageError.value = ''
      klineChartError.value = toErrorMessage(error, '日K加载失败')
    }
  } finally {
    if (klineLoadingKey.value === key) {
      klineLoadingKey.value = ''
    }
  }
}

async function renderKline(key: string) {
  const points = klineCache.get(key)
  if (!points || points.length === 0) {
    return
  }
  await nextTick()
  const container = klineContainers.get(key)
  if (!container || expandedKey.value !== key) {
    return
  }
  const echarts = await import('echarts')
  let chart = klineCharts.get(key)
  if (!chart) {
    chart = echarts.init(container)
    klineCharts.set(key, chart)
  }

  const rootStyle = getComputedStyle(document.documentElement)
  const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
  const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
  const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()
  const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
  const splitLine = rootStyle.getPropertyValue('--color-chart-split').trim()
  const dates = points.map((point) => point.label)
  const candleData = points.map((point) => [point.open, point.close, point.low, point.high])
  // 成交量柱子跟随当日 K 线红涨绿跌：收盘 ≥ 开盘为红，否则为绿。
  const volumeData = points.map((point) => ({
    value: point.volume ?? 0,
    itemStyle: { color: point.close >= point.open ? '#DC2626' : '#16A34A' },
  }))
  // 默认展示最近半个月（约 10 个交易日）的行情，K线与成交量共用同一 dataZoom 保持横轴对应。
  const defaultSpan = 10
  const zoomStartIndex = Math.max(0, points.length - defaultSpan)
  const zoomEndIndex = Math.max(0, points.length - 1)

  chart.setOption({
    animation: false,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      confine: true,
      // tooltip 固定停在图表顶部，避免压在光标/手指下方遮挡数据。
      position: (point: number[], _params: unknown, _dom: unknown, _rect: unknown, size: { contentSize: number[] }) => {
        const [pointerX] = point
        const [tooltipWidth] = size.contentSize
        const chartWidth = chart?.getWidth() ?? 0
        const x = Math.min(Math.max(pointerX - tooltipWidth / 2, 0), Math.max(0, chartWidth - tooltipWidth))
        return [x, 0]
      },
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipText },
      formatter: (params: any) => {
        const items = Array.isArray(params) ? params : [params]
        if (items.length === 0) {
          return '--'
        }
        const title = items[0]?.axisValueLabel || items[0]?.axisValue || '--'
        const lines = [title]
        for (const entry of items) {
          if (entry.seriesType === 'candlestick' && Array.isArray(entry.data)) {
            const [, open, close, low, high] = entry.data
            lines.push(`${entry.marker}${entry.seriesName} 开 ${formatKlineNumber(open)} 收 ${formatKlineNumber(close)} 高 ${formatKlineNumber(high)} 低 ${formatKlineNumber(low)}`)
            continue
          }
          if (entry.seriesType === 'bar') {
            lines.push(`${entry.marker}${entry.seriesName} ${formatKlineNumber(entry.value, 0)}`)
          }
        }
        return lines.join('<br/>')
      },
    },
    legend: {
      top: 0,
      left: 0,
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: axisText, fontSize: 11 },
      data: ['日K', '成交量'],
    },
    grid: [
      { left: 42, right: 14, top: 42, height: '54%' },
      { left: 42, right: 14, top: '76%', height: '14%' },
    ],
    xAxis: [
      { type: 'category', data: dates, boundaryGap: true, axisTick: { alignWithLabel: true }, axisLabel: { color: axisText, fontSize: 10 } },
      { type: 'category', gridIndex: 1, data: dates, boundaryGap: true, axisTick: { alignWithLabel: true, show: false }, axisLabel: { show: false } },
    ],
    yAxis: [
      { scale: true, axisLabel: { color: axisText, fontSize: 10 }, splitLine: { lineStyle: { color: splitLine } } },
      { scale: true, gridIndex: 1, axisLabel: { show: false }, axisTick: { show: false }, axisLine: { show: false }, splitLine: { show: false } },
    ],
    dataZoom: [{ type: 'inside', xAxisIndex: [0, 1], startValue: zoomStartIndex, endValue: zoomEndIndex }],
    series: [
      {
        name: '日K',
        type: 'candlestick',
        data: candleData,
        barWidth: '60%',
        itemStyle: {
          color: '#DC2626',
          color0: '#16A34A',
          borderColor: '#DC2626',
          borderColor0: '#16A34A',
        },
      },
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumeData,
        barWidth: '60%',
      },
    ],
  }, true)
  requestAnimationFrame(() => chart?.resize())
}

function setKlineContainer(key: string, el: Element | null) {
  if (el instanceof HTMLElement) {
    klineContainers.set(key, el)
  } else {
    klineContainers.delete(key)
  }
}

function disposeKlineChart(key: string) {
  const chart = klineCharts.get(key)
  if (chart) {
    chart.dispose()
    klineCharts.delete(key)
  }
  if (klineErrorKey.value === key) {
    klineErrorKey.value = ''
  }
}

function disposeAllKlineCharts() {
  for (const chart of klineCharts.values()) {
    chart.dispose()
  }
  klineCharts.clear()
}

// 日K数据前端直连腾讯行情，复用投资详情页的接口口径。
async function fetchStockKline(symbol: string): Promise<KlinePoint[]> {
  const response = await fetch(`https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=${encodeURIComponent(`${symbol},day,,,120,qfq`)}`)
  const data = await response.json()
  const rows = data?.data?.[symbol]?.qfqday || data?.data?.[symbol]?.day || []
  if (!Array.isArray(rows)) {
    return []
  }
  return rows
    .map((row: any) => ({
      label: row?.[0] as string,
      open: Number(row?.[1]),
      close: Number(row?.[2]),
      high: Number(row?.[3]),
      low: Number(row?.[4]),
      volume: Number(row?.[5]),
    }))
    .filter((item: KlinePoint) => item.label && Number.isFinite(item.close))
}

function toScreenerSymbol(code: string, market: string) {
  const prefix = market === 'SH' ? 'sh' : market === 'SZ' ? 'sz' : market === 'BJ' ? 'bj' : (code.startsWith('6') ? 'sh' : 'sz')
  return `${prefix}${code}`
}

function formatKlineNumber(value: unknown, fractionDigits = 2) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized.toFixed(fractionDigits) : '--'
}

function toErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
</script>

<template>
  <section class="stock-screener-page" aria-label="A股选股">
    <PageHeader title="A股选股" back-to="/finance/more-features" back-label="返回更多功能">
      <template #right>
        <button
          v-if="scanIsRunning"
          class="header-scan-button danger"
          type="button"
          :disabled="isStoppingScan"
          @click="handleHeaderScanAction"
        >
          {{ isStoppingScan ? '停止中' : '停止扫描' }}
        </button>
        <CommonHeaderRefreshButton
          v-else
          label="刷新选股数据"
          :loading="isSubmittingScan"
          @click="handleHeaderScanAction"
        />
      </template>
    </PageHeader>

    <section v-if="marketStatus" class="market-status-card" aria-label="大盘状态">
      <header class="market-status-heading">
        <div>
          <strong>大盘状态</strong>
        </div>
        <span class="market-status-time">{{ formatMarketTime(marketStatus.updatedAt) }}</span>
      </header>

      <div class="market-index-grid" aria-label="主要指数">
        <article v-for="index in primaryMarketIndices" :key="index.code">
          <span>{{ index.name }}</span>
          <strong>{{ formatIndexValue(index.value) }}</strong>
          <b :class="marketChangeClass(index.changePercent)">{{ formatSignedPercent(index.changePercent) }}</b>
        </article>
      </div>

      <div class="market-breadth">
        <div class="market-breadth-counts">
          <span><b class="positive">{{ marketStatus.advanceCount }}</b> 上涨</span>
          <span><b class="negative">{{ marketStatus.declineCount }}</b> 下跌</span>
          <span><b>{{ marketStatus.flatCount }}</b> 平盘</span>
        </div>
        <div
          class="market-breadth-track"
          role="img"
          :aria-label="`上涨股票占比 ${marketStatus.advanceRatio.toFixed(1)}%`"
        >
          <span class="advance" :style="{ width: `${marketStatus.advanceRatio}%` }"></span>
        </div>
      </div>

      <footer class="market-status-footer">
        <span>成交额 {{ formatTurnover(marketStatus.turnover) }}</span>
        <span>{{ marketStatus.source }}</span>
      </footer>
    </section>

    <div v-else-if="isMarketStatusLoading" class="market-status-placeholder" aria-live="polite">
      大盘状态加载中…
    </div>
    <div v-else-if="marketStatusError" class="market-status-error" role="alert">
      <span>{{ marketStatusError }}</span>
      <button type="button" @click="loadMarketStatus">重新加载</button>
    </div>

    <section
      v-if="limitStatistics"
      class="limit-statistics-card"
      aria-label="涨跌停数据"
    >
      <header class="limit-statistics-heading">
        <div>
          <strong>涨跌停数据</strong>
        </div>
        <span class="limit-statistics-time">{{ formatMarketTime(limitStatistics.updatedAt) }}</span>
      </header>

      <div class="limit-summary-grid">
        <article class="limit-up-summary">
          <span>涨停</span>
          <div><strong>{{ limitStatistics.limitUpCount }}</strong><b>只</b></div>
        </article>
        <article class="limit-down-summary">
          <span>跌停</span>
          <div><strong>{{ limitStatistics.limitDownCount }}</strong><b>只</b></div>
        </article>
        <article>
          <span>炸板</span>
          <div><strong>{{ limitStatistics.brokenLimitCount }}</strong><b>只</b></div>
        </article>
        <article>
          <span>封板率</span>
          <div><strong>{{ limitStatistics.sealRate.toFixed(1) }}</strong><b>%</b></div>
        </article>
      </div>

      <div class="limit-stock-tabs" role="tablist" aria-label="涨跌停股票列表">
        <button
          id="limit-tab-up"
          :class="{ active: activeLimitTab === 'up' }"
          type="button"
          role="tab"
          aria-controls="limit-stock-panel"
          :aria-selected="activeLimitTab === 'up'"
          @click="activeLimitTab = 'up'"
        >
          涨停 <b>{{ limitStatistics.limitUpCount }}</b>
        </button>
        <button
          id="limit-tab-down"
          :class="{ active: activeLimitTab === 'down' }"
          type="button"
          role="tab"
          aria-controls="limit-stock-panel"
          :aria-selected="activeLimitTab === 'down'"
          @click="activeLimitTab = 'down'"
        >
          跌停 <b>{{ limitStatistics.limitDownCount }}</b>
        </button>
      </div>

      <section
        id="limit-stock-panel"
        class="limit-stock-panel"
        role="tabpanel"
        :aria-labelledby="activeLimitTab === 'up' ? 'limit-tab-up' : 'limit-tab-down'"
        :aria-label="activeLimitTab === 'up' ? '涨停股票' : '跌停股票'"
      >
        <div v-if="activeLimitStocks.length" class="limit-stock-list">
          <div v-for="stock in activeLimitStocks" :key="`${activeLimitTab}-${stock.code}`">
            <span><b>{{ stock.name }}</b><small>{{ stock.code }} · {{ stock.industry || '其他' }}</small></span>
            <strong :class="activeLimitTab === 'up' ? 'positive' : 'negative'">
              {{ formatSignedPercent(stock.changePercent) }}
            </strong>
          </div>
        </div>
        <p v-else>{{ activeLimitTab === 'up' ? '暂无涨停股票' : '暂无跌停股票' }}</p>
      </section>

      <footer class="limit-statistics-footer">
        <span>盘中触及涨停但未封住计为炸板</span>
        <span>{{ limitStatistics.source }}</span>
      </footer>
    </section>

    <div v-else-if="isLimitStatisticsLoading" class="limit-statistics-placeholder" aria-live="polite">
      涨跌停数据加载中…
    </div>
    <div v-else-if="limitStatisticsError" class="limit-statistics-error" role="alert">
      <span>{{ limitStatisticsError }}</span>
      <button type="button" @click="loadLimitStatistics">重新加载</button>
    </div>

    <p v-if="actionMessage" class="page-message" aria-live="polite">{{ actionMessage }}</p>
    <p v-if="pageError" class="page-message error" role="alert">{{ pageError }}</p>

    <section class="rule-panel">
      <div class="rule-panel-heading">
        <div>
          <strong>选股规则</strong>
        </div>
      </div>

      <CommonSelect v-model="activeRuleKey" label="规则" :options="ruleOptions" />

      <div class="rule-strategy" :aria-label="`${activeRule.title}策略说明`">
        <h3>{{ activeRule.title }}</h3>
        <p>{{ activeRule.description }}</p>
        <ul>
          <li v-for="point in activeRule.points" :key="point">{{ point }}</li>
        </ul>
      </div>

      <CommonSwitch v-model="criteria.includeChiNext" label="包含创业板（300/301）" />
      <CommonSwitch v-model="criteria.includeStar" label="包含科创板（688）" />

      <button class="apply-button" type="button" :disabled="isLoading" @click="applyRules()">
        {{ isLoading ? '筛选中…' : '搜索匹配股票' }}
      </button>
    </section>

    <CommonLoading v-if="isLoading && results.length === 0" text="正在读取全市场指标..." />

    <section v-else class="result-section" aria-label="选股结果">
      <div class="result-heading">
        <div>
          <span>选股结果</span>
          <h2>{{ resultTotal }} 只股票</h2>
        </div>
        <span v-if="dataTradeDate" class="trade-date">{{ formatDate(dataTradeDate) }}</span>
      </div>

      <article
        v-for="item in results"
        :key="`${item.signalDate}-${item.stockCode}`"
        :class="['stock-card', { expanded: expandedKey === `${item.signalDate}-${item.stockCode}` }]"
      >
        <header
          role="button"
          tabindex="0"
          :aria-expanded="expandedKey === `${item.signalDate}-${item.stockCode}`"
          :aria-label="`${item.stockName} 日K走势`"
          @click="toggleCard(item)"
          @keydown.enter.prevent="toggleCard(item)"
          @keydown.space.prevent="toggleCard(item)"
        >
          <div class="stock-identity">
            <span class="market-badge">{{ marketLabel(item.market) }}</span>
            <div>
              <h3>{{ item.stockName }}</h3>
              <p>{{ item.stockCode }}</p>
            </div>
          </div>
          <div class="stock-card-trailing">
            <div class="stock-price">
              <strong>{{ formatPrice(item.signalClose) }}</strong>
              <span :class="marketChangeClass(itemChangePercent(item))">{{ formatSignedPercent(itemChangePercent(item)) }}</span>
            </div>
            <svg class="expand-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path d="m7 10 5 5 5-5" />
            </svg>
          </div>
        </header>

        <div v-if="expandedKey === `${item.signalDate}-${item.stockCode}`" class="kline-panel">
          <div
            v-show="klineLoadingKey !== `${item.signalDate}-${item.stockCode}` && klineErrorKey !== `${item.signalDate}-${item.stockCode}`"
            :ref="(el) => setKlineContainer(`${item.signalDate}-${item.stockCode}`, el as Element | null)"
            class="kline-chart"
          ></div>
          <div v-if="klineLoadingKey === `${item.signalDate}-${item.stockCode}`" class="kline-status" aria-live="polite">
            日K加载中…
          </div>
          <div v-else-if="klineErrorKey === `${item.signalDate}-${item.stockCode}`" class="kline-status error" role="alert">
            <span>{{ klineChartError }}</span>
            <button type="button" @click="loadKline(item)">重试</button>
          </div>
        </div>
      </article>

      <div v-if="results.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 19V9m5 10V5m5 14v-7m5 7V3M3 19h18" />
        </svg>
        <strong>{{ screenPage?.run ? '暂无符合当前规则的股票' : '还没有可用的扫描数据' }}</strong>
        <p>{{ screenPage?.run ? '可以适当降低跌幅阈值或取消优选条件' : '点击右上角刷新按钮启动首次全市场扫描' }}</p>
      </div>

      <button v-if="hasMore" class="load-more-button" type="button" :disabled="isLoadingMore" @click="loadMore">
        {{ isLoadingMore ? '加载中…' : `加载更多（已显示 ${results.length} / ${resultTotal}）` }}
      </button>
    </section>

    <p class="risk-note">筛选结果仅基于公开历史行情和技术形态，不构成投资建议。</p>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
