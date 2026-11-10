<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

// 金价页：使用 ECharts 折线图展示价格走势，并支持暗黑模式配色。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'

import PageHeader from '@/business/components/common/PageHeader/index.vue'

import {
  getGoldPrices,
  type GoldPrice,
  type GoldPriceRange,
} from '@/business/api/modules/market'
import { useTheme } from '@/business/utils/theme'
import { getErrorMessage } from '@/business/api/request'
import { useVisibilityPolling } from '@/business/composables/useVisibilityPolling'

type TrendKey = '1日' | '7日' | '30日' | '3个月' | '1年' | '3年'
type DeltaTone = 'positive' | 'negative' | 'neutral'
type DeltaDisplay = {
  text: string
  tone: DeltaTone
}
type ChartExtremum = {
  index: number
  label: string
  price: number
}
const PRICE_REFRESH_INTERVAL_MS = 60_000
const EMPTY_DELTA: DeltaDisplay = { text: '--', tone: 'neutral' }

const activeTrend = ref<TrendKey>('1日')
const trendOptions: TrendKey[] = ['1日', '7日', '30日', '3个月', '1年', '3年']

const { isDark } = useTheme()
const chartRef = ref<HTMLDivElement | null>(null)
const goldPrice = ref<GoldPrice | null>(null)
const isRefreshingPrice = ref(false)
const isLoadingPrice = ref(false)
const isLoadingTrend = ref(false)
const goldPriceError = ref('')
let echartsLib: typeof import('@/business/utils/echarts') | null = null
let chartIns: ECharts | null = null

const trendRangeMap: Record<TrendKey, GoldPriceRange> = {
  '1日': '1d',
  '7日': '7d',
  '30日': '30d',
  '3个月': '3m',
  '1年': '1y',
  '3年': '3y',
}

const activeRange = computed(() => trendRangeMap[activeTrend.value])
const currentGoldPrice = computed(() => goldPrice.value)
const hasSpotGold = computed(
  () =>
    Number.isFinite(Number(currentGoldPrice.value?.spotGold?.price)) &&
    Number(currentGoldPrice.value?.spotGold?.price) > 0,
)
const hasLondonGold = computed(
  () =>
    Number.isFinite(Number(currentGoldPrice.value?.londonGold?.price)) &&
    Number(currentGoldPrice.value?.londonGold?.price) > 0,
)
const chartPoints = computed(() => currentGoldPrice.value?.chartPoints ?? [])
const isLoadingGoldPrice = computed(
  () => isLoadingPrice.value && !currentGoldPrice.value,
)

const trendData = computed(() => ({
  x: chartPoints.value.map((item) => item.label),
  y: chartPoints.value.map((item) => item.price),
}))

const chartExtrema = computed(() => {
  const validPoints = chartPoints.value
    .map((item, index) => ({ ...item, index }))
    .filter(
      (item) => Number.isFinite(Number(item.price)) && Number(item.price) > 0,
    )

  if (validPoints.length === 0) {
    return { high: null, low: null } as {
      high: ChartExtremum | null
      low: ChartExtremum | null
    }
  }

  return validPoints.reduce(
    (result, item) => {
      const price = Number(item.price)
      if (!result.high || price > result.high.price) {
        result.high = { index: item.index, label: item.label, price }
      }
      if (!result.low || price < result.low.price) {
        result.low = { index: item.index, label: item.label, price }
      }
      return result
    },
    { high: null, low: null } as {
      high: ChartExtremum | null
      low: ChartExtremum | null
    },
  )
})

const spotDelta = computed(() =>
  hasSpotGold.value
    ? formatDelta(
        currentGoldPrice.value?.spotGold?.change,
        currentGoldPrice.value?.spotGold?.changePercent,
      )
    : EMPTY_DELTA,
)

