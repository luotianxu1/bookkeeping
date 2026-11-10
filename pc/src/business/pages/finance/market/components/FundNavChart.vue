<script setup lang="ts">
import {
  nextTick,
  onActivated,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import type { ECharts } from 'echarts'
import type { FundNavItem } from '@/business/api/modules/market'
import { readChartToken } from '@/business/utils/chart-theme'
import { useTheme } from '@/business/utils/theme'
const props = defineProps<{ points: FundNavItem[]; metric: 'nav' | 'accNav' }>()
const element = ref<HTMLElement | null>(null),
  loading = ref(true),
  failed = ref(false)
const { isDark } = useTheme()
let chart: ECharts | null = null,
  observer: ResizeObserver | null = null,
  alive = true
function render() {
  if (!chart) return
  const text = readChartToken('--color-text-muted', '#64748b'),
    border = readChartToken('--color-border', '#e2e8f0')
  chart.setOption(
    {
      animation: false,
      grid: { left: 2, right: 8, top: 16, bottom: 8, containLabel: true },
      tooltip: {
        trigger: 'axis',
        confine: true,
        renderMode: 'richText',
        backgroundColor: readChartToken('--color-bg-card', '#fff'),
        borderColor: border,
        textStyle: { color: text },
        valueFormatter: (v: unknown) =>
          v == null ? '—' : Number(v).toFixed(4),
      },
      xAxis: {
        type: 'category',
        data: props.points.map((p) => p.date),
        boundaryGap: false,
        axisLabel: { color: text, fontSize: 10, hideOverlap: true },
        axisLine: { lineStyle: { color: border } },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        scale: true,
        splitNumber: 4,
        axisLabel: { color: text, fontSize: 10 },
        splitLine: { lineStyle: { color: border, type: 'dashed' } },
      },
      series: [
        {
          type: 'line',
          name: props.metric === 'nav' ? '单位净值' : '累计净值',
          showSymbol: props.points.length === 1,
          connectNulls: false,
          data: props.points.map((p) =>
            p[props.metric] != null &&
            String(p[props.metric]).trim() &&
            Number.isFinite(Number(p[props.metric]))
              ? Number(p[props.metric])
              : null,
          ),
          lineStyle: { width: 2 },
          itemStyle: { color: readChartToken('--color-brand', '#2563eb') },
        },
      ],
    },
    true,
  )
}
async function initialize() {
  loading.value = true
  failed.value = false
  try {
    const echarts = await import('@/business/utils/echarts')
    await nextTick()
    if (!alive || !element.value) return
    chart ??= echarts.init(element.value)
    render()
    observer ??= new ResizeObserver(() => chart?.resize())
    observer.observe(element.value)
  } catch {
    if (alive) failed.value = true
  } finally {
    if (alive) loading.value = false
  }
}
watch([() => props.points, () => props.metric, isDark], render, {
  flush: 'post',
})
onMounted(initialize)
onActivated(() => {
  void nextTick(() => chart?.resize())
})
onBeforeUnmount(() => {
  alive = false
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="fund-chart-wrap">
    <div
      ref="element"
      class="pc-chart fund-chart"
      role="img"
      :aria-label="`${metric === 'nav' ? '单位' : '累计'}净值走势图`"
      :aria-busy="loading"
    />
    <p v-if="loading" class="chart-message">走势图加载中…</p>
    <p v-else-if="failed" class="chart-message" role="alert">
      走势图加载失败
      <el-button @click="initialize">重试</el-button>
    </p>
  </div>
</template>
