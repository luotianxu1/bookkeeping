import type { Transaction as ApiTransaction } from '@/api/modules/finance'
import type { DayGroup } from '@/types/finance'

export function buildTransactionDayGroups(source: ApiTransaction[]): DayGroup[] {
  const grouped = new Map<string, ApiTransaction[]>()
  source.forEach((transaction) => {
    const dayKey = formatDayKey(transaction)
    const group = grouped.get(dayKey) ?? []
    group.push(transaction)
    grouped.set(dayKey, group)
  })

  return Array.from(grouped.entries()).map(([dayKey, groupTransactions]) => {
    const income = groupTransactions
      .filter((transaction) => transaction.type === 'income')
      .reduce((total, transaction) => total + Number(transaction.amount), 0)
    const expense = groupTransactions
      .filter((transaction) => transaction.type === 'expense')
      .reduce((total, transaction) => total + Number(transaction.amount), 0)
    const surplus = income - expense

    return {
      date: formatDayLabel(dayKey),
      income: plainAmount(income),
      expense: plainAmount(expense),
      surplus: `${surplus < 0 ? '-' : ''}${plainAmount(Math.abs(surplus))}`,
      transactions: groupTransactions.map((transaction) => ({
        name: transaction.title,
        time: transactionSubtitle(transaction),
        category: transaction.categoryName ?? '',
        type: transaction.type,
        amount: signedAmount(transaction),
      })),
    }
  })
}

function formatAmount(value: number) {
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function parseTransactionDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return date
}

function formatTransactionTime(value: string) {
  const date = parseTransactionDate(value)
  if (!date) return value
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function transactionSubtitle(transaction: ApiTransaction) {
  const category = transaction.categoryName ?? (transaction.type === 'income' ? '收入' : '支出')
  return [transaction.accountName, category, formatTransactionTime(transaction.occurredAt)]
    .filter(Boolean)
    .join(' · ')
}

function formatDayKey(transaction: ApiTransaction) {
  const date = parseTransactionDate(transaction.occurredAt)
  if (!date) return transaction.occurredAt
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatDayLabel(dayKey: string) {
  const date = parseTransactionDate(`${dayKey}T00:00:00`)
  if (!date) return dayKey

  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)

  if (isSameDate(date, today)) return '今天'
  if (isSameDate(date, yesterday)) return '昨天'

  const pad = (part: number) => String(part).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function isSameDate(left: Date, right: Date) {
  return (
    left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate()
  )
}

function signedAmount(transaction: ApiTransaction) {
  const sign = transaction.type === 'income' ? '+' : '-'
  return `${sign} ¥${formatAmount(Number(transaction.amount))}`
}

function plainAmount(value: number) {
  return `¥${formatAmount(value)}`
}
