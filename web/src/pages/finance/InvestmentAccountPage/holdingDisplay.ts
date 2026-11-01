// 投资账户页持仓展示的纯函数集合：只做「持仓数据 → 展示文案/色调」的映射，不持有响应式状态。
// 依赖页面级状态的函数（定投计划、待确认金额、总市值）一律通过参数显式传入。
import { formatAmount } from '@/utils/format'
import type { InvestmentPosition, InvestmentTransaction } from '@/api/modules/finance'

export type HoldingTone = 'positive' | 'negative' | 'neutral' | 'inherit'

export function formatCurrency(value: number, digits = 2) {
  const numeric = Number(value)
  const sign = numeric < 0 ? '-' : ''
  return `${sign}¥${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(numeric))}`
}

export function isAfterQuoteRefreshTime(date: Date) {
  const hours = date.getHours()
  const minutes = date.getMinutes()
  return hours > 21 || (hours === 21 && minutes >= 30)
}

export function formatQuantity(value: number, unitName?: string | null) {
  return `${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value)} ${unitName || '份'}`
}

export function formatPrice(value: number) {
  return formatCurrency(value, 4)
}

export function formatMonthDay(value?: string | null) {
  if (!value) {
    return '--'
  }
  const normalized = /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    const matched = value.match(/^\d{4}-(\d{2})-(\d{2})/)
    return matched ? `${matched[1]}-${matched[2]}` : value
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day}`
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

export function isPendingSubscription(position: InvestmentPosition) {
  return position.subscriptionStatus === 'pending'
}

export function hasConfirmedHoldingQuantity(position: InvestmentPosition) {
  const quantity = Number(position.holdingQuantity ?? 0)
  return Number.isFinite(quantity) && quantity > 0
}

export function shouldDisplayHolding(position: InvestmentPosition) {
  if (position.productType !== 'fund') {
    return true
  }
  return hasConfirmedHoldingQuantity(position) || isPendingSubscription(position)
}

export function getHoldingStatusText(position: InvestmentPosition) {
  if (!isPendingSubscription(position) || hasConfirmedHoldingQuantity(position)) {
    return formatQuantity(position.holdingQuantity, position.unitName)
  }

  return ''
}

export function getHoldingQuantityTag(position: InvestmentPosition) {
  const quantityText = getHoldingStatusText(position)
  return quantityText || '待确认'
}

export function getHoldingSyncText(position: InvestmentPosition) {
  const syncSource = resolveHoldingSyncTime(position)
  if (!syncSource) {
    return ''
  }
  const date = new Date(syncSource.value)
  if (Number.isNaN(date.getTime())) {
    return syncSource.value
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

export function resolveHoldingSyncTime(position: InvestmentPosition) {
  if (position.productType === 'fund' && position.lastSyncedAt) {
    return {
      value: position.lastSyncedAt,
    }
  }

  if (position.productType === 'fund' && position.subscriptionConfirmedAt) {
    return {
      value: position.subscriptionConfirmedAt,
    }
  }

  if (position.lastSyncedAt) {
    return {
      value: position.lastSyncedAt,
    }
  }

  return null
}

export function getHoldingMarketValueLabel(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '申购金额' : '持仓市值'
}

export function getHoldingMarketValue(position: InvestmentPosition, pendingAdditionalAmount: number) {
  return isPendingSubscription(position)
    ? formatCurrency(position.costAmount + pendingAdditionalAmount, 2)
    : formatCurrency(position.marketValue, 2)
}

export function getHoldingActionLabel(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '申购处理中' : '今日盈亏'
}

export function getHoldingActionValue(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return formatCurrency(position.costAmount, 0)
  }
  return position.dayProfit === null || position.dayProfit === undefined ? '--' : formatCurrency(position.dayProfit)
}

export function getHoldingActionRate(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return ''
  }
  return position.dayProfitRate === null || position.dayProfitRate === undefined ? '--' : `${formatAmount(position.dayProfitRate)}%`
}

export function getHoldingActionTone(position: InvestmentPosition): HoldingTone {
  if (isPendingSubscription(position)) {
    return 'inherit'
  }
  if (position.dayProfit === null || position.dayProfit === undefined) {
    return 'neutral'
  }
  const dayProfit = Number(position.dayProfit)
  if (!Number.isFinite(dayProfit) || dayProfit === 0) {
    return 'neutral'
  }
  return dayProfit > 0 ? 'positive' : 'negative'
}

export function getHoldingDayProfitText(position: InvestmentPosition) {
  if (isPendingSubscription(position) || position.dayProfit === null || position.dayProfit === undefined) {
    return '--'
  }
  return formatCurrency(position.dayProfit)
}

export function getHoldingDayProfitRateText(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return '待确认'
  }
  return position.dayProfitRate === null || position.dayProfitRate === undefined
    ? '--'
    : `${formatAmount(position.dayProfitRate)}%`
}

export function getHoldingDayProfitTone(position: InvestmentPosition): HoldingTone {
  if (isPendingSubscription(position)) {
    return 'neutral'
  }
  return getHoldingActionTone(position)
}

export function getHoldingPrimaryLabel(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return '确认净值'
  }
  return position.productType === 'stock' ? '最新价' : '最新净值'
}

export function getHoldingPrimaryValue(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return formatMonthDay(position.subscriptionAppliedDate)
  }
  return formatPrice(position.currentPrice)
}

export function getHoldingSecondaryLabel(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '确认份额' : '成本价'
}

export function getHoldingSecondaryValue(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return formatMonthDay(position.subscriptionExpectedConfirmDate || position.subscriptionAppliedDate)
  }
  return formatPrice(position.avgCostPrice)
}

export function getHoldingBottomLabel(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '申购金额' : '累计总收益'
}

export function getHoldingTotalProfit(position: InvestmentPosition) {
  return Number(position.cumulativeProfit ?? 0) + Number(position.holdingProfit ?? 0)
}

export function getHoldingTotalProfitRate(position: InvestmentPosition) {
  const costAmount = Number(position.costAmount ?? 0)
  if (!Number.isFinite(costAmount) || costAmount <= 0) {
    return 0
  }
  return (getHoldingTotalProfit(position) / costAmount) * 100
}

export function getHoldingBottomValue(position: InvestmentPosition) {
  return isPendingSubscription(position) ? formatCurrency(position.costAmount, 0) : formatCurrency(getHoldingTotalProfit(position))
}

export function getHoldingBottomRate(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '待确认' : `${formatAmount(getHoldingTotalProfitRate(position))}%`
}

export function getHoldingProfitLabel(position: InvestmentPosition) {
  return isPendingSubscription(position) ? '持仓收益' : '持仓收益'
}

export function getHoldingProfitValue(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return '--'
  }
  return formatCurrency(position.holdingProfit)
}

export function getHoldingProfitRate(position: InvestmentPosition) {
  if (isPendingSubscription(position)) {
    return '待确认'
  }
  return `${formatAmount(position.holdingProfitRate)}%`
}

export function getAllocationPercent(position: InvestmentPosition, totalMarketValue: number) {
  const total = Number(totalMarketValue)
  if (!Number.isFinite(total) || total <= 0) {
    return 0
  }
  const percent = (Number(position.marketValue) / total) * 100
  if (!Number.isFinite(percent)) {
    return 0
  }
  return Math.min(Math.max(percent, 0), 100)
}

export function compareHoldingsByProfitRate(a: InvestmentPosition, b: InvestmentPosition) {
  const aPending = isPendingSubscription(a)
  const bPending = isPendingSubscription(b)
  if (aPending !== bPending) {
    return aPending ? 1 : -1
  }

  const profitDiff = getHoldingTotalProfit(b) - getHoldingTotalProfit(a)
  if (profitDiff !== 0) {
    return profitDiff
  }

  const rateDiff = getHoldingTotalProfitRate(b) - getHoldingTotalProfitRate(a)
  if (rateDiff !== 0) {
    return rateDiff
  }

  return Number(b.marketValue ?? 0) - Number(a.marketValue ?? 0)
}

export function getHoldingPendingAmount(position: InvestmentPosition, pendingAmountsByPositionId: Map<number, number>) {
  return pendingAmountsByPositionId.get(position.id) ?? 0
}

export function isInitialFundSubscriptionTransaction(entry: InvestmentTransaction) {
  return entry.remark === '基金首次申购'
    || entry.transactionNo.startsWith('INIT-PENDING-')
    || entry.transactionNo.startsWith('INIT-')
}

export function hasAutoInvestPlan(position: InvestmentPosition, autoInvestPositionIds: Set<number>) {
  return position.productType === 'fund' && autoInvestPositionIds.has(position.id)
}

export function hasRecentDividendReminder(position: InvestmentPosition) {
  return position.productType === 'fund' && Boolean(position.hasRecentDividendPlan && position.recentDividendDate)
}

export function getDividendReminderLabel(position: InvestmentPosition) {
  const status = position.recentDividendStatus
  if (status === 'paid') {
    return '最近已分红'
  }
  if (status === 'planned') {
    return '计划分红'
  }
  if (status === 'cancelled') {
    return '分红取消'
  }
  return '已公告分红'
}

export function getDividendReminderText(position: InvestmentPosition) {
  const dateText = formatMonthDay(position.recentDividendDate)
  const perUnit = Number(position.recentDividendPerUnit ?? 0)
  if (!Number.isFinite(perUnit) || perUnit <= 0) {
    return `${dateText} 分红`
  }
  return `${dateText} · ${perUnit.toFixed(4)}/${position.unitName || '份'}`
}

export function getFundPositionSubmitMessage(position: InvestmentPosition) {
  if (position.subscriptionStatus === 'confirmed') {
    return '基金申购已按最新净值确认，份额已生成'
  }
  const appliedDate = position.subscriptionAppliedDate || '--'
  const expectedDate = position.subscriptionExpectedConfirmDate || appliedDate
  if (expectedDate === appliedDate) {
    return `基金申购已提交，将按 ${appliedDate} 净值确认份额`
  }
  return `基金申购已提交，将按 ${appliedDate} 净值确认，预计 ${expectedDate} 完成`
}
