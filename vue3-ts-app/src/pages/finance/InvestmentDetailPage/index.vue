<script setup lang="ts">
// 投资详情页：通过后端聚合接口展示持仓、实时行情和走势，并支持加仓、减仓、修改。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { ECharts } from 'echarts'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  createInvestmentAutoInvestPlan,
  createInvestmentTransaction,
  deleteInvestmentPosition,
  deleteInvestmentAutoInvestPlan,
  getAccounts,
  getInvestmentAutoInvestPlans,
  getInvestmentPositionDetail,
  getInvestmentTransactions,
  updateInvestmentAutoInvestPlan,
  updateInvestmentPosition,
  type Account,
  type InvestmentAssetDetail,
  type InvestmentAutoInvestFrequency,
  type InvestmentAutoInvestPlan,
  type InvestmentChartPoint,
  type InvestmentDetailStat,
  type InvestmentDividendRecord,
  type InvestmentFundRedeemFeeOption,
  type InvestmentPosition,
  type InvestmentTransaction,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

type FundTrendRange = '1m' | '3m' | '6m' | '1y' | '3y'
type TradeFundFeeMode = 'auto' | `manual-${number}`

const route = useRoute()
const router = useRouter()
const isLoading = ref(false)
const pageError = ref('')
const detail = ref<InvestmentAssetDetail | null>(null)
const transactions = ref<InvestmentTransaction[]>([])
const autoInvestPlans = ref<InvestmentAutoInvestPlan[]>([])
const fundingAccounts = ref<Account[]>([])
const externalStatus = ref('')
const chartRef = ref<HTMLDivElement | null>(null)
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showTradeModal = ref(false)
const currentTradeAction = ref<'buy' | 'sell'>('buy')
const tradeInputMode = ref<'amount' | 'quantity'>('amount')
const tradeFundingAccountId = ref('')
const tradeAmount = ref('')
const tradeQuantity = ref('')
const tradePrice = ref('')
const tradeRemark = ref('')
const tradeError = ref('')
const tradeTimeSlot = ref<'before_1500' | 'after_1500'>('before_1500')
const tradeFundFeeMode = ref<TradeFundFeeMode>('auto')
const showEditModal = ref(false)
const editPrice = ref('')
const editHoldingQuantity = ref('')
const editCostPrice = ref('')
const editIncludeInNetWorth = ref(true)
const editRemark = ref('')
const editError = ref('')
const isSubmitting = ref(false)
const showAutoInvestModal = ref(false)
const editingAutoInvestPlanId = ref<number | null>(null)
const autoInvestFundingAccountId = ref('')
const autoInvestFrequency = ref<InvestmentAutoInvestFrequency>('daily')
const autoInvestAmount = ref('')
const autoInvestNextExecuteDate = ref('')
const autoInvestRemark = ref('')
const autoInvestError = ref('')
const isSavingAutoInvest = ref(false)
const showDeleteModal = ref(false)
const isDeletingPosition = ref(false)
const deleteError = ref('')
const selectedFundTrendRange = ref<FundTrendRange>('3m')
const fullFundChartPoints = ref<InvestmentChartPoint[]>([])
let chart: ECharts | null = null

