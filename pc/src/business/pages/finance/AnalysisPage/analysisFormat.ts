import { formatAmount } from '@/business/utils/format'
import type {
  Transaction,
  TransactionAnalysisCategoryBreakdownItem,
  TransactionAnalysisPeriod,
  TransactionAnalysisPeriodSummary,
} from '@/business/api/modules/finance'

export type SummaryTab = '收入' | '支出' | '结余'
export type TrendTone = 'income' | 'expense' | 'neutral'

const integerCurrencyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
})

export function findPreferredSummaryKey(
  items: TransactionAnalysisPeriodSummary[],
) {
  return (
    items.find((item) => item.transactionCount > 0)?.key ?? items[0]?.key ?? ''
  )
}

export function findPreferredDayKey(
  items: TransactionAnalysisPeriodSummary[],
  currentMonth?: string | null,
) {
  if (currentMonth === buildMonthKey(new Date())) {
    const todayKey = buildDateKey(new Date())
    if (items.some((item) => item.key === todayKey)) {
      return todayKey
    }
  }

  return findPreferredSummaryKey(items)
}

export function getBreakdownItemKey(
  item: TransactionAnalysisCategoryBreakdownItem,
) {
  return item.categoryId == null
    ? `name:${item.categoryName}`
    : `id:${item.categoryId}`
}

export function isTransactionInBreakdown(
  transaction: Transaction,
  item: TransactionAnalysisCategoryBreakdownItem,
) {
  if (item.categoryId != null) {
    return transaction.categoryId === item.categoryId
  }
  return (
    (transaction.categoryName ||
      (transaction.type === 'income' ? '收入' : '支出')) === item.categoryName
  )
}

export function formatIntegerAmount(value: number) {
  return integerCurrencyFormatter.format(Number(value ?? 0))
}

export function formatSignedAmount(value: number) {
  const numericValue = Number(value ?? 0)
  if (numericValue > 0) return `+${formatAmount(numericValue)}`
  if (numericValue < 0) return `-${formatAmount(Math.abs(numericValue))}`
  return formatAmount(0)
}

export function formatSignedIntegerAmount(value: number) {
  const numericValue = Number(value ?? 0)
  if (numericValue > 0) return `+${formatIntegerAmount(numericValue)}`
  if (numericValue < 0) return `-${formatIntegerAmount(Math.abs(numericValue))}`
  return formatIntegerAmount(0)
}

export function amountValue(value: string) {
  const normalized = value.replace(/[^\d.+-]/g, '')
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : 0
}

export function formatAxisValue(value: number) {
  const numericValue = Number(value ?? 0)
  const absoluteValue = Math.abs(numericValue)

  if (absoluteValue >= 10000) {
    return `${(numericValue / 10000).toFixed(1)}w`
  }

  return `${Math.round(numericValue)}`
}

export function buildMonthKey(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${date.getFullYear()}-${month}`
}

export function buildDateKey(date: Date) {
  const day = String(date.getDate()).padStart(2, '0')
  return `${buildMonthKey(date)}-${day}`
}

export function formatMonthDayLabel(key: string) {
  const [, monthText, dayText] = key.split('-')
  if (!monthText || !dayText) {
    return key
  }
  return `${Number(monthText)}月${Number(dayText)}日`
}

export function formatYearMonthLabel(key: string) {
  const [yearText, monthText] = key.split('-')
  if (!yearText || !monthText) {
    return key
  }
  return `${yearText}年${Number(monthText)}月`
}

export function detailMetaText(transaction: Transaction) {
  const category =
    transaction.categoryName ||
    (transaction.type === 'income' ? '收入' : '支出')
  return [transaction.accountName, category, formatTime(transaction.occurredAt)]
    .filter(Boolean)
    .join(' · ')
}

export function breakdownDetailMetaText(transaction: Transaction) {
  const category =
    transaction.categoryName ||
    (transaction.type === 'income' ? '收入' : '支出')
  return [
    transaction.accountName,
    category,
    formatDateTime(transaction.occurredAt),
  ]
    .filter(Boolean)
    .join(' · ')
}

export function detailAmountText(transaction: Transaction) {
  return formatSignedAmount(
    transaction.type === 'income'
      ? Number(transaction.amount)
      : -Number(transaction.amount),
  )
}

export function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

export function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return [
    `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`,
    `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`,
  ].join(' ')
}

export function buildMetricTrend(
  summaryItem: TransactionAnalysisPeriodSummary,
  summaryTab: SummaryTab,
): TrendTone {
  if (summaryTab === '收入') {
    return summaryItem.income > 0 ? 'income' : 'neutral'
  }
  if (summaryTab === '支出') {
    return summaryItem.expense > 0
      ? 'expense'
      : summaryItem.expense < 0
        ? 'income'
        : 'neutral'
  }
  if (summaryItem.surplus > 0) return 'income'
  if (summaryItem.surplus < 0) return 'expense'
  return 'neutral'
}

export function buildMetricAmount(
  summaryItem: TransactionAnalysisPeriodSummary,
  analysisPeriod: TransactionAnalysisPeriod,
  summaryTab: SummaryTab,
) {
  const amountFormatter =
    analysisPeriod === 'month' ? formatIntegerAmount : formatAmount
  const signedAmountFormatter =
    analysisPeriod === 'month' ? formatSignedIntegerAmount : formatSignedAmount

  if (summaryTab === '收入') {
    return summaryItem.income > 0
      ? `+${amountFormatter(summaryItem.income)}`
      : amountFormatter(0)
  }
  if (summaryTab === '支出') {
    return signedAmountFormatter(-summaryItem.expense)
  }
  return signedAmountFormatter(summaryItem.surplus)
}
