<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import { getUsMarketOverview, type UsMarketChartPoint, type UsMarketIndexQuote, type UsMarketOverview } from '@/api/modules/finance'

const marketOverview = ref<UsMarketOverview | null>(null)
const isLoading = ref(false)
const isRefreshing = ref(false)
const errorMessage = ref('')
const isDark = ref(false)
let refreshTimer: ReturnType<typeof window.setTimeout> | null = null
let mediaQuery: MediaQueryList | null = null
let echartsLib: (typeof import('echarts')) | null = null
const chartRefs = new Map<string, HTMLDivElement>()
const chartInstances = new Map<string, ECharts>()

const indices = computed<UsMarketIndexQuote[]>(() => (
  (marketOverview.value?.indices ?? []).map((item) => ({
    ...item,
    chartPoints: normalizeChartPoints(item.chartPoints),
  }))
))
const pageFootText = computed(() => {
  if (!marketOverview.value?.updatedAt) {
    return '等待行情更新'
  }

  return `更新时间 ${formatTime(marketOverview.value.updatedAt)}`
})

function getStatusText(item: UsMarketIndexQuote) {
  if (item.stale) {
    return '使用最近一次成功缓存'
  }
  return '实时更新'
}

function getRefreshInterval() {
  return Math.max(marketOverview.value?.autoRefreshIntervalSeconds ?? 60, 30) * 1000
}

function scheduleRefresh() {
  clearRefreshTimer()
  refreshTimer = window.setTimeout(() => {
    void loadOverview(true)
  }, getRefreshInterval())
}

function clearRefreshTimer() {
  if (refreshTimer !== null) {
    window.clearTimeout(refreshTimer)
    refreshTimer = null
  }
}

async function loadOverview(silent = false) {
  if (silent) {
    isRefreshing.value = true
  } else {
    isLoading.value = true
  }
  errorMessage.value = ''

  try {
    marketOverview.value = await getUsMarketOverview()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '美股行情加载失败'
  } finally {
    isLoading.value = false
    isRefreshing.value = false
    scheduleRefresh()
  }
}

function normalizeChartPoints(points: unknown): UsMarketChartPoint[] {
  if (!Array.isArray(points)) {
    return []
  }

  return points
    .map((point) => {
      if (!point || typeof point !== 'object') {
        return null
      }

      const candidate = point as Partial<UsMarketChartPoint>
      const label = typeof candidate.label === 'string' ? candidate.label : ''
      const price = Number(candidate.price)
      if (!label || !Number.isFinite(price)) {
        return null
      }

      return {
        label,
        price,
      }
    })
    .filter((point): point is UsMarketChartPoint => point !== null)
}

function formatNumber(value?: number | null, fractionDigits = 2) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })
}

function formatSigned(value?: number | null, fractionDigits = 2) {
  const normalized = Number(value ?? 0)
  const sign = normalized >= 0 ? '+' : ''
  return `${sign}${formatNumber(normalized, fractionDigits)}`
}

function formatChangePercent(value?: number | null) {
  return `${formatSigned(value)}%`
}

function formatTime(value?: string) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--'
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')

  return `${year}年${month}月${day}日 ${hour}:${minute}:${second}`
}

function setChartRef(code: string, element: Element | null) {
  if (element instanceof HTMLDivElement) {
    chartRefs.set(code, element)
    renderCharts()
    return
  }

  chartRefs.delete(code)
  const chart = chartInstances.get(code)
  chart?.dispose()
  chartInstances.delete(code)
}

function updateThemeState() {
  isDark.value = Boolean(mediaQuery?.matches)
}

function handleThemeChange() {
  updateThemeState()
}

async function ensureEcharts() {
  if (!echartsLib) {
    echartsLib = await import('echarts')
  }
  return echartsLib
}