const positionId = computed(() => {
  const raw = Array.isArray(route.query.positionId) ? route.query.positionId[0] : route.query.positionId
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

const backTo = computed(() => {
  const accountId = detail.value?.position?.accountId
  return accountId ? `/finance/accounts/investment/${accountId}` : '/finance/accounts/investment'
})

const summaryAmount = computed(() =>
  currentPosition.value?.subscriptionStatus === 'pending'
    ? formatCurrency(Number(detail.value?.position?.costAmount ?? 0))
    : formatCurrency(Number(detail.value?.position?.marketValue ?? 0)),
)
const todayProfitValue = computed(() => {
  if (currentPosition.value?.subscriptionStatus === 'pending') {
    return '--'
  }
  if (detail.value?.position?.dayProfit === null || detail.value?.position?.dayProfit === undefined) {
    return '--'
  }
  return formatCurrency(Number(detail.value.position.dayProfit))
})
const todayProfitTone = computed<'positive' | 'negative' | 'neutral'>(() => {
  if (currentPosition.value?.subscriptionStatus === 'pending') {
    return 'neutral'
  }
  if (detail.value?.position?.dayProfit === null || detail.value?.position?.dayProfit === undefined) {
    return 'neutral'
  }
  return toneByNumber(Number(detail.value.position.dayProfit))
})
const todayValue = computed(() => {
  if (detail.value?.position?.dayProfitRate === null || detail.value?.position?.dayProfitRate === undefined) {
    return '--'
  }
  return formatNumber(Number(detail.value.position.dayProfitRate)) + '%'
})
const todayTone = computed<'positive' | 'negative' | 'neutral'>(() => {
  const fallbackRate = detail.value?.position?.dayProfitRate
  const resolvedValue = fallbackRate === null || fallbackRate === undefined ? null : Number(fallbackRate)
  if (resolvedValue === null || !Number.isFinite(resolvedValue) || resolvedValue === 0) {
    return 'neutral'
  }
  return resolvedValue > 0 ? 'positive' : 'negative'
})
const dividendRecords = computed<InvestmentDividendRecord[]>(() => detail.value?.dividendRecords ?? [])
const showTodayMetrics = computed(() => {
  if (currentPosition.value?.subscriptionStatus === 'pending') {
    return false
  }
  return isTodayMarketData(currentPosition.value?.lastSyncedAt)
})
const displayUpdatedAt = computed(() => {
  if (currentPosition.value?.subscriptionStatus === 'pending') {
    return '待确认'
  }
  const syncedAt = formatDateTimeLabel(currentPosition.value?.lastSyncedAt)
  return syncedAt === '--' ? '同步于 --' : `同步于 ${syncedAt}`
})
const transactionCountText = computed(() => `共 ${transactions.value.length} 条`)
const currentPosition = computed<InvestmentPosition | null>(() => detail.value?.position ?? null)
const isPendingSubscription = computed(() => currentPosition.value?.subscriptionStatus === 'pending')
const isFundPosition = computed(() => (detail.value?.productType || currentPosition.value?.productType) === 'fund')
const showAutoInvestSection = computed(() => isFundPosition.value && !isPendingSubscription.value)
const currentUnitName = computed(() => detail.value?.unitName || detail.value?.position.unitName || '份')
const chartCostBaseline = computed(() => {
  const costPrice = Number(currentPosition.value?.avgCostPrice ?? 0)
  return Number.isFinite(costPrice) && costPrice > 0 ? costPrice : null
})
const tradeModalTitle = computed(() => currentTradeAction.value === 'buy' ? '加仓' : '减仓')
const tradeAmountLabel = computed(() => {
  if (isFundPosition.value) {
    return currentTradeAction.value === 'buy' ? '申购金额' : '回款金额'
  }
  return currentTradeAction.value === 'buy' ? '加仓金额' : '减仓金额'
})
const tradeAccountLabel = computed(() => currentTradeAction.value === 'buy' ? '资金账户' : '回款账户')
const fundRedeemFeeOptions = computed<InvestmentFundRedeemFeeOption[]>(() => detail.value?.fundRedeemFeeOptions ?? [])
const showFundRedeemFeeSelector = computed(() =>
  isFundPosition.value && currentTradeAction.value === 'sell' && fundRedeemFeeOptions.value.length > 0,
)
const selectedTradeFundFeeOption = computed<InvestmentFundRedeemFeeOption | null>(() => {
  if (tradeFundFeeMode.value === 'auto') {
    return null
  }
  const index = Number(tradeFundFeeMode.value.replace('manual-', ''))
  return Number.isInteger(index) && index >= 0 ? fundRedeemFeeOptions.value[index] ?? null : null
})
const tradeFundFeeSelectOptions = computed(() => [
  { label: '自动计算（推荐）', value: 'auto' as const },
  ...fundRedeemFeeOptions.value.map((option, index) => ({
    label: `${option.label}（${formatPercentValue(Number(option.feeRate))}）`,
    value: `manual-${index}` as TradeFundFeeMode,
  })),
])
const editFundCostAmountPreview = computed(() => {
  const quantity = Number(editHoldingQuantity.value)
  const costPrice = Number(editCostPrice.value)
  if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isFinite(costPrice) || costPrice < 0) {
    return '--'
  }
  return formatCurrency(quantity * costPrice)
})
const showTradeInputMode = computed(() => currentTradeAction.value === 'buy' && !isFundPosition.value)
const tradePrimaryLabel = computed(() => {
  if (isFundPosition.value) {
    return currentTradeAction.value === 'buy' ? '申购金额' : '赎回份额'
  }
  if (currentTradeAction.value === 'buy') {
    return tradeInputMode.value === 'amount' ? '加仓金额' : '加仓份额'
  }
  return '减仓金额'
})
const tradePrimaryPlaceholder = computed(() => {
  if (isFundPosition.value) {
    return currentTradeAction.value === 'buy' ? '请输入申购金额' : '请输入赎回份额'
  }
  if (currentTradeAction.value === 'buy') {
    return tradeInputMode.value === 'amount' ? '请输入金额' : '请输入份额'
  }
  return '请输入金额'
})
const tradeModeOptions = ['按金额', '按份额和净值']
const autoInvestFrequencyOptions = [
  { label: '每日', value: 'daily' },
  { label: '每周', value: 'weekly' },
  { label: '每月', value: 'monthly' },
]
const tradeTimeSlotOptions = [
  { label: '15点前', value: 'before_1500' },
  { label: '15点后', value: 'after_1500' },
]
const fundTrendRangeOptions = [
  { label: '近1月', value: '1m' },
  { label: '近3月', value: '3m' },
  { label: '近6月', value: '6m' },
  { label: '近1年', value: '1y' },
  { label: '近3年', value: '3y' },
]
const tradeModeValue = computed({
  get: () => tradeInputMode.value === 'quantity' ? '按份额和净值' : '按金额',
  set: (value: string) => {
    tradeInputMode.value = value === '按份额和净值' ? 'quantity' : 'amount'
  },
})
const showTradePriceField = computed(() => !isFundPosition.value)
const showTradeQuantityPreview = computed(() => !isFundPosition.value)
const showTradeAmountPreview = computed(() => !isFundPosition.value && currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity')
const tradeQuantityPreview = computed(() => {
  const amount = getTradeAmountValue()
  const quantity = getTradeQuantityValue()
  if (!Number.isFinite(quantity) || quantity <= 0) {
    return '--'
  }
  if (currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity') {
    return `${formatNumber(quantity, 2)} ${currentUnitName.value}`
  }
  const price = Number(tradePrice.value)
  if (!Number.isFinite(amount) || !Number.isFinite(price) || amount <= 0 || price <= 0) {
    return '--'
  }
  return `${formatNumber(quantity, 2)} ${currentUnitName.value}`
})
const tradeAmountPreview = computed(() => {
  const amount = getTradeAmountValue()
  if (!Number.isFinite(amount) || amount <= 0) {
    return '--'
  }
  return formatCurrency(amount)
})
const tradeFundFeeAmountPreview = computed(() => {
  if (!showFundRedeemFeeSelector.value) {
    return '--'
  }
  if (tradeFundFeeMode.value === 'auto') {
    return '按持仓批次自动计算'
  }
  const amount = getTradeFundSellFeeAmountValue()
  return Number.isFinite(amount) && amount >= 0 ? formatCurrency(amount) : '--'
})
const tradeFundNetAmountPreview = computed(() => {
  if (!showFundRedeemFeeSelector.value) {
    return '--'
  }
  const amount = getTradeAmountValue()
  if (!Number.isFinite(amount) || amount <= 0) {
    return '--'
  }
  if (tradeFundFeeMode.value === 'auto') {
    return '以提交结果为准'
  }
  const feeAmount = getTradeFundSellFeeAmountValue()
  return Number.isFinite(feeAmount) ? formatCurrency(amount - feeAmount) : '--'
})

function getTradeAmountValue() {
  if (isFundPosition.value) {
    if (currentTradeAction.value === 'buy') {
      return Number(tradeAmount.value)
    }
    const quantity = Number(tradeQuantity.value)
    const price = Number(detail.value?.latestPrice ?? currentPosition.value?.currentPrice ?? 0)
    return Number.isFinite(quantity) && quantity > 0 && Number.isFinite(price) && price > 0 ? quantity * price : NaN
  }
  if (currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity') {
    const quantity = Number(tradeQuantity.value)
    const price = Number(tradePrice.value)
    return quantity > 0 && price > 0 ? quantity * price : NaN
  }
  return Number(tradeAmount.value)
}

function getTradeFundSellFeeAmountValue() {
  if (!isFundPosition.value || currentTradeAction.value !== 'sell') {
    return NaN
  }
  const option = selectedTradeFundFeeOption.value
  const amount = getTradeAmountValue()
  if (!option || !Number.isFinite(amount) || amount <= 0) {
    return NaN
  }
  const feeRate = Number(option.feeRate)
  if (!Number.isFinite(feeRate) || feeRate < 0) {
    return NaN
  }
  return Number((amount * feeRate).toFixed(2))
}

function getTradeFundSellFeeAmountPayload() {
  if (!isFundPosition.value || currentTradeAction.value !== 'sell' || tradeFundFeeMode.value === 'auto') {
    return undefined
  }
  const feeAmount = getTradeFundSellFeeAmountValue()
  return Number.isFinite(feeAmount) && feeAmount >= 0 ? feeAmount : undefined
}

function getTradeQuantityValue() {
  if (isFundPosition.value) {
    if (currentTradeAction.value === 'sell') {
      return Number(tradeQuantity.value)
    }
    return NaN
  }
  if (currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity') {
    return Number(tradeQuantity.value)
  }
  const amount = Number(tradeAmount.value)
  const price = Number(tradePrice.value)
  if (!Number.isFinite(amount) || !Number.isFinite(price) || amount <= 0 || price <= 0) {
    return NaN
  }
  return amount / price
}

onMounted(() => {
  loadDetail()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  disposeChart()
})

watch([detail, transactions], async () => {
  await nextTick()
  requestAnimationFrame(() => {
    renderChart()
  })
})

watch(selectedFundTrendRange, () => {
  applyFundTrendRange()
})

async function loadDetail() {
  if (!positionId.value) {
    pageError.value = '投资资产不存在'
    return
  }

  isLoading.value = true
  pageError.value = ''
  try {
    fullFundChartPoints.value = []
    selectedFundTrendRange.value = '3m'
    const currentUser = getStoredCurrentUser()
    const [detailData, transactionList, planList, accountList] = await Promise.all([
      getInvestmentPositionDetail(positionId.value),
      currentUser
        ? getInvestmentTransactions({ userId: currentUser.id, positionId: positionId.value })
        : Promise.resolve([]),
      currentUser
        ? getInvestmentAutoInvestPlans({ userId: currentUser.id, positionId: positionId.value })
        : Promise.resolve([]),
      currentUser
        ? getAccounts({ userId: currentUser.id, status: 'active' })
        : Promise.resolve([]),
    ])
    detail.value = detailData
    transactions.value = transactionList
    autoInvestPlans.value = planList
    fundingAccounts.value = accountList.filter((account) => account.accountTypeCode === 'cash')
    loadExternalMarketData(detailData)
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '投资详情加载失败'
  } finally {
    isLoading.value = false
  }
}

async function renderChart() {
  if (!chartRef.value || !detail.value) {
    return
  }
  disposeChart()

  const points = detail.value.chartPoints ?? []
  if (points.length === 0) {
    return
  }

  const echarts = await import('echarts')
  chart = echarts.init(chartRef.value)
  if (detail.value.chartType === 'candlestick') {
    renderStockChart(points)
  } else {
    renderLineChart(points)
  }
  requestAnimationFrame(() => chart?.resize())
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

async function loadFundMarketData(baseDetail: InvestmentAssetDetail, fundCode: string) {
  const [baseResult, trendResult] = await Promise.allSettled([
    jsonpRequest<Record<string, any>>(
      `https://fundmobapi.eastmoney.com/FundMApi/FundBaseTypeInformation.ashx?FCODE=${encodeURIComponent(fundCode)}&deviceid=Wap&plat=Wap&product=EFund&version=2.0.0`,
      ['callback'],
    ),
    fetchFundTrend(fundCode),
  ])

  const baseInfo = baseResult.status === 'fulfilled' ? baseResult.value?.Datas ?? {} : {}
  const officialPrice = Number(baseInfo.DWJZ)
  const cumulativePrice = Number(baseInfo.LJJZ)
  const latestPrice = officialPrice
  const changePercent = Number(baseInfo.RZDF)
  const updatedAt = baseInfo.FSRQ
  const chartPoints = trendResult.status === 'fulfilled'
    ? buildFundTrendPoints(
        trendResult.value,
        { label: updatedAt, value: cumulativePrice },
        { label: updatedAt, value: latestPrice },
      )
    : mergeLatestFundTrendPoint([], {
        label: updatedAt,
        value: Number.isFinite(cumulativePrice) ? cumulativePrice : latestPrice,
      })
  fullFundChartPoints.value = chartPoints

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

function renderLineChart(points: InvestmentChartPoint[]) {
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

  if (costPrice !== null) {
    series.push({
      name: isFundTrendChart ? '持仓成本' : '持仓成本价',
      type: 'line',
      smooth: false,
      showSymbol: false,
      data: points.map(() => costPrice),
      lineStyle: {
        width: 1.5,
        type: 'dashed',
      },
    })
  }

  series.push(...tradeMarkerSeries)

  chart?.setOption({
    color: ['#1D4ED8', '#F59E0B', '#2563EB', '#DC2626'],
    tooltip: {
      trigger: 'axis',
      confine: true,
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
        color: '#64748B',
        fontSize: 11,
      },
      data: series.map((entry) => entry.name),
    },
    grid: { top: 42, right: 14, bottom: 26, left: 42 },
    xAxis: {
      type: 'category',
      data: points.map((point) => point.label),
      axisLabel: { color: '#64748B', fontSize: 10 },
      axisLine: { lineStyle: { color: '#D9E5FF' } },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: {
        color: '#64748B',
        fontSize: 10,
        formatter: (value: number) => formatAxisChangeLabel(value, baselineValue),
      },
      splitLine: { lineStyle: { color: '#EDF2FB' } },
    },
    dataZoom: [{ type: 'inside', start: 0, end: 100 }],
    series,
  })
}

function renderStockChart(points: InvestmentChartPoint[]) {
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
        color: '#64748B',
        fontSize: 11,
      },
      data: ['K线', '买入点', '卖出点'],
    },
    grid: [
      { left: 42, right: 14, top: 42, height: '54%' },
      { left: 42, right: 14, top: '76%', height: '14%' },
    ],
    xAxis: [
      { type: 'category', data: dates, boundaryGap: false, axisLabel: { color: '#64748B', fontSize: 10 } },
      { type: 'category', gridIndex: 1, data: dates, boundaryGap: false, axisLabel: { show: false }, axisTick: { show: false } },
    ],
    yAxis: [
      {
        scale: true,
        axisLabel: {
          color: '#64748B',
          fontSize: 10,
          formatter: (value: number) => formatAxisChangeLabel(value),
        },
        splitLine: { lineStyle: { color: '#EDF2FB' } },
      },
      { scale: true, gridIndex: 1, splitNumber: 2, axisLabel: { color: '#64748B', fontSize: 10 }, splitLine: { show: false } },
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
        itemStyle: { color: '#93C5FD' },
      },
      ...tradeMarkerSeries,
    ],
  })
}

