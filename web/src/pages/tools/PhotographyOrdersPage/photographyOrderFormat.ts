// PhotographyOrdersPage / PhotographyOrderOverviewPage 共享的纯格式化与状态判断函数。
import type { Account } from '@/api/modules/finance'
import type { PhotographyOrder, PhotographyOrderType } from '@/api/modules/tool'

export const typeOptions: Array<{ label: string; value: PhotographyOrderType }> = [
  { label: '周岁', value: 'first_birthday' },
  { label: '百天', value: 'hundred_days' },
  { label: '订婚', value: 'engagement' },
  { label: '答谢宴', value: 'thanks_banquet' },
  { label: '婚礼', value: 'wedding' },
  { label: '领证', value: 'marriage_registration' },
  { label: '毕业照', value: 'graduation' },
]

export function isCashAccount(account: Account) {
  return account.accountTypeCode === 'cash' || account.accountTypeName?.includes('现金')
}

export function parseAmount(value: string) {
  const normalized = Number(value.trim() || '0')
  if (!Number.isFinite(normalized)) {
    return 0
  }
  return roundAmount(normalized)
}

export function roundAmount(value: number) {
  return Number(value.toFixed(2))
}

export function nullableText(value: string) {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

export function normalizeDateTimeLocal(value: string) {
  return value.length === 16 ? `${value}:00` : value
}

export function normalizeDateTimeInput(value: string) {
  return value.replace(' ', 'T').slice(0, 16)
}

export function buildDefaultDateTimeLocal() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hour = String(now.getHours()).padStart(2, '0')
  const minute = String(now.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

export function formatDateTime(value: string) {
  if (!value) {
    return '--'
  }

  const [datePart = '', timePart = ''] = value.replace('T', ' ').split(' ')
  const displayDate = datePart.replace(/-/g, '.')
  return `${displayDate} ${timePart.slice(0, 5)}`
}

export function formatCurrency(value: number | string | null | undefined) {
  const amount = Number(value ?? 0)
  return `¥${amount.toLocaleString('zh-CN', {
    minimumFractionDigits: amount % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  })}`
}

export function orderTypeLabel(type: string) {
  return typeOptions.find((option) => option.value === type)?.label ?? type
}

export function isShotByTime(order: PhotographyOrder, now = Date.now()) {
  if (isCancelled(order)) {
    return false
  }
  const shootAt = new Date(order.shootAt).getTime()
  return Number.isFinite(shootAt) && shootAt <= now
}

export function isFinalPaid(order: PhotographyOrder) {
  return Boolean(order.finalReceivedAt)
}

export function isCancelled(order: PhotographyOrder) {
  return order.status === 'cancelled'
}

export function depositStatusLabel(order: PhotographyOrder) {
  if (isShotByTime(order)) {
    return '已拍摄'
  }
  return Number(order.depositAmount) > 0 ? '已付订金' : '未付订金'
}

export function depositStatusClass(order: PhotographyOrder) {
  return isShotByTime(order) ? 'order-card-deposit--shot' : 'order-card-deposit--pending'
}

export function finalStatusLabel(order: PhotographyOrder) {
  if (isCancelled(order)) {
    return '已取消'
  }
  if (Number(order.finalAmount ?? 0) <= 0) {
    return '无尾款'
  }

  return isFinalPaid(order) ? '尾款已收' : '待收尾款'
}

export function finalStatusClass(order: PhotographyOrder) {
  if (isCancelled(order)) {
    return 'order-card-final--cancelled'
  }
  if (Number(order.finalAmount ?? 0) <= 0) {
    return 'order-card-final--empty'
  }

  return isFinalPaid(order) ? 'order-card-final--paid' : 'order-card-final--pending'
}

export function amountTextClass(value: number | string | null | undefined) {
  return Number(value ?? 0) === 0 ? 'is-zero' : ''
}

export function buildCurrentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

// ---- 以下来自 PhotographyOrderOverviewPage ----

export function formatYAxisAmount(value: number) {
  return `¥${(value / 1000).toLocaleString('zh-CN', {
    maximumFractionDigits: 1,
  })}k`
}

export function formatDateLabel(value: string | null | undefined) {
  if (!value) return ''
  const matched = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!matched) return value
  return `${Number(matched[2])}月${Number(matched[3])}日`
}

export function receivedAmount(order: PhotographyOrder) {
  let total = 0
  if (order.depositReceivedAt) {
    total += Number(order.depositAmount ?? 0)
  }
  if (order.finalReceivedAt) {
    total += Number(order.finalAmount ?? 0)
  }
  return total
}

export function statusLabel(order: PhotographyOrder) {
  if (isCancelled(order)) {
    return '已取消'
  }

  if (isShotByTime(order)) {
    return '已拍摄'
  }

  return Number(order.depositAmount ?? 0) > 0 ? '已付订金' : '未付订金'
}

export function statusClass(order: PhotographyOrder) {
  if (isCancelled(order)) {
    return 'overview-order-tag--cancelled'
  }

  return isShotByTime(order) ? 'overview-order-tag--shot' : 'overview-order-tag--pending'
}

export function hasOrderFoot(order: PhotographyOrder) {
  return Boolean(order.address || order.contactInfo || order.remark)
}

// 注意：typeAccent 读取 DOM（getComputedStyle），非纯函数，仅因与订单类型强相关而放在本文件。
export function typeAccent(type: string) {
  const rootStyle = getComputedStyle(document.documentElement)
  return {
    wedding: rootStyle.getPropertyValue('--color-warning-strong').trim(),
    graduation: rootStyle.getPropertyValue('--color-brand-strong').trim(),
    first_birthday: rootStyle.getPropertyValue('--color-success-strong').trim(),
    hundred_days: rootStyle.getPropertyValue('--color-teal').trim(),
    engagement: rootStyle.getPropertyValue('--color-danger').trim(),
    thanks_banquet: rootStyle.getPropertyValue('--color-purple').trim(),
    marriage_registration: rootStyle.getPropertyValue('--color-orange').trim(),
  }[type] ?? rootStyle.getPropertyValue('--color-brand').trim()
}

export function isBucketWeekend(key: string) {
  const weekday = new Date(`${key}T12:00:00`).getDay()
  return weekday === 0 || weekday === 6
}
