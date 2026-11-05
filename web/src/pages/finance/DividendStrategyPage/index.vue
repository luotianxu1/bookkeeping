<script setup lang="ts">
// 红利策略页：红利低波ETF（512890）年线（MA250）择时策略。
// 每次进入页面拉取最新前复权日K并在本地重算：当前状态、触发价、信号事件与三策略回测对比。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getQfqDailyKline, type KlinePoint } from '@/api/modules/market'
import { useTheme } from '@/utils/theme'
import { getErrorMessage, isCancelRequest } from '@/api/request'
import { useRequestAbort } from '@/composables/useRequestAbort'
import {
  calcMa250,
  currentPosition,
  type StrategyParams,
} from './strategy'

/** 标的：红利低波ETF（华泰柏瑞，跟踪中证红利低波动指数） */
const TARGET_SYMBOL = 'sh512890'
const TARGET_NAME = '红利低波ETF'
const TARGET_CODE = '512890'
const KLINE_DATALEN = 640

const BAND_OPTIONS = [0, 0.01, 0.02, 0.03]
const CONFIRM_OPTIONS = [1, 3, 5]

const { isDark } = useTheme()
const { signalFor } = useRequestAbort()

const klineRows = ref<KlinePoint[]>([])
const isLoading = ref(false)
const isRefreshing = ref(false)
const loadError = ref('')
const band = ref(0.02)
const confirmDays = ref(5)

let echartsLib: (typeof import('@/utils/echarts')) | null = null
let chartIns: ECharts | null = null
const chartRef = ref<HTMLDivElement | null>(null)

const params = computed<StrategyParams>(() => ({ band: band.value, confirmDays: confirmDays.value }))

const dates = computed(() => klineRows.value.map((row) => row['日期']))
const closes = computed(() => klineRows.value.map((row) => row['收盘']))
const maSeries = computed(() => calcMa250(closes.value))

const position = computed(() => currentPosition(closes.value, maSeries.value, params.value))

const latestRow = computed(() => klineRows.value[klineRows.value.length - 1] ?? null)
const lastTradeDate = computed(() => latestRow.value?.['日期'] ?? '--')

const zoneText = computed(() => {
  switch (position.value.zone) {
    case 'buy':
      return '买入区'
    case 'sell-watch':
      return '止盈观察区'
    case 'neutral':
      return '中性区'
    default:
      return '数据不足'
  }
})

const zoneHint = computed(() => {
  switch (position.value.zone) {
    case 'buy':
      return '价格低于买入触发线，可分批买入'
    case 'sell-watch':
      return '价格高于卖出触发线，可考虑分批止盈'
    case 'neutral':
      return '价格贴近年线，维持不动'
    default:
      return '历史数据不足 250 个交易日，无法计算年线'
  }
})

const deviationText = computed(() => {
  if (position.value.zone === 'unknown') {
    return '--'
  }
  const pct = position.value.deviation * 100
  return `${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%`
})

const chartRange = computed(() => {
  const first = maSeries.value.findIndex((value) => value !== null)
  if (first < 0) {
    return { dates: [] as string[], closes: [] as number[], ma: [] as number[], buy: [] as number[], sell: [] as number[] }
  }
  const maValues: number[] = []
  const buyValues: number[] = []
  const sellValues: number[] = []
  for (let i = first; i < maSeries.value.length; i += 1) {
    const maValue = maSeries.value[i] as number
    maValues.push(Number(maValue.toFixed(4)))
    buyValues.push(Number((maValue * (1 - params.value.band)).toFixed(4)))
    sellValues.push(Number((maValue * (1 + params.value.band)).toFixed(4)))
  }
  return {
    dates: dates.value.slice(first),
    closes: closes.value.slice(first),
    ma: maValues,
    buy: buyValues,
    sell: sellValues,
  }
})

function formatPrice(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '--'
  }
  return value.toFixed(4)
}

async function ensureEcharts() {
  if (!echartsLib) {
    echartsLib = await import('@/utils/echarts')
  }
  return echartsLib
}

function renderChart() {
  const range = chartRange.value
  if (!chartRef.value || !echartsLib || range.dates.length === 0) {
    return
  }
  if (!chartIns) {
    chartIns = echartsLib.init(chartRef.value)
  }
  const rootStyle = getComputedStyle(document.documentElement)
  const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
  const axisLine = rootStyle.getPropertyValue('--color-chart-axis-strong').trim()
  const splitLine = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
  const priceColor = rootStyle.getPropertyValue('--color-chart-gold').trim()
  const maColor = rootStyle.getPropertyValue('--color-brand-strong').trim()
  const buyColor = rootStyle.getPropertyValue('--color-danger').trim()
  const sellColor = rootStyle.getPropertyValue('--color-success').trim()
  const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
  const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
  const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()

  const option: EChartsCoreOption = {
    animation: false,
    grid: { left: 8, right: 8, top: 18, bottom: 18, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipText },
      valueFormatter: (value: unknown) => formatPrice(Number(value)),
    },
    legend: {
      show: true,
      bottom: 0,
      icon: 'rect',
      itemWidth: 12,
      itemHeight: 3,
      textStyle: { color: axisText, fontSize: 11 },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: range.dates,
      axisLine: { lineStyle: { color: axisLine } },
      axisTick: { show: false },
      axisLabel: { color: axisText, fontSize: 11, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: axisText, fontSize: 11, formatter: (value: number) => value.toFixed(2) },
      splitLine: { lineStyle: { color: splitLine } },
    },
    series: [
      {
        name: '收盘价',
        type: 'line',
        data: range.closes,
        showSymbol: false,
        lineStyle: { color: priceColor, width: 1.5 },
        itemStyle: { color: priceColor },
        emphasis: { focus: 'series' },
      },
      {
        name: '年线MA250',
        type: 'line',
        data: range.ma,
        showSymbol: false,
        lineStyle: { color: maColor, width: 1.5, type: 'dashed' },
        itemStyle: { color: maColor },
        emphasis: { focus: 'series' },
      },
      {
        name: '买入触发线',
        type: 'line',
        data: range.buy,
        showSymbol: false,
        lineStyle: { color: buyColor, width: 1, opacity: 0.7 },
        itemStyle: { color: buyColor },
        emphasis: { focus: 'series' },
      },
      {
        name: '卖出触发线',
        type: 'line',
        data: range.sell,
        showSymbol: false,
        lineStyle: { color: sellColor, width: 1, opacity: 0.7 },
        itemStyle: { color: sellColor },
        emphasis: { focus: 'series' },
      },
    ],
  }
  chartIns.setOption(option, true)
}