function buildTradeMarkerSeries(points: InvestmentChartPoint[], chartType: 'line' | 'candlestick') {
  const pointMap = new Map(points.map((point) => [normalizeDateLabel(point.label), point]))
  const buyData: Array<Record<string, any>> = []
  const sellData: Array<Record<string, any>> = []

  for (const entry of transactions.value) {
    if (entry.tradeType !== 'buy' && entry.tradeType !== 'sell') {
      continue
    }

    const point = pointMap.get(normalizeDateLabel(entry.tradeAt))
    if (!point) {
      continue
    }

    const rawPrice = Number(entry.price)
    const fallbackPrice = chartType === 'candlestick'
      ? Number(point.close ?? point.open ?? point.high ?? point.low ?? point.value)
      : Number(point.value ?? point.close ?? point.open ?? point.high ?? point.low)
    const targetPrice = Number.isFinite(rawPrice) && rawPrice > 0 ? rawPrice : fallbackPrice
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
      symbolSize: 10,
      itemStyle: { color: '#2563EB' },
      emphasis: { scale: 1.15 },
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
      symbolSize: 12,
      itemStyle: { color: '#DC2626' },
      emphasis: { scale: 1.15 },
      z: 5,
    })
  }

  return result
}

function disposeChart() {
  if (chart) {
    chart.dispose()
    chart = null
  }
}

function resizeChart() {
  chart?.resize()
}

function openTradeModal(action: 'buy' | 'sell') {
  const position = currentPosition.value
  if (!position || isPendingSubscription.value) {
    return
  }
  currentTradeAction.value = action
  tradeInputMode.value = 'amount'
  tradeTimeSlot.value = 'before_1500'
  tradeFundingAccountId.value = fundingAccounts.value[0] ? String(fundingAccounts.value[0].id) : ''
  tradeAmount.value = ''
  tradeQuantity.value = ''
  tradePrice.value = isFundPosition.value ? '' : String(Number(detail.value?.latestPrice ?? position.currentPrice ?? 0) || '')
  tradeRemark.value = ''
  tradeFundFeeMode.value = 'auto'
  tradeError.value = ''
  showTradeModal.value = true
}

function closeTradeModal(force = false) {
  if (isSubmitting.value && !force) {
    return
  }
  showTradeModal.value = false
  tradeError.value = ''
}

function openEditModal() {
  const position = currentPosition.value
  if (!position) {
    return
  }
  editPrice.value = isPendingSubscription.value ? '' : String(Number(detail.value?.latestPrice ?? position.currentPrice ?? 0) || '')
  editHoldingQuantity.value = isFundPosition.value
    ? formatEditableFundQuantity(position.holdingQuantity)
    : String(Number(position.holdingQuantity ?? 0) || '')
  editCostPrice.value = String(Number(position.avgCostPrice ?? 0) || '')
  editIncludeInNetWorth.value = Boolean(position.includeInNetWorth)
  editRemark.value = position.remark || ''
  editError.value = ''
  showEditModal.value = true
}

function closeEditModal(force = false) {
  if (isSubmitting.value && !force) {
    return
  }
  showEditModal.value = false
  editError.value = ''
}

function resetAutoInvestForm() {
  autoInvestFundingAccountId.value = fundingAccounts.value[0] ? String(fundingAccounts.value[0].id) : ''
  autoInvestFrequency.value = 'daily'
  autoInvestAmount.value = ''
  autoInvestNextExecuteDate.value = formatDateInput(new Date())
  autoInvestRemark.value = ''
  autoInvestError.value = ''
}

function openAutoInvestModal(plan?: InvestmentAutoInvestPlan) {
  if (!currentPosition.value || !showAutoInvestSection.value) {
    return
  }
  autoInvestError.value = ''
  if (!plan) {
    editingAutoInvestPlanId.value = null
    resetAutoInvestForm()
  } else {
    editingAutoInvestPlanId.value = plan.id
    autoInvestFundingAccountId.value = String(plan.fundingAccountId)
    autoInvestFrequency.value = plan.frequency === 'monthly'
      ? 'monthly'
      : plan.frequency === 'weekly'
        ? 'weekly'
        : 'daily'
    autoInvestAmount.value = String(Number(plan.amount) || '')
    autoInvestNextExecuteDate.value = plan.nextExecuteDate
    autoInvestRemark.value = plan.remark || ''
  }
  showAutoInvestModal.value = true
}

function closeAutoInvestModal(force = false) {
  if (isSavingAutoInvest.value && !force) {
    return
  }
  showAutoInvestModal.value = false
  autoInvestError.value = ''
}

function openDeleteModal() {
  if (!currentPosition.value || isDeletingPosition.value) {
    return
  }
  deleteError.value = ''
  showDeleteModal.value = true
}

function closeDeleteModal(force = false) {
  if (isDeletingPosition.value && !force) {
    return
  }
  showDeleteModal.value = false
  deleteError.value = ''
}

