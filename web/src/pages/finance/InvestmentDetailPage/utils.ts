// 投资详情页共享工具：类型、常量与纯函数（无 Vue 响应式依赖）。
import type {
    Account,
  InvestmentChartPoint,
  InvestmentDetailStat,
  InvestmentDividendRecord,
  InvestmentPosition,
  InvestmentTransaction,
} from '@/api/modules/finance'
import { getQfqDailyKline } from '@/api/modules/market'

export type FundTrendRange = '1m' | '3m' | '6m' | '1y' | '3y'
export type FundChartView = 'netValue' | 'profit'
export type TradeFundFeeMode = 'auto' | `manual-${number}`
export type FundTrendValueMode = 'cumulative' | 'net'
export type FundTrendValueEntry = {
  cumulative?: number | null
  net?: number | null
}
export type FundPeriodIncreaseItem = {
  title?: string | null
  syl?: string | number | null
}
export type FundPeriodIncreaseResponse = {
  Datas?: FundPeriodIncreaseItem[] | null
}
export type FundProfitState = {
  holdingQuantity: number
  costAmount: number
  realizedProfit: number
}
export type FundProfitChartDataPoint = [number, number | null]

export const TRANSACTION_PAGE_SIZE = 10
export const FUND_QUANTITY_MATCH_TOLERANCE = 0.01

export const TRADE_MARKER_COLORS = {
  buy: '#DC2626',
  sell: '#16A34A',
} as const

export const FUND_PERIOD_RETURN_FIELDS = [
  { title: 'Y', label: '近1个月收益率' },
  { title: '3Y', label: '近3个月收益率' },
  { title: '6Y', label: '近6个月收益率' },
  { title: '1N', label: '近1年收益率' },
  { title: '2N', label: '近2年收益率' },
  { title: '3N', label: '近3年收益率' },
  { title: '5N', label: '近5年收益率' },
] as const

export const FUND_TREND_RANGE_RETURN_TITLES: Record<FundTrendRange, string> = {
  '1m': 'Y',
  '3m': '3Y',
  '6m': '6Y',
  '1y': '1N',
  '3y': '3N',
}

export const tradeModeOptions = ['按金额', '按份额和净值']
export const tradeQuantityQuickFillPresets = [
  { label: '全部', ratio: 1 },
  { label: '2/1', ratio: 0.5 },
  { label: '4/1', ratio: 0.25 },
] as const
export const autoInvestFrequencyOptions = [
  { label: '每日', value: 'daily' },
  { label: '每周', value: 'weekly' },
  { label: '每月', value: 'monthly' },
]
export const tradeTimeSlotOptions = [
  { label: '15点前', value: 'before_1500' },
  { label: '15点后', value: 'after_1500' },
]
export const dividendTypeOptions = [
  { label: '现金分红', value: 'cash' },
  { label: '红利再投资', value: 'reinvest' },
]
export const fundTrendRangeOptions = [
  { label: '近1月', value: '1m' },
  { label: '近3月', value: '3m' },
  { label: '近6月', value: '6m' },
  { label: '近1年', value: '1y' },
  { label: '近3年', value: '3y' },
]
export const fundChartViewOptions = [
  { label: '累计净值', value: 'netValue' },
  { label: '累计收益', value: 'profit' },
]

// ---------- 数值与文案格式化 ----------

export function formatNumber(value: number, digits = 2) {
  if (!Number.isFinite(value)) return '--'
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value)
}

export function formatPercentValue(value: number, digits = 2) {
  if (!Number.isFinite(value)) return '--'
  return `${formatNumber(value * 100, digits)}%`
}

export function formatCurrency(value: number) {
  if (!Number.isFinite(value)) return '¥--'
  const sign = value < 0 ? '-' : ''
  return `${sign}¥${formatNumber(Math.abs(value))}`
}

export function formatCompactCurrency(value: number) {
  if (!Number.isFinite(value)) return '¥--'
  const sign = value < 0 ? '-' : ''
  const absoluteValue = Math.abs(value)
  if (absoluteValue >= 10000) {
    return `${sign}¥${formatNumber(absoluteValue / 10000, 1)}万`
  }
  return `${sign}¥${formatNumber(absoluteValue, 0)}`
}

