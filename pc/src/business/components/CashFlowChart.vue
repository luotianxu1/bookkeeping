<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Transaction } from '@/business/api/modules/finance'
import type { ECharts } from 'echarts'
import { readChartToken } from '@/business/utils/chart-theme'
const props = defineProps<{ rows: Transaction[] }>()
const chartEl = ref<HTMLElement>()
const error = ref('')
let chart: ECharts | undefined
let observer: ResizeObserver | undefined
let alive = true
const days = computed(() => {
  const now = new Date()
  return Array.from({ length: 14 }, (_, offset) => {
    const date = new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate() - 13 + offset,
    )
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    const rows = props.rows.filter((row) => row.occurredAt.startsWith(key))
    return {
      key,
      income: rows
        .filter((row) => row.type === 'income')
        .reduce((sum, row) => sum + Number(row.amount), 0),
      expense: rows
        .filter((row) => row.type === 'expense')
        .reduce((sum, row) => sum + Number(row.amount), 0),
    }
  })
})
function draw() {
  const incomeColor = readChartToken('--color-danger', '#d65365')
  const expenseColor = readChartToken('--color-success', '#249c78')
  chart?.setOption({
    animation: false,
    tooltip: { trigger: 'axis', confine: true },
    legend: { top: 0, right: 0 },
    grid: { left: 16, right: 16, top: 42, bottom: 12, containLabel: true },
    xAxis: {
      type: 'category',
      data: days.value.map((day) => day.key.slice(5)),
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { type: 'dashed', color: '#e9edf4' } },
    },
    series: [
      {
        name: '收入',
        type: 'bar',
        data: days.value.map((day) => day.income),
        itemStyle: { color: incomeColor, borderRadius: [3, 3, 0, 0] },
        maxBarWidth: 14,
      },
      {
        name: '净支出',
        type: 'bar',
        data: days.value.map((day) => ({
          value: day.expense,
          itemStyle: { color: day.expense < 0 ? incomeColor : expenseColor },
        })),
        itemStyle: { color: expenseColor, borderRadius: [3, 3, 0, 0] },
        maxBarWidth: 14,
      },
    ],
  })
}
onMounted(async () => {
  try {
    const echarts = await import('@/business/utils/echarts-statistics')
    if (!alive || !chartEl.value) return
    chart = echarts.init(chartEl.value)
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(chartEl.value)
    draw()
  } catch {
    error.value = '图表加载失败'
  }
})
watch(days, draw)
onBeforeUnmount(() => {
  alive = false
  observer?.disconnect()
  chart?.dispose()
})
</script>
<template>
  <div
    ref="chartEl"
    class="desktop-chart"
    role="img"
    aria-label="近两周现金收支趋势"
  >
    <el-empty v-if="error" :description="error" />
  </div>
</template>