async function saveAutoInvestPlan() {
  if (isSavingAutoInvest.value || !currentPosition.value) {
    return
  }
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    autoInvestError.value = '请先登录后再操作'
    return
  }
  const fundingAccountId = Number(autoInvestFundingAccountId.value)
  const amount = Number(autoInvestAmount.value)
  if (!Number.isFinite(fundingAccountId) || fundingAccountId <= 0) {
    autoInvestError.value = '请选择资金账户'
    return
  }
  if (!Number.isFinite(amount) || amount <= 0) {
    autoInvestError.value = '请输入有效的定投金额'
    return
  }
  if (!autoInvestNextExecuteDate.value) {
    autoInvestError.value = '请选择下次执行日期'
    return
  }

  isSavingAutoInvest.value = true
  autoInvestError.value = ''

  try {
    const existingPlan = editingAutoInvestPlanId.value
      ? autoInvestPlans.value.find((item) => item.id === editingAutoInvestPlanId.value) ?? null
      : null
    const payload = {
      userId: currentUser.id,
      accountId: currentPosition.value.accountId,
      positionId: currentPosition.value.id,
      fundingAccountId,
      frequency: autoInvestFrequency.value,
      amount: Number(amount.toFixed(2)),
      currencyCode: currentPosition.value.currencyCode || 'CNY',
      nextExecuteDate: autoInvestNextExecuteDate.value,
      status: existingPlan?.status === 'paused' ? 'paused' as const : existingPlan?.status === 'cancelled' ? 'cancelled' as const : 'active' as const,
      remark: autoInvestRemark.value.trim() || null,
    }
    if (editingAutoInvestPlanId.value) {
      await updateInvestmentAutoInvestPlan(editingAutoInvestPlanId.value, payload)
    } else {
      await createInvestmentAutoInvestPlan(payload)
    }
    closeAutoInvestModal(true)
    showFeedback(editingAutoInvestPlanId.value ? '定投计划已更新' : '定投计划已创建', 'success')
    await loadDetail()
  } catch (error) {
    const message = error instanceof Error ? error.message : '定投计划保存失败'
    autoInvestError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingAutoInvest.value = false
  }
}

async function updateAutoInvestPlanStatus(plan: InvestmentAutoInvestPlan, status: 'active' | 'paused' | 'cancelled') {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || isSavingAutoInvest.value) {
    return
  }
  isSavingAutoInvest.value = true
  try {
    await updateInvestmentAutoInvestPlan(plan.id, {
      userId: currentUser.id,
      accountId: plan.accountId,
      positionId: plan.positionId,
      fundingAccountId: plan.fundingAccountId,
      frequency: plan.frequency === 'monthly'
        ? 'monthly'
        : plan.frequency === 'weekly'
          ? 'weekly'
          : 'daily',
      amount: plan.amount,
      currencyCode: plan.currencyCode || 'CNY',
      nextExecuteDate: plan.nextExecuteDate,
      status,
      remark: plan.remark || null,
    })
    showFeedback(status === 'active' ? '定投计划已恢复' : status === 'paused' ? '定投计划已暂停' : '定投计划已停用', 'success')
    await loadDetail()
  } catch (error) {
    showFeedback(error instanceof Error ? error.message : '定投计划更新失败', 'error')
  } finally {
    isSavingAutoInvest.value = false
  }
}

async function removeAutoInvestPlan(plan: InvestmentAutoInvestPlan) {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || isSavingAutoInvest.value) {
    return
  }
  if (!window.confirm('确认删除该定投计划吗？')) {
    return
  }
  isSavingAutoInvest.value = true
  try {
    await deleteInvestmentAutoInvestPlan(plan.id, currentUser.id)
    showFeedback('定投计划已删除', 'success')
    await loadDetail()
  } catch (error) {
    showFeedback(error instanceof Error ? error.message : '删除定投计划失败', 'error')
  } finally {
    isSavingAutoInvest.value = false
  }
}

async function confirmDeletePosition() {
  if (!currentPosition.value || isDeletingPosition.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    deleteError.value = '请先登录后再删除投资资产'
    return
  }

  isDeletingPosition.value = true
  deleteError.value = ''

  try {
    await deleteInvestmentPosition(currentPosition.value.id, currentUser.id)
    closeDeleteModal(true)
    showFeedback('删除成功', 'success')
    await router.push(backTo.value)
  } catch (error) {
    const message = error instanceof Error ? error.message : '投资资产删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeletingPosition.value = false
  }
}

async function submitTrade() {
  if (isSubmitting.value || !currentPosition.value || !detail.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    tradeError.value = '请先登录后再操作'
    return
  }

  const fundingAccountId = Number(tradeFundingAccountId.value)
  const amount = getTradeAmountValue()
  const price = Number(tradePrice.value)
  const quantity = getTradeQuantityValue()

  if (!Number.isFinite(fundingAccountId) || fundingAccountId <= 0) {
    tradeError.value = '请选择资金账户'
    return
  }
  if (isFundPosition.value) {
    if (currentTradeAction.value === 'buy') {
      if (!Number.isFinite(amount) || amount <= 0) {
        tradeError.value = '请输入有效的申购金额'
        return
      }
    } else {
      if (!Number.isFinite(quantity) || quantity <= 0) {
        tradeError.value = '请输入有效的赎回份额'
        return
      }
      if (quantity > Number(currentPosition.value.availableQuantity)) {
        tradeError.value = '赎回份额不能超过可用持仓'
        return
      }
    }

    isSubmitting.value = true
    tradeError.value = ''

    try {
      const transaction = await createInvestmentTransaction({
        userId: currentUser.id,
        accountId: currentPosition.value.accountId,
        positionId: currentPosition.value.id,
        productId: currentPosition.value.productId,
        tradeType: currentTradeAction.value,
        quantity: currentTradeAction.value === 'sell' ? Number(quantity.toFixed(6)) : 0,
        price: null,
        amount: Number((currentTradeAction.value === 'buy' ? amount : getTradeAmountValue()).toFixed(2)),
        feeAmount: currentTradeAction.value === 'sell' ? getTradeFundSellFeeAmountPayload() : 0,
        taxAmount: 0,
        currencyCode: currentPosition.value.currencyCode || 'CNY',
        tradeAt: toApiDateTime(new Date()),
        fundingAccountId,
        subscriptionTimeSlot: tradeTimeSlot.value,
        remark: tradeRemark.value.trim() || null,
      })
      closeTradeModal(true)
      showFeedback(getFundTransactionSubmitMessage(transaction), 'success')
      await loadDetail()
    } catch (error) {
      const message = error instanceof Error ? error.message : `${tradeModalTitle.value}失败`
      tradeError.value = message
      showFeedback(message, 'error')
    } finally {
      isSubmitting.value = false
    }
    return
  }
  if (!Number.isFinite(amount) || amount <= 0) {
    tradeError.value = currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity'
      ? '请输入有效的加仓份额'
      : `请输入有效的${tradeAmountLabel.value}`
    return
  }
  if (!Number.isFinite(price) || price <= 0) {
    tradeError.value = '请输入有效的成交价格'
    return
  }
  if (!Number.isFinite(quantity) || quantity <= 0) {
    tradeError.value = '金额或价格不正确'
    return
  }
  if (currentTradeAction.value === 'sell' && quantity > Number(currentPosition.value.availableQuantity)) {
    tradeError.value = '减仓数量不能超过可用持仓'
    return
  }

  isSubmitting.value = true
  tradeError.value = ''

  try {
    await createInvestmentTransaction({
      userId: currentUser.id,
      accountId: currentPosition.value.accountId,
      positionId: currentPosition.value.id,
      productId: currentPosition.value.productId,
      tradeType: currentTradeAction.value,
      quantity: Number(quantity.toFixed(6)),
      price,
      amount: Number(amount.toFixed(2)),
      feeAmount: 0,
      taxAmount: 0,
      currencyCode: currentPosition.value.currencyCode || 'CNY',
      tradeAt: toApiDateTime(new Date()),
      fundingAccountId,
      remark: tradeRemark.value.trim() || null,
    })
    closeTradeModal(true)
    showFeedback(currentTradeAction.value === 'buy' ? '加仓成功' : '减仓成功', 'success')
    await loadDetail()
  } catch (error) {
    const message = error instanceof Error ? error.message : `${tradeModalTitle.value}失败`
    tradeError.value = message
    showFeedback(message, 'error')
  } finally {
    isSubmitting.value = false
  }
}

