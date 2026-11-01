import { nextTick, onUnmounted, ref, watch } from 'vue'
import type { ECharts } from 'echarts'
import { getErrorMessage } from '@/api/request'
import { readChartToken } from '@/utils/chart-theme'
import { useTheme } from '@/utils/theme'
import type { StockScreenItem } from '@/api/modules/finance'
import { getQfqDailyKline } from '@/api/modules/market'
import { formatKlineNumber, toScreenerSymbol } from './screenerFormat'

type KlinePoint = {
  label: string
  open: number
  close: number
  high: number
  low: number
  volume: number
}

// 日K经行情中心 /api/market/kline/qfq（腾讯前复权，与投资详情页同口径），不再浏览器直连。
async function fetchStockKline(symbol: string): Promise<KlinePoint[]> {
  const data = await getQfqDailyKline(symbol, 120)
  return (data?.kline ?? [])
    .map(row => ({
      label: row['日期'],
      open: row['开盘'],
      close: row['收盘'],
      high: row['最高'],
      low: row['最低'],
      volume: row['成交量'],
    }))
    .filter((item: KlinePoint) => item.label && Number.isFinite(item.close))
}

export function useExpandedKline(options: {
  cardKey: (item: StockScreenItem) => string
  onError: (message: string) => void
}) {
  const { cardKey, onError } = options

  // 展开的股票 K 线：一次只展开一只，数据前端直连腾讯行情，按 key 缓存已拉取的点位。
  const expandedKey = ref('')
  const klineLoadingKey = ref('')
  const klineErrorKey = ref('')
  const klineChartError = ref('')
  const klineCache = new Map<string, KlinePoint[]>()
  const klineCharts = new Map<string, ECharts>()
  const klineContainers = new Map<string, HTMLElement>()

  async function toggleCard(item: StockScreenItem) {
    const key = cardKey(item)
    if (expandedKey.value === key) {
      disposeKlineChart(key)
      expandedKey.value = ''
      return
    }
    // 一次只展开一只：先销毁上一只的图表实例。
    if (expandedKey.value) {
      disposeKlineChart(expandedKey.value)
    }
    expandedKey.value = key
    await loadKline(item)
  }

  async function loadKline(item: StockScreenItem) {
    const key = cardKey(item)
    klineErrorKey.value = ''
    if (klineCache.has(key)) {
      await renderKline(key)
      return
    }
    klineLoadingKey.value = key
    try {
      const points = await fetchStockKline(toScreenerSymbol(item.stockCode, item.market))
      if (points.length === 0) {
        throw new Error('未获取到日K数据')
      }
      klineCache.set(key, points)
      // 拉取期间用户可能已收起或切换到别的卡片。
      if (expandedKey.value === key) {
        await renderKline(key)
      }
    } catch (error) {
      if (expandedKey.value === key) {
        klineErrorKey.value = key
        onError('')
        klineChartError.value = getErrorMessage(error, '日K加载失败')
      }
    } finally {
      if (klineLoadingKey.value === key) {
        klineLoadingKey.value = ''
      }
    }
  }

  async function renderKline(key: string) {
    const points = klineCache.get(key)
    if (!points || points.length === 0) {
      return
    }
    await nextTick()
    const container = klineContainers.get(key)
    if (!container || expandedKey.value !== key) {
      return
    }
    const echarts = await import('@/utils/echarts')
    let chart = klineCharts.get(key)
    if (!chart) {
      chart = echarts.init(container)
      klineCharts.set(key, chart)
    }

    const rootStyle = getComputedStyle(document.documentElement)
    const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
    const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
    const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()
    const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
    const splitLine = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
    // 红涨绿跌：颜色走全局令牌，随明暗主题切换。
    const upColor = readChartToken('--color-danger', '#dc2626')
    const downColor = readChartToken('--color-success', '#16a34a')
    const dates = points.map((point) => point.label)
    const candleData = points.map((point) => [point.open, point.close, point.low, point.high])
    // 成交量柱子跟随当日 K 线红涨绿跌：收盘 ≥ 开盘为红，否则为绿。
    const volumeData = points.map((point) => ({
      value: point.volume ?? 0,
      itemStyle: { color: point.close >= point.open ? upColor : downColor },
    }))
    // 默认展示最近半个月（约 10 个交易日）的行情，K线与成交量共用同一 dataZoom 保持横轴对应。
    const defaultSpan = 10
    const zoomStartIndex = Math.max(0, points.length - defaultSpan)
    const zoomEndIndex = Math.max(0, points.length - 1)

    chart.setOption({
      animation: false,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross' },
        confine: true,
        // tooltip 固定停在图表顶部，避免压在光标/手指下方遮挡数据。
        position: (point: number[], _params: unknown, _dom: unknown, _rect: unknown, size: { contentSize: number[] }) => {
          const [pointerX] = point
          const [tooltipWidth] = size.contentSize
          const chartWidth = chart?.getWidth() ?? 0
          const x = Math.min(Math.max(pointerX - tooltipWidth / 2, 0), Math.max(0, chartWidth - tooltipWidth))
          return [x, 0]
        },
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
          for (const entry of items) {
            if (entry.seriesType === 'candlestick' && Array.isArray(entry.data)) {
              const [, open, close, low, high] = entry.data
              lines.push(`${entry.marker}${entry.seriesName} 开 ${formatKlineNumber(open)} 收 ${formatKlineNumber(close)} 高 ${formatKlineNumber(high)} 低 ${formatKlineNumber(low)}`)
              continue
            }
            if (entry.seriesType === 'bar') {
              lines.push(`${entry.marker}${entry.seriesName} ${formatKlineNumber(entry.value, 0)}`)
            }
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
        textStyle: { color: axisText, fontSize: 11 },
        data: ['日K', '成交量'],
      },
      grid: [
        { left: 42, right: 14, top: 42, height: '54%' },
        { left: 42, right: 14, top: '76%', height: '14%' },
      ],
      xAxis: [
        { type: 'category', data: dates, boundaryGap: true, axisTick: { alignWithLabel: true }, axisLabel: { color: axisText, fontSize: 10 } },
        { type: 'category', gridIndex: 1, data: dates, boundaryGap: true, axisTick: { alignWithLabel: true, show: false }, axisLabel: { show: false } },
      ],
      yAxis: [
        { scale: true, axisLabel: { color: axisText, fontSize: 10 }, splitLine: { lineStyle: { color: splitLine } } },
        { scale: true, gridIndex: 1, axisLabel: { show: false }, axisTick: { show: false }, axisLine: { show: false }, splitLine: { show: false } },
      ],
      dataZoom: [{ type: 'inside', xAxisIndex: [0, 1], startValue: zoomStartIndex, endValue: zoomEndIndex }],
      series: [
        {
          name: '日K',
          type: 'candlestick',
          data: candleData,
          barWidth: '60%',
          itemStyle: {
            color: upColor,
            color0: downColor,
            borderColor: upColor,
            borderColor0: downColor,
          },
        },
        {
          name: '成交量',
          type: 'bar',
          xAxisIndex: 1,
          yAxisIndex: 1,
          data: volumeData,
          barWidth: '60%',
        },
      ],
    }, true)
    requestAnimationFrame(() => chart?.resize())
  }

  function setKlineContainer(key: string, el: Element | null) {
    if (el instanceof HTMLElement) {
      klineContainers.set(key, el)
    } else {
      klineContainers.delete(key)
    }
  }

  function disposeKlineChart(key: string) {
    const chart = klineCharts.get(key)
    if (chart) {
      chart.dispose()
      klineCharts.delete(key)
    }
    if (klineErrorKey.value === key) {
      klineErrorKey.value = ''
    }
  }

  function disposeAllKlineCharts() {
    for (const chart of klineCharts.values()) {
      chart.dispose()
    }
    klineCharts.clear()
  }

  // 主题切换时重建当前展开的 K 线图，让蜡烛红绿跟随令牌换色。
  const { isDark } = useTheme()
  watch(isDark, () => {
    const key = expandedKey.value
    if (key && klineCache.has(key)) {
      void renderKline(key)
    }
  })

  onUnmounted(() => {
    disposeAllKlineCharts()
  })

  return {
    expandedKey,
    klineLoadingKey,
    klineErrorKey,
    klineChartError,
    toggleCard,
    loadKline,
    setKlineContainer,
    disposeAllKlineCharts,
  }
}
