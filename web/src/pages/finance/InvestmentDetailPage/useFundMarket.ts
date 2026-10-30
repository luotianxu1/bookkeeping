// 基金/股票外部行情加载与基金累计收益点位计算。
import { computed, ref, watch, type ComputedRef, type Ref } from 'vue'
import type {
  InvestmentAssetDetail,
  InvestmentChartPoint,
  InvestmentPosition,
  InvestmentTransaction,
} from '@/api/modules/finance'
import {
  buildFundPeriodReturnMap,
  buildFundPeriodReturnStats,
  buildFundProfitFallbackPoints,
  buildFundTrendPoints,
  buildFundTrendValueContext,
  compareFundCostTransactions,
  createEmptyFundProfitState,
  applyFundProfitTransaction,
  fetchFundTrend,
  fetchStockKline,
  fetchStockQuote,
  filterFundTrendPoints,
  formatDate,
  FUND_QUANTITY_MATCH_TOLERANCE,
  FUND_TREND_RANGE_RETURN_TITLES,
  fundTrendRangeOptions,
  formatNumber,
  hasReliableFundTradeHistory,
  jsonpRequest,
  normalizeDateLabel,
  parsePointLabelDate,
  resolveFundCostTransactionTime,
  resolveFundProfitNetValue,
  resolveTradeMarkerDateLabel,
  stat,
  formatTencentTime,
  toTencentSymbol,
  toneByNumber,
  type FundPeriodIncreaseResponse,
  type FundTrendRange,
  type FundTrendValueEntry,
  type FundTrendValueMode,
} from './utils'

type FundMarketSnapshot = {
  expiresAt: number
  baseInfo: Record<string, any>
  trend: { netWorthTrend: any[]; acWorthTrend: any[] }
  periodReturns: Record<string, number | null>
}

const FUND_MARKET_CACHE_TTL_MS = 10 * 60 * 1000
const fundMarketCache = new Map<string, FundMarketSnapshot>()

export type UseFundMarketOptions = {
  detail: Ref<InvestmentAssetDetail | null>
  transactions: Ref<InvestmentTransaction[]>
  currentPosition: ComputedRef<InvestmentPosition | null>
  isFundPosition: ComputedRef<boolean>
  currentTotalProfit: ComputedRef<number>
  showFundCumulativeProfitChart: ComputedRef<boolean>
  mergeDetail: (partial: Partial<InvestmentAssetDetail>) => void
}