async function submitEdit() {
  if (isSubmitting.value || !currentPosition.value) {
    return
  }
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    editError.value = '请先登录后再操作'
    return
  }
  const price = Number(editPrice.value)
  const holdingQuantity = Number(editHoldingQuantity.value)
  const costPrice = Number(editCostPrice.value)
  const frozenQuantity = Number(currentPosition.value.frozenQuantity ?? 0)
  const isEditingFundPosition = isFundPosition.value && !isPendingSubscription.value
  if (!isPendingSubscription.value && (!Number.isFinite(price) || price <= 0)) {
    editError.value = '请输入有效的当前价格'
    return
  }
  if (isEditingFundPosition) {
    if (!Number.isFinite(holdingQuantity) || holdingQuantity <= 0) {
      editError.value = '请输入有效的当前份额'
      return
    }
    if (!Number.isFinite(costPrice) || costPrice < 0) {
      editError.value = '请输入有效的持仓成本价'
      return
    }
    if (holdingQuantity < frozenQuantity) {
      editError.value = '当前份额不能小于冻结份额'
      return
    }
  }

  isSubmitting.value = true
  editError.value = ''

  try {
    const nextHoldingQuantity = isEditingFundPosition ? holdingQuantity : Number(currentPosition.value.holdingQuantity)
    const nextCostAmount = isEditingFundPosition
      ? Number((holdingQuantity * costPrice).toFixed(2))
      : Number(currentPosition.value.costAmount)
    const nextAvailableQuantity = isEditingFundPosition
      ? Number((holdingQuantity - frozenQuantity).toFixed(2))
      : Number(currentPosition.value.availableQuantity)
    await updateInvestmentPosition(currentPosition.value.id, {
      userId: currentUser.id,
      accountId: currentPosition.value.accountId,
      productId: currentPosition.value.productId,
      holdingQuantity: isEditingFundPosition ? Number(nextHoldingQuantity.toFixed(2)) : nextHoldingQuantity,
      availableQuantity: nextAvailableQuantity,
      frozenQuantity,
      costAmount: nextCostAmount,
      currentPrice: isPendingSubscription.value ? undefined : price,
      includeInNetWorth: editIncludeInNetWorth.value,
      status: currentPosition.value.status,
      remark: editRemark.value.trim() || null,
    })
    closeEditModal(true)
    showFeedback('修改成功', 'success')
    await loadDetail()
  } catch (error) {
    const message = error instanceof Error ? error.message : '修改失败'
    editError.value = message
    showFeedback(message, 'error')
  } finally {
    isSubmitting.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function statClass(entry: InvestmentDetailStat) {
  if (entry.tone === 'primary') return 'tone-primary'
  return ''
}

function statTone(entry: InvestmentDetailStat) {
  if (entry.tone === 'positive') return 'positive'
  if (entry.tone === 'negative') return 'negative'
  if (entry.tone === 'neutral') return 'neutral'
  return 'inherit'
}

function mergeDetail(partial: Partial<InvestmentAssetDetail>) {
  if (!detail.value) {
    return
  }
  detail.value = {
    ...detail.value,
    ...partial,
  }
}

function stat(label: string, value: string, tone?: string): InvestmentDetailStat {
  return { label, value, tone }
}

function toneByNumber(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return 'neutral'
  }
  return value > 0 ? 'positive' : 'negative'
}

function jsonpRequest<T>(url: string, callbackParams: string[], timeoutMs = 8000) {
  return new Promise<T>((resolve, reject) => {
    const callbackName = `__jsonp_${Date.now()}_${Math.floor(Math.random() * 100000)}`
    const script = document.createElement('script')
    const globalWindow = window as unknown as Window & Record<string, unknown>
    const timer = window.setTimeout(() => {
      cleanup()
      reject(new Error('行情接口请求超时'))
    }, timeoutMs)

    function cleanup() {
      window.clearTimeout(timer)
      script.remove()
      delete globalWindow[callbackName]
    }

    globalWindow[callbackName] = (data: T) => {
      cleanup()
      resolve(data)
    }

    script.onerror = () => {
      cleanup()
      reject(new Error('行情接口加载失败'))
    }

    const connector = url.includes('?') ? '&' : '?'
    const callbackQuery = callbackParams
      .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(callbackName)}`)
      .join('&')
    script.src = `${url}${connector}${callbackQuery}`
    document.body.appendChild(script)
  })
}

function fetchFundTrend(fundCode: string) {
  return new Promise<{ netWorthTrend: any[]; acWorthTrend: any[] }>((resolve, reject) => {
    const script = document.createElement('script')
    const globalWindow = window as Window & Record<string, any>
    const timer = window.setTimeout(() => {
      cleanup()
      reject(new Error('基金走势请求超时'))
    }, 10000)

    function cleanup() {
      window.clearTimeout(timer)
      script.remove()
    }

    globalWindow.Data_netWorthTrend = undefined
    globalWindow.Data_ACWorthTrend = undefined

    script.onload = () => {
      const netWorthTrend = Array.isArray(globalWindow.Data_netWorthTrend) ? globalWindow.Data_netWorthTrend.slice() : []
      const acWorthTrend = Array.isArray(globalWindow.Data_ACWorthTrend) ? globalWindow.Data_ACWorthTrend.slice() : []
      cleanup()
      resolve({ netWorthTrend, acWorthTrend })
    }

    script.onerror = () => {
      cleanup()
      reject(new Error('基金走势加载失败'))
    }

    script.src = `https://fund.eastmoney.com/pingzhongdata/${encodeURIComponent(fundCode)}.js?v=${Date.now()}`
    document.body.appendChild(script)
  })
}

function buildFundTrendPoints(
  trend: { netWorthTrend: any[]; acWorthTrend?: any[] },
  latestAccumulativePoint?: { label?: string | null; value?: number | null },
  latestNetPoint?: { label?: string | null; value?: number | null },
) {
  const acRows = Array.isArray(trend.acWorthTrend) ? trend.acWorthTrend : []
  if (acRows.length > 0) {
    const points = acRows
      .map((item) => ({ ts: Number(item?.[0]), value: Number(item?.[1]) }))
      .filter((item) => Number.isFinite(item.ts) && Number.isFinite(item.value))
      .sort((a, b) => a.ts - b.ts)
      .map((item) => ({
        label: formatDate(item.ts),
        value: item.value,
      }))

    return mergeLatestFundTrendPoint(points, latestAccumulativePoint)
  }

  const netRows = Array.isArray(trend.netWorthTrend) ? trend.netWorthTrend : []
  const points = netRows
    .map((item) => ({ ts: Number(item?.x), value: Number(item?.y) }))
    .filter((item) => Number.isFinite(item.ts) && Number.isFinite(item.value))
    .sort((a, b) => a.ts - b.ts)
    .map((item) => ({
      label: formatDate(item.ts),
      value: item.value,
    }))

  return mergeLatestFundTrendPoint(points, latestNetPoint ?? latestAccumulativePoint)
}

