<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { ECharts, EChartsCoreOption } from 'echarts'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import {
  getAssetTrend,
  type AssetTrend,
  type AssetTrendAllocation,
  type AssetTrendRange,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

type TrendRangeLabel = '近7日' | '近30日' | '年内' | '全部'

const route = useRoute()

const rangeOptions: TrendRangeLabel[] = ['近7日', '近30日', '年内', '全部']
const rangeMap: Record<TrendRangeLabel, AssetTrendRange> = {
  近7日: '7d',
  近30日: '30d',
  年内: 'ytd',
  全部: 'all',
}
const reverseRangeMap: Record<AssetTrendRange, TrendRangeLabel> = {
  '7d': '近7日',
  '30d': '近30日',
  ytd: '年内',
  all: '全部',
}

const activeRange = ref<TrendRangeLabel>('年内')
const trend = ref<AssetTrend | null>(null)
const isLoading = ref(false)
const pageError = ref('')
const chartRef = ref<HTMLDivElement | null>(null)
const isDark = ref(false)

let mediaQuery: MediaQueryList | null = null
let echartsLib: (typeof import('echarts')) | null = null
let chartIns: ECharts | null = null

const accountId = computed(() => {
  const raw = route.query.accountId
  const normalized = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(normalized)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
})

const backTo = computed(() => (
  accountId.value ? `/finance/accounts/investment/${accountId.value}` : '/finance'
))
const backLabel = computed(() => (
  accountId.value ? '返回投资账户' : '返回财务首页'
))

const totalAssets = computed(() => formatCurrency(trend.value?.totalAssets))
const latestTrendPoints = computed(() => trend.value?.trendPoints ?? [])
const previousPointLabel = computed(() => {
  const range = (trend.value?.range ?? rangeMap[activeRange.value]) as AssetTrendRange | string
  if (range === '7d' || range === '30d') return '较上一天'
  if (range === 'ytd') return '较上月'
  if (range === 'all') return '较上年'
  return '较上期'
})
const previousPointAmount = computed<number | null>(() => {
  const points = latestTrendPoints.value
  if (points.length < 2) {
    return null
  }
  const currentValue = Number(points[points.length - 1]?.value ?? 0)
  const previousValue = Number(points[points.length - 2]?.value ?? 0)
  return currentValue - previousValue
})
const previousPointAmountText = computed(() => (
  previousPointAmount.value === null ? '--' : formatCurrency(previousPointAmount.value)
))
const previousPointRateText = computed(() => {
  const points = latestTrendPoints.value
  if (points.length < 2) {
    return '--'
  }
  const currentValue = Number(points[points.length - 1]?.value ?? 0)
  const previousValue = Number(points[points.length - 2]?.value ?? 0)
  if (previousValue <= 0) {
    return '--'
  }
  const rate = ((currentValue - previousValue) / previousValue) * 100
  return `${rate.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}%`
})
const hasTrendPoints = computed(() => (trend.value?.trendPoints.length ?? 0) > 0)
const rangeDateText = computed(() => {
  if (!trend.value?.startDate || !trend.value?.endDate) {
    return '--'
  }
  return `${trend.value.startDate} 至 ${trend.value.endDate}`
})
const syncText = computed(() => {
  if (!trend.value?.lastSyncedAt) {
    return `统计区间 ${rangeDateText.value}`
  }
  return `更新于 ${formatDateTime(trend.value.lastSyncedAt)}`
})
const trendSubtitle = computed(() => {
  if (!trend.value) {
    return '年内净资产变化'
  }
  return `${trend.value.rangeLabel}净资产变化`
})
const allocations = computed(() => trend.value?.allocations ?? [])

const chartOption = computed<EChartsCoreOption>(() => {
  const points = trend.value?.trendPoints ?? []
  const isSinglePoint = points.length === 1
  const axisText = isDark.value ? '#8FA3C7' : '#94A3B8'
  const axisLine = isDark.value ? '#253045' : '#CBD5E1'
  const splitLine = isDark.value ? '#1E293B' : '#E2E8F0'
  const areaStart = isDark.value ? 'rgba(37,99,235,0.34)' : 'rgba(37,99,235,0.20)'
  const areaEnd = isDark.value ? 'rgba(37,99,235,0.04)' : 'rgba(37,99,235,0.02)'

  return {
    animation: false,
    grid: { left: 18, right: 18, top: 16, bottom: 8, containLabel: true },
      tooltip: {
        trigger: 'axis',
        backgroundColor: isDark.value ? '#0F172A' : '#FFFFFF',
        borderColor: isDark.value ? '#253045' : '#E2E8F0',
        textStyle: { color: isDark.value ? '#E2E8F0' : '#0F172A' },
        valueFormatter: (value: number | string) => formatCurrency(Number(value ?? 0)),
      },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((item) => item.label),
      axisLine: { lineStyle: { color: axisLine } },
      axisTick: { show: false },
      axisLabel: {
        color: axisText,
        fontSize: 11,
        interval: points.length > 12 ? 'auto' : 0,
        hideOverlap: false,
      },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: axisText,
        fontSize: 11,
        formatter: (value: number) => formatCompactMoney(value),
      },
      splitLine: { lineStyle: { color: splitLine } },
    },
    series: [
      {
        type: 'line',
        smooth: !isSinglePoint,
        showSymbol: isSinglePoint,
        symbol: 'circle',
        symbolSize: isSinglePoint ? 10 : 7,
        data: points.map((item) => item.value),
        lineStyle: { width: 3, color: '#2563EB' },
        itemStyle: { color: '#2563EB' },
        label: isSinglePoint
          ? {
              show: true,
              position: 'top',
              color: isDark.value ? '#E2E8F0' : '#0F172A',
              fontSize: 12,
              fontWeight: 700,
              formatter: ({ value }: { value: number | string }) => `¥ ${formatCurrency(Number(value ?? 0))}`,
            }
          : undefined,
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
  if (!chartRef.value || !echartsLib || !hasTrendPoints.value) {
    chartIns?.dispose()
    chartIns = null
    return
  }

  if (!chartIns) {
    chartIns = echartsLib.init(chartRef.value)
  }

  chartIns.setOption(chartOption.value, true)
}

function onResize() {
  chartIns?.resize()
}

async function loadTrend() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看资产趋势'
    trend.value = null
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const result = await getAssetTrend({
      userId: currentUser.id,
      accountId: accountId.value,
      range: rangeMap[activeRange.value],
    })
    trend.value = result
    const normalizedRange = reverseRangeMap[(result.range as AssetTrendRange) ?? 'ytd']
    if (normalizedRange) {
      activeRange.value = normalizedRange
    }
  } catch (error) {
    trend.value = null
    pageError.value = error instanceof Error ? error.message : '资产趋势加载失败'
  } finally {
    isLoading.value = false
    await nextTick()
    renderChart()
  }
}

function getAllocationTrackStyle(item: AssetTrendAllocation) {
  const colors = getAccountTypeColors(item.accountTypeCode)
  return {
    '--allocation-fill': colors.fill,
    '--allocation-track': colors.track,
    '--allocation-width': `${Math.max(0, Math.min(Number(item.percent ?? 0), 100))}%`,
  }
}

function getAllocationDotStyle(item: AssetTrendAllocation) {
  return {
    backgroundColor: getAccountTypeColors(item.accountTypeCode).fill,
  }
}

function getAccountTypeColors(value?: string | null) {
  if (value === 'cash') {
    return { fill: '#16A34A', track: '#ECFDF5' }
  }
  if (value === 'investment') {
    return { fill: '#DC2626', track: '#FEF2F2' }
  }
  if (value === 'gold') {
    return { fill: '#D97706', track: '#FFF7ED' }
  }
  if (value === 'other_asset') {
    return { fill: '#2563EB', track: '#EFF6FF' }
  }
  if (value === 'debt' || value === 'liability' || value === 'other_liability' || value === 'credit_card') {
    return { fill: '#7C3AED', track: '#F5F3FF' }
  }
  if (value === 'human_relation') {
    return { fill: '#EA580C', track: '#FFF7ED' }
  }
  return { fill: '#64748B', track: '#F1F5F9' }
}

function formatCurrency(value?: number | null) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatCompactMoney(value: number) {
  const normalized = Number(value ?? 0)
  if (Math.abs(normalized) >= 10000) {
    return `${(normalized / 10000).toFixed(2)}w`
  }
  return normalized.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

onMounted(async () => {
  mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  updateThemeState()
  mediaQuery.addEventListener('change', handleThemeChange)
  await ensureEcharts()
  await loadTrend()
  renderChart()
  window.addEventListener('resize', onResize)
})

watch(activeRange, () => {
  void loadTrend()
})

watch([trend, isDark], () => {
  renderChart()
}, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  mediaQuery?.removeEventListener('change', handleThemeChange)
  chartIns?.dispose()
  chartIns = null
})
</script>

<template>
  <section class="investment-trend-page" aria-label="资产趋势">
    <PageHeader title="资产趋势" :back-to="backTo" :back-label="backLabel" />

    <p v-if="pageError" class="investment-trend-message investment-trend-message-error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else-if="trend">
      <SegmentedControl
        v-model="activeRange"
        :options="rangeOptions"
        label="趋势区间选择"
        variant="surface"
      />

      <section class="investment-trend-summary-card" aria-label="资产总览">
        <div class="investment-trend-summary-top">
          <div class="investment-trend-summary-main">
            <AmountText tag="strong" tone="inherit" :value="totalAssets" />
            <span>{{ syncText }}</span>
          </div>
          <div class="investment-trend-summary-side">
            <span>{{ previousPointLabel }}</span>
            <AmountText
              tag="strong"
              class="summary-profit"
              :value="previousPointAmountText"
              show-sign
              show-unit
            />
            <AmountText tag="span" class="summary-rate" :value="previousPointRateText" show-sign />
          </div>
        </div>
      </section>

      <section class="investment-trend-card" aria-label="总资产走势">
        <header class="investment-trend-card-head">
          <div>
            <strong>总资产走势</strong>
            <p>{{ trendSubtitle }}</p>
          </div>
        </header>
        <div v-if="hasTrendPoints" ref="chartRef" class="investment-trend-chart"></div>
        <p v-else class="investment-trend-empty">当前区间暂无可展示的资产走势</p>
        <p class="investment-trend-foot">{{ rangeDateText }}</p>
      </section>

      <section class="investment-trend-card" aria-label="资产分布">
        <header class="investment-trend-card-head">
          <div>
            <strong>资产分布</strong>
            <p>按资产类型</p>
          </div>
        </header>

        <div v-if="allocations.length > 0" class="allocation-list">
          <article
            v-for="item in allocations"
            :key="`${item.accountTypeCode}-${item.label}`"
            class="allocation-item"
          >
            <div class="allocation-row">
              <div class="allocation-title">
                <span class="allocation-dot" :style="getAllocationDotStyle(item)"></span>
                <strong>{{ item.label }}</strong>
              </div>
              <div class="allocation-side">
                <AmountText tag="span" tone="inherit" :value="formatCurrency(item.balance)" />
                <span>{{ `${Number(item.percent ?? 0).toFixed(2)}%` }}</span>
              </div>
            </div>
            <div class="allocation-track" :style="getAllocationTrackStyle(item)">
              <span class="allocation-fill"></span>
            </div>
          </article>
        </div>
        <p v-else class="investment-trend-empty">暂无可展示的资产分布</p>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