const londonDelta = computed(() =>
  hasLondonGold.value
    ? formatDelta(
        currentGoldPrice.value?.londonGold?.change,
        currentGoldPrice.value?.londonGold?.changePercent,
      )
    : EMPTY_DELTA,
)

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
  if (
    !Number.isFinite(normalizedChange) ||
    !Number.isFinite(normalizedPercent)
  ) {
    return EMPTY_DELTA
  }

  const displayChange = normalizeDisplayNumber(normalizedChange)
  const displayPercent = normalizeDisplayNumber(normalizedPercent, 1)
  const toneSource = displayPercent !== 0 ? displayPercent : displayChange

  return {
    text: `${formatSignedNumber(displayChange)} (${formatPercentage(displayPercent, true)})`,
    tone: getDeltaTone(toneSource),
  }
}

function normalizeDisplayNumber(value: number, fractionDigits = 2) {
  const rounded = Number(value.toFixed(fractionDigits))
  return Object.is(rounded, -0) ? 0 : rounded
}

function formatSignedNumber(value: number, fractionDigits = 2) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}${formatUnsignedNumber(Math.abs(value), fractionDigits)}`
}

function formatUnsignedNumber(value: number, fractionDigits = 2) {
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })
}

function getDeltaTone(value: number): DeltaTone {
  if (value > 0) return 'positive'
  if (value < 0) return 'negative'
  return 'neutral'
}

const jewelryRows = computed(
  () =>
    currentGoldPrice.value?.jewelryPrices
      .map((item) => ({
        brand: item.brandName,
        price: `${formatMoney(item.price, 0)}/g`,
      }))
      .filter((item) => item.price !== '--/g') ?? [],
)

const chartOption = computed<EChartsCoreOption>(() => {
  const rootStyle = getComputedStyle(document.documentElement)
  const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
  const axisLine = rootStyle
    .getPropertyValue('--color-chart-axis-strong')
    .trim()
  const splitLine = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
  const lineColor = rootStyle.getPropertyValue('--color-chart-gold').trim()
  const areaStart = rootStyle
    .getPropertyValue('--color-chart-gold-area-start')
    .trim()
  const areaEnd = rootStyle
    .getPropertyValue('--color-chart-gold-area-end')
    .trim()
  const tooltipBg = rootStyle
    .getPropertyValue('--color-chart-tooltip-bg')
    .trim()
  const tooltipBorder = rootStyle
    .getPropertyValue('--color-chart-tooltip-border')
    .trim()
  const tooltipText = rootStyle
    .getPropertyValue('--color-chart-tooltip-text')
    .trim()
  const dangerColor = rootStyle.getPropertyValue('--color-danger').trim()
  const successColor = rootStyle.getPropertyValue('--color-success').trim()
  const xLabels = trendData.value.x
  const extremaMarkPoints = buildExtremaMarkPoints(
    chartExtrema.value.high,
    chartExtrema.value.low,
    xLabels,
    dangerColor,
    successColor,
    tooltipBg,
    tooltipBorder,
    tooltipText,
  )

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
      boundaryGap: activeRange.value === '1y',
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
        lineStyle: { width: 1.5, color: lineColor },
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
        markPoint: {
          symbol: 'circle',
          symbolSize: 9,
          label: {
            color: tooltipText,
            fontSize: 10,
            fontWeight: 700,
          },
          data: extremaMarkPoints,
        },
      },
    ],
  }
})

function buildExtremaMarkPoints(
  high: ChartExtremum | null,
  low: ChartExtremum | null,
  labels: string[],
  highColor: string,
  lowColor: string,
  labelBg: string,
  labelBorder: string,
  labelText: string,
) {
  const baseLabel = {
    color: labelText,
    backgroundColor: 'transparent',
    borderWidth: 0,
    padding: [2, 0],
    fontSize: 10,
    fontWeight: 700,
  }

  return [
    high
      ? {
          name: '最高价',
          coord: [high.index, high.price],
          value: formatMoney(high.price),
          itemStyle: {
            color: highColor,
            borderColor: labelBg || labelBorder,
            borderWidth: 2,
          },
          label: {
            ...baseLabel,
            formatter: formatMoney(high.price),
            position: getExtremumLabelPosition(high.index, labels),
          },
        }
      : null,
    low && (!high || low.label !== high.label || low.price !== high.price)
      ? {
          name: '最低价',
          coord: [low.index, low.price],
          value: formatMoney(low.price),
          itemStyle: {
            color: lowColor,
            borderColor: labelBg || labelBorder,
            borderWidth: 2,
          },
          label: {
            ...baseLabel,
            formatter: formatMoney(low.price),
            position: getExtremumLabelPosition(low.index, labels),
          },
        }
      : null,
  ].filter(Boolean)
}

function getExtremumLabelPosition(index: number, labels: string[]) {
  if (index > labels.length / 2) {
    return 'left'
  }

  return 'right'
}

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
    echartsLib = await import('@/business/utils/echarts')
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
  return loadGoldPriceWithOptions({
    forceRefreshCurrent: true,
    includeChart: true,
  })
}

async function loadGoldPriceWithOptions(
  options: {
    forceRefreshCurrent?: boolean
    includeChart?: boolean
  } = {},
) {
  const { forceRefreshCurrent = false, includeChart = true } = options

  isLoadingPrice.value = true
  goldPriceError.value = ''

  try {
    const nextPrice = await getGoldPrices(
      activeRange.value,
      forceRefreshCurrent,
      includeChart,
    )
    goldPrice.value =
      includeChart && goldPrice.value
        ? nextPrice
        : {
            ...nextPrice,
            chartPoints: includeChart
              ? nextPrice.chartPoints
              : (goldPrice.value?.chartPoints ?? []),
          }
  } catch (error) {
    goldPriceError.value = getErrorMessage(error, '金价加载失败')
  } finally {
    isLoadingPrice.value = false
  }
}

async function loadTrendData() {
  isLoadingTrend.value = true

  try {
    await loadGoldPriceWithOptions({ includeChart: true })
  } catch (error) {
    goldPriceError.value = getErrorMessage(error, '走势加载失败')
  } finally {
    isLoadingTrend.value = false
  }
}

async function refreshCurrentGoldPrice() {
  if (isRefreshingPrice.value) {
    return
  }

  isRefreshingPrice.value = true

  try {
    await loadGoldPriceWithOptions({
      forceRefreshCurrent: true,
      includeChart: true,
    })
  } catch (error) {
    goldPriceError.value = getErrorMessage(error, '金价刷新失败')
  } finally {
    isRefreshingPrice.value = false
  }
}

// 可见性感知轮询：隐藏时跳过，恢复可见立即刷新（不含走势图，减少开销）
useVisibilityPolling(
  () => loadGoldPriceWithOptions({ includeChart: false }),
  PRICE_REFRESH_INTERVAL_MS,
)

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
  <section class="desktop-workspace">
    <PageHeader title="黄金行情">
      <template #right>
        <el-button
          :loading="isRefreshingPrice"
          @click="refreshCurrentGoldPrice"
        >
          刷新金价
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="goldPriceError"
      :title="goldPriceError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[
        {
          label: '现货金 CNY/g',
          value: formatMoney(currentGoldPrice?.spotGold?.price),
        },
        { label: '现货涨跌', value: spotDelta.text },
        {
          label: '伦敦金 USD/oz',
          value: formatMoney(currentGoldPrice?.londonGold?.price),
        },
        { label: '伦敦金涨跌', value: londonDelta.text },
      ]"
    />
    <div class="desktop-main-aside">
      <PagePanel title="金价走势">
        <template #actions>
          <el-radio-group v-model="activeTrend" :disabled="isLoadingTrend">
            <el-radio-button
              v-for="item in trendOptions"
              :key="item"
              :value="item"
            >
              {{ item }}
            </el-radio-button>
          </el-radio-group>
        </template>
        <div ref="chartRef" class="desktop-chart" style="height: 400px" />
        <p class="desktop-note">{{ marketFootText }}</p>
      </PagePanel>
      <DesktopTable
        :rows="jewelryRows"
        :loading="isLoadingGoldPrice"
        title="品牌首饰金价"
      >
        <el-table-column
          prop="brand"
          label="品牌"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <el-table-column
          prop="price"
          label="元 / 克"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
      </DesktopTable>
    </div>
  </section>
</template>