function mergeLatestFundTrendPoint(
  points: InvestmentChartPoint[],
  latestPoint?: { label?: string | null; value?: number | null },
) {
  const normalizedLabel = normalizeDateLabel(latestPoint?.label || '')
  const numericValue = Number(latestPoint?.value)
  if (!normalizedLabel || !Number.isFinite(numericValue)) {
    return points
  }

  const merged = points.filter((point) => normalizeDateLabel(point.label) !== normalizedLabel)
  merged.push({
    label: normalizedLabel,
    value: numericValue,
  })
  merged.sort((left, right) => {
    const leftDate = parsePointLabelDate(left.label)?.getTime() ?? 0
    const rightDate = parsePointLabelDate(right.label)?.getTime() ?? 0
    return leftDate - rightDate
  })
  return merged
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

function filterFundTrendPoints(points: InvestmentChartPoint[], range: FundTrendRange) {
  if (!points.length) {
    return []
  }

  const datedPoints = points
    .map((point) => {
      const date = parsePointLabelDate(point.label)
      return date ? { point, date } : null
    })
    .filter((item): item is { point: InvestmentChartPoint; date: Date } => Boolean(item))

  if (!datedPoints.length) {
    return points
  }

  const latestDate = datedPoints[datedPoints.length - 1].date
  const cutoff = new Date(latestDate)

  if (range === '1m') {
    cutoff.setMonth(cutoff.getMonth() - 1)
  } else if (range === '3m') {
    cutoff.setMonth(cutoff.getMonth() - 3)
  } else if (range === '6m') {
    cutoff.setMonth(cutoff.getMonth() - 6)
  } else if (range === '1y') {
    cutoff.setFullYear(cutoff.getFullYear() - 1)
  } else {
    cutoff.setFullYear(cutoff.getFullYear() - 3)
  }

  const filtered = datedPoints
    .filter((item) => item.date >= cutoff)
    .map((item) => item.point)

  return filtered.length ? filtered : points
}

function parsePointLabelDate(label: string) {
  const normalized = normalizeDateLabel(label)
  const parsed = new Date(`${normalized}T00:00:00`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

async function fetchStockQuote(symbol: string) {
  const response = await fetch(`https://qt.gtimg.cn/q=${encodeURIComponent(symbol)}`)
  const buffer = await response.arrayBuffer()
  let text = ''
  try {
    text = new TextDecoder('gbk').decode(buffer)
  } catch {
    text = new TextDecoder().decode(buffer)
  }
  const start = text.indexOf('"')
  const end = text.lastIndexOf('"')
  if (start < 0 || end <= start) {
    throw new Error('股票行情格式异常')
  }
  const arr = text.slice(start + 1, end).split('~')
  return {
    name: arr[1],
    code: arr[2],
    price: arr[3],
    prevClose: arr[4],
    open: arr[5],
    volume: arr[6],
    timeRaw: arr[30],
    change: arr[31],
    changePct: arr[32],
    high: arr[33],
    low: arr[34],
  }
}

async function fetchStockKline(symbol: string) {
  const response = await fetch(`https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=${encodeURIComponent(`${symbol},day,,,260,qfq`)}`)
  const data = await response.json()
  const rows = data?.data?.[symbol]?.qfqday || data?.data?.[symbol]?.day || []
  if (!Array.isArray(rows)) {
    return []
  }
  return rows
    .map((row) => ({
      label: row?.[0],
      open: Number(row?.[1]),
      close: Number(row?.[2]),
      high: Number(row?.[3]),
      low: Number(row?.[4]),
      volume: Number(row?.[5]),
      value: Number(row?.[2]),
    }))
    .filter((item) => item.label && Number.isFinite(item.close))
}

function toTencentSymbol(code: string, market?: string | null) {
  if (market === 'SSE') return `sh${code}`
  if (market === 'SZSE') return `sz${code}`
  return code.startsWith('6') ? `sh${code}` : `sz${code}`
}

function getTradeTypeLabel(entry: InvestmentTransaction) {
  if (entry.settlementStatus === 'pending') {
    if (entry.tradeType === 'buy') {
      return '加仓待确认'
    }
    if (entry.tradeType === 'sell') {
      return '减仓待确认'
    }
  }
  const map: Record<string, string> = {
    buy: '买入',
    sell: '卖出',
    dividend: '分红',
    bonus: '送股',
  }
  return map[entry.tradeType] ?? entry.tradeType
}

function getTradeQuantityClass(type: string) {
  if (type === 'sell') {
    return 'trend-down'
  }
  return 'trend-up'
}

function getTradeQuantityText(entry: InvestmentTransaction, unitName?: string | null) {
  if (entry.settlementStatus === 'pending' && entry.tradeType === 'buy') {
    return '待确认份额'
  }
  const sign = entry.tradeType === 'sell' ? '-' : '+'
  return `${sign} ${formatNumber(Number(entry.quantity), 2)} ${unitName || '份'}`
}

function formatTradeTime(value: string) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const today = new Date()
  const isToday = date.toDateString() === today.toDateString()
  const time = date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
  if (isToday) {
    return `今天 ${time}`
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day} ${time}`
}

function formatDateInput(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function isTodayMarketData(value?: string | null) {
  if (!value) {
    return false
  }
  const normalized = value.trim().replace(' ', 'T')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    const matched = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
    if (!matched) {
      return false
    }
    return value.slice(0, 10) === formatDateInput(new Date())
  }
  return formatDateInput(date) === formatDateInput(new Date())
}

function formatAutoInvestFrequency(value: string) {
  if (value === 'daily') return '每日定投'
  return value === 'monthly' ? '每月定投' : '每周定投'
}

function formatAutoInvestStatus(value: string) {
  if (value === 'paused') return '已暂停'
  if (value === 'cancelled') return '已停用'
  return '执行中'
}

function formatDateTimeLabel(value?: string | null) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${formatDate(date.getTime())} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function formatDividendDate(value?: string | null) {
  if (!value) return '--'
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatDividendPerUnit(value?: number | null) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? `${formatNumber(numeric, 4)} / ${currentUnitName.value}` : '--'
}

function getDividendStatusLabel(value?: string | null) {
  if (value === 'paid') return '已分红'
  if (value === 'confirmed') return '已公告'
  if (value === 'planned') return '计划中'
  if (value === 'cancelled') return '已取消'
  return value || '--'
}

function normalizeDateLabel(value: string) {
  if (!value) return ''
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return value
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.slice(0, 10)
  }
  return formatDate(date.getTime())
}

function getTooltipPointValue(item: any) {
  if (Array.isArray(item?.value)) {
    return item.value[item.value.length - 1]
  }
  return item?.value
}

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

function formatDate(timestamp: number) {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return '--'
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatEditableFundQuantity(value?: number | null) {
  const numeric = Number(value ?? 0)
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return ''
  }
  return numeric.toFixed(2)
}

function formatTencentTime(raw?: string) {
  if (!raw || raw.length !== 14) return ''
  return `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)} ${raw.slice(8, 10)}:${raw.slice(10, 12)}:${raw.slice(12, 14)}`
}

function toApiDateTime(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`
}

function formatNumber(value: number, digits = 2) {
  if (!Number.isFinite(value)) return '--'
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value)
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

function formatPercentValue(value: number, digits = 2) {
  if (!Number.isFinite(value)) return '--'
  return `${formatNumber(value * 100, digits)}%`
}

function formatCurrency(value: number) {
  if (!Number.isFinite(value)) return '¥--'
  const sign = value < 0 ? '-' : ''
  return `${sign}¥${formatNumber(Math.abs(value))}`
}

function getNetTransactionAmount(entry: InvestmentTransaction) {
  return Number(entry.amount) - Number(entry.feeAmount ?? 0) - Number(entry.taxAmount ?? 0)
}

function formatAmountLabel(entry: InvestmentTransaction) {
  if (entry.settlementStatus === 'pending' && entry.tradeType === 'sell') {
    return `预计到账 ${formatCurrency(getNetTransactionAmount(entry))}`
  }
  if (entry.settlementStatus === 'pending' && entry.tradeType === 'buy') {
    return `申购金额 ${formatCurrency(Number(entry.amount))}`
  }
  if (entry.tradeType === 'sell') {
    return `到账金额 ${formatCurrency(getNetTransactionAmount(entry))}`
  }
  return `金额 ${formatCurrency(Number(entry.amount))}`
}

function getFundTransactionSubmitMessage(entry: InvestmentTransaction) {
  if (entry.tradeType === 'sell') {
    const appliedDate = entry.settlementAppliedDate || '--'
    const expectedDate = entry.settlementExpectedDate || appliedDate
    const feeText = formatCurrency(Number(entry.feeAmount ?? 0))
    const netAmountText = formatCurrency(getNetTransactionAmount(entry))
    if (entry.settlementStatus === 'confirmed') {
      return `基金减仓已按确认净值结算，到账 ${netAmountText}`
    }
    if (expectedDate === appliedDate) {
      return `基金减仓申请已提交，预计手续费 ${feeText}，将按 ${appliedDate} 净值确认，预计到账 ${netAmountText}`
    }
    return `基金减仓申请已提交，预计手续费 ${feeText}，将按 ${appliedDate} 净值确认，预计 ${expectedDate} 到账 ${netAmountText}`
  }

  if (entry.settlementStatus === 'confirmed') {
    return '基金加仓已按最新净值确认，份额已更新'
  }
  const appliedDate = entry.settlementAppliedDate || '--'
  const expectedDate = entry.settlementExpectedDate || appliedDate
  if (expectedDate === appliedDate) {
    return `基金加仓申请已提交，将按 ${appliedDate} 净值确认份额`
  }
  return `基金加仓申请已提交，将按 ${appliedDate} 净值确认，预计 ${expectedDate} 完成`
}
</script>

