import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import { readChartToken } from '@/business/utils/chart-theme'
import { useTheme } from '@/business/utils/theme'
import { formatAmount } from '@/business/utils/format'
import type {
  TransactionAnalysis,
  TransactionAnalysisCategoryBreakdownItem,
} from '@/business/api/modules/finance'
import {
  formatAxisValue,
  formatSignedAmount,
  type SummaryTab,
} from './analysisFormat'

export function useAnalysisCharts(options: {
  breakdownItems: ComputedRef<TransactionAnalysisCategoryBreakdownItem[]>
  summaryTab: Ref<SummaryTab>
  activeMetric: ComputedRef<'income' | 'expense' | 'surplus'>
  analysis: Ref<TransactionAnalysis | null>
}) {
  const { breakdownItems, summaryTab, activeMetric, analysis } = options
  const { isDark } = useTheme()

  const pieRef = ref<HTMLDivElement | null>(null)
  const lineRef = ref<HTMLDivElement | null>(null)
  let echartsLib: typeof import('@/business/utils/echarts-statistics') | null =
    null
  let pieChart: ECharts | null = null
  let lineChart: ECharts | null = null

  function getToneColor(metric: 'income' | 'expense' | 'surplus'): string {
    // 依赖 isDark 让模板/配置在主题切换时重算取色
    void isDark.value
    if (metric === 'income') {
      return readChartToken('--color-danger', '#f43f5e')
    }
    if (metric === 'expense') {
      return readChartToken('--color-success', '#10b981')
    }
    return readChartToken('--color-brand-strong', '#2563eb')
  }

  function handleResize() {
    pieChart?.resize()
    lineChart?.resize()
  }

  async function ensureEcharts() {
    if (!echartsLib) {
      echartsLib = await import('@/business/utils/echarts-statistics')
    }
    return echartsLib
  }

  async function syncCharts() {
    await ensureEcharts()
    await nextTick()

    if (!pieRef.value && !lineRef.value) {
      return
    }

    if (!pieRef.value && pieChart) {
      pieChart.dispose()
      pieChart = null
    }

    if (pieRef.value) {
      if (pieChart && pieChart.getDom() !== pieRef.value) {
        pieChart.dispose()
        pieChart = null
      }
      pieChart = pieChart ?? echartsLib!.init(pieRef.value)
      pieChart.setOption(buildPieOption(), true)
    }

    if (lineRef.value) {
      if (lineChart && lineChart.getDom() !== lineRef.value) {
        lineChart.dispose()
        lineChart = null
      }
      lineChart = lineChart ?? echartsLib!.init(lineRef.value)
      lineChart.setOption(buildLineOption(), true)
    }
  }

  function buildPieOption(): EChartsCoreOption {
    const rootStyle = getComputedStyle(document.documentElement)
    const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
    const splitLine = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
    const tooltipBg = rootStyle
      .getPropertyValue('--color-chart-tooltip-bg')
      .trim()
    const tooltipBorder = rootStyle
      .getPropertyValue('--color-chart-tooltip-border')
      .trim()
    const tooltipText = rootStyle
      .getPropertyValue('--color-chart-tooltip-text')
      .trim()

    if (!breakdownItems.value.some((item) => item.amount > 0)) {
      return {
        animation: false,
        graphic: [
          {
            type: 'text',
            left: 'center',
            top: 'middle',
            style: {
              text:
                summaryTab.value === '结余' ? '结余不按分类拆分' : '暂无数据',
              fill: axisText,
              fontSize: 12,
              fontWeight: 500,
            },
          },
        ],
        series: [
          {
            type: 'pie',
            radius: ['46%', '72%'],
            center: ['50%', '50%'],
            silent: true,
            label: { show: false },
            data: [{ value: 1, itemStyle: { color: splitLine } }],
          },
        ],
      }
    }

    return {
      animation: false,
      tooltip: {
        trigger: 'item',
        backgroundColor: tooltipBg,
        borderColor: tooltipBorder,
        textStyle: { color: tooltipText },
        valueFormatter: (value: unknown) => formatAmount(Number(value ?? 0)),
      },
      series: [
        {
          type: 'pie',
          radius: ['46%', '72%'],
          center: ['50%', '50%'],
          label: { show: false },
          data: breakdownItems.value
            .filter((item) => item.amount > 0)
            .map((item) => ({
              value: item.amount,
              name: item.categoryName,
              itemStyle: {
                color:
                  item.categoryColor ||
                  readChartToken('--color-chart-axis-strong', '#cbd5e1'),
              },
            })),
        },
      ],
    }
  }

  function buildLineOption(): EChartsCoreOption {
    const rootStyle = getComputedStyle(document.documentElement)
    const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
    const axisLine = rootStyle
      .getPropertyValue('--color-chart-axis-strong')
      .trim()
    const splitLine = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
    const tooltipBg = rootStyle
      .getPropertyValue('--color-chart-tooltip-bg')
      .trim()
    const tooltipBorder = rootStyle
      .getPropertyValue('--color-chart-tooltip-border')
      .trim()
    const tooltipText = rootStyle
      .getPropertyValue('--color-chart-tooltip-text')
      .trim()
    const metric = activeMetric.value
    const color = getToneColor(metric)
    const data = (analysis.value?.trendPoints ?? []).map((point) =>
      Number(point[metric] ?? 0),
    )
    const seriesData =
      metric === 'surplus'
        ? data.map((value) => ({
            value,
            itemStyle: {
              color:
                value >= 0 ? getToneColor('income') : getToneColor('expense'),
              borderRadius: value >= 0 ? [6, 6, 0, 0] : [0, 0, 6, 6],
            },
          }))
        : data

    return {
      animation: false,
      grid: { left: 44, right: 12, top: 14, bottom: 22 },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow',
        },
        backgroundColor: tooltipBg,
        borderColor: tooltipBorder,
        textStyle: { color: tooltipText },
        valueFormatter: (value: unknown) => {
          const numericValue = Number(value ?? 0)
          return metric === 'surplus'
            ? formatSignedAmount(numericValue)
            : formatAmount(numericValue)
        },
      },
      xAxis: {
        type: 'category',
        boundaryGap: true,
        data: (analysis.value?.trendPoints ?? []).map((point) => point.label),
        axisLine: { lineStyle: { color: axisLine } },
        axisTick: { show: false },
        axisLabel: { color: axisText, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        axisLine: {
          show: true,
          lineStyle: { color: axisLine },
        },
        axisTick: { show: false },
        axisLabel: {
          show: true,
          color: axisText,
          fontSize: 11,
          formatter: (value: number) => formatAxisValue(value),
        },
        splitLine: { lineStyle: { color: splitLine } },
      },
      series: [
        {
          type: 'bar',
          data: seriesData,
          barMaxWidth: 18,
          itemStyle: {
            color,
            borderRadius: [6, 6, 0, 0],
          },
        },
      ],
    }
  }

  watch(isDark, () => {
    void syncCharts()
  })

  onMounted(() => {
    window.addEventListener('resize', handleResize)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    pieChart?.dispose()
    lineChart?.dispose()
  })

  return {
    pieRef,
    lineRef,
    syncCharts,
  }
}
