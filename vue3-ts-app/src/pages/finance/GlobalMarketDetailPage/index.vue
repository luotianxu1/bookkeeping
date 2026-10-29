<script setup lang="ts">
// 全球行情详情页：展示品种实时报价卡片与K线蜡烛图。东财统一主源（经服务端代理），商品/中证红利分钟K与美债日K走补充源。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { ECharts, EChartsCoreOption } from 'echarts'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonSkeleton from '@/components/common/CommonSkeleton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  getGlobalMarketKline,
  getGlobalMarketQuoteBySymbol,
  type GlobalMarketKLineItem,
  type GlobalMarketQuote,
} from '@/api/modules/finance'
import { useTheme } from '@/utils/theme'

const PERIOD_OPTIONS = [
  { key: 'm1', label: '分时', bars: 600 },
  { key: 'm5', label: '5分', bars: 400 },
  { key: 'm15', label: '15分', bars: 400 },
  { key: 'm30', label: '30分', bars: 300 },
  { key: 'm60', label: '60分', bars: 250 },
  { key: 'day', label: '日K', bars: 120 },
  { key: 'week', label: '周K', bars: 150 },
  { key: 'month', label: '月K', bars: 120 },
  { key: 'year', label: '年K', bars: 50 },
] as const

// 各品种的计价单位
const UNIT_MAP: Record<string, string> = {
  hf_OIL: '美元/桶',
  hf_XAU: '美元/盎司',
  hf_XAG: '美元/盎司',
  DINIW: '点',
  US10Y: '%',
  N225: '点',
  KS11: '点',
  NDX100: '点',
  SPX: '点',
  SH000001: '点',
  NDX: '点',
  CSI500: '点',
  CSI_DIVIDEND: '点',
}

const route = useRoute()
const { isDark } = useTheme()

const symbol = computed(() => String(route.params.symbol ?? 'hf_OIL'))
const quote = ref<GlobalMarketQuote | null>(null)
const klineItems = ref<GlobalMarketKLineItem[]>([])
const selectedPeriod = ref<string>('')
const isRefreshing = ref(false)
const isInitialLoading = ref(true)
const loadError = ref('')

const isLinePeriod = computed(() => selectedPeriod.value === 'm1')
const unit = computed(() => UNIT_MAP[symbol.value] ?? '')

function barsForPeriod(period: string) {
  return PERIOD_OPTIONS.find((option) => option.key === period)?.bars ?? 120
}

let echartsLib: (typeof import('echarts')) | null = null
let chartInstance: ECharts | null = null
const chartEl = ref<HTMLDivElement | null>(null)

const pageTitle = computed(() => quote.value?.name || symbol.value)

function toneClass(item: GlobalMarketQuote | null) {
  if (!item || item.change == null) {
    return ''
  }
  return item.change >= 0 ? 'up' : 'down'
}

function formatNumber(value?: number | null) {
  if (value == null || !Number.isFinite(value)) {
    return '--'
  }
  return value.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatChange(item: GlobalMarketQuote) {
  if (item.change == null || item.changePercent == null) {
    return '--'
  }
  const sign = item.change >= 0 ? '+' : ''
  return `${sign}${formatNumber(item.change)}  ${sign}${item.changePercent.toFixed(2)}%`
}

function formatQuoteTime(item: GlobalMarketQuote) {
  if (!item.quoteDate) {
    return ''
  }
  return item.quoteTime ? `${item.quoteDate} ${item.quoteTime}` : item.quoteDate
}

async function refresh() {
  if (isRefreshing.value) {
    return
  }
  isRefreshing.value = true
  try {
    const [quoteData, klineData] = await Promise.all([
      getGlobalMarketQuoteBySymbol(symbol.value),
      getGlobalMarketKline(symbol.value, selectedPeriod.value, barsForPeriod(selectedPeriod.value)),
    ])
    loadError.value = ''
    quote.value = quoteData ?? null
    klineItems.value = klineData
    await nextTick()
    renderChart()
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '行情加载失败'
  } finally {
    isRefreshing.value = false
    isInitialLoading.value = false
  }
}

function selectPeriod(period: string) {
  if (selectedPeriod.value === period) {
    return
  }
  selectedPeriod.value = period
  void refresh()
}

function buildChartOption(): EChartsCoreOption | null {
  const items = klineItems.value.filter((item) => item.close != null && item.open != null)
  if (items.length < 2) {
    return null
  }
  const rootStyle = getComputedStyle(document.documentElement)
  const upColor = rootStyle.getPropertyValue('--color-danger').trim() || '#dc2626'
  const downColor = rootStyle.getPropertyValue('--color-success').trim() || '#16a34a'
  const axisColor = rootStyle.getPropertyValue('--color-chart-axis').trim()
  const brandColor = rootStyle.getPropertyValue('--color-brand').trim() || upColor

  const dates = items.map((item) => item.date)
  // ECharts 蜡烛图数据格式：[open, close, low, high]
  const values = items.map((item) => [
    item.open ?? 0,
    item.close ?? 0,
    item.low ?? 0,
    item.high ?? 0,
  ])

  // 周期对应的横轴标签格式：分钟周期只显示时分，日线只显示月日
  const axisLabelFormatter = (value: string) => {
    if (selectedPeriod.value === 'm1' || selectedPeriod.value === 'm5'
      || selectedPeriod.value === 'm15' || selectedPeriod.value === 'm30'
      || selectedPeriod.value === 'm60') {
      return value.includes(' ') ? value.slice(11, 16) : value
    }
    if (selectedPeriod.value === 'month' || selectedPeriod.value === 'year') {
      return value
    }
    return value.slice(5)
  }

  const series = isLinePeriod.value
    ? [{
        type: 'line',
        showSymbol: false,
        data: items.map((item) => item.close ?? 0),
        lineStyle: { width: 1.5, color: brandColor },
        areaStyle: { color: brandColor, opacity: 0.1 },
      }]
    : [{
        type: 'candlestick',
        data: values,
        itemStyle: {
          color: upColor,
          color0: downColor,
          borderColor: upColor,
          borderColor0: downColor,
        },
      }]

  return {
    animation: false,
    grid: { left: 8, right: 8, top: 16, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: axisColor } },
      axisTick: { show: false },
      axisLabel: {
        color: axisColor,
        fontSize: 10,
        formatter: axisLabelFormatter,
        interval: Math.max(0, Math.floor(dates.length / 6) - 1),
      },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: axisColor, fontSize: 10 },
      splitLine: { lineStyle: { color: axisColor, opacity: 0.3 } },
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100,
      },
    ],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      confine: true,
    },
    series,
  }
}