<template>
  <section class="investment-detail-page" aria-label="投资详情">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="投资详情" :back-to="backTo" back-label="返回投资账户" />

    <p v-if="pageError" class="investment-detail-message investment-detail-message-error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else-if="detail">
      <section class="investment-detail-summary-card" aria-label="投资详情总览">
        <div class="investment-detail-summary-head">
          <div class="investment-detail-summary-main">
            <div class="investment-detail-summary-title">
              <strong>{{ detail.name || detail.position.productName }}</strong>
              <span>{{ detail.symbol || detail.position.productSymbol }} · {{ detail.productType === 'stock' ? '股票' : detail.productType === 'fund' ? '基金' : '投资资产' }}</span>
            </div>
            <AmountText tag="p" class="investment-detail-summary-amount" tone="inherit" :value="summaryAmount" />
            <p class="investment-detail-summary-updated">{{ displayUpdatedAt }}</p>
          </div>
          <div v-if="showTodayMetrics" class="investment-detail-summary-side">
            <div class="investment-detail-summary-metric">
              <span>当日盈亏</span>
              <AmountText tag="strong" :tone="todayProfitTone" :value="todayProfitValue" />
            </div>
            <div class="investment-detail-summary-metric">
              <span>当日收益率</span>
              <AmountText tag="strong" :tone="todayTone" :value="todayValue" />
            </div>
          </div>
        </div>
      </section>

      <section class="investment-detail-actions" aria-label="持仓操作">
        <button class="investment-detail-action-button buy" type="button" :disabled="isPendingSubscription" @click="openTradeModal('buy')">加仓</button>
        <button class="investment-detail-action-button sell" type="button" :disabled="isPendingSubscription" @click="openTradeModal('sell')">减仓</button>
        <button
          class="investment-detail-action-button auto-invest"
          type="button"
          :disabled="!showAutoInvestSection"
          @click="openAutoInvestModal()"
        >
          定投
        </button>
        <button class="investment-detail-action-button edit" type="button" @click="openEditModal">修改</button>
        <button class="investment-detail-action-button delete" type="button" :disabled="isDeletingPosition" @click="openDeleteModal">删除</button>
      </section>

      <section class="investment-detail-card" aria-label="行情走势">
        <header class="investment-detail-card-head">
          <h2>{{ detail.chartType === 'candlestick' ? '股票日K走势' : '累计净值走势' }}</h2>
          <span>{{ externalStatus || detail.source || '行情接口' }}</span>
        </header>
        <SegmentedControl
          v-if="detail.productType === 'fund'"
          v-model="selectedFundTrendRange"
          :options="fundTrendRangeOptions"
          label="基金累计净值区间切换"
          class="investment-detail-trend-range"
          variant="surface"
        />
        <div v-if="detail.chartPoints.length" ref="chartRef" class="investment-detail-chart"></div>
        <p v-else class="investment-detail-empty">暂无走势数据</p>
      </section>

      <section class="investment-detail-card" aria-label="资产详细数据">
        <h2>{{ detail.productType === 'stock' ? '股票详细数据' : detail.productType === 'fund' ? '基金详细数据' : '资产详细数据' }}</h2>
        <div class="investment-detail-grid">
          <div v-for="entry in detail.marketStats" :key="entry.label" class="investment-detail-grid-item">
            <span>{{ entry.label }}</span>
            <AmountText tag="strong" :class="statClass(entry)" :tone="statTone(entry)" :value="entry.value" />
          </div>
        </div>
      </section>

      <section class="investment-detail-card" aria-label="持仓分析">
        <h2>持仓分析</h2>
        <div class="investment-detail-grid">
          <div v-for="entry in detail.holdingStats" :key="entry.label" class="investment-detail-grid-item">
            <span>{{ entry.label }}</span>
            <AmountText tag="strong" :class="statClass(entry)" :tone="statTone(entry)" :value="entry.value" />
          </div>
        </div>
      </section>

      <section class="investment-detail-card" aria-label="近一年分红记录">
        <header class="investment-detail-card-head">
          <h2>近一年分红记录</h2>
          <span>{{ dividendRecords.length }} 条</span>
        </header>
        <div v-if="dividendRecords.length > 0" class="investment-detail-dividend-list">
          <article
            v-for="entry in dividendRecords"
            :key="entry.id"
            class="investment-detail-dividend-item"
          >
            <div class="investment-detail-dividend-top">
              <div class="investment-detail-dividend-title">
                <strong>{{ formatDividendDate(entry.payDate) }}</strong>
              </div>
              <em class="investment-detail-dividend-status">{{ getDividendStatusLabel(entry.status) }}</em>
            </div>

            <div class="investment-detail-dividend-grid">
              <div>
                <span>每单位分红</span>
                <strong>{{ formatDividendPerUnit(entry.dividendPerUnit) }}</strong>
              </div>
              <div>
                <span>分红</span>
                <strong>{{ formatCurrency(Number(entry.expectedAmount ?? 0)) }}</strong>
              </div>
            </div>
          </article>
        </div>
        <p v-else class="investment-detail-empty">近一年暂无分红记录</p>
      </section>

      <section v-if="showAutoInvestSection" class="investment-detail-card" aria-label="定投计划">
        <header class="investment-detail-card-head">
          <h2>定投计划</h2>
        </header>

        <div v-if="autoInvestPlans.length > 0" class="investment-auto-invest-list">
          <article
            v-for="plan in autoInvestPlans"
            :key="plan.id"
            class="investment-auto-invest-item"
          >
            <div class="investment-auto-invest-top">
              <div class="investment-auto-invest-main">
                <strong>{{ formatCurrency(Number(plan.amount)) }}</strong>
                <span>{{ formatAutoInvestFrequency(plan.frequency) }}</span>
              </div>
              <em :class="['investment-auto-invest-status', `is-${plan.status}`]">{{ formatAutoInvestStatus(plan.status) }}</em>
            </div>

            <div class="investment-auto-invest-grid">
              <div>
                <span>下次执行</span>
                <strong>{{ plan.nextExecuteDate }}</strong>
              </div>
              <div>
                <span>扣款账户</span>
                <strong>{{ plan.fundingAccountName || '--' }}</strong>
              </div>
              <div>
                <span>最近执行</span>
                <strong>{{ formatDateTimeLabel(plan.lastExecutedAt) }}</strong>
              </div>
              <div>
                <span>备注</span>
                <strong>{{ plan.remark?.trim() || '--' }}</strong>
              </div>
            </div>

            <div class="investment-auto-invest-actions">
              <button type="button" :disabled="isSavingAutoInvest" @click="openAutoInvestModal(plan)">修改</button>
              <button
                type="button"
                :disabled="isSavingAutoInvest"
                @click="updateAutoInvestPlanStatus(plan, plan.status === 'active' ? 'paused' : 'active')"
              >
                {{ plan.status === 'active' ? '暂停' : '恢复' }}
              </button>
              <button type="button" class="danger" :disabled="isSavingAutoInvest" @click="removeAutoInvestPlan(plan)">删除</button>
            </div>
          </article>
        </div>
        <p v-else class="investment-detail-empty">暂无定投计划</p>
      </section>

      <section class="investment-detail-transactions-wrap" aria-label="交易记录">
        <header class="investment-detail-transactions-head">
          <strong>交易记录</strong>
          <span>{{ transactionCountText }}</span>
        </header>

        <section class="investment-detail-transactions-card">
          <article
            v-for="entry in transactions"
            :key="entry.id"
            class="investment-detail-transaction-item"
          >
            <div class="investment-detail-transaction-left">
              <strong>{{ getTradeTypeLabel(entry) }}</strong>
              <span>{{ formatTradeTime(entry.tradeAt) }}</span>
            </div>
            <div class="investment-detail-transaction-right">
              <span>{{ formatAmountLabel(entry) }}</span>
              <AmountText
                tag="strong"
                :class="getTradeQuantityClass(entry.tradeType)"
                tone="inherit"
                :value="getTradeQuantityText(entry, detail.unitName || detail.position.unitName)"
              />
            </div>
          </article>
          <p v-if="transactions.length === 0" class="investment-detail-empty">暂无交易记录</p>
        </section>
      </section>
    </template>

    <CommonModal
      v-model="showTradeModal"
      :title="tradeModalTitle"
      size="compact"
      :close-on-overlay="!isSubmitting"
      @close="closeTradeModal"
    >
      <div class="investment-detail-modal-form">
        <label class="investment-detail-modal-field">
          <span>资产名称</span>
          <input
            class="investment-detail-field-control"
            :value="detail?.name || detail?.position.productName || ''"
            type="text"
            readonly
          />
        </label>

        <SegmentedControl
          v-if="showTradeInputMode"
          v-model="tradeModeValue"
          :options="tradeModeOptions"
          label="加仓方式切换"
        />

        <div :class="['investment-detail-modal-row', { 'single-column': isFundPosition }]">
          <label class="investment-detail-modal-field">
            <span>{{ tradePrimaryLabel }}</span>
            <input
              v-if="isFundPosition ? currentTradeAction === 'buy' : currentTradeAction !== 'buy' || tradeInputMode === 'amount'"
              v-model="tradeAmount"
              class="investment-detail-field-control"
              type="number"
              inputmode="decimal"
              :placeholder="tradePrimaryPlaceholder"
            />
            <input
              v-else
              v-model="tradeQuantity"
              class="investment-detail-field-control"
              type="number"
              inputmode="decimal"
              :placeholder="tradePrimaryPlaceholder"
            />
          </label>

          <label v-if="showTradePriceField" class="investment-detail-modal-field">
            <span>成交价格</span>
            <input
              v-model="tradePrice"
              class="investment-detail-field-control"
              type="number"
              inputmode="decimal"
              placeholder="请输入价格"
            />
          </label>
        </div>

        <label class="investment-detail-modal-field">
          <span>{{ tradeAccountLabel }}</span>
          <select v-model="tradeFundingAccountId" class="investment-detail-field-control">
            <option value="" disabled>请选择账户</option>
            <option v-for="account in fundingAccounts" :key="account.id" :value="String(account.id)">
              {{ account.name }}（余额 {{ formatCurrency(Number(account.currentBalance)) }}）
            </option>
          </select>
        </label>

        <label v-if="showTradeQuantityPreview" class="investment-detail-modal-field">
          <span>数量</span>
          <input class="investment-detail-field-control" :value="tradeQuantityPreview" type="text" readonly />
        </label>

        <label v-if="showTradeAmountPreview" class="investment-detail-modal-field">
          <span>金额</span>
          <input class="investment-detail-field-control" :value="tradeAmountPreview" type="text" readonly />
        </label>

        <label v-if="isFundPosition" class="investment-detail-modal-field">
          <span>{{ currentTradeAction === 'buy' ? '申购时点' : '赎回时点' }}</span>
          <SegmentedControl
            v-model="tradeTimeSlot"
            :options="tradeTimeSlotOptions"
            label="基金交易时点"
          />
        </label>

        <label v-if="showFundRedeemFeeSelector" class="investment-detail-modal-field">
          <span>手续费档位</span>
          <select v-model="tradeFundFeeMode" class="investment-detail-field-control">
            <option v-for="option in tradeFundFeeSelectOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>

        <label v-if="showFundRedeemFeeSelector" class="investment-detail-modal-field">
          <span>预计手续费</span>
          <input class="investment-detail-field-control" :value="tradeFundFeeAmountPreview" type="text" readonly />
        </label>

        <label v-if="showFundRedeemFeeSelector" class="investment-detail-modal-field">
          <span>预计到账</span>
          <input class="investment-detail-field-control" :value="tradeFundNetAmountPreview" type="text" readonly />
        </label>

        <label class="investment-detail-modal-field">
          <span>备注</span>
          <textarea
            v-model="tradeRemark"
            class="investment-detail-textarea-control"
            rows="3"
            maxlength="200"
            placeholder="选填"
          ></textarea>
        </label>

        <p v-if="tradeError" class="investment-detail-modal-error">{{ tradeError }}</p>
      </div>

      <template #footer>
        <div class="investment-detail-modal-actions">
          <CommonButton variant="secondary" :disabled="isSubmitting" @click="closeTradeModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSubmitting" @click="submitTrade">
            {{ isSubmitting ? '提交中...' : tradeModalTitle }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showAutoInvestModal"
      :title="editingAutoInvestPlanId ? '修改定投计划' : '新增定投计划'"
      size="compact"
      :close-on-overlay="!isSavingAutoInvest"
      @close="closeAutoInvestModal"
    >
      <div class="investment-detail-modal-form">
        <label class="investment-detail-modal-field">
          <span>资产名称</span>
          <input
            class="investment-detail-field-control"
            :value="detail?.name || detail?.position.productName || ''"
            type="text"
            readonly
          />
        </label>

        <label class="investment-detail-modal-field">
          <span>扣款账户</span>
          <select v-model="autoInvestFundingAccountId" class="investment-detail-field-control">
            <option value="" disabled>请选择资金账户</option>
            <option v-for="account in fundingAccounts" :key="account.id" :value="String(account.id)">
              {{ account.name }}（余额 {{ formatCurrency(Number(account.currentBalance)) }}）
            </option>
          </select>
        </label>

        <label class="investment-detail-modal-field">
          <span>定投周期</span>
          <SegmentedControl
            v-model="autoInvestFrequency"
            :options="autoInvestFrequencyOptions"
            label="定投周期"
          />
        </label>

        <div class="investment-detail-modal-row">
          <label class="investment-detail-modal-field">
            <span>定投金额</span>
            <input
              v-model="autoInvestAmount"
              class="investment-detail-field-control"
              type="number"
              inputmode="decimal"
              placeholder="请输入定投金额"
            />
          </label>

          <label class="investment-detail-modal-field">
            <span>下次执行日</span>
            <input
              v-model="autoInvestNextExecuteDate"
              class="investment-detail-field-control"
              type="date"
            />
          </label>
        </div>

        <label class="investment-detail-modal-field">
          <span>备注</span>
          <textarea
            v-model="autoInvestRemark"
            class="investment-detail-textarea-control"
            rows="3"
            maxlength="200"
            placeholder="选填"
          ></textarea>
        </label>

        <p class="investment-detail-description">
          到期后系统会自动按计划金额生成一笔基金申购，默认按 15 点前规则提交。
        </p>

        <p v-if="autoInvestError" class="investment-detail-modal-error">{{ autoInvestError }}</p>
      </div>

      <template #footer>
        <div class="investment-detail-modal-actions">
          <CommonButton variant="secondary" :disabled="isSavingAutoInvest" @click="closeAutoInvestModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSavingAutoInvest" @click="saveAutoInvestPlan">
            {{ isSavingAutoInvest ? '保存中...' : '保存计划' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showDeleteModal"
      title="删除投资资产"
      size="compact"
      :close-on-overlay="!isDeletingPosition"
      @close="closeDeleteModal"
    >
      <div class="investment-detail-modal-form">
        <p class="investment-detail-description">
          删除后该资产的持仓与交易记录会一并移除，确认删除“{{ detail?.name || detail?.position.productName || '当前资产' }}”吗？
        </p>
        <p v-if="deleteError" class="investment-detail-modal-error">{{ deleteError }}</p>
      </div>

      <template #footer>
        <div class="investment-detail-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeletingPosition" @click="closeDeleteModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isDeletingPosition" @click="confirmDeletePosition">
            {{ isDeletingPosition ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showEditModal"
      title="修改持仓"
      size="compact"
      :close-on-overlay="!isSubmitting"
      @close="closeEditModal"
    >
      <div class="investment-detail-modal-form">
        <label class="investment-detail-modal-field">
          <span>资产名称</span>
          <input
            class="investment-detail-field-control"
            :value="detail?.name || detail?.position.productName || ''"
            type="text"
            readonly
          />
        </label>

        <label v-if="!isPendingSubscription" class="investment-detail-modal-field">
          <span>当前价格</span>
          <input
            v-model="editPrice"
            class="investment-detail-field-control investment-detail-number-control"
            type="number"
            inputmode="decimal"
            placeholder="请输入当前价格"
          />
        </label>

        <div v-if="isFundPosition && !isPendingSubscription" class="investment-detail-modal-row">
          <label class="investment-detail-modal-field">
            <span>当前份额</span>
            <input
              v-model="editHoldingQuantity"
              class="investment-detail-field-control investment-detail-number-control"
              type="number"
              inputmode="decimal"
              step="0.01"
              placeholder="请输入当前份额"
            />
          </label>

          <label class="investment-detail-modal-field">
            <span>持仓成本价</span>
            <input
              v-model="editCostPrice"
              class="investment-detail-field-control investment-detail-number-control"
              type="number"
              inputmode="decimal"
              placeholder="请输入持仓成本价"
            />
          </label>
        </div>

        <p v-if="isPendingSubscription" class="investment-detail-description">
          场外基金待确认时不支持手动修改价格；若目标申购日净值已同步，系统会直接确认份额和成本价，否则会在净值同步后自动完成。
        </p>

        <p v-if="isFundPosition && !isPendingSubscription" class="investment-detail-description">
          总持仓成本将按 当前份额 × 持仓成本价 自动计算，当前约为 {{ editFundCostAmountPreview }}，保存后立即生效。
        </p>

        <label class="investment-detail-switch-field">
          <span>计入总资产</span>
          <button
            type="button"
            :class="['investment-detail-switch', { active: editIncludeInNetWorth }]"
            @click="editIncludeInNetWorth = !editIncludeInNetWorth"
          >
            <span></span>
          </button>
        </label>

        <label class="investment-detail-modal-field">
          <span>备注</span>
          <textarea
            v-model="editRemark"
            class="investment-detail-textarea-control"
            rows="3"
            maxlength="200"
            placeholder="选填"
          ></textarea>
        </label>

        <p v-if="editError" class="investment-detail-modal-error">{{ editError }}</p>
      </div>

      <template #footer>
        <div class="investment-detail-modal-actions">
          <CommonButton variant="secondary" :disabled="isSubmitting" @click="closeEditModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSubmitting" @click="submitEdit">
            {{ isSubmitting ? '保存中...' : '保存修改' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
