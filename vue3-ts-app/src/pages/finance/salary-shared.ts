import type {
  SalaryAccountType,
  SalaryAccountRecordItem,
} from '@/api/modules/finance'

export const salaryAccountPathMap: Record<string, string> = {
  social_security: 'social-security',
  housing_fund: 'housing-fund',
  medical: 'medical',
}

export function formatSalaryCurrency(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return `¥${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number.isFinite(amount) ? amount : 0)}`
}

export function formatSalaryCurrencyPlain(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number.isFinite(amount) ? amount : 0)
}

export function formatSalaryPercent(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return `${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(Number.isFinite(amount) ? amount : 0)}%`
}

export function getSalaryAccountRoutePath(accountType: SalaryAccountType | string) {
  const mapped = salaryAccountPathMap[String(accountType)] || String(accountType)
  return `/finance/salary/accounts/${mapped}`
}

export function normalizeSalaryAccountType(routeValue: string) {
  switch (routeValue) {
    case 'housing-fund':
      return 'housing_fund' as const
    case 'medical':
      return 'medical' as const
    default:
      return 'social_security' as const
  }
}

export function salaryAccountDisplayName(accountType: string) {
  switch (accountType) {
    case 'housing_fund':
    case 'housing-fund':
      return '公积金账户'
    case 'medical':
      return '医保账户'
    default:
      return '社保账户'
  }
}

export function monthInputToDate(value: string) {
  return value ? `${value}-01` : ''
}

export function dateToMonthInput(value?: string | null) {
  if (!value) {
    return ''
  }
  return value.slice(0, 7)
}

export function createRecentYearOptions(count = 4) {
  const currentYear = new Date().getFullYear()
  return Array.from({ length: count }, (_, index) => {
    const year = currentYear - index
    return {
      label: `${year} 年`,
      value: String(year),
    }
  })
}

export function recordCanDelete(record: SalaryAccountRecordItem | null) {
  return Boolean(record && record.editable && record.recordType !== 'initial')
}
