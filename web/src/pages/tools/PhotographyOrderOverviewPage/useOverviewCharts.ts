// PhotographyOrderOverviewPage 的 echarts 图表逻辑（折线趋势 + 两张饼图）。
import { onBeforeUnmount, ref, type ComputedRef, type Ref } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import type {
  PhotographyOrderOverviewTrendPoint,
  PhotographyOrderOverviewTypeStat,
  PhotographyOrderOverviewView,
} from '@/api/modules/tool'
import { formatYAxisAmount, typeAccent } from '../PhotographyOrdersPage/photographyOrderFormat'

export function useOverviewCharts(options: {
  trendPoints: ComputedRef<PhotographyOrderOverviewTrendPoint[]>
  typeStats: ComputedRef<PhotographyOrderOverviewTypeStat[]>
  viewMode: Ref<PhotographyOrderOverviewView>
  trendSeriesName: ComputedRef<string>
}) {
  const { trendPoints, typeStats, viewMode, trendSeriesName } = options

  const lineChartRef = ref<HTMLDivElement | null>(null)
  const pieChartRef = ref<HTMLDivElement | null>(null)
  const revenuePieChartRef = ref<HTMLDivElement | null>(null)
  let echartsLib: (typeof import('@/utils/echarts-statistics')) | null = null
  let lineChart: ECharts | null = null
  let pieChart: ECharts | null = null
  let revenuePieChart: ECharts | null = null

  function resolveDisplayedTrendPoints() {
    return trendPoints.value
  }

  async function ensureCharts() {
    if (!echartsLib) {
      echartsLib = await import('@/utils/echarts-statistics')
    }
    if (lineChart && lineChartRef.value && lineChart.getDom() !== lineChartRef.value) {
      lineChart.dispose()
      lineChart = null
    }
    if (pieChart && pieChartRef.value && pieChart.getDom() !== pieChartRef.value) {
      pieChart.dispose()
      pieChart = null
    }
    if (revenuePieChart && revenuePieChartRef.value && revenuePieChart.getDom() !== revenuePieChartRef.value) {
      revenuePieChart.dispose()
      revenuePieChart = null
    }
    if (lineChartRef.value && !lineChart) {
      lineChart = echartsLib.init(lineChartRef.value)
    }
    if (pieChartRef.value && !pieChart) {
      pieChart = echartsLib.init(pieChartRef.value)
    }
    if (revenuePieChartRef.value && !revenuePieChart) {
      revenuePieChart = echartsLib.init(revenuePieChartRef.value)
    }
  }

  function renderLineChart() {
    if (!lineChart) return

    const displayedTrendPoints = resolveDisplayedTrendPoints()
    const labels = displayedTrendPoints.map((item) => item.label)
    const incomeValues = displayedTrendPoints.map((item) => Number(item.contractAmount ?? 0))
    const rootStyle = getComputedStyle(document.documentElement)
    const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
    const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
    const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()
    const chartAxis = rootStyle.getPropertyValue('--color-chart-axis').trim()
    const chartSplit = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
    const incomeColor = rootStyle.getPropertyValue('--color-danger').trim()

    const option: EChartsCoreOption = {
      grid: { left: 0, right: 18, top: 28, bottom: 28, containLabel: true },
      tooltip: {
        trigger: 'axis',
        backgroundColor: tooltipBg,
        borderColor: tooltipBorder,
        textStyle: { color: tooltipText },
      },
      legend: {
        top: 0,
        right: 0,
        itemWidth: 10,
        itemHeight: 10,
        textStyle: { color: chartAxis, fontSize: 11 },
      },
      xAxis: {
        type: 'category',
        data: labels,
        axisLine: { lineStyle: { color: chartSplit } },
        axisLabel: {
          color: chartAxis,
          fontSize: 11,
          interval: viewMode.value === 'month' ? 0 : 'auto',
        },
      },
      yAxis: [
        {
          type: 'value',
          axisLine: { show: false },
          splitLine: { lineStyle: { color: chartSplit } },
          axisLabel: {
            color: chartAxis,
            fontSize: 11,
            formatter: formatYAxisAmount,
          },
        },
      ],
      series: [
        {
          name: trendSeriesName.value,
          type: 'bar',
          data: incomeValues,
          barMaxWidth: 18,
          itemStyle: { color: incomeColor, borderRadius: [8, 8, 0, 0] },
          emphasis: { itemStyle: { color: incomeColor } },
        },
      ],
    }

    lineChart.setOption(option, true)
  }

  function buildPieOption(valueGetter: (item: PhotographyOrderOverviewTypeStat) => number): EChartsCoreOption {
    return {
      tooltip: { trigger: 'item' },
      series: [
        {
          type: 'pie',
          radius: ['58%', '78%'],
          center: ['50%', '48%'],
          avoidLabelOverlap: false,
          label: { show: false },
          emphasis: { scale: true },
          data: typeStats.value.map((item) => ({
            name: item.label,
            value: valueGetter(item),
            itemStyle: { color: typeAccent(item.type) },
          })),
        },
      ],
    }
  }

  function renderQuantityPieChart() {
    if (!pieChart) return

    pieChart.setOption(buildPieOption((item) => Number(item.orderCount || 0)), true)
  }

  function renderRevenuePieChart() {
    if (!revenuePieChart) return

    revenuePieChart.setOption(buildPieOption((item) => Number(item.contractAmount || 0)), true)
  }

  function handleResize() {
    lineChart?.resize()
    pieChart?.resize()
    revenuePieChart?.resize()
  }

  window.addEventListener('resize', handleResize)

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    lineChart?.dispose()
    pieChart?.dispose()
    revenuePieChart?.dispose()
    lineChart = null
    pieChart = null
    revenuePieChart = null
  })

  return {
    lineChartRef,
    pieChartRef,
    revenuePieChartRef,
    ensureCharts,
    renderLineChart,
    renderQuantityPieChart,
    renderRevenuePieChart,
    handleResize,
  }
}
