<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import { readChartToken } from '@/business/utils/chart-theme'
import { useTheme } from '@/business/utils/theme'
import type { EstimateScenario, ScenarioId } from './projection'

const props = defineProps<{
  scenarios: EstimateScenario[]
  target: number
  selected: ScenarioId
  years: number
}>()
const { isDark } = useTheme()
const container = ref<HTMLElement | null>(null)
const loading = ref(true)
const error = ref(false)
let chart: ECharts | null = null
let observer: ResizeObserver | null = null
let alive = true

function render() {
  if (!chart) return
  const textColor = readChartToken('--color-text-secondary', '#64748b')
  const border = readChartToken('--color-bg-soft-2', '#e2e8f0')
  const colors = [
    readChartToken('--color-warning', '#d97706'),
    readChartToken('--color-brand', '#2563eb'),
    readChartToken('--color-success', '#059669'),
  ]
  const option: EChartsCoreOption = {
    animation: false,
    grid: { left: 6, right: 12, top: 16, bottom: 48, containLabel: true },
    legend: {
      bottom: 0,
      itemWidth: 14,
      itemHeight: 7,
      textStyle: { color: textColor, fontSize: 11 },
      selectedMode: false,
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      renderMode: 'richText',
      backgroundColor: readChartToken('--color-bg-card', '#fff'),
      borderColor: border,
      textStyle: {
        color: readChartToken('--color-text-primary', '#0f172a'),
        fontSize: 12,
      },
      valueFormatter: (value: unknown) =>
        `¥ ${Number(Array.isArray(value) ? value[1] : value).toLocaleString('zh-CN', { maximumFractionDigits: 0 })}`,
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: props.years,
      interval: props.years / 5,
      axisLabel: {
        color: textColor,
        fontSize: 10,
        formatter: (value: number) => (value === 0 ? '现在' : `${value}年`),
      },
      axisLine: { lineStyle: { color: border } },
      axisTick: { show: false },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      min: 0,
      splitNumber: 4,
      axisLabel: {
        color: textColor,
        fontSize: 10,
        formatter: (value: number) =>
          value >= 1e8
            ? `${Number((value / 1e8).toFixed(1))}亿`
            : value >= 1e4
              ? `${Number((value / 1e4).toFixed(1))}万`
              : String(Math.round(value)),
      },
      splitLine: { lineStyle: { color: border, type: 'dashed' } },
    },
    series: [
      ...props.scenarios.map((scenario, index) => ({
        name: `${scenario.label}资产`,
        type: 'line',
        showSymbol: false,
        data: scenario.points.map((point) => [
          point.year,
          Math.round(point.assets * 100) / 100,
        ]),
        lineStyle: {
          width: scenario.id === props.selected ? 3 : 1.5,
          color: colors[index],
          opacity: scenario.id === props.selected ? 1 : 0.65,
        },
        itemStyle: { color: colors[index] },
        emphasis: { focus: 'series' },
      })),
      {
        name: '目标金额',
        type: 'line',
        showSymbol: false,
        data:
          props.scenarios[0]?.points.map((point) => [
            point.year,
            props.target,
          ]) ?? [],
        itemStyle: { color: textColor },
        lineStyle: { type: 'dashed', width: 1.5, color: textColor },
      },
    ],
  }
  chart.setOption(option, true)
}

async function initialize() {
  error.value = false
  loading.value = true
  try {
    const echarts = await import('@/business/utils/echarts')
    await nextTick()
    if (!alive || !container.value) return
    chart ??= echarts.init(container.value)
    render()
    observer ??= new ResizeObserver(() => chart?.resize())
    observer.observe(container.value)
  } catch {
    if (alive) error.value = true
  } finally {
    if (alive) loading.value = false
  }
}

watch(
  [
    () => props.scenarios,
    () => props.target,
    () => props.selected,
    () => props.years,
    isDark,
  ],
  render,
  { flush: 'post' },
)
onMounted(initialize)
onBeforeUnmount(() => {
  alive = false
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="projection-chart-wrap">
    <div
      ref="container"
      class="pc-chart projection-chart"
      role="img"
      :aria-label="`未来${years}年资产趋势，展示保守、基准、乐观方案及目标金额，当前突出${scenarios.find((item) => item.id === selected)?.label}方案`"
      :aria-busy="loading"
    />
    <p v-if="loading" class="chart-message">趋势图加载中…</p>
    <div v-else-if="error" class="chart-message" role="alert">
      趋势图加载失败
      <CommonButton size="sm" variant="secondary" @click="initialize">
        重试图表
      </CommonButton>
    </div>
  </div>
</template>
