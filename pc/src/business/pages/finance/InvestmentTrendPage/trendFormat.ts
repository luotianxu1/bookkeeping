import { readChartToken } from '@/business/utils/chart-theme'
import type { AssetTrendAllocation } from '@/business/api/modules/finance'

export function formatCurrency(value?: number | null) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

export function formatSignedCurrency(value?: number | null) {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '--'
  }
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}¥ ${formatCurrency(Math.abs(value))}`
}

export function formatCompactMoney(value: number) {
  const normalized = Number(value ?? 0)
  if (Math.abs(normalized) >= 100000000) {
    return `${(normalized / 100000000).toFixed(2)}亿`
  }
  if (Math.abs(normalized) >= 10000) {
    return `${(normalized / 10000).toFixed(2)}万`
  }
  return normalized.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

export function formatAxisMoney(value: number, axisRange?: number) {
  const normalized = Number(value ?? 0)
  const span = Number(axisRange ?? 0)

  if (span >= 100000) {
    return formatCompactMoney(normalized)
  }

  return normalized.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: span > 0 && span < 1 ? 2 : 0,
  })
}

export function formatDateTime(value?: string | null) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function getAllocationTrackStyle(
  item: AssetTrendAllocation,
  isDark: boolean,
) {
  const colors = getAccountTypeColors(item.accountTypeCode, isDark)
  return {
    '--allocation-fill': colors.fill,
    '--allocation-track': colors.track,
    '--allocation-width': `${Math.max(0, Math.min(Number(item.percent ?? 0), 100))}%`,
  }
}

export function getAllocationDotStyle(
  item: AssetTrendAllocation,
  isDark: boolean,
) {
  return {
    backgroundColor: getAccountTypeColors(item.accountTypeCode, isDark).fill,
  }
}

export function getAccountTypeColors(value?: string | null, isDark?: boolean) {
  // 依赖调用方传入 isDark 让取色在主题切换时重算
  void isDark
  if (value === 'cash') {
    return {
      fill: readChartToken('--color-success', '#16a34a'),
      track: readChartToken('--color-bg-success-soft', '#ecfdf5'),
    }
  }
  if (value === 'investment') {
    return {
      fill: readChartToken('--color-danger', '#dc2626'),
      track: readChartToken('--color-danger-soft', '#fef2f2'),
    }
  }
  if (value === 'gold') {
    return {
      fill: readChartToken('--color-warning-strong', '#d97706'),
      track: readChartToken('--color-warning-soft', '#fff7ed'),
    }
  }
  if (value === 'other_asset') {
    return {
      fill: readChartToken('--color-brand-strong', '#2563eb'),
      track: readChartToken('--color-bg-brand-soft', '#eff6ff'),
    }
  }
  if (
    value === 'debt' ||
    value === 'liability' ||
    value === 'other_liability' ||
    value === 'credit_card'
  ) {
    return {
      fill: readChartToken('--color-purple', '#7c3aed'),
      track: readChartToken('--color-bg-purple-soft', '#f3e8ff'),
    }
  }
  if (value === 'human_relation') {
    return {
      fill: readChartToken('--color-orange', '#c2410c'),
      track: readChartToken('--color-warning-soft', '#fff7ed'),
    }
  }
  return {
    fill: readChartToken('--color-text-muted', '#64748b'),
    track: readChartToken('--color-bg-neutral-soft', '#eef2f8'),
  }
}
