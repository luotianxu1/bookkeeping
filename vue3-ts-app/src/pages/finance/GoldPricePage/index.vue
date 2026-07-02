<script setup lang="ts">
// 金价页：使用 ECharts 折线图展示价格走势，并支持暗黑模式配色。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import type { GoldPriceRange } from '@/api/modules/finance'
import { refreshGoldPriceCache as refreshRealtimeGoldPriceCache } from '@/utils/gold-price-cache'
import { getCachedGoldPrice, goldPriceCacheState, refreshGoldPriceCache } from '@/utils/gold-price-detail-cache'
import { useTheme } from '@/utils/theme'

type TrendKey = '1日' | '7日' | '30日' | '1年'

const activeTrend = ref<TrendKey>('1日')
const trendOptions: TrendKey[] = ['1日', '7日', '30日', '1年']

const { isDark } = useTheme()
const chartRef = ref<HTMLDivElement | null>(null)
const isRefreshingPrice = ref(false)
const isLoadingTrend = ref(false)
const goldPriceError = ref('')
let echartsLib: (typeof import('echarts')) | null = null
let chartIns: ECharts | null = null

const trendRangeMap: Record<TrendKey, GoldPriceRange> = {
  '1日': '1d',
  '7日': '7d',
  '30日': '30d',
  '1年': '1y',
}

const activeRange = computed(() => trendRangeMap[activeTrend.value])
const currentGoldPrice = computed(() => (
  getCachedGoldPrice(activeRange.value) ?? getCachedGoldPrice('1d')
))
const hasSpotGold = computed(() => (
  Number.isFinite(Number(currentGoldPrice.value?.spotGold?.price))
  && Number(currentGoldPrice.value?.spotGold?.price) > 0
))
const hasLondonGold = computed(() => (
  Number.isFinite(Number(currentGoldPrice.value?.londonGold?.price))
  && Number(currentGoldPrice.value?.londonGold?.price) > 0
))
const chartPoints = computed(() => (
  getCachedGoldPrice(activeRange.value)?.chartPoints ?? []
))
const isLoadingGoldPrice = computed(() => (
  activeRange.value === '1d' && !currentGoldPrice.value && goldPriceCacheState.isBootstrapping
))

const trendData = computed(() => ({
  x: chartPoints.value.map((item) => item.label),
  y: chartPoints.value.map((item) => item.price),
}))

const quoteRows = computed(() => {
  if (!currentGoldPrice.value?.stats) return []

  return [
    { label: '今日开盘', value: formatMoney(currentGoldPrice.value.stats.openPrice) },
    { label: '最高', value: formatMoney(currentGoldPrice.value.stats.highPrice) },
    { label: '最低', value: formatMoney(currentGoldPrice.value.stats.lowPrice) },
    { label: '买入参考', value: formatMoney(currentGoldPrice.value.stats.buyPrice) },
    { label: '卖出参考', value: formatMoney(currentGoldPrice.value.stats.sellPrice) },
  ].filter((item) => item.value !== '--')
})

const showPriceFallback = computed(() => (
  !isLoadingGoldPrice.value && !hasSpotGold.value && !hasLondonGold.value
))

const spotDeltaText = computed(() => (
  hasSpotGold.value
    ? formatDelta(currentGoldPrice.value?.spotGold?.change, currentGoldPrice.value?.spotGold?.changePercent)
    : '--'
))

const londonDeltaText = computed(() => (
  hasLondonGold.value
    ? formatDelta(currentGoldPrice.value?.londonGold?.change, currentGoldPrice.value?.londonGold?.changePercent)
    : '--'
))

const marketFootText = computed(() => {
  if (!currentGoldPrice.value || (!hasSpotGold.value && !hasLondonGold.value)) {
    return '暂无可靠行情数据'
  }

  return `更新时间 ${formatTime(currentGoldPrice.value.updatedAt)}`
})

function formatMoney(value?: number | null, fractionDigits = 2) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return '--'
  }

  return numeric.toLocaleString('zh-CN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })
}

