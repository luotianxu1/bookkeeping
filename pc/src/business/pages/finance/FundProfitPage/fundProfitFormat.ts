import { formatPercentage } from '@/business/utils/percentage'
// FundProfitPage 的纯格式化/工具函数（无状态，可单测）。
import type {
  FundProfitDetail,
  FundProfitPageSummaryMetric,
} from '@/business/api/modules/finance'

export const summaryShortcutOrder: Record<string, number> = {
  today: 0,
  '7d': 1,
  month: 2,
  cumulative: 3,
}

export function formatMonthValue(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}`
}

export function formatMonthLabel(value: string) {
  const matched = /^(\d{4})-(\d{2})$/.exec(value)
  if (!matched) {
    return value
  }
  return `${matched[1]}年${matched[2]}月`
}

export function shiftMonthValue(value: string, offset: number) {
  const [yearText, monthText] = value.split('-')
  const date = new Date(Number(yearText), Number(monthText) - 1 + offset, 1)
  return formatMonthValue(date)
}

export function formatNumber(value: number, digits = 2) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(Number(value ?? 0)))
}

export function formatCurrency(value: number, digits = 2) {
  return `¥${formatNumber(value, digits)}`
}

export function formatSignedCurrency(value: number, digits = 2) {
  const normalized = Number(value ?? 0)
  const sign = normalized > 0 ? '+' : normalized < 0 ? '-' : ''
  return `${sign}${formatCurrency(normalized, digits)}`
}

export function formatRate(value: number) {
  return formatPercentage(value, true)
}

export function formatHoldingQuantity(value: number) {
  return `${formatNumber(value, 2)}份`
}

export function formatDateTime(value?: string | null) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

export function getTone(value: number) {
  if (value > 0) {
    return 'positive'
  }
  if (value < 0) {
    return 'negative'
  }
  return 'neutral'
}

export function getDetailAccumulatedProfit(item: FundProfitDetail) {
  return item.holdingAmount - item.costAmount
}

export function isAfterQuoteRefreshTime(date: Date) {
  const hours = date.getHours()
  const minutes = date.getMinutes()
  return hours > 21 || (hours === 21 && minutes >= 30)
}

export function wait(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

export function sortSummaryShortcuts(items: FundProfitPageSummaryMetric[]) {
  return [...items].sort((left, right) => {
    const leftOrder = summaryShortcutOrder[left.key] ?? 99
    const rightOrder = summaryShortcutOrder[right.key] ?? 99
    return leftOrder - rightOrder
  })
}

export function normalizeView(value: string) {
  if (value === 'month' || value === 'year') {
    return value
  }
  return 'day'
}
