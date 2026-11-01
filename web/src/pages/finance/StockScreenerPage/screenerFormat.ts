import type { StockScreenItem, StockScreenRun } from '@/api/modules/finance'

export function clampNumber(value: number, min: number, max: number, fallback: number) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? Math.min(max, Math.max(min, normalized)) : fallback
}

export function formatDate(value?: string | null) {
  if (!value) return '--'
  const parts = value.split('-')
  return parts.length === 3 ? `${parts[0]}.${parts[1]}.${parts[2]}` : value
}

export function formatPrice(value?: number | null) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized.toFixed(2) : '--'
}

export function formatSignedPercent(value?: number | null) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized)) return '--'
  return `${normalized > 0 ? '+' : ''}${normalized.toFixed(2)}%`
}

export function formatPercent(value?: number | null) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? `${normalized.toFixed(0)}%` : '--'
}

export function formatIndexValue(value?: number | null) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized.toFixed(2) : '--'
}

export function formatTurnover(value?: number | null) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized) || normalized <= 0) return '--'
  if (normalized >= 1_000_000_000_000) return `${(normalized / 1_000_000_000_000).toFixed(2)}万亿`
  return `${(normalized / 100_000_000).toFixed(0)}亿`
}

export function formatMarketTime(value?: string | null) {
  if (!value) return '--:--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--:--'
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

export function marketChangeClass(value?: number | null) {
  const normalized = Number(value)
  return normalized > 0 ? 'positive' : normalized < 0 ? 'negative' : 'neutral'
}

export function marketLabel(market: string) {
  return market === 'SH' ? '沪市' : market === 'SZ' ? '深市' : market === 'BJ' ? '北交所' : market
}

// 今日涨幅：信号日收盘相对前一日收盘的百分比。
export function itemChangePercent(item: StockScreenItem) {
  const prev = Number(item.previousClose)
  const close = Number(item.signalClose)
  if (!Number.isFinite(prev) || !Number.isFinite(close) || prev <= 0) {
    return null
  }
  return ((close - prev) / prev) * 100
}

export function toScreenerSymbol(code: string, market: string) {
  const prefix = market === 'SH' ? 'sh' : market === 'SZ' ? 'sz' : market === 'BJ' ? 'bj' : (code.startsWith('6') ? 'sh' : 'sz')
  return `${prefix}${code}`
}

export function formatKlineNumber(value: unknown, fractionDigits = 2) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized.toFixed(fractionDigits) : '--'
}

export function createOptimisticRunningState(current: StockScreenRun | null, afterRunId: number): StockScreenRun {
  return {
    id: current?.id || afterRunId,
    tradeDate: current?.tradeDate || null,
    triggerName: 'manual-api',
    status: 'running',
    totalStocks: 0,
    processedStocks: 0,
    matchedStocks: 0,
    failedStocks: 0,
    dataSource: current?.dataSource || '公开行情',
    resultMessage: null,
    errorMessage: null,
    startedAt: new Date().toISOString(),
    finishedAt: null,
  }
}