function buildChartOption(item: UsMarketIndexQuote): EChartsCoreOption {
  const isPositive = Number(item.changePercent ?? 0) >= 0
  const lineColor = isPositive ? '#ef4444' : '#16a34a'
  const axisText = isDark.value ? '#8FA3C7' : '#94A3B8'
  const axisLine = isDark.value ? '#253045' : '#CBD5E1'
  const splitLine = isDark.value ? '#1E293B' : '#E2E8F0'
  const areaStart = isPositive ? 'rgba(239,68,68,0.22)' : 'rgba(22,163,74,0.20)'
  const areaEnd = isPositive ? 'rgba(239,68,68,0.04)' : 'rgba(22,163,74,0.04)'

  return {
    animation: false,
    grid: { left: 8, right: 8, top: 12, bottom: 18, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark.value ? '#0F172A' : '#FFFFFF',
      borderColor: isDark.value ? '#253045' : '#E2E8F0',
      textStyle: { color: isDark.value ? '#E2E8F0' : '#0F172A' },
      valueFormatter: (value: number | string) => formatNumber(Number(value ?? 0)),
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: item.chartPoints.map((point) => point.label),
      axisLine: { lineStyle: { color: axisLine } },
      axisTick: { show: false },
      axisLabel: { color: axisText, fontSize: 11, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: axisText, fontSize: 11 },
      splitLine: { lineStyle: { color: splitLine } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: item.chartPoints.map((point) => point.price),
        lineStyle: { width: 3, color: lineColor },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: areaStart },
              { offset: 1, color: areaEnd },
            ],
          },
        },
      },
    ],
  }
}

function renderCharts() {
  if (!echartsLib) return

  for (const item of indices.value) {
    const element = chartRefs.get(item.code)
    if (!element || item.chartPoints.length === 0) {
      continue
    }

    let chart = chartInstances.get(item.code)
    if (!chart) {
      chart = echartsLib.init(element)
      chartInstances.set(item.code, chart)
    }
    chart.setOption(buildChartOption(item), true)
  }
}

function resizeCharts() {
  chartInstances.forEach((chart) => chart.resize())
}

onMounted(async () => {
  mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  updateThemeState()
  mediaQuery.addEventListener('change', handleThemeChange)
  await ensureEcharts()
  void loadOverview()
  window.addEventListener('resize', resizeCharts)
})

watch(indices, async () => {
  await nextTick()
  renderCharts()
}, { deep: true, flush: 'post' })

watch(isDark, () => {
  renderCharts()
})

onBeforeUnmount(() => {
  clearRefreshTimer()
  window.removeEventListener('resize', resizeCharts)
  mediaQuery?.removeEventListener('change', handleThemeChange)
  chartInstances.forEach((chart) => chart.dispose())
  chartInstances.clear()
  chartRefs.clear()
})
</script>

<template>
  <section class="us-market-page" aria-label="美股行情">
    <header class="us-market-header">
      <PageHeader title="美股" back-to="/finance/more-features" back-label="返回更多功能" />
      <button
        class="us-market-refresh"
        type="button"
        :disabled="isRefreshing"
        aria-label="刷新美股行情"
        @click="loadOverview(true)"
      >
        <span :class="['us-market-refresh-icon', { spinning: isRefreshing }]">↻</span>
      </button>
    </header>

    <section class="overview-card" aria-label="美股指数概览">
      <header class="card-head">
        <p>主要指数</p>
        <span>{{ isRefreshing ? '更新中...' : '自动刷新' }}</span>
      </header>
      <p v-if="isLoading && indices.length === 0" class="market-message">正在获取美股实时行情</p>
      <p v-if="errorMessage" class="market-message market-message-error">{{ errorMessage }}</p>
      <div class="market-list" role="list">
        <article v-for="item in indices" :key="item.code" class="market-list-item" role="listitem">
          <div class="market-list-left">
            <strong>{{ item.name }}</strong>
            <p>{{ item.alias }}</p>
          </div>
          <div class="market-list-right">
            <AmountText tag="strong" class="market-list-change" :value="formatChangePercent(item.changePercent)" />
            <span :class="['market-pill', { stale: item.stale }]">{{ getStatusText(item) }}</span>
          </div>
        </article>
      </div>
      <p class="market-foot">{{ pageFootText }}</p>
    </section>

    <section class="chart-section" aria-label="指数趋势图">
      <article v-for="item in indices" :key="`${item.code}-trend`" class="chart-card">
        <header class="card-head">
          <p>{{ item.name }}趋势</p>
          <span>{{ item.code }}</span>
        </header>
        <div :ref="(element) => setChartRef(item.code, element as Element | null)" class="chart-canvas" />
        <p v-if="item.chartPoints.length === 0" class="market-message">暂无可用走势数据</p>
      </article>
    </section>

    <section class="tips-card" aria-label="行情说明">
      <header class="card-head">
        <p>数据说明</p>
        <span>行情聚合</span>
      </header>
      <p>标普500使用实时报价字段生成关键节点图，纳指100使用真实时间序列数据绘制 ECharts 趋势图。</p>
      <p>当外部源暂时异常时，会自动保留最近一次成功抓取的数据，避免页面空白。</p>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