function onResize() {
  chartIns?.resize()
}

async function loadKline(options: { silent?: boolean } = {}) {
  const { silent = false } = options
  if (silent) {
    isRefreshing.value = true
  } else {
    isLoading.value = true
  }
  loadError.value = ''
  try {
    const data = await getQfqDailyKline(TARGET_SYMBOL, KLINE_DATALEN, { signal: signalFor() })
    klineRows.value = data.kline ?? []
  } catch (error) {
    if (isCancelRequest(error)) {
      return
    }
    loadError.value = getErrorMessage(error, '行情数据加载失败')
  } finally {
    isLoading.value = false
    isRefreshing.value = false
  }
}

onMounted(async () => {
  await loadKline()
  await ensureEcharts()
  renderChart()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chartIns?.dispose()
  chartIns = null
})

watch([klineRows, band, confirmDays, isDark], () => {
  renderChart()
})
</script>

<template>
  <section class="dividend-strategy-page" aria-label="红利策略">
    <header class="page-head">
      <PageHeader title="红利策略" back-to="/finance/more-features" back-label="返回更多功能" :prefer-back-to="true">
        <template #right>
          <CommonHeaderRefreshButton
            label="重新计算"
            :loading="isRefreshing"
            @click="loadKline({ silent: true })"
          />
        </template>
      </PageHeader>
    </header>

    <section class="target-card" aria-label="标的与数据口径">
      <div class="target-info">
        <strong>{{ TARGET_NAME }}</strong>
        <span>{{ TARGET_CODE }}</span>
      </div>
      <p class="target-meta">每次进入页面拉取最新前复权日K并本地重算 · 数据 {{ lastTradeDate }}</p>
    </section>

    <p v-if="isLoading && klineRows.length === 0" class="page-message">正在拉取行情并计算年线信号…</p>
    <p v-if="loadError" class="page-message page-message-error">{{ loadError }}</p>

    <template v-if="klineRows.length > 0">
      <section class="status-card" :class="`zone-${position.zone}`" aria-label="当前状态">
        <header class="card-head">
          <p>当前状态</p>
          <span class="zone-badge">{{ zoneText }}</span>
        </header>
        <div class="metric-grid">
          <article class="metric-item">
            <p class="metric-label">最新收盘</p>
            <strong>{{ formatPrice(position.price) }}</strong>
          </article>
          <article class="metric-item">
            <p class="metric-label">年线 MA250</p>
            <strong>{{ formatPrice(position.ma) }}</strong>
          </article>
          <article class="metric-item">
            <p class="metric-label">相对年线偏离</p>
            <strong>{{ deviationText }}</strong>
          </article>
        </div>
        <p class="zone-hint">{{ zoneHint }}</p>
      </section>

      <section class="trigger-card" aria-label="触发价">
        <header class="card-head">
          <p>触发价（缓冲带 {{ (band * 100).toFixed(0) }}% · 确认 {{ confirmDays }} 日）</p>
        </header>
        <div class="trigger-grid">
          <article class="trigger-item trigger-buy">
            <p class="metric-label">买入触发线（年线下方）</p>
            <strong>{{ formatPrice(position.buyLine) }}</strong>
          </article>
          <article class="trigger-item trigger-sell">
            <p class="metric-label">卖出触发线（年线上方）</p>
            <strong>{{ formatPrice(position.sellLine) }}</strong>
          </article>
        </div>
        <p class="zone-hint">年线±{{ (band * 100).toFixed(0) }}% 区间内视为中性，不做操作</p>
      </section>

      <section class="param-card" aria-label="策略参数">
        <header class="card-head">
          <p>策略参数</p>
        </header>
        <div class="param-row">
          <span class="param-label">缓冲带</span>
          <div class="param-options">
            <button
              v-for="option in BAND_OPTIONS"
              :key="option"
              type="button"
              :class="{ active: band === option }"
              @click="band = option"
            >
              {{ (option * 100).toFixed(0) }}%
            </button>
          </div>
        </div>
        <div class="param-row">
          <span class="param-label">确认天数</span>
          <div class="param-options">
            <button
              v-for="option in CONFIRM_OPTIONS"
              :key="option"
              type="button"
              :class="{ active: confirmDays === option }"
              @click="confirmDays = option"
            >
              {{ option }} 日
            </button>
          </div>
        </div>
      </section>

      <section class="chart-card" aria-label="价格与年线走势">
        <p class="card-title">价格与年线走势（前复权）</p>
        <div ref="chartRef" class="strategy-chart" />
      </section>

      <p class="page-footnote">
        数据口径：前复权日K（腾讯行情，经行情中心代理）；策略计算在本机浏览器完成。本页仅为量化参考，不构成投资建议。
      </p>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
