export const DEFAULT_CASH_ACCOUNT_ICON = 'wallet'

export const cashAccountIconOptions = [
  { label: '现金 / 钱包', value: 'wallet' },
  { label: '通用银行卡', value: 'bank-card' },
  { label: '中国工商银行', value: 'bank-icbc' },
  { label: '中国建设银行', value: 'bank-ccb' },
  { label: '中国农业银行', value: 'bank-abc' },
  { label: '中国银行', value: 'bank-boc' },
  { label: '招商银行', value: 'bank-cmb' },
  { label: '交通银行', value: 'bank-bocom' },
  { label: '中国邮政储蓄银行', value: 'bank-psbc' },
  { label: '中信银行', value: 'bank-citic' },
  { label: '中国民生银行', value: 'bank-cmbc' },
  { label: '兴业银行', value: 'bank-cib' },
  { label: '浦发银行', value: 'bank-spdb' },
  { label: '平安银行', value: 'bank-pingan' },
  { label: '中国光大银行', value: 'bank-ceb' },
  { label: '广发银行', value: 'bank-cgb' },
  { label: '网商银行', value: 'bank-mybank' },
  { label: '支付宝 / 移动钱包', value: 'alipay' },
  { label: '备用金', value: 'reserve-fund' },
]

const cashAccountIconKeys = new Set<string>(cashAccountIconOptions.map((option) => option.value))

export function resolveCashAccountIcon(value?: string | null) {
  const normalized = value?.trim().toLowerCase().replace(/_/g, '-') ?? ''
  if (normalized === 'cash') return DEFAULT_CASH_ACCOUNT_ICON
  return cashAccountIconKeys.has(normalized) ? normalized : DEFAULT_CASH_ACCOUNT_ICON
}
