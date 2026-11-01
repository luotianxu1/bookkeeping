// 黄金账户持仓页的纯格式化/计算函数：不依赖组件状态，供页面 script 与模板复用。
import type { GoldAccountHolding, GoldAccountSummary } from '@/api/modules/finance'
import { formatAmount } from '@/utils/format'

export function parseAccountId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

export function buildSummary(items: GoldAccountHolding[], cumulativeProfit: number): GoldAccountSummary {
  const totalWeight = items.reduce((total, item) => total + Number(item.weight ?? 0), 0)
  const purchaseTotal = items.reduce((total, item) => total + Number(item.purchaseAmount ?? 0), 0)
  const estimatedValue = items.reduce((total, item) => {
    const marketValue = Number(item.marketValue ?? 0)
    if (marketValue > 0) {
      return total + marketValue
    }
    return total + (Number(item.weight ?? 0) * Number(item.currentPrice ?? 0))
  }, 0)
  const estimatedProfit = items.reduce((total, item) => total + Number(item.holdingProfit ?? 0), 0)

  return {
    totalWeight,
    averagePrice: totalWeight > 0 ? purchaseTotal / totalWeight : 0,
    purchaseTotal,
    estimatedValue,
    estimatedProfit,
    profitRate: purchaseTotal > 0 ? (estimatedProfit / purchaseTotal) * 100 : 0,
    cumulativeProfit: Number(cumulativeProfit ?? 0),
  }
}

export function decorateHolding(item: GoldAccountHolding, price: number) {
  if (!Number.isFinite(price) || price <= 0) {
    return item
  }

  const weight = Number(item.weight ?? 0)
  const purchaseAmount = Number(item.purchaseAmount ?? 0)
  const marketValue = Number((weight * price).toFixed(2))
  const holdingProfit = Number((marketValue - purchaseAmount).toFixed(2))

  return {
    ...item,
    currentPrice: price,
    marketValue,
    holdingProfit,
  }
}

export function toFixedNumber(value: number, digits: number) {
  return Number(value.toFixed(digits))
}

export function formatSummaryWeight(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

export function formatRate(value: number | null | undefined) {
  const rate = Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  return `${rate}%`
}

export function formatAveragePriceTag(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return amount > 0 ? `${formatAmount(amount)}元/克` : '--'
}

export function formatCompactWeight(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  if (!Number.isFinite(amount)) {
    return '0'
  }
  return amount.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })
}

export function formatHoldingMeta(weight: number | null | undefined, amount: number | null | undefined) {
  return `${formatCompactWeight(weight)}克 ${formatAmount(amount)}元`
}

export function formatGoldUpdatedAt(value: string | null | undefined) {
  if (!value) {
    return '等待刷新'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '刚刚更新'
  }

  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

export function getProfitToneClass(value: number | null | undefined) {
  return Number(value ?? 0) < 0 ? 'negative' : 'positive'
}

export function formatCreatedDate(value: string | null | undefined) {
  if (!value) {
    return '--'
  }

  const matched = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (matched) {
    return `${matched[1]}.${matched[2]}.${matched[3]}`
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}.${month}.${day}`
}

export function toApiDateTime(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`
}
