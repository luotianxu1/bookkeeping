// 投资详情页两张 echarts（走势/累计收益）的生命周期与渲染逻辑。
import { nextTick, onBeforeUnmount, onMounted, ref, watch, type ComputedRef, type Ref } from 'vue'
import type { ECharts } from 'echarts'
import type {
  InvestmentAssetDetail,
  InvestmentChartPoint,
  InvestmentPosition,
  InvestmentTransaction,
} from '@/api/modules/finance'
import { useTheme } from '@/utils/theme'
import {
  buildFundProfitChartSeriesData,
  formatCompactCurrency,
  formatCurrency,
  formatDate,
  formatNumber,
  getTooltipPointValue,
  normalizeDateLabel,
  resolveTradeMarkerDateLabel,
  TRADE_MARKER_COLORS,
} from './utils'

export type UseTrendChartsOptions = {
  detail: Ref<InvestmentAssetDetail | null>
  transactions: Ref<InvestmentTransaction[]>
  currentPosition: ComputedRef<InvestmentPosition | null>
  chartCostBaseline: Ref<number | null>
  cumulativeProfitPoints: ComputedRef<InvestmentChartPoint[]>
  showFundCumulativeProfitChart: ComputedRef<boolean>
  cumulativeProfitChartAriaLabel: ComputedRef<string>
  selectedFundChartView: Ref<'netValue' | 'profit'>
}

