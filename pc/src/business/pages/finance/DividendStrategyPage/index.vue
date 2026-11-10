<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'

// 红利策略页：红利低波ETF（512890）年线（MA250）择时策略。
// 每次进入页面拉取最新前复权日K并在本地重算：当前状态、触发价、信号事件与三策略回测对比。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getQfqDailyKline,
  type KlinePoint,
} from '@/business/api/modules/market'
import { useTheme } from '@/business/utils/theme'
import { getErrorMessage, isCancelRequest } from '@/business/api/request'
import { useRequestAbort } from '@/business/composables/useRequestAbort'
import { calcMa250, currentPosition, type StrategyParams } from './strategy'

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

let echartsLib: typeof import('@/business/utils/echarts') | null = null
let chartIns: ECharts | null = null
const chartRef = ref<HTMLDivElement | null>(null)

const params = computed<StrategyParams>(() => ({
  band: band.value,
  confirmDays: confirmDays.value,
}))

const dates = computed(() => klineRows.value.map((row) => row['日期']))
const closes = computed(() => klineRows.value.map((row) => row['收盘']))
const maSeries = computed(() => calcMa250(closes.value))

const position = computed(() =>
  currentPosition(closes.value, maSeries.value, params.value),
)

const latestRow = computed(
  () => klineRows.value[klineRows.value.length - 1] ?? null,
)
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
  return `${pct >= 0 ? '+' : ''}${pct.toFixed(1)}%`
})

const chartRange = computed(() => {
  const first = maSeries.value.findIndex((value) => value !== null)
  if (first < 0) {
    return {
      dates: [] as string[],
      closes: [] as number[],
      ma: [] as number[],
      buy: [] as number[],
      sell: [] as number[],
    }
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
    echartsLib = await import('@/business/utils/echarts')
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
  const axisLine = rootStyle
    .getPropertyValue('--color-chart-axis-strong')
    .trim()
  const splitLine = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
  const priceColor = rootStyle.getPropertyValue('--color-chart-gold').trim()
  const maColor = rootStyle.getPropertyValue('--color-brand-strong').trim()
  const buyColor = rootStyle.getPropertyValue('--color-danger').trim()
  const sellColor = rootStyle.getPropertyValue('--color-success').trim()
  const tooltipBg = rootStyle
    .getPropertyValue('--color-chart-tooltip-bg')
    .trim()
  const tooltipBorder = rootStyle
    .getPropertyValue('--color-chart-tooltip-border')
    .trim()
  const tooltipText = rootStyle
    .getPropertyValue('--color-chart-tooltip-text')
    .trim()

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
      axisLabel: {
        color: axisText,
        fontSize: 11,
        formatter: (value: number) => value.toFixed(2),
      },
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
    const data = await getQfqDailyKline(TARGET_SYMBOL, KLINE_DATALEN, {
      signal: signalFor(),
    })
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
  <section class="desktop-workspace">
    <PageHeader title="红利策略">
      <template #right>
        <el-button :loading="isRefreshing" @click="loadKline({ silent: true })">
          刷新行情
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <template v-if="klineRows.length">
      <MetricStrip
        :items="[
          { label: '最新收盘', value: formatPrice(position.price) },
          { label: '年线 MA250', value: formatPrice(position.ma) },
          { label: '年线偏离', value: deviationText },
          { label: '当前状态', value: zoneText },
        ]"
      />
      <div class="desktop-main-aside">
        <PagePanel title="价格与年线走势">
          <div ref="chartRef" class="desktop-chart" style="height: 420px" />
        </PagePanel>
        <PagePanel title="策略参数">
          <el-form label-position="top">
            <el-form-item label="缓冲带">
              <el-radio-group v-model="band">
                <el-radio-button
                  v-for="option in BAND_OPTIONS"
                  :key="option"
                  :value="option"
                >
                  {{ (option * 100).toFixed(1) }}%
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="确认天数">
              <el-radio-group v-model="confirmDays">
                <el-radio-button
                  v-for="option in CONFIRM_OPTIONS"
                  :key="option"
                  :value="option"
                >
                  {{ option }} 日
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-form>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="标的">
              {{ TARGET_NAME }} {{ TARGET_CODE }}
            </el-descriptions-item>
            <el-descriptions-item label="买入触发">
              {{ formatPrice(position.buyLine) }}
            </el-descriptions-item>
            <el-descriptions-item label="卖出触发">
              {{ formatPrice(position.sellLine) }}
            </el-descriptions-item>
            <el-descriptions-item label="交易日">
              {{ lastTradeDate }}
            </el-descriptions-item>
          </el-descriptions>
          <p class="desktop-note">{{ zoneHint }}</p>
        </PagePanel>
      </div>
    </template>
  </section>
</template>