export function useFundMarket(options: UseFundMarketOptions) {
  const { detail, transactions, currentPosition, isFundPosition, currentTotalProfit, showFundCumulativeProfitChart, mergeDetail } = options

  const externalStatus = ref('')
  const selectedFundTrendRange = ref<FundTrendRange>('3m')
  const fullFundChartPoints = ref<InvestmentChartPoint[]>([])
  const fundPeriodReturnMap = ref<Record<string, number | null>>({})
  const fundTrendValueMap = ref<Record<string, FundTrendValueEntry>>({})
  const fundTrendValueMode = ref<FundTrendValueMode>('cumulative')

  const selectedFundPeriodReturn = computed(() =>
    fundPeriodReturnMap.value[FUND_TREND_RANGE_RETURN_TITLES[selectedFundTrendRange.value]] ?? null,
  )
  const selectedFundPeriodReturnTone = computed<'positive' | 'negative' | 'neutral'>(() => {
    if (externalStatus.value || selectedFundPeriodReturn.value === null) {
      return 'neutral'
    }
    return toneByNumber(selectedFundPeriodReturn.value)
  })
  const fundTrendPeriodReturnText = computed(() => {
    if (externalStatus.value) {
      return externalStatus.value
    }
    const rangeLabel = fundTrendRangeOptions.find((item) => item.value === selectedFundTrendRange.value)?.label ?? ''
    const value = selectedFundPeriodReturn.value
    return `${rangeLabel}收益率 ${value === null ? '--' : `${formatNumber(value)}%`}`
  })

  function resetFundMarketState() {
    fullFundChartPoints.value = []
    fundPeriodReturnMap.value = {}
    fundTrendValueMap.value = {}
    fundTrendValueMode.value = 'cumulative'
    selectedFundTrendRange.value = '3m'
  }

  function resolveFundTradeCumulativeValue(entry: InvestmentTransaction) {
    const dateLabel = resolveTradeMarkerDateLabel(entry, true)
    if (!dateLabel) {
      return null
    }

    const trendEntry = fundTrendValueMap.value[dateLabel]
    const cumulativeValue = Number(trendEntry?.cumulative)
    if (Number.isFinite(cumulativeValue) && cumulativeValue > 0) {
      return cumulativeValue
    }

    const fallbackValue = Number(trendEntry?.net)
    return Number.isFinite(fallbackValue) && fallbackValue > 0 ? fallbackValue : null
  }

  function resolveFundTradeNetValue(entry: InvestmentTransaction) {
    const confirmedPrice = Number(entry.price)
    if (Number.isFinite(confirmedPrice) && confirmedPrice > 0) {
      return confirmedPrice
    }

    const dateLabel = resolveTradeMarkerDateLabel(entry, true)
    if (!dateLabel) {
      return null
    }

    const trendEntry = fundTrendValueMap.value[dateLabel]
    const netValue = Number(trendEntry?.net)
    return Number.isFinite(netValue) && netValue > 0 ? netValue : null
  }

  function resolveFundTradeCumulativeOffset(entry: InvestmentTransaction) {
    const cumulativeValue = Number(resolveFundTradeCumulativeValue(entry))
    const netValue = Number(resolveFundTradeNetValue(entry))
    if (!Number.isFinite(cumulativeValue) || cumulativeValue <= 0 || !Number.isFinite(netValue) || netValue <= 0) {
      return null
    }

    return Math.max(cumulativeValue - netValue, 0)
  }

  function resolveEstimatedFundCumulativeChartCostBaseline() {
    const averageCostPrice = Number(currentPosition.value?.avgCostPrice ?? 0)
    if (!Number.isFinite(averageCostPrice) || averageCostPrice <= 0) {
      return null
    }

    const latestCumulativePoint = fullFundChartPoints.value
      .slice()
      .reverse()
      .find((point) => Number.isFinite(Number(point.value)) && Number(point.value) > 0)
    if (!latestCumulativePoint) {
      return null
    }

    const latestCumulativeValue = Number(latestCumulativePoint.value)
    const latestDateLabel = normalizeDateLabel(latestCumulativePoint.label)
    const mappedNetValue = Number(fundTrendValueMap.value[latestDateLabel]?.net)
    const currentNetValue = Number(detail.value?.latestPrice ?? 0)
    const latestNetValue = Number.isFinite(mappedNetValue) && mappedNetValue > 0
      ? mappedNetValue
      : currentNetValue
    const cumulativeOffset = latestCumulativeValue - latestNetValue
    if (!Number.isFinite(latestNetValue) || latestNetValue <= 0 || !Number.isFinite(cumulativeOffset) || cumulativeOffset < 0) {
      return null
    }

    const baseline = averageCostPrice + cumulativeOffset
    return Number.isFinite(baseline) && baseline > 0 ? baseline : null
  }

  function resolveFundCumulativeChartCostBaseline() {
    const currentHoldingQuantity = Number(currentPosition.value?.holdingQuantity ?? 0)
    if (!Number.isFinite(currentHoldingQuantity) || currentHoldingQuantity <= 0) {
      return null
    }
    const currentCostAmount = Number(currentPosition.value?.costAmount ?? 0)
    if (!Number.isFinite(currentCostAmount) || currentCostAmount <= 0) {
      return null
    }

    let holdingQuantity = 0
    let cumulativeOffsetAmount = 0

    const sortedTransactions = transactions.value
      .filter((entry) => entry.tradeType === 'buy' || entry.tradeType === 'sell')
      .filter((entry) => entry.settlementStatus !== 'pending')
      .slice()
      .sort(compareFundCostTransactions)

    for (const entry of sortedTransactions) {
      const quantity = Number(entry.quantity)
      if (!Number.isFinite(quantity) || quantity <= 0) {
        continue
      }

      if (entry.tradeType === 'buy') {
        const cumulativeOffset = Number(resolveFundTradeCumulativeOffset(entry))
        if (!Number.isFinite(cumulativeOffset) || cumulativeOffset < 0) {
          continue
        }
        holdingQuantity += quantity
        cumulativeOffsetAmount += cumulativeOffset * quantity
        continue
      }

      if (holdingQuantity <= 0) {
        continue
      }

      const soldQuantity = Math.min(quantity, holdingQuantity)
      const averageOffset = cumulativeOffsetAmount / holdingQuantity
      cumulativeOffsetAmount -= averageOffset * soldQuantity
      holdingQuantity -= soldQuantity
    }

    if (holdingQuantity <= 0) {
      return resolveEstimatedFundCumulativeChartCostBaseline()
    }

    const quantityDiff = Number(Math.abs(holdingQuantity - currentHoldingQuantity).toFixed(6))
    if (quantityDiff > FUND_QUANTITY_MATCH_TOLERANCE) {
      return resolveEstimatedFundCumulativeChartCostBaseline()
    }

    const baseline = (currentCostAmount + cumulativeOffsetAmount) / currentHoldingQuantity
    return Number.isFinite(baseline) && baseline > 0
      ? baseline
      : resolveEstimatedFundCumulativeChartCostBaseline()
  }

  const chartCostBaseline = computed<number | null>(() => {
    const costPrice = Number(currentPosition.value?.avgCostPrice ?? 0)
    if (!Number.isFinite(costPrice) || costPrice <= 0) {
      return null
    }
    if (!isFundPosition.value || detail.value?.chartType !== 'line' || fundTrendValueMode.value !== 'cumulative') {
      return costPrice
    }
    return resolveFundCumulativeChartCostBaseline()
  })

  function ensureFundProfitBoundaryPoints(
    points: InvestmentChartPoint[],
    tradeEvents: Array<{ entry: InvestmentTransaction; timestamp: number }>,
    currentProfitOverride: number | null = null,
  ) {
    const position = currentPosition.value
    if (!position) {
      return points
    }

    const normalizedPoints = new Map<string, InvestmentChartPoint>()
    for (const point of points) {
      const label = normalizeDateLabel(point.label)
      const value = Number(point.value)
      if (label && Number.isFinite(value)) {
        normalizedPoints.set(label, { label, value })
      }
    }

    const firstBuyEvent = tradeEvents.find((item) => item.entry.tradeType === 'buy')
    const positionStartTimestamp = firstBuyEvent?.timestamp
      ?? parsePointLabelDate(normalizeDateLabel(position.createdAt))?.getTime()
      ?? NaN
    const firstPointTimestamp = Array.from(normalizedPoints.values())
      .map((point) => parsePointLabelDate(point.label)?.getTime() ?? NaN)
      .filter(Number.isFinite)
      .sort((left, right) => left - right)[0]

    if (Number.isFinite(positionStartTimestamp)
      && (!Number.isFinite(firstPointTimestamp) || positionStartTimestamp >= firstPointTimestamp)) {
      const startLabel = formatDate(positionStartTimestamp)
      if (!normalizedPoints.has(startLabel)) {
        normalizedPoints.set(startLabel, { label: startLabel, value: 0 })
      }
    }

    const lastSyncedTimestamp = new Date(position.lastSyncedAt || detail.value?.updatedAt || '').getTime()
    const currentTimestamp = Number.isFinite(lastSyncedTimestamp) ? lastSyncedTimestamp : Date.now()
    const currentLabel = formatDate(Math.max(currentTimestamp, Number.isFinite(positionStartTimestamp) ? positionStartTimestamp : 0))
    const totalProfit = currentProfitOverride ?? currentTotalProfit.value
    if (currentLabel && Number.isFinite(totalProfit)) {
      normalizedPoints.set(currentLabel, {
        label: currentLabel,
        value: Number(totalProfit.toFixed(2)),
      })
    }

    return Array.from(normalizedPoints.values()).sort((left, right) => {
      const leftTime = parsePointLabelDate(left.label)?.getTime() ?? 0
      const rightTime = parsePointLabelDate(right.label)?.getTime() ?? 0
      return leftTime - rightTime
    })
  }

  function buildFundCumulativeProfitPoints(points: InvestmentChartPoint[]) {
    if (!showFundCumulativeProfitChart.value) {
      return []
    }

    const position = currentPosition.value
    if (!position) {
      return []
    }

    const tradeEvents = transactions.value
      .filter((entry) => entry.status === 'normal')
      .filter((entry) => entry.settlementStatus !== 'pending')
      .filter((entry) => entry.tradeType === 'buy' || entry.tradeType === 'sell')
      .map((entry) => ({ entry, timestamp: resolveFundCostTransactionTime(entry) }))
      .filter((item) => item.timestamp > 0)
      .sort((left, right) => left.timestamp - right.timestamp || Number(left.entry.id) - Number(right.entry.id))

    if (!hasReliableFundTradeHistory(tradeEvents.map((item) => item.entry), Number(position.holdingQuantity))) {
      return ensureFundProfitBoundaryPoints(
        buildFundProfitFallbackPoints(position, points, fundTrendValueMap.value),
        tradeEvents,
      )
    }

    const expectedRealizedProfit = Number(position.cumulativeProfit ?? 0)
    const finalState = tradeEvents.reduce(
      (state, item) => applyFundProfitTransaction(state, item.entry),
      createEmptyFundProfitState(),
    )
    const realizedProfitOffset = Number.isFinite(expectedRealizedProfit)
      ? expectedRealizedProfit - finalState.realizedProfit
      : 0
    let state = createEmptyFundProfitState()
    let transactionIndex = 0
    let hasStarted = false

    const calculatedPoints = points.flatMap((point) => {
      const pointDate = parsePointLabelDate(point.label)
      if (!pointDate) {
        return []
      }
      const pointEndTime = pointDate.getTime() + 24 * 60 * 60 * 1000 - 1
      while (transactionIndex < tradeEvents.length && tradeEvents[transactionIndex].timestamp <= pointEndTime) {
        state = applyFundProfitTransaction(state, tradeEvents[transactionIndex].entry)
        transactionIndex += 1
        hasStarted = true
      }
      if (!hasStarted) {
        return []
      }

      const netValue = resolveFundProfitNetValue(point, fundTrendValueMap.value)
      if (!Number.isFinite(netValue) || netValue <= 0) {
        return []
      }
      const profit = state.realizedProfit
        + realizedProfitOffset
        + state.holdingQuantity * netValue
        - state.costAmount
      return [{
        label: point.label,
        value: Number(profit.toFixed(2)),
      }]
    })
    const currentNetValue = Number(position.currentPrice ?? detail.value?.latestPrice ?? 0)
    const currentProfit = Number.isFinite(currentNetValue) && currentNetValue > 0
      ? Number((
          finalState.realizedProfit
          + realizedProfitOffset
          + finalState.holdingQuantity * currentNetValue
          - finalState.costAmount
        ).toFixed(2))
      : null
    return ensureFundProfitBoundaryPoints(calculatedPoints, tradeEvents, currentProfit)
  }

  const cumulativeProfitPoints = computed(() => buildFundCumulativeProfitPoints(fullFundChartPoints.value))

  async function loadFundMarketData(baseDetail: InvestmentAssetDetail, fundCode: string) {
    const cached = fundMarketCache.get(fundCode)
    let baseInfo: Record<string, any>
    let trend: { netWorthTrend: any[]; acWorthTrend: any[] }
    let periodReturns: Record<string, number | null>
    if (cached && cached.expiresAt > Date.now()) {
      baseInfo = cached.baseInfo
      trend = cached.trend
      periodReturns = cached.periodReturns
    } else {
      const [baseResult, trendResult, periodIncreaseResult] = await Promise.allSettled([
        jsonpRequest<Record<string, any>>(
          `https://fundmobapi.eastmoney.com/FundMApi/FundBaseTypeInformation.ashx?FCODE=${encodeURIComponent(fundCode)}&deviceid=Wap&plat=Wap&product=EFund&version=2.0.0`,
          ['callback'],
        ),
        fetchFundTrend(fundCode),
        jsonpRequest<FundPeriodIncreaseResponse>(
          `https://fundmobapi.eastmoney.com/FundMNewApi/FundMNPeriodIncrease?FCODE=${encodeURIComponent(fundCode)}&deviceid=Wap&plat=Wap&product=EFund&version=2.0.0`,
          ['callback'],
        ),
      ])
      baseInfo = baseResult.status === 'fulfilled' ? baseResult.value?.Datas ?? {} : {}
      const periodIncreaseRows = periodIncreaseResult.status === 'fulfilled'
        ? periodIncreaseResult.value?.Datas ?? []
        : []
      periodReturns = buildFundPeriodReturnMap(periodIncreaseRows)
      trend = trendResult.status === 'fulfilled'
        ? trendResult.value
        : { netWorthTrend: [], acWorthTrend: [] }
      fundMarketCache.set(fundCode, {
        expiresAt: Date.now() + FUND_MARKET_CACHE_TTL_MS,
        baseInfo,
        trend,
        periodReturns,
      })
    }

    const officialPrice = Number(baseInfo.DWJZ)
    const cumulativePrice = Number(baseInfo.LJJZ)
    const latestPrice = officialPrice
    const changePercent = Number(baseInfo.RZDF)
    const updatedAt = baseInfo.FSRQ
    const trendValueContext = buildFundTrendValueContext(
      trend,
      { label: updatedAt, value: cumulativePrice },
      { label: updatedAt, value: latestPrice },
    )
    const chartPoints = buildFundTrendPoints(
      trend,
      { label: updatedAt, value: cumulativePrice },
      { label: updatedAt, value: latestPrice },
    )
    fullFundChartPoints.value = chartPoints
    fundPeriodReturnMap.value = periodReturns
    fundTrendValueMap.value = trendValueContext.values
    fundTrendValueMode.value = trendValueContext.mode

    mergeDetail({
      name: baseInfo.SHORTNAME || baseDetail.name,
      latestPrice: Number.isFinite(latestPrice) ? latestPrice : baseDetail.latestPrice,
      changePercent: Number.isFinite(changePercent) ? changePercent : baseDetail.changePercent,
      updatedAt: updatedAt || baseDetail.updatedAt,
      source: '东方财富（累计净值）',
      chartType: 'line',
      chartPoints: filterFundTrendPoints(chartPoints, selectedFundTrendRange.value),
      marketStats: [
        stat('资产类型', '基金'),
        stat('基金代码', fundCode),
        stat('基金类型', baseInfo.FTYPE || '-'),
        stat('当前净值（单位净值）', Number.isFinite(latestPrice) ? formatNumber(latestPrice, 4) : '-', toneByNumber(changePercent)),
        stat('累计净值', baseInfo.LJJZ || '-'),
        stat('当日涨跌幅', Number.isFinite(changePercent) ? `${formatNumber(changePercent)}%` : '-', toneByNumber(changePercent)),
        stat('净值日期', updatedAt || '-'),
        stat('基金公司', baseInfo.JJGS || '-'),
        stat('申购状态', baseInfo.SGZT || '-'),
        stat('赎回状态', baseInfo.SHZT || '-'),
        ...buildFundPeriodReturnStats(periodReturns),
      ],
    })
  }

  async function loadStockMarketData(baseDetail: InvestmentAssetDetail, stockCode: string) {
    const symbol = toTencentSymbol(stockCode, baseDetail.position.market || baseDetail.market)
    const [quote, kline] = await Promise.all([
      fetchStockQuote(symbol),
      fetchStockKline(symbol).catch(() => []),
    ])

    const latestPrice = Number(quote.price)
    const change = Number(quote.change)
    const changePercent = Number(quote.changePct)

    mergeDetail({
      name: quote.name || baseDetail.name,
      latestPrice: Number.isFinite(latestPrice) ? latestPrice : baseDetail.latestPrice,
      change: Number.isFinite(change) ? change : baseDetail.change,
      changePercent: Number.isFinite(changePercent) ? changePercent : baseDetail.changePercent,
      updatedAt: formatTencentTime(quote.timeRaw) || baseDetail.updatedAt,
      source: '腾讯行情',
      chartType: 'candlestick',
      chartPoints: kline,
      marketStats: [
        stat('资产类型', '股票'),
        stat('股票代码', stockCode),
        stat('市场', baseDetail.market || '-'),
        stat('当前净值（当前价）', Number.isFinite(latestPrice) ? formatNumber(latestPrice, 2) : '-', toneByNumber(change)),
        stat('涨跌额', Number.isFinite(change) ? formatNumber(change, 2) : '-', toneByNumber(change)),
        stat('涨跌幅', Number.isFinite(changePercent) ? `${formatNumber(changePercent)}%` : '-', toneByNumber(changePercent)),
        stat('今开', quote.open || '-'),
        stat('昨收', quote.prevClose || '-'),
        stat('最高', quote.high || '-'),
        stat('最低', quote.low || '-'),
        stat('成交量（手）', quote.volume || '-'),
        stat('更新时间', formatTencentTime(quote.timeRaw) || '-'),
      ],
    })
  }

  async function loadExternalMarketData(baseDetail: InvestmentAssetDetail) {
    const symbol = baseDetail.symbol || baseDetail.position.productSymbol
    if (!symbol) {
      return
    }

    externalStatus.value = '行情加载中...'
    try {
      if (baseDetail.productType === 'stock') {
        await loadStockMarketData(baseDetail, symbol)
      } else if (baseDetail.productType === 'fund') {
        await loadFundMarketData(baseDetail, symbol)
      }
      externalStatus.value = ''
    } catch (error) {
      externalStatus.value = error instanceof Error ? error.message : '行情加载失败'
    }
  }

  function applyFundTrendRange() {
    if (!detail.value || detail.value.productType !== 'fund') {
      return
    }
    const nextPoints = filterFundTrendPoints(fullFundChartPoints.value, selectedFundTrendRange.value)
    mergeDetail({
      chartPoints: nextPoints,
    })
  }

  watch(selectedFundTrendRange, () => {
    applyFundTrendRange()
  })

  return {
    externalStatus,
    selectedFundTrendRange,
    fullFundChartPoints,
    fundPeriodReturnMap,
    fundTrendValueMap,
    fundTrendValueMode,
    selectedFundPeriodReturn,
    selectedFundPeriodReturnTone,
    fundTrendPeriodReturnText,
    chartCostBaseline,
    cumulativeProfitPoints,
    resetFundMarketState,
    loadExternalMarketData,
  }
}