function formatDelta(change?: number | null, changePercent?: number | null) {
  const normalizedChange = Number(change)
  const normalizedPercent = Number(changePercent)
  if (!Number.isFinite(normalizedChange) || !Number.isFinite(normalizedPercent)) {
    return '--'
  }

  const sign = normalizedChange >= 0 ? '+' : ''
  return `${sign}${formatSignedMoney(normalizedChange)} (${sign}${formatSignedMoney(normalizedPercent, 2)}%)`
}

function formatSignedMoney(value: number, fractionDigits = 2) {
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })
}

const jewelryRows = computed(() => (
  currentGoldPrice.value?.jewelryPrices.map((item) => ({
    brand: item.brandName,
    price: `${formatMoney(item.price, 0)}/g`,
  })).filter((item) => item.price !== '--/g') ?? []
))

const chartOption = computed<EChartsCoreOption>(() => {
  const rootStyle = getComputedStyle(document.documentElement)
  const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
  const axisLine = rootStyle.getPropertyValue('--color-chart-axis-strong').trim()
  const splitLine = rootStyle.getPropertyValue('--color-chart-split').trim()
  const lineColor = rootStyle.getPropertyValue('--color-chart-gold').trim()
  const areaStart = rootStyle.getPropertyValue('--color-chart-gold-area-start').trim()
  const areaEnd = rootStyle.getPropertyValue('--color-chart-gold-area-end').trim()
  const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
  const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
  const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()
  const xLabels = trendData.value.x

  return {
    animation: false,
    grid: { left: 8, right: 8, top: 14, bottom: 18, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipText },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xLabels,
      axisLine: { lineStyle: { color: axisLine } },
      axisTick: { show: false },
      axisLabel: {
        color: axisText,
        fontSize: 11,
        hideOverlap: true,
        showMinLabel: true,
        showMaxLabel: true,
        interval: (index: number) => shouldShowAxisLabel(index, xLabels),
        formatter: (value: string) => formatAxisLabel(value),
      },
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
        smooth: activeRange.value !== '1d',
        showSymbol: false,
        data: trendData.value.y,
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
})

function shouldShowAxisLabel(index: number, labels: string[]) {
  if (activeRange.value !== '7d') {
    return true
  }

  if (index <= 0 || index >= labels.length - 1) {
    return true
  }

  const currentLabel = getAxisDayLabel(labels[index])
  const previousLabel = getAxisDayLabel(labels[index - 1])
  const nextLabel = getAxisDayLabel(labels[index + 1])

  return currentLabel !== previousLabel || currentLabel !== nextLabel
}

function formatAxisLabel(value: string) {
  if (activeRange.value !== '7d') {
    return value
  }

  const [dayLabel] = value.split(' ')
  return dayLabel ?? value
}

function getAxisDayLabel(value: string) {
  return value.split(' ')[0] ?? value
}

async function ensureEcharts() {
  if (!echartsLib) {
    echartsLib = await import('echarts')
  }
  return echartsLib
}

function renderChart() {
  if (!chartRef.value || !echartsLib) return
  if (!chartIns) {
    chartIns = echartsLib.init(chartRef.value)
  }
  chartIns.setOption(chartOption.value, true)
}

function onResize() {
  chartIns?.resize()
}

async function loadGoldPrice() {
  goldPriceError.value = ''

  try {
    await Promise.all([
      refreshGoldPriceCache('1d'),
      refreshRealtimeGoldPriceCache(),
    ])
  } catch (error) {
    goldPriceError.value = error instanceof Error ? error.message : '金价加载失败'
  }
}

async function loadTrendData() {
  if (activeRange.value === '1d') {
    await loadGoldPrice()
    return
  }

  isLoadingTrend.value = true
  goldPriceError.value = ''

  try {
    await refreshGoldPriceCache(activeRange.value)
  } catch (error) {
    goldPriceError.value = error instanceof Error ? error.message : '走势加载失败'
  } finally {
    isLoadingTrend.value = false
  }
}

async function refreshCurrentGoldPrice() {
  if (isRefreshingPrice.value) {
    return
  }

  isRefreshingPrice.value = true
  goldPriceError.value = ''

  try {
    await Promise.all([
      refreshGoldPriceCache(activeRange.value),
      refreshRealtimeGoldPriceCache(),
    ])
  } catch (error) {
    goldPriceError.value = error instanceof Error ? error.message : '金价刷新失败'
  } finally {
    isRefreshingPrice.value = false
  }
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

onMounted(async () => {
  await ensureEcharts()
  await loadGoldPrice()
  renderChart()
  window.addEventListener('resize', onResize)
})

watch(activeTrend, () => {
  void loadTrendData()
})

watch([chartPoints, isDark], () => {
  renderChart()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chartIns?.dispose()
  chartIns = null
})
</script>

<template>
  <section class="gold-price-page" aria-label="金价">
    <header class="gold-price-header">
      <PageHeader title="金价" back-label="返回更多功能" />
      <button
        class="gold-price-refresh"
        type="button"
        :disabled="isRefreshingPrice"
        aria-label="刷新金价"
        @click="refreshCurrentGoldPrice"
      >
        <span :class="['gold-price-refresh-icon', { spinning: isRefreshingPrice }]">↻</span>
      </button>
    </header>

    <section class="spot-london-card" aria-label="现货与伦敦金价">
      <header class="card-head">
        <p>现货 / 伦敦金价</p>
        <span>{{ goldPriceCacheState.isRefreshingPrimary ? '更新中...' : '实时更新' }}</span>
      </header>
      <p v-if="isLoadingGoldPrice && !currentGoldPrice" class="gold-price-message">
        正在获取实时行情
      </p>
      <p v-if="goldPriceError" class="gold-price-message gold-price-message-error">
        {{ goldPriceError }}
      </p>
      <p v-if="showPriceFallback" class="gold-price-message">
        暂无可靠实时行情
      </p>
      <div class="market-grid">
        <article class="market-item">
          <p class="market-label">现货金 (CNY/g)</p>
          <AmountText tag="strong" :value="formatMoney(currentGoldPrice?.spotGold?.price)" />
          <AmountText tag="em" :value="spotDeltaText" />
        </article>
        <article class="market-item">
          <p class="market-label">伦敦金 (USD/oz)</p>
          <AmountText tag="strong" :value="formatMoney(currentGoldPrice?.londonGold?.price)" />
          <AmountText tag="em" :value="londonDeltaText" />
        </article>
      </div>
      <p class="market-foot">{{ marketFootText }}</p>
    </section>

    <section class="trend-tabs" aria-label="趋势范围">
      <button
        v-for="item in trendOptions"
        :key="item"
        type="button"
        :class="{ active: activeTrend === item }"
        :disabled="isLoadingTrend"
        @click="activeTrend = item"
      >
        {{ item }}
      </button>
    </section>

    <section class="trend-card" aria-label="价格走势">
      <p class="card-title">价格走势</p>
      <div ref="chartRef" class="trend-chart" />
      <p v-if="!isLoadingTrend && chartPoints.length === 0" class="gold-price-message">
        暂无可靠走势数据
      </p>
    </section>

    <section class="quote-card" aria-label="关键报价">
      <div v-for="item in quoteRows" :key="item.label" class="quote-row">
        <span>{{ item.label }}</span>
        <AmountText tag="strong" :value="item.value" />
      </div>
      <p v-if="quoteRows.length === 0" class="gold-price-message">暂无报价数据</p>
    </section>

    <section class="jewelry-card" aria-label="首饰金价参考">
      <header class="card-head">
        <p>首饰金价参考 (CNY/g)</p>
        <span>品牌门店价</span>
      </header>
      <div v-for="item in jewelryRows" :key="item.brand" class="jewelry-row">
        <span>{{ item.brand }}</span>
        <AmountText tag="strong" :value="item.price" />
      </div>
      <p v-if="!isLoadingGoldPrice && jewelryRows.length === 0" class="gold-price-message">
        暂无可靠门店金价数据
      </p>
    </section>

  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
