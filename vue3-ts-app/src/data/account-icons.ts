export const DEFAULT_CASH_ACCOUNT_ICON = 'wallet'
export const DEFAULT_INVESTMENT_ACCOUNT_ICON = 'fund'

function withAccountIcons<T extends { value: string }>(options: T[]) {
  return options.map((option) => ({ ...option, icon: option.value }))
}

export const paymentAccountIconOptions = [
  { label: '支付宝 / 余额', value: 'alipay' },
  { label: '微信 / 零钱', value: 'wechat-pay' },
  { label: '数字人民币', value: 'digital-yuan' },
  { label: 'QQ 钱包', value: 'qq-wallet' },
  { label: '京东钱包', value: 'jd-wallet' },
  { label: '云闪付', value: 'unionpay' },
  { label: '美团支付', value: 'meituan-pay' },
  { label: 'Apple Pay', value: 'apple-pay' },
  { label: 'PayPal', value: 'paypal' },
]

export const brokerAccountIconOptions = [
  { label: '中信证券', value: 'broker-citics' },
  { label: '华泰证券', value: 'broker-htsc' },
  { label: '国泰海通证券', value: 'broker-gtht' },
  { label: '招商证券', value: 'broker-cms' },
  { label: '广发证券', value: 'broker-gf' },
  { label: '中国银河证券', value: 'broker-galaxy' },
  { label: '国信证券', value: 'broker-guosen' },
  { label: '申万宏源证券', value: 'broker-swhy' },
  { label: '中信建投证券', value: 'broker-csc' },
  { label: '东方财富证券', value: 'broker-eastmoney' },
  { label: '国金证券', value: 'broker-gjzq' },
  { label: '平安证券', value: 'broker-pingan' },
  { label: '华宝证券', value: 'broker-huabaosc' },
]

const bankAccountIconOptions = [
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
]

export const allAccountIconOptions = withAccountIcons([
  ...bankAccountIconOptions,
  ...paymentAccountIconOptions,
  ...brokerAccountIconOptions,
])

// Retain these aliases for callers that still group options by account type.
export const cashAccountIconOptions = allAccountIconOptions
export const investmentAccountIconOptions = allAccountIconOptions

const supportedAccountIconKeys = new Set<string>([
  ...allAccountIconOptions.map((option) => option.value),
  'wallet',
  'fund',
  'stock',
  'credit-card',
  'debt',
  'gold',
  'human-relation',
  'liability',
  'other-asset',
  'other-liability',
  'other',
  'reserve-fund',
])

export function resolveCashAccountIcon(value?: string | null) {
  const normalized = value?.trim().toLowerCase().replace(/_/g, '-') ?? ''
  if (normalized === 'cash') return DEFAULT_CASH_ACCOUNT_ICON
  return supportedAccountIconKeys.has(normalized) ? normalized : DEFAULT_CASH_ACCOUNT_ICON
}

export function resolveInvestmentAccountIcon(value?: string | null) {
  const normalized = value?.trim().toLowerCase().replace(/_/g, '-') ?? ''
  if (normalized === 'investment') return DEFAULT_INVESTMENT_ACCOUNT_ICON
  return supportedAccountIconKeys.has(normalized) ? normalized : DEFAULT_INVESTMENT_ACCOUNT_ICON
}