export function useTrendCharts(options: UseTrendChartsOptions) {
  const { detail, transactions, chartCostBaseline, cumulativeProfitPoints, showFundCumulativeProfitChart, cumulativeProfitChartAriaLabel } = options
  const { isDark } = useTheme()

  const chartRef = ref<HTMLDivElement | null>(null)
  const cumulativeProfitChartRef = ref<HTMLDivElement | null>(null)
  let chart: ECharts | null = null
  let cumulativeProfitChart: ECharts | null = null

  // ---------- tooltip / 坐标轴文案（依赖成本基线） ----------

  function getChangePercentByBaseline(value: unknown, baseline = chartCostBaseline.value) {
    const numeric = Number(value)
    if (!baseline || !Number.isFinite(numeric)) {
      return null
    }
    return ((numeric - baseline) / baseline) * 100
  }

  function formatAxisChangeLabel(value: unknown, baseline = chartCostBaseline.value) {
    const percent = getChangePercentByBaseline(value, baseline)
    if (percent === null) {
      return '--'
    }
    return `${percent > 0 ? '+' : ''}${formatNumber(percent)}%`
  }

  function formatPriceWithChange(value: unknown, digits: number, baseline = chartCostBaseline.value) {
    const numeric = Number(value)
    if (!Number.isFinite(numeric)) {
      return '--'
    }
    const percent = getChangePercentByBaseline(numeric, baseline)
    const priceText = formatNumber(numeric, digits)
    if (percent === null) {
      return priceText
    }
    return `${priceText}（${percent > 0 ? '+' : ''}${formatNumber(percent)}%）`
  }

  function formatValueWithBaselinePercent(value: unknown, digits: number, baseline: number | null) {
    return formatPriceWithChange(value, digits, baseline)
  }

  function formatTooltipPointValue(value: unknown, digits: number) {
    const numeric = Number(value)
    return Number.isFinite(numeric) ? formatNumber(numeric, digits) : '--'
  }

  function resolveLineChartBaseline(points: InvestmentChartPoint[], isFundTrendChart: boolean) {
    if (isFundTrendChart) {
      const firstPoint = points.find((point) => Number.isFinite(Number(point.value)))
      const firstValue = Number(firstPoint?.value)
      return Number.isFinite(firstValue) && firstValue > 0 ? firstValue : null
    }

    const costPrice = Number(chartCostBaseline.value)
    return Number.isFinite(costPrice) && costPrice > 0 ? costPrice : null
  }

  function isChartValueInRange(points: InvestmentChartPoint[], value: number) {
    if (!Number.isFinite(value)) {
      return false
    }

    const values = points
      .map((point) => Number(point.value ?? point.close))
      .filter((pointValue) => Number.isFinite(pointValue))
    if (values.length === 0) {
      return false
    }

    const minValue = Math.min(...values)
    const maxValue = Math.max(...values)
    return value >= minValue && value <= maxValue
  }

  function buildTradeMarkerSeries(points: InvestmentChartPoint[], chartType: 'line' | 'candlestick') {
    const isFundLineChart = chartType === 'line' && detail.value?.productType === 'fund'
    const pointMap = new Map(points.map((point) => [normalizeDateLabel(point.label), point]))
    const buyData: Array<Record<string, any>> = []
    const sellData: Array<Record<string, any>> = []

    for (const entry of transactions.value) {
      if (entry.tradeType !== 'buy' && entry.tradeType !== 'sell') {
        continue
      }

      const tradeDateLabel = resolveTradeMarkerDateLabel(entry, isFundLineChart)
      const point = pointMap.get(tradeDateLabel)
      if (!point) {
        continue
      }

      const rawPrice = Number(entry.price)
      const fallbackPrice = chartType === 'candlestick'
        ? Number(point.close ?? point.open ?? point.high ?? point.low ?? point.value)
        : Number(point.value ?? point.close ?? point.open ?? point.high ?? point.low)
      const targetPrice = isFundLineChart
        ? fallbackPrice
        : Number.isFinite(rawPrice) && rawPrice > 0 ? rawPrice : fallbackPrice
      if (!Number.isFinite(targetPrice) || targetPrice <= 0) {
        continue
      }

      const marker = {
        value: [point.label, targetPrice],
        tradeAt: entry.tradeAt,
        amount: entry.amount,
        price: targetPrice,
        settlementStatus: entry.settlementStatus,
      }

      if (entry.tradeType === 'buy') {
        buyData.push(marker)
      } else {
        sellData.push(marker)
      }
    }

    const result: Array<Record<string, any>> = []
    if (buyData.length > 0) {
      result.push({
        name: '买入点',
        type: 'scatter',
        data: buyData,
        xAxisIndex: 0,
        yAxisIndex: 0,
        symbol: 'circle',
        symbolSize: 6,
        itemStyle: { color: TRADE_MARKER_COLORS.buy },
        emphasis: { scale: 1.1 },
        z: 5,
      })
    }
    if (sellData.length > 0) {
      result.push({
        name: '卖出点',
        type: 'scatter',
        data: sellData,
        xAxisIndex: 0,
        yAxisIndex: 0,
        symbol: 'diamond',
        symbolSize: 7,
        itemStyle: { color: TRADE_MARKER_COLORS.sell },
        emphasis: { scale: 1.1 },
        z: 5,
      })
    }

    return result
  }

  async function renderChart() {
    disposeChart()
    const chartContainer = chartRef.value
    if (!chartContainer || !detail.value) {
      return
    }

    const points = detail.value.chartPoints ?? []
    if (points.length === 0) {
      return
    }

    const echarts = await import('echarts')
    if (chartRef.value !== chartContainer) {
      return
    }
    chart = echarts.init(chartContainer)
    if (detail.value.chartType === 'candlestick') {
      renderStockChart(points)
    } else {
      renderLineChart(points)
    }
    requestAnimationFrame(() => chart?.resize())
  }

  function renderLineChart(points: InvestmentChartPoint[]) {
    const rootStyle = getComputedStyle(document.documentElement)
    const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
    const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
    const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()
    const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
    const axisLine = rootStyle.getPropertyValue('--color-chart-axis-strong').trim()
    const splitLine = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
    const brandColor = rootStyle.getPropertyValue('--color-brand').trim()
    const brandStrongColor = rootStyle.getPropertyValue('--color-brand-strong').trim()
    const warningStrongColor = rootStyle.getPropertyValue('--color-warning-strong').trim()
    const dangerColor = rootStyle.getPropertyValue('--color-danger').trim()
    const isFundTrendChart = detail.value?.productType === 'fund'
    const baselineValue = resolveLineChartBaseline(points, isFundTrendChart)
    const costPrice = chartCostBaseline.value
    const tradeMarkerSeries = buildTradeMarkerSeries(points, 'line')
    const series: Array<Record<string, any>> = [
      {
        name: isFundTrendChart ? '累计净值' : '单位净值',
        type: 'line',
        smooth: false,
        showSymbol: false,
        data: points.map((point) => point.value ?? null),
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.08 },
      },
    ]

    if (costPrice !== null && isChartValueInRange(points, costPrice)) {
      series.push({
        name: isFundTrendChart ? '持仓成本' : '持仓成本价',
        type: 'line',
        smooth: false,
        showSymbol: false,
        data: points.map(() => costPrice),
        itemStyle: { color: warningStrongColor },
        lineStyle: {
          width: 1.5,
          type: 'dashed',
          color: warningStrongColor,
        },
      })
    }

    series.push(...tradeMarkerSeries)

    chart?.setOption({
      color: [brandColor, warningStrongColor, brandStrongColor, dangerColor],
      tooltip: {
        trigger: 'axis',
        confine: true,
        backgroundColor: tooltipBg,
        borderColor: tooltipBorder,
        textStyle: { color: tooltipText },
        formatter: (params: any) => {
          const items = Array.isArray(params) ? params : [params]
          if (items.length === 0) {
            return '--'
          }
          const title = items[0]?.axisValueLabel || items[0]?.axisValue || '--'
          const lines = [title]
          for (const item of items) {
            const pointValue = getTooltipPointValue(item)
            lines.push(
              `${item.marker}${item.seriesName} ${
                isFundTrendChart
                  ? formatValueWithBaselinePercent(pointValue, 4, baselineValue)
                  : formatPriceWithChange(pointValue, 4)
              }`,
            )
          }
          return lines.join('<br/>')
        },
      },
      legend: {
        top: 0,
        left: 0,
        icon: 'roundRect',
        itemWidth: 10,
        itemHeight: 10,
        textStyle: {
          color: axisText,
          fontSize: 11,
        },
        data: series.map((entry) => entry.name),
      },
      grid: { top: 42, right: 14, bottom: 26, left: 42 },
      xAxis: {
        type: 'category',
        data: points.map((point) => point.label),
        axisLabel: { color: axisText, fontSize: 10 },
        axisLine: { lineStyle: { color: axisLine } },
      },
      yAxis: {
        type: 'value',
        scale: true,
        axisLabel: {
          color: axisText,
          fontSize: 10,
          formatter: (value: number) => formatAxisChangeLabel(value, baselineValue),
        },
        splitLine: { lineStyle: { color: splitLine } },
      },
      dataZoom: [{ type: 'inside', start: 0, end: 100 }],
      series,
    }, { replaceMerge: ['series'] })
  }

  function renderStockChart(points: InvestmentChartPoint[]) {
    const rootStyle = getComputedStyle(document.documentElement)
    const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
    const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
    const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()
    const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
    const splitLine = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
    const brandSoftColor = rootStyle.getPropertyValue('--color-brand-soft').trim()
    const dates = points.map((point) => point.label)
    const candleData = points.map((point) => [point.open, point.close, point.low, point.high])
    const volumeData = points.map((point) => point.volume ?? 0)
    const tradeMarkerSeries = buildTradeMarkerSeries(points, 'candlestick')

    chart?.setOption({
      animation: false,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross' },
        confine: true,
        backgroundColor: tooltipBg,
        borderColor: tooltipBorder,
        textStyle: { color: tooltipText },
        formatter: (params: any) => {
          const items = Array.isArray(params) ? params : [params]
          if (items.length === 0) {
            return '--'
          }
          const title = items[0]?.axisValueLabel || items[0]?.axisValue || '--'
          const lines = [title]
          for (const item of items) {
            if (item.seriesType === 'candlestick' && Array.isArray(item.data)) {
              const [open, close, low, high] = item.data
              lines.push(`${item.marker}${item.seriesName} 开 ${formatTooltipPointValue(open, 2)} 收 ${formatTooltipPointValue(close, 2)} 高 ${formatTooltipPointValue(high, 2)} 低 ${formatTooltipPointValue(low, 2)}`)
              continue
            }
            if (item.seriesType === 'bar') {
              lines.push(`${item.marker}${item.seriesName} ${formatTooltipPointValue(item.value, 0)}`)
              continue
            }
            lines.push(`${item.marker}${item.seriesName} ${formatPriceWithChange(getTooltipPointValue(item), 2)}`)
          }
          return lines.join('<br/>')
        },
      },
      legend: {
        top: 0,
        left: 0,
        icon: 'roundRect',
        itemWidth: 10,
        itemHeight: 10,
        textStyle: {
          color: axisText,
          fontSize: 11,
        },
        data: ['K线', '买入点', '卖出点'],
      },
      grid: [
        { left: 42, right: 14, top: 42, height: '54%' },
        { left: 42, right: 14, top: '76%', height: '14%' },
      ],
      xAxis: [
        { type: 'category', data: dates, boundaryGap: false, axisLabel: { color: axisText, fontSize: 10 } },
        { type: 'category', gridIndex: 1, data: dates, boundaryGap: false, axisLabel: { show: false }, axisTick: { show: false } },
      ],
      yAxis: [
        {
          scale: true,
          axisLabel: {
            color: axisText,
            fontSize: 10,
            formatter: (value: number) => formatAxisChangeLabel(value),
          },
          splitLine: { lineStyle: { color: splitLine } },
        },
        { scale: true, gridIndex: 1, splitNumber: 2, axisLabel: { color: axisText, fontSize: 10 }, splitLine: { show: false } },
      ],
      dataZoom: [{ type: 'inside', xAxisIndex: [0, 1], start: 60, end: 100 }],
      series: [
        {
          name: 'K线',
          type: 'candlestick',
          data: candleData,
          itemStyle: {
            color: '#DC2626',
            color0: '#16A34A',
            borderColor: '#DC2626',
            borderColor0: '#16A34A',
          },
        },
        {
          name: '成交量',
          type: 'bar',
          xAxisIndex: 1,
          yAxisIndex: 1,
          data: volumeData,
          itemStyle: { color: brandSoftColor },
        },
        ...tradeMarkerSeries,
      ],
    })
  }

  async function renderCumulativeProfitChart() {
    disposeCumulativeProfitChart()
    const chartContainer = cumulativeProfitChartRef.value
    if (!chartContainer || !showFundCumulativeProfitChart.value || cumulativeProfitPoints.value.length === 0) {
      return
    }

    const points = cumulativeProfitPoints.value
    const rootStyle = getComputedStyle(document.documentElement)
    const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
    const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
    const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()
    const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
    const axisLine = rootStyle.getPropertyValue('--color-chart-axis-strong').trim()
    const splitLine = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
    const positiveColor = rootStyle.getPropertyValue('--color-danger').trim()
    const negativeColor = rootStyle.getPropertyValue('--color-success').trim()
    const echarts = await import('echarts')
    const profitSeriesData = buildFundProfitChartSeriesData(points)

    if (cumulativeProfitChartRef.value !== chartContainer) {
      return
    }
    cumulativeProfitChart = echarts.init(chartContainer)
    cumulativeProfitChart.setOption({
      animationDuration: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 240,
      aria: {
        enabled: true,
        description: cumulativeProfitChartAriaLabel.value,
      },
      tooltip: {
        trigger: 'axis',
        confine: true,
        backgroundColor: tooltipBg,
        borderColor: tooltipBorder,
        textStyle: { color: tooltipText },
        formatter: (params: any) => {
          const items = Array.isArray(params) ? params : [params]
          const item = items.find((entry: any) => {
            const entryValue = Array.isArray(entry?.value) ? entry.value[1] : entry?.value
            return entryValue !== null && entryValue !== undefined && Number.isFinite(Number(entryValue))
          }) ?? items[0]
          const value = Array.isArray(item?.value) ? item.value[1] : item?.value
          const timestamp = Array.isArray(item?.value) ? Number(item.value[0]) : Number(item?.axisValue)
          const dateLabel = Number.isFinite(timestamp)
            ? formatDate(timestamp)
            : item?.axisValueLabel || item?.axisValue || '--'
          return `${dateLabel}<br/>${item?.marker || ''}累计收益 ${formatCurrency(Number(value))}`
        },
      },
      grid: { top: 18, right: 14, bottom: 26, left: 56 },
      xAxis: {
        type: 'time',
        axisLabel: {
          color: axisText,
          fontSize: 10,
          hideOverlap: true,
          formatter: (value: number) => formatDate(value),
        },
        axisLine: { lineStyle: { color: axisLine } },
      },
      yAxis: {
        type: 'value',
        scale: true,
        axisLabel: {
          color: axisText,
          fontSize: 10,
          formatter: (value: number) => formatCompactCurrency(value),
        },
        splitLine: { lineStyle: { color: splitLine } },
      },
      dataZoom: [{ type: 'inside', start: 0, end: 100 }],
      series: [
        {
          name: '累计收益',
          type: 'line',
          smooth: false,
          showSymbol: points.length <= 2,
          symbol: 'circle',
          symbolSize: 7,
          connectNulls: false,
          itemStyle: { color: positiveColor },
          lineStyle: { width: 2, color: positiveColor },
          data: profitSeriesData.positive,
          markLine: {
            silent: true,
            symbol: 'none',
            label: { show: false },
            lineStyle: { color: axisLine, type: 'dashed', width: 1 },
            data: [{ yAxis: 0 }],
          },
        },
        {
          name: '累计收益',
          type: 'line',
          smooth: false,
          showSymbol: points.length <= 2,
          symbol: 'circle',
          symbolSize: 7,
          connectNulls: false,
          itemStyle: { color: negativeColor },
          lineStyle: { width: 2, color: negativeColor },
          data: profitSeriesData.negative,
        },
      ],
    })
    requestAnimationFrame(() => cumulativeProfitChart?.resize())
  }

  function disposeChart() {
    if (chart) {
      chart.dispose()
      chart = null
    }
  }

  function disposeCumulativeProfitChart() {
    if (cumulativeProfitChart) {
      cumulativeProfitChart.dispose()
      cumulativeProfitChart = null
    }
  }

  function disposeCharts() {
    disposeChart()
    disposeCumulativeProfitChart()
  }

  function resizeChart() {
    chart?.resize()
    cumulativeProfitChart?.resize()
  }

  onMounted(() => {
    window.addEventListener('resize', resizeChart)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resizeChart)
    disposeCharts()
  })

  watch([detail, transactions, isDark], async () => {
    await nextTick()
    requestAnimationFrame(() => {
      renderChart()
      renderCumulativeProfitChart()
    })
  })

  watch(options.selectedFundChartView, async () => {
    await nextTick()
    requestAnimationFrame(() => {
      renderChart()
      renderCumulativeProfitChart()
    })
  })

  return {
    chartRef,
    cumulativeProfitChartRef,
  }
}