export function formatDate(timestamp: number) {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return '--'
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function formatDateInput(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function isTodayMarketData(value?: string | null) {
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

export function formatDateTimeLabel(value?: string | null) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${formatDate(date.getTime())} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

export function formatDividendDate(value?: string | null) {
  if (!value) return '--'
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function formatTradeTime(value: string) {
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

export function formatTencentTime(raw?: string) {
  if (!raw || raw.length !== 14) return ''
  return `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)} ${raw.slice(8, 10)}:${raw.slice(10, 12)}:${raw.slice(12, 14)}`
}

export function formatEditableFundQuantity(value?: number | null) {
  const numeric = Number(value ?? 0)
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return ''
  }
  return numeric.toFixed(2)
}

export function formatTradeQuantityInput(value?: number | null) {
  const numeric = Number(value ?? 0)
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return ''
  }
  return numeric.toFixed(6).replace(/(?:\.0+|(\.\d*?)0+)$/, '$1')
}

export function normalizeDateLabel(value: string) {
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

export function parsePointLabelDate(label: string) {
  const normalized = normalizeDateLabel(label)
  const parsed = new Date(`${normalized}T00:00:00`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

export function resolveTradeAtPayload(
  value: string,
  timeSlot?: 'before_1500' | 'after_1500',
) {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return ''
  }

  const time = timeSlot === 'after_1500'
    ? '15:01:00'
    : timeSlot === 'before_1500'
      ? '14:59:00'
      : '12:00:00'

  return `${value}T${time}`
}

// ---------- 统计项与语气 ----------

export function stat(label: string, value: string, tone?: string): InvestmentDetailStat {
  return { label, value, tone }
}

export function statClass(entry: InvestmentDetailStat) {
  if (entry.tone === 'primary') return 'tone-primary'
  return ''
}

export function statTone(entry: InvestmentDetailStat) {
  if (entry.tone === 'positive') return 'positive'
  if (entry.tone === 'negative') return 'negative'
  if (entry.tone === 'neutral') return 'neutral'
  return 'inherit'
}

export function toneByNumber(value: number) {
  if (!Number.isFinite(value) || value === 0) {
    return 'neutral'
  }
  return value > 0 ? 'positive' : 'negative'
}

// ---------- 资金账户选项 ----------

export function buildFundingAccountOptions(accounts: Account[]) {
  if (!accounts.length) {
    return [{ label: '暂无可用现金账户', value: '', disabled: true }]
  }

  return accounts.map((account) => ({
    label: `${account.name}（余额 ${formatCurrency(Number(account.currentBalance ?? 0))}）`,
    value: String(account.id),
  }))
}

// ---------- 交易记录展示 ----------

export function getTradeTypeLabel(entry: InvestmentTransaction) {
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

export function isInitialFundSubscriptionTransaction(entry: InvestmentTransaction) {
  return entry.remark === '基金首次申购'
    || entry.transactionNo.startsWith('INIT-PENDING-')
    || entry.transactionNo.startsWith('INIT-')
}

export function getTradeQuantityClass(type: string) {
  if (type === 'sell') {
    return 'trend-down'
  }
  return 'trend-up'
}

export function getTradeQuantityText(entry: InvestmentTransaction, unitName?: string | null) {
  if (entry.settlementStatus === 'pending' && entry.tradeType === 'buy') {
    return '待确认份额'
  }
  const sign = entry.tradeType === 'sell' ? '-' : '+'
  return `${sign} ${formatNumber(Number(entry.quantity), 2)} ${unitName || '份'}`
}

export function getNetTransactionAmount(entry: InvestmentTransaction) {
  return Number(entry.amount) - Number(entry.feeAmount ?? 0) - Number(entry.taxAmount ?? 0)
}

export function formatAmountLabel(entry: InvestmentTransaction) {
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

export function getFundTransactionSubmitMessage(entry: InvestmentTransaction) {
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
    return '基金加仓已按确认日净值确认，份额已更新'
  }
  const appliedDate = entry.settlementAppliedDate || '--'
  const expectedDate = entry.settlementExpectedDate || appliedDate
  if (expectedDate === appliedDate) {
    return `基金加仓申请已提交，将按 ${appliedDate} 净值确认份额`
  }
  return `基金加仓申请已提交，将按 ${appliedDate} 净值确认，预计 ${expectedDate} 完成`
}

// ---------- 分红展示 ----------

export function getDividendRecordKey(entry: InvestmentDividendRecord) {
  return entry.id ?? `${entry.productId}-${entry.payDate ?? 'unknown'}-${entry.dividendPerUnit ?? 0}`
}

export function getDividendStatusLabel(value?: string | null, actualAmount?: number | null) {
  if (actualAmount !== null && actualAmount !== undefined) return '已到账'
  if (value === 'paid') return '已公告'
  if (value === 'confirmed') return '已公告'
  if (value === 'planned') return '计划中'
  if (value === 'cancelled') return '已取消'
  return value || '--'
}

// ---------- 定投展示 ----------

export function formatAutoInvestFrequency(value: string) {
  if (value === 'daily') return '每日定投'
  return value === 'monthly' ? '每月定投' : '每周定投'
}

export function formatAutoInvestStatus(value: string) {
  if (value === 'paused') return '已暂停'
  if (value === 'cancelled') return '已停用'
  return '执行中'
}

// ---------- 外部行情接口（东方财富 / 腾讯） ----------

export function jsonpRequest<T>(url: string, callbackParams: string[], timeoutMs = 8000) {
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

export function fetchFundTrend(fundCode: string) {
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

export async function fetchStockQuote(symbol: string) {
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

export async function fetchStockKline(symbol: string) {
  // 经行情中心 /api/market/kline/qfq（腾讯 fqkline 前复权，与原直连同口径），不再浏览器直连
  const data = await getQfqDailyKline(symbol, 260)
  const rows = data?.kline ?? []
  return rows
    .map((row) => ({
      label: row['日期'],
      open: row['开盘'],
      close: row['收盘'],
      high: row['最高'],
      low: row['最低'],
      volume: row['成交量'],
      value: row['收盘'],
    }))
    .filter((item) => item.label && Number.isFinite(item.close))
}

export function toTencentSymbol(code: string, market?: string | null) {
  if (market === 'SSE') return `sh${code}`
  if (market === 'SZSE') return `sz${code}`
  return code.startsWith('6') ? `sh${code}` : `sz${code}`
}

// ---------- 基金走势点位构建 ----------

export function buildFundTrendPoints(
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

export function buildFundTrendValueContext(
  trend: { netWorthTrend: any[]; acWorthTrend?: any[] },
  latestAccumulativePoint?: { label?: string | null; value?: number | null },
  latestNetPoint?: { label?: string | null; value?: number | null },
) {
  const values: Record<string, FundTrendValueEntry> = {}
  const acRows = Array.isArray(trend.acWorthTrend) ? trend.acWorthTrend : []
  const netRows = Array.isArray(trend.netWorthTrend) ? trend.netWorthTrend : []

  for (const item of acRows) {
    const ts = Number(item?.[0])
    const value = Number(item?.[1])
    if (!Number.isFinite(ts) || !Number.isFinite(value) || value <= 0) {
      continue
    }
    setFundTrendValue(values, formatDate(ts), 'cumulative', value)
  }

  for (const item of netRows) {
    const ts = Number(item?.x)
    const value = Number(item?.y)
    if (!Number.isFinite(ts) || !Number.isFinite(value) || value <= 0) {
      continue
    }
    setFundTrendValue(values, formatDate(ts), 'net', value)
  }

  setFundTrendValue(values, latestAccumulativePoint?.label, 'cumulative', latestAccumulativePoint?.value)
  setFundTrendValue(values, latestNetPoint?.label, 'net', latestNetPoint?.value)

  const hasCumulative = Object.values(values).some((entry) => Number.isFinite(Number(entry.cumulative)) && Number(entry.cumulative) > 0)
  return {
    values,
    mode: hasCumulative ? 'cumulative' as const : 'net' as const,
  }
}

export function setFundTrendValue(
  values: Record<string, FundTrendValueEntry>,
  label: string | null | undefined,
  key: keyof FundTrendValueEntry,
  rawValue: unknown,
) {
  const normalizedLabel = normalizeDateLabel(label || '')
  const value = Number(rawValue)
  if (!normalizedLabel || !Number.isFinite(value) || value <= 0) {
    return
  }
  values[normalizedLabel] = {
    ...(values[normalizedLabel] ?? {}),
    [key]: value,
  }
}

export function mergeLatestFundTrendPoint(
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

export function filterFundTrendPoints(points: InvestmentChartPoint[], range: FundTrendRange) {
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

// ---------- 基金区间收益率 ----------

export function buildFundPeriodReturnMap(rows: FundPeriodIncreaseItem[]) {
  return Object.fromEntries(
    rows.map((item) => [String(item.title ?? '').toUpperCase(), parseFundPeriodReturn(item.syl)]),
  ) as Record<string, number | null>
}

export function parseFundPeriodReturn(value: string | number | null | undefined) {
  if (value === null || value === undefined || String(value).trim() === '') {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export function buildFundPeriodReturnStats(returnByPeriod: Record<string, number | null>) {
  return FUND_PERIOD_RETURN_FIELDS.map(({ title, label }) => {
    const value = returnByPeriod[title]
    return stat(
      label,
      value !== undefined && value !== null ? `${formatNumber(value)}%` : '--',
      value !== undefined && value !== null ? toneByNumber(value) : 'neutral',
    )
  })
}

// ---------- 基金成本与累计收益数学 ----------

export function compareFundCostTransactions(left: InvestmentTransaction, right: InvestmentTransaction) {
  const leftTime = resolveFundCostTransactionTime(left)
  const rightTime = resolveFundCostTransactionTime(right)
  if (leftTime !== rightTime) {
    return leftTime - rightTime
  }
  return Number(left.id ?? 0) - Number(right.id ?? 0)
}

export function resolveFundCostTransactionTime(entry: InvestmentTransaction) {
  if (entry.settlementAppliedDate) {
    const appliedTime = new Date(`${entry.settlementAppliedDate}T00:00:00`).getTime()
    if (Number.isFinite(appliedTime)) {
      return appliedTime
    }
  }

  const fallbackTime = new Date(entry.settlementConfirmedAt || entry.tradeAt).getTime()
  return Number.isFinite(fallbackTime) ? fallbackTime : 0
}

export function resolveTradeMarkerDateLabel(entry: InvestmentTransaction, isFundLineChart: boolean) {
  if (!isFundLineChart) {
    return normalizeDateLabel(entry.tradeAt)
  }

  const candidateDates = [
    entry.settlementAppliedDate,
    entry.settlementConfirmedAt,
    entry.tradeAt,
  ]

  for (const candidate of candidateDates) {
    const normalized = normalizeDateLabel(candidate || '')
    if (normalized) {
      return normalized
    }
  }

  return ''
}

export function createEmptyFundProfitState(): FundProfitState {
  return {
    holdingQuantity: 0,
    costAmount: 0,
    realizedProfit: 0,
  }
}

export function applyFundProfitTransaction(state: FundProfitState, entry: InvestmentTransaction): FundProfitState {
  const quantity = Number(entry.quantity ?? 0)
  if (!Number.isFinite(quantity) || quantity <= 0) {
    return state
  }

  const amount = Number(entry.amount ?? 0)
  const feeAmount = Number(entry.feeAmount ?? 0)
  const taxAmount = Number(entry.taxAmount ?? 0)
  if (![amount, feeAmount, taxAmount].every(Number.isFinite)) {
    return state
  }

  if (entry.tradeType === 'buy') {
    return {
      holdingQuantity: state.holdingQuantity + quantity,
      costAmount: state.costAmount + amount + feeAmount + taxAmount,
      realizedProfit: state.realizedProfit,
    }
  }

  if (entry.tradeType !== 'sell' || state.holdingQuantity <= 0) {
    return state
  }
  const soldQuantity = Math.min(quantity, state.holdingQuantity)
  const soldCostAmount = state.costAmount / state.holdingQuantity * soldQuantity
  const proceedsRatio = quantity > 0 ? soldQuantity / quantity : 0
  const netProceeds = (amount - feeAmount - taxAmount) * proceedsRatio
  return {
    holdingQuantity: Math.max(state.holdingQuantity - soldQuantity, 0),
    costAmount: Math.max(state.costAmount - soldCostAmount, 0),
    realizedProfit: state.realizedProfit + netProceeds - soldCostAmount,
  }
}

export function hasReliableFundTradeHistory(entries: InvestmentTransaction[], expectedHoldingQuantity: number) {
  if (entries.length === 0 || !Number.isFinite(expectedHoldingQuantity) || expectedHoldingQuantity <= 0) {
    return false
  }
  const finalState = entries.reduce(applyFundProfitTransaction, createEmptyFundProfitState())
  const quantityDifference = Number(Math.abs(finalState.holdingQuantity - expectedHoldingQuantity).toFixed(6))
  return quantityDifference <= FUND_QUANTITY_MATCH_TOLERANCE
}

export function resolveFundProfitNetValue(point: InvestmentChartPoint, fundTrendValueMap: Record<string, FundTrendValueEntry>) {
  const trendEntry = fundTrendValueMap[normalizeDateLabel(point.label)]
  const netValue = Number(trendEntry?.net)
  if (Number.isFinite(netValue) && netValue > 0) {
    return netValue
  }
  return Number(point.value)
}

export function buildFundProfitFallbackPoints(
  position: InvestmentPosition,
  points: InvestmentChartPoint[],
  fundTrendValueMap: Record<string, FundTrendValueEntry>,
) {
  const holdingQuantity = Number(position.holdingQuantity ?? 0)
  const averageCostPrice = Number(position.avgCostPrice ?? 0)
  const realizedProfit = Number(position.cumulativeProfit ?? 0)
  const positionStartTime = parsePointLabelDate(normalizeDateLabel(position.createdAt))?.getTime() ?? NaN
  if (![holdingQuantity, averageCostPrice, realizedProfit].every(Number.isFinite) || holdingQuantity <= 0) {
    return []
  }

  return points.flatMap((point) => {
    const pointDate = parsePointLabelDate(point.label)
    if (!pointDate || (Number.isFinite(positionStartTime) && pointDate.getTime() < positionStartTime)) {
      return []
    }
    const netValue = resolveFundProfitNetValue(point, fundTrendValueMap)
    if (!Number.isFinite(netValue) || netValue <= 0) {
      return []
    }
    return [{
      label: point.label,
      value: Number((realizedProfit + (netValue - averageCostPrice) * holdingQuantity).toFixed(2)),
    }]
  })
}

export function buildFundProfitChartSeriesData(points: InvestmentChartPoint[]) {
  const expandedPoints: Array<{ timestamp: number; value: number }> = []
  const datedPoints = points.flatMap((point) => {
    const timestamp = parsePointLabelDate(point.label)?.getTime() ?? NaN
    const value = Number(point.value)
    return Number.isFinite(timestamp) && Number.isFinite(value) ? [{ timestamp, value }] : []
  })

  datedPoints.forEach((point, index) => {
    const previousPoint = datedPoints[index - 1]
    if (previousPoint && previousPoint.value * point.value < 0) {
      const crossingRatio = Math.abs(previousPoint.value)
        / (Math.abs(previousPoint.value) + Math.abs(point.value))
      expandedPoints.push({
        timestamp: Math.round(previousPoint.timestamp + (point.timestamp - previousPoint.timestamp) * crossingRatio),
        value: 0,
      })
    }
    expandedPoints.push(point)
  })

  return {
    positive: expandedPoints.map<FundProfitChartDataPoint>((point) => [
      point.timestamp,
      point.value >= 0 ? point.value : null,
    ]),
    negative: expandedPoints.map<FundProfitChartDataPoint>((point) => [
      point.timestamp,
      point.value <= 0 ? point.value : null,
    ]),
  }
}

// ---------- 图表 tooltip 取值 ----------

export function getTooltipPointValue(item: any) {
  if (Array.isArray(item?.value)) {
    return item.value[item.value.length - 1]
  }
  return item?.value
}
