// 负债账户详情页的纯格式化/解析函数：不依赖组件状态，供页面 script 与模板复用。
import type {
  LiabilityRecord,
  LiabilityRepaymentStatus,
} from '@/business/api/modules/finance'

export function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(value))
}

export function formatCurrency(value: number) {
  return `¥${formatNumber(value)}`
}

export function roundCurrency(value: number) {
  return Math.round(value * 100) / 100
}

export function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

export function formatRecordAmount(record: LiabilityRecord) {
  return formatCurrency(Number(record.amount ?? 0))
}

export function formatInstallmentLabel(record: LiabilityRecord) {
  if (record.repaymentType === 'prepayment') {
    return '提前还款结清'
  }
  if (!record.installmentTotalPeriods || !record.installmentCurrentPeriod) {
    return '单笔待还'
  }
  return `第 ${record.installmentCurrentPeriod}/${record.installmentTotalPeriods} 期`
}

export function formatRemainingLabel(record: LiabilityRecord) {
  if (record.repaymentType === 'prepayment') {
    return '结清剩余贷款'
  }
  if (!record.installmentTotalPeriods || !record.installmentCurrentPeriod) {
    return '未设置分期'
  }
  return `剩余 ${Math.max(record.installmentTotalPeriods - record.installmentCurrentPeriod, 0)} 期`
}

export function formatRepaymentStatus(
  status: LiabilityRepaymentStatus,
  repaymentType?: string | null,
) {
  if (repaymentType === 'prepayment') {
    return '提前结清'
  }
  return status === 'paid' ? '已还' : '待还'
}

export function toDateTimeLocalValue(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

export function toIsoLocalString(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toISOString().slice(0, 19)
}

export function parseDateTimeLocal(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function parsePositiveAmount(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return Number.NaN
  }
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? parsed : Number.NaN
}

// 注意：本函数语义是“正整数、空串返回 null、非法输入返回 NaN”，
// 与 AccountManagementPage 中的同名函数（非负数归一化，语义不同）不是同一实现，不得混用或合并。
export function normalizePositiveInteger(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const parsed = Number(trimmed)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return Number.NaN
  }
  return parsed
}
