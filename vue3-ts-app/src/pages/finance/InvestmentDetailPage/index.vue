<script setup lang="ts">
// 投资详情页：通过后端聚合接口展示持仓、实时行情和走势，并支持加仓、减仓、修改。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { ECharts } from 'echarts'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  createInvestmentTransaction,
  getAccounts,
  getInvestmentPositionDetail,
  getInvestmentTransactions,
  updateInvestmentPosition,
  type Account,
  type InvestmentAssetDetail,
  type InvestmentChartPoint,
  type InvestmentDetailStat,
  type InvestmentPosition,
  type InvestmentTransaction,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const route = useRoute()
const isLoading = ref(false)
const pageError = ref('')
const detail = ref<InvestmentAssetDetail | null>(null)
const transactions = ref<InvestmentTransaction[]>([])
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
const showEditModal = ref(false)
const editPrice = ref('')
const editIncludeInNetWorth = ref(true)
const editRemark = ref('')
const editError = ref('')
const isSubmitting = ref(false)
let chart: ECharts | null = null

const positionId = computed(() => {
  const raw = Array.isArray(route.query.positionId) ? route.query.positionId[0] : route.query.positionId
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

const backTo = computed(() => {
  const accountId = detail.value?.position?.accountId
  return accountId ? `/finance/accounts/investment?accountId=${accountId}` : '/finance/accounts/investment'
})

const summaryAmount = computed(() => formatCurrency(Number(detail.value?.position?.marketValue ?? 0)))
const todayValue = computed(() => {
  const changePercent = detail.value?.changePercent
  if (changePercent !== null && changePercent !== undefined) {
    return `${formatNumber(Number(changePercent))}%`
  }
  return formatNumber(Number(detail.value?.position?.dayProfitRate ?? 0)) + '%'
})
const displayUpdatedAt = computed(() => detail.value?.updatedAt ? `同步于 ${detail.value.updatedAt}` : '同步于 --')
const transactionCountText = computed(() => `共 ${transactions.value.length} 条`)
const currentPosition = computed<InvestmentPosition | null>(() => detail.value?.position ?? null)
const currentUnitName = computed(() => detail.value?.unitName || detail.value?.position.unitName || '份')
const tradeModalTitle = computed(() => currentTradeAction.value === 'buy' ? '加仓' : '减仓')
const tradeAmountLabel = computed(() => currentTradeAction.value === 'buy' ? '加仓金额' : '减仓金额')
const tradeAccountLabel = computed(() => currentTradeAction.value === 'buy' ? '资金账户' : '回款账户')
const showTradeInputMode = computed(() => currentTradeAction.value === 'buy')
const tradePrimaryLabel = computed(() => {
  if (currentTradeAction.value === 'buy') {
    return tradeInputMode.value === 'amount' ? '加仓金额' : '加仓份额'
  }
  return '减仓金额'
})
const tradePrimaryPlaceholder = computed(() => {
  if (currentTradeAction.value === 'buy') {
    return tradeInputMode.value === 'amount' ? '请输入金额' : '请输入份额'
  }
  return '请输入金额'
})
const tradeModeOptions = ['按金额', '按份额和净值']
const tradeModeValue = computed({
  get: () => tradeInputMode.value === 'quantity' ? '按份额和净值' : '按金额',
  set: (value: string) => {
    tradeInputMode.value = value === '按份额和净值' ? 'quantity' : 'amount'
  },
})
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

function getTradeAmountValue() {
  if (currentTradeAction.value === 'buy' && tradeInputMode.value === 'quantity') {
    const quantity = Number(tradeQuantity.value)
    const price = Number(tradePrice.value)
    return quantity > 0 && price > 0 ? quantity * price : NaN
  }
  return Number(tradeAmount.value)
}

function getTradeQuantityValue() {
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

watch(detail, async () => {
  await nextTick()
  requestAnimationFrame(() => {
    renderChart()
  })
})

async function loadDetail() {
  if (!positionId.value) {
    pageError.value = '投资资产不存在'
    return
  }

  isLoading.value = true
  pageError.value = ''
  try {
    const currentUser = getStoredCurrentUser()
    const [detailData, transactionList, accountList] = await Promise.all([
      getInvestmentPositionDetail(positionId.value),
      currentUser
        ? getInvestmentTransactions({ userId: currentUser.id, positionId: positionId.value })
        : Promise.resolve([]),
      currentUser
        ? getAccounts({ userId: currentUser.id, status: 'active' })
        : Promise.resolve([]),
    ])
    detail.value = detailData
    transactions.value = transactionList
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
  const [baseResult, estimateResult, trendResult] = await Promise.allSettled([
    jsonpRequest<Record<string, any>>(
      `https://fundmobapi.eastmoney.com/FundMApi/FundBaseTypeInformation.ashx?FCODE=${encodeURIComponent(fundCode)}&deviceid=Wap&plat=Wap&product=EFund&version=2.0.0`,
      ['callback'],
    ),
    jsonpRequest<Record<string, any>>(
      `https://fund.eastmoney.com/data/funddataforgznew.aspx?fc=${encodeURIComponent(fundCode)}&t=basewap`,
      ['cb'],
    ),
    fetchFundTrend(fundCode),
  ])

  const baseInfo = baseResult.status === 'fulfilled' ? baseResult.value?.Datas ?? {} : {}
  const estimateInfo = estimateResult.status === 'fulfilled' ? estimateResult.value ?? {} : {}
  const estimatePrice = Number(estimateInfo.gsz)
  const officialPrice = Number(baseInfo.DWJZ)
  const useEstimate = Number.isFinite(estimatePrice) && estimatePrice > 0
  const latestPrice = useEstimate ? estimatePrice : officialPrice
  const changePercent = useEstimate ? Number(estimateInfo.gszzl) : Number(baseInfo.RZDF)
  const updatedAt = useEstimate ? estimateInfo.gztime : baseInfo.FSRQ
  const chartPoints = trendResult.status === 'fulfilled' ? buildFundTrendPoints(trendResult.value) : []

  mergeDetail({
    name: baseInfo.SHORTNAME || baseDetail.name,
    latestPrice: Number.isFinite(latestPrice) ? latestPrice : baseDetail.latestPrice,
    changePercent: Number.isFinite(changePercent) ? changePercent : baseDetail.changePercent,
    updatedAt: updatedAt || baseDetail.updatedAt,
    source: '东方财富',
    chartType: 'line',
    chartPoints,
    marketStats: [
      stat('资产类型', '基金'),
      stat('基金代码', fundCode),
      stat('基金类型', baseInfo.FTYPE || '-'),
      stat(useEstimate ? '当前净值（估算）' : '当前净值（单位净值）', Number.isFinite(latestPrice) ? formatNumber(latestPrice, 4) : '-', toneByNumber(changePercent)),
      stat('累计净值', baseInfo.LJJZ || '-'),
      stat('当日涨跌幅', Number.isFinite(changePercent) ? `${formatNumber(changePercent)}%` : '-', toneByNumber(changePercent)),
      stat(useEstimate ? '估值时间' : '净值日期', updatedAt || '-'),
      stat('最新官方净值', estimateInfo.dwjz || baseInfo.DWJZ || '-'),
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
  chart?.setOption({
    color: ['#1D4ED8'],
    tooltip: { trigger: 'axis', confine: true },
    grid: { top: 18, right: 14, bottom: 26, left: 42 },
    xAxis: {
      type: 'category',
      data: points.map((point) => point.label),
      axisLabel: { color: '#64748B', fontSize: 10 },
      axisLine: { lineStyle: { color: '#D9E5FF' } },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: '#64748B', fontSize: 10 },
      splitLine: { lineStyle: { color: '#EDF2FB' } },
    },
    dataZoom: [{ type: 'inside', start: 60, end: 100 }],
    series: [{
      name: '单位净值',
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: points.map((point) => point.value ?? null),
      lineStyle: { width: 2 },
      areaStyle: { opacity: 0.08 },
    }],
  })
}

function renderStockChart(points: InvestmentChartPoint[]) {
  const dates = points.map((point) => point.label)
  const candleData = points.map((point) => [point.open, point.close, point.low, point.high])
  const volumeData = points.map((point) => point.volume ?? 0)

  chart?.setOption({
    animation: false,
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, confine: true },
    grid: [
      { left: 42, right: 14, top: 18, height: '58%' },
      { left: 42, right: 14, top: '76%', height: '14%' },
    ],
    xAxis: [
      { type: 'category', data: dates, boundaryGap: false, axisLabel: { color: '#64748B', fontSize: 10 } },
      { type: 'category', gridIndex: 1, data: dates, boundaryGap: false, axisLabel: { show: false }, axisTick: { show: false } },
    ],
    yAxis: [
      { scale: true, axisLabel: { color: '#64748B', fontSize: 10 }, splitLine: { lineStyle: { color: '#EDF2FB' } } },
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
    ],
  })
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
  if (!position) {
    return
  }
  currentTradeAction.value = action
  tradeInputMode.value = 'amount'
  tradeFundingAccountId.value = fundingAccounts.value[0] ? String(fundingAccounts.value[0].id) : ''
  tradeAmount.value = ''
  tradeQuantity.value = ''
  tradePrice.value = String(Number(detail.value?.latestPrice ?? position.currentPrice ?? 0) || '')
  tradeRemark.value = ''
  tradeError.value = ''
  showTradeModal.value = true
}

function closeTradeModal() {
  if (isSubmitting.value) {
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
  editPrice.value = String(Number(detail.value?.latestPrice ?? position.currentPrice ?? 0) || '')
  editIncludeInNetWorth.value = Boolean(position.includeInNetWorth)
  editRemark.value = position.remark || ''
  editError.value = ''
  showEditModal.value = true
}

function closeEditModal() {
  if (isSubmitting.value) {
    return
  }
  showEditModal.value = false
  editError.value = ''
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
    closeTradeModal()
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
  if (!Number.isFinite(price) || price <= 0) {
    editError.value = '请输入有效的当前价格'
    return
  }

  isSubmitting.value = true
  editError.value = ''

  try {
    await updateInvestmentPosition(currentPosition.value.id, {
      userId: currentUser.id,
      accountId: currentPosition.value.accountId,
      productId: currentPosition.value.productId,
      holdingQuantity: Number(currentPosition.value.holdingQuantity),
      availableQuantity: Number(currentPosition.value.availableQuantity),
      frozenQuantity: Number(currentPosition.value.frozenQuantity),
      costAmount: Number(currentPosition.value.costAmount),
      currentPrice: price,
      includeInNetWorth: editIncludeInNetWorth.value,
      status: currentPosition.value.status,
      remark: editRemark.value.trim() || null,
    })
    closeEditModal()
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
  if (entry.tone === 'positive') return 'tone-positive'
  if (entry.tone === 'negative') return 'tone-negative'
  if (entry.tone === 'primary') return 'tone-primary'
  return ''
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

function buildFundTrendPoints(trend: { netWorthTrend: any[] }) {
  const rows = Array.isArray(trend.netWorthTrend) ? trend.netWorthTrend : []
  const latest = rows
    .map((item) => ({ ts: Number(item?.x), value: Number(item?.y) }))
    .filter((item) => Number.isFinite(item.ts) && Number.isFinite(item.value))
    .sort((a, b) => a.ts - b.ts)
  const cutoff = Date.now() - 365 * 24 * 60 * 60 * 1000
  return latest
    .filter((item) => item.ts >= cutoff)
    .map((item) => ({
      label: formatDate(item.ts),
      value: item.value,
    }))
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

function getTradeTypeLabel(type: string) {
  const map: Record<string, string> = {
    buy: '买入',
    sell: '卖出',
    dividend: '分红',
    bonus: '送股',
  }
  return map[type] ?? type
}

function getTradeQuantityClass(type: string) {
  if (type === 'sell') {
    return 'trend-down'
  }
  return 'trend-up'
}

function getTradeQuantityText(entry: InvestmentTransaction, unitName?: string | null) {
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

function formatDate(timestamp: number) {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return '--'
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
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

function formatCurrency(value: number) {
  if (!Number.isFinite(value)) return '¥--'
  const sign = value < 0 ? '-' : ''
  return `${sign}¥${formatNumber(Math.abs(value))}`
}

function formatAmountLabel(value: number) {
  return `金额 ${formatCurrency(value)}`
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
          <div class="investment-detail-summary-title">
            <strong>{{ detail.name || detail.position.productName }}</strong>
            <span>{{ detail.symbol || detail.position.productSymbol }} · {{ detail.productType === 'stock' ? '股票' : detail.productType === 'fund' ? '基金' : '投资资产' }}</span>
          </div>
          <div class="investment-detail-summary-side">
            <span>当日涨跌</span>
            <AmountText tag="strong" :value="todayValue" />
          </div>
        </div>
        <AmountText tag="p" class="investment-detail-summary-amount" tone="inherit" :value="summaryAmount" />
        <p class="investment-detail-summary-updated">{{ displayUpdatedAt }}</p>
      </section>

      <section class="investment-detail-actions" aria-label="持仓操作">
        <button class="investment-detail-action-button buy" type="button" @click="openTradeModal('buy')">加仓</button>
        <button class="investment-detail-action-button sell" type="button" @click="openTradeModal('sell')">减仓</button>
        <button class="investment-detail-action-button edit" type="button" @click="openEditModal">修改</button>
      </section>

      <section class="investment-detail-card" aria-label="行情走势">
        <header class="investment-detail-card-head">
          <h2>{{ detail.chartType === 'candlestick' ? '股票日K走势' : '业绩走势' }}</h2>
          <span>{{ externalStatus || detail.source || '行情接口' }}</span>
        </header>
        <div v-if="detail.chartPoints.length" ref="chartRef" class="investment-detail-chart"></div>
        <p v-else class="investment-detail-empty">暂无走势数据</p>
      </section>

      <section class="investment-detail-card" aria-label="资产详细数据">
        <h2>{{ detail.productType === 'stock' ? '股票详细数据' : detail.productType === 'fund' ? '基金详细数据' : '资产详细数据' }}</h2>
        <div class="investment-detail-grid">
          <div v-for="entry in detail.marketStats" :key="entry.label" class="investment-detail-grid-item">
            <span>{{ entry.label }}</span>
            <AmountText tag="strong" :class="statClass(entry)" tone="inherit" :value="entry.value" />
          </div>
        </div>
      </section>

      <section class="investment-detail-card" aria-label="持仓分析">
        <h2>持仓分析</h2>
        <div class="investment-detail-grid">
          <div v-for="entry in detail.holdingStats" :key="entry.label" class="investment-detail-grid-item">
            <span>{{ entry.label }}</span>
            <AmountText tag="strong" :class="statClass(entry)" tone="inherit" :value="entry.value" />
          </div>
        </div>
      </section>

      <section class="investment-detail-card" aria-label="行情说明">
        <h2>行情说明</h2>
        <p class="investment-detail-description">{{ detail.description || '行情数据来自公开接口，仅用于个人记账参考。' }}</p>
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
              <strong>{{ getTradeTypeLabel(entry.tradeType) }}</strong>
              <span>{{ formatTradeTime(entry.tradeAt) }}</span>
            </div>
            <div class="investment-detail-transaction-right">
              <span>{{ formatAmountLabel(Number(entry.amount)) }}</span>
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

        <div class="investment-detail-modal-row">
          <label class="investment-detail-modal-field">
            <span>{{ tradePrimaryLabel }}</span>
            <input
              v-if="currentTradeAction !== 'buy' || tradeInputMode === 'amount'"
              v-model="tradeAmount"
              class="investment-detail-field-control investment-detail-number-control"
              type="number"
              inputmode="decimal"
              :placeholder="tradePrimaryPlaceholder"
            />
            <input
              v-else
              v-model="tradeQuantity"
              class="investment-detail-field-control investment-detail-number-control"
              type="number"
              inputmode="decimal"
              :placeholder="tradePrimaryPlaceholder"
            />
          </label>

          <label class="investment-detail-modal-field">
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

        <label class="investment-detail-modal-field">
          <span>预计数量</span>
          <input class="investment-detail-field-control" :value="tradeQuantityPreview" type="text" readonly />
        </label>

        <label v-if="currentTradeAction === 'buy' && tradeInputMode === 'quantity'" class="investment-detail-modal-field">
          <span>预计金额</span>
          <input class="investment-detail-field-control" :value="tradeAmountPreview" type="text" readonly />
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

        <label class="investment-detail-modal-field">
          <span>当前价格</span>
          <input
            v-model="editPrice"
            class="investment-detail-field-control investment-detail-number-control"
            type="number"
            inputmode="decimal"
            placeholder="请输入当前价格"
          />
        </label>

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