function renderChart() {
  const option = buildChartOption()
  if (!option || !chartEl.value) {
    return
  }
  if (!echartsLib) {
    return
  }
  if (!chartInstance) {
    chartInstance = echartsLib.init(chartEl.value)
  }
  chartInstance.setOption(option, true)
}

function onResize() {
  chartInstance?.resize()
}

onMounted(async () => {
  echartsLib = await import('echarts')
  selectedPeriod.value = 'day'
  await refresh()
  window.addEventListener('resize', onResize)
})

watch(isDark, () => {
  renderChart()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <section class="global-market-detail-page" aria-label="全球行情详情">
    <header class="global-market-detail-header">
      <PageHeader :title="pageTitle" back-to="/finance/global-market" back-label="返回全球行情">
        <template #right>
          <CommonHeaderRefreshButton
            label="刷新行情"
            :loading="isRefreshing"
            @click="refresh"
          />
        </template>
      </PageHeader>
    </header>

    <div v-if="loadError" class="detail-error" role="alert">
      <span>{{ loadError }}</span>
      <button type="button" @click="refresh">重新加载</button>
    </div>

    <div v-else-if="isInitialLoading" class="detail-loading">
      <CommonSkeleton variant="card" />
    </div>

    <article v-if="quote" class="quote-card" :class="toneClass(quote)">
      <header class="quote-card-head">
        <span class="quote-name">{{ quote.name }}</span>
        <span class="quote-code">{{ quote.symbol }}</span>
      </header>

      <p class="quote-price">{{ formatNumber(quote.price) }} <small v-if="unit">{{ unit }}</small></p>
      <p class="quote-change">{{ formatChange(quote) }}</p>

      <div class="quote-detail">
        <span>今开 <b>{{ formatNumber(quote.open) }}</b></span>
        <span>昨收 <b>{{ formatNumber(quote.preClose) }}</b></span>
        <span>最高 <b>{{ formatNumber(quote.high) }}</b></span>
        <span>最低 <b>{{ formatNumber(quote.low) }}</b></span>
      </div>

      <footer class="quote-card-foot">
        <time>{{ formatQuoteTime(quote) }}</time>
        <span>{{ quote.dataSource }}</span>
      </footer>
    </article>

    <section class="kline-card" aria-label="K线图">
      <header class="kline-head">
        <h2>K线</h2>
        <div class="range-tabs" role="tablist" aria-label="K线周期">
          <button
            v-for="option in PERIOD_OPTIONS"
            :key="option.key"
            type="button"
            role="tab"
            :class="{ active: selectedPeriod === option.key }"
            :aria-selected="selectedPeriod === option.key"
            @click="selectPeriod(option.key)"
          >
            {{ option.label }}
          </button>
        </div>
      </header>
      <div ref="chartEl" class="kline-chart" />
      <p v-if="!loadError && !isInitialLoading && klineItems.length === 0 && !isRefreshing" class="kline-empty">
        暂无K线数据
      </p>
    </section>

    <footer class="detail-foot">
      数据来源：东方财富为主（经服务端代理），商品与A股分钟K来自新浪，中证红利K线来自腾讯，美债收益率日K来自美联储FRED。分时为最近交易时段，分钟K约含近数日数据，日K历史可回溯数十年，周/月/年K由数据源聚合。数据可能延迟，不构成投资建议。
    </footer>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
