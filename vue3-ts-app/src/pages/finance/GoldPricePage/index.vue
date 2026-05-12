<script setup lang="ts">
// 金价页：使用 ECharts 折线图展示价格走势，并支持暗黑模式配色。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'

type TrendKey = '1日' | '7日' | '30日' | '1年'

const activeTrend = ref<TrendKey>('1日')
const trendOptions: TrendKey[] = ['1日', '7日', '30日', '1年']

const isDark = ref(false)
const chartRef = ref<HTMLDivElement | null>(null)
let mediaQuery: MediaQueryList | null = null
let echartsLib: (typeof import('echarts')) | null = null
let chartIns: ECharts | null = null

const trendDataMap: Record<TrendKey, { x: string[]; y: number[] }> = {
  '1日': {
    x: ['09:30', '10:30', '11:30', '13:00', '14:00', '15:00'],
    y: [556.1, 557.3, 558.5, 557.8, 559.2, 559.36],
  },
  '7日': {
    x: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    y: [551.8, 553.6, 552.9, 556.2, 557.5, 558.7, 559.36],
  },
  '30日': {
    x: ['1', '5', '10', '15', '20', '25', '30'],
    y: [544.2, 546.7, 549.8, 551.3, 553.5, 556.4, 559.36],
  },
  '1年': {
    x: ['1月', '3月', '5月', '7月', '9月', '11月'],
    y: [482.5, 501.2, 522.6, 538.7, 548.3, 559.36],
  },
}

const quoteRows = [
  { label: '今日开盘', value: '556.10' },
  { label: '最高', value: '560.42' },
  { label: '最低', value: '553.87' },
  { label: '买入参考', value: '559.50' },
  { label: '卖出参考', value: '559.20' },
]

const jewelryRows = [
  { brand: '周大福', price: '728/g' },
  { brand: '老凤祥', price: '726/g' },
  { brand: '六福珠宝', price: '727/g' },
]

const goldAccountRows = [
  { name: '招商银行纸黄金', grams: '68.90 g', amount: '45,320.00' },
  { name: '支付宝黄金', grams: '36.60 g', amount: '24,100.00' },
]

const chartOption = computed<EChartsCoreOption>(() => {
  const current = trendDataMap[activeTrend.value]
  const axisText = isDark.value ? '#8FA3C7' : '#94A3B8'
  const axisLine = isDark.value ? '#253045' : '#CBD5E1'
  const splitLine = isDark.value ? '#1E293B' : '#E2E8F0'
  const areaStart = isDark.value ? 'rgba(59,130,246,0.34)' : 'rgba(59,130,246,0.22)'
  const areaEnd = isDark.value ? 'rgba(59,130,246,0.02)' : 'rgba(59,130,246,0.03)'

  return {
    animation: false,
    grid: { left: 8, right: 8, top: 14, bottom: 18, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark.value ? '#0F172A' : '#FFFFFF',
      borderColor: isDark.value ? '#253045' : '#E2E8F0',
      textStyle: { color: isDark.value ? '#E2E8F0' : '#0F172A' },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: current.x,
      axisLine: { lineStyle: { color: axisLine } },
      axisTick: { show: false },
      axisLabel: { color: axisText, fontSize: 11 },
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
        data: current.y,
        lineStyle: { width: 3, color: '#3B82F6' },
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

onMounted(async () => {
  mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  updateThemeState()
  mediaQuery.addEventListener('change', handleThemeChange)
  await ensureEcharts()
  renderChart()
  window.addEventListener('resize', onResize)
})

watch([activeTrend, isDark], () => {
  renderChart()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  mediaQuery?.removeEventListener('change', handleThemeChange)
  chartIns?.dispose()
  chartIns = null
})
</script>

<template>
  <section class="gold-price-page" aria-label="金价">
    <PageHeader title="金价" back-label="返回更多功能" />

    <section class="spot-london-card" aria-label="现货与伦敦金价">
      <header class="card-head">
        <p>现货 / 伦敦金价</p>
        <span>实时更新</span>
      </header>
      <div class="market-grid">
        <article class="market-item">
          <p class="market-label">现货金 (CNY/g)</p>
          <AmountText tag="strong" value="559.36" />
          <AmountText tag="em" value="+3.12 (+0.56%)" />
        </article>
        <article class="market-item">
          <p class="market-label">伦敦金 (USD/oz)</p>
          <AmountText tag="strong" value="2,368.40" />
          <AmountText tag="em" value="+8.25 (+0.35%)" />
        </article>
      </div>
      <p class="market-foot">现货更新时间 14:32 · 伦敦时间 08:32</p>
    </section>

    <section class="trend-tabs" aria-label="趋势范围">
      <button
        v-for="item in trendOptions"
        :key="item"
        type="button"
        :class="{ active: activeTrend === item }"
        @click="activeTrend = item"
      >
        {{ item }}
      </button>
    </section>

    <section class="trend-card" aria-label="价格走势">
      <p class="card-title">价格走势</p>
      <div ref="chartRef" class="trend-chart" />
    </section>

    <section class="quote-card" aria-label="关键报价">
      <div v-for="item in quoteRows" :key="item.label" class="quote-row">
        <span>{{ item.label }}</span>
        <AmountText tag="strong" :value="item.value" />
      </div>
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
    </section>

    <section class="gold-account-card" aria-label="黄金账户列表">
      <header class="card-head">
        <p>黄金账户列表</p>
      </header>
      <article v-for="item in goldAccountRows" :key="item.name" class="gold-account-row">
        <div class="gold-account-left">
          <strong>{{ item.name }}</strong>
          <span>{{ item.grams }}</span>
        </div>
        <div class="gold-account-right">
          <AmountText tag="strong" :value="item.amount" />
          <span class="gold-account-arrow">&gt;</span>
        </div>
      </article>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
