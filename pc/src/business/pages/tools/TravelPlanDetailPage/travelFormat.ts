// 旅行详情页的纯展示格式化辅助（自 index.vue 抽出，无状态依赖）。
import type { TravelPlanDetail } from '@/business/api/modules/tool'

export type TravelTone = 'blue' | 'green' | 'orange' | 'violet'

/** 行程/费用类型 → 色调 */
export function typeTone(type: TravelTone | string): TravelTone {
  switch (type) {
    case 'blue':
    case 'transport':
      return 'blue'
    case 'green':
    case 'scenic':
      return 'green'
    case 'orange':
    case 'dining':
      return 'orange'
    default:
      return 'violet'
  }
}

export function expenseAmountText(amount: number) {
  return `¥ ${amount}`
}

export function overviewExpenseRowAmount(item: { amount: number }) {
  return `¥ ${item.amount}`
}

export function formatMetric(value: number | undefined) {
  return `¥ ${(value ?? 0).toLocaleString('zh-CN')}`
}

export function formatDayTabLabel(day: TravelPlanDetail['days'][number]) {
  if (day.travelDate) {
    return formatDate(day.travelDate)
  }
  return `Day ${day.dayIndex}`
}

export function formatDate(dateText?: string | null) {
  return dateText ? dateText.split('-').join('.') : ''
}

export function calculateDayCount(
  startDate?: string | null,
  endDate?: string | null,
) {
  if (!startDate || !endDate) {
    return 0
  }

  const start = new Date(`${startDate}T00:00:00`)
  const end = new Date(`${endDate}T00:00:00`)
  if (
    Number.isNaN(start.getTime()) ||
    Number.isNaN(end.getTime()) ||
    end < start
  ) {
    return 0
  }

  const millisecondsPerDay = 24 * 60 * 60 * 1000
  return Math.floor((end.getTime() - start.getTime()) / millisecondsPerDay) + 1
}

export function addDays(startDate?: string | null, offset = 0) {
  if (!startDate) {
    return ''
  }

  const nextDate = new Date(`${startDate}T00:00:00`)
  if (Number.isNaN(nextDate.getTime())) {
    return ''
  }

  nextDate.setDate(nextDate.getDate() + offset)
  const year = nextDate.getFullYear()
  const month = `${nextDate.getMonth() + 1}`.padStart(2, '0')
  const day = `${nextDate.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function normalizeTimeValue(value: string) {
  return value ? `${value}:00` : undefined
}

export function formatStartTime(time?: string | null) {
  if (!time) {
    return '待定'
  }
  return time.slice(0, 5)
}

export function formatDistance(distanceMeters?: number | null) {
  if (!distanceMeters || distanceMeters <= 0) {
    return ''
  }
  if (distanceMeters >= 1000) {
    return `${(distanceMeters / 1000).toFixed(1)} km`
  }
  return `${distanceMeters} m`
}

export function formatDuration(durationSeconds?: number | null) {
  if (!durationSeconds || durationSeconds <= 0) {
    return ''
  }
  const totalMinutes = Math.round(durationSeconds / 60)
  if (totalMinutes < 60) {
    return `${totalMinutes} 分钟`
  }
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return minutes > 0 ? `${hours} 小时 ${minutes} 分钟` : `${hours} 小时`
}

export function itineraryDescription(
  item: TravelPlanDetail['days'][number]['itineraries'][number],
) {
  return item.remark || item.address || item.poiName || '待补充详细说明'
}

export function expenseNote(
  item: TravelPlanDetail['days'][number]['expenses'][number],
) {
  return expenseTypeLabel(item.type)
}

export function expenseTypeLabel(type: string) {
  if (type === 'transport') {
    return '交通'
  }
  if (type === 'scenic') {
    return '景区'
  }
  if (type === 'dining') {
    return '餐饮'
  }
  if (type === 'accommodation') {
    return '住宿'
  }
  return '其他'
}

export function routeCountText(itineraryCount: number) {
  return `${itineraryCount} 个点位`
}

export function routeTransferSummary(
  item: TravelPlanDetail['days'][number]['itineraries'][number],
) {
  const parts = [
    formatDistance(item.distanceMeters),
    formatDuration(item.durationSeconds),
  ].filter(Boolean)
  if (parts.length === 0) {
    return '路程待补充'
  }
  return `路程 ${parts.join(' · ')}`
}
