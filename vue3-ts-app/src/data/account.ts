// 账户管理静态数据：来自 Pencil「账户管理」页面设计稿。
import type {
  AccountGroup,
  AccountOverview,
  InvestmentDetailOverview,
  InvestmentDetailStat,
  InvestmentDetailTransaction,
} from '@/types/account'

export const accountOverview: AccountOverview = {
  label: '总资产',
  amount: '168,420.00',
}

export const accountGroups: AccountGroup[] = [
  {
    title: '现金账户',
    path: '/finance/accounts/cash',
    items: [
      {
        icon: '💵',
        name: '钱包',
        subtitle: '日常零用',
        amount: '2,300.00',
        path: '/finance/accounts/cash/1',
      },
      { icon: '🏦', name: '招商银行卡', subtitle: '储蓄卡', amount: '10,000.00' },
    ],
  },
  {
    title: '投资账户',
    items: [
      {
        icon: '📈',
        name: '基金账户',
        subtitle: '长期投资',
        amount: '56,800.00',
        path: '/finance/accounts/investment',
      },
      {
        icon: '🥇',
        name: '黄金账户',
        subtitle: '贵金属配置',
        amount: '12,300.00',
        path: '/finance/accounts/gold',
      },
      { icon: '◉', name: '股票账户', subtitle: '短线交易', amount: '99,320.00' },
    ],
  },
]

// 现金账户页静态数据：来自 Pencil「现金账户」页面设计稿。
export const cashAccountOverview = {
  label: '现金账户总额',
  amount: '12,300.00',
}

export const cashAccountItems: AccountGroup['items'] = [
  {
    icon: '💵',
    name: '钱包',
    subtitle: '日常零用',
    amount: '2,300.00',
    path: '/finance/accounts/cash/1',
  },
  { icon: '🏦', name: '招商银行卡', subtitle: '储蓄卡', amount: '10,000.00' },
  { icon: '💳', name: '支付宝', subtitle: '第三方钱包', amount: '0.00' },
  { icon: '🧧', name: '备用金', subtitle: '紧急使用', amount: '0.00' },
]

// 现金资产详情静态数据：来自 Pencil「现金资产-详情」页面设计稿。
export const cashAssetDetailOverview = {
  accountName: '钱包',
  assetType: '现金资产',
  amount: '2,300.00',
  updatedAt: '最近更新：今天 21:36',
}

export const cashAssetHistory = [
  {
    title: '手动修改余额',
    time: '今天 21:36',
    change: '+ 300.00',
    balance: '余额 2,300.00',
    trend: 'up' as const,
  },
  {
    title: '买菜支出',
    time: '昨天 18:20',
    change: '- 86.00',
    balance: '余额 2,000.00',
    trend: 'down' as const,
  },
  {
    title: '转入备用金',
    time: '03-20 09:12',
    change: '+ 500.00',
    balance: '余额 2,086.00',
    trend: 'up' as const,
  },
  {
    title: '初始录入',
    time: '03-18 10:00',
    change: '+ 1,586.00',
    balance: '余额 1,586.00',
    trend: 'up' as const,
  },
]

// 投资账户页静态数据：来自 Pencil「投资账户」页面设计稿。
export const investmentOverview = {
  label: '投资总市值',
  amount: '54,712',
  syncText: '同步于 刚刚',
  todayLabel: '今日盈亏',
  todayValue: '-371',
  todayRate: '-3.13%',
}

export const investmentMetrics = [
  { label: '持仓盈亏', value: '-3,203' },
  { label: '持仓盈亏率', value: '-5.53%' },
  { label: '累计盈亏', value: '-3,166' },
  { label: '累计盈亏率', value: '-5.47%' },
]

export const investmentTabs = ['全部', 'A股', '基金', '其他']

export const investmentHoldings = [
  {
    name: '中银国有企业债A',
    units: 'x 7733.47 份',
    tag: '基金',
    marketValue: '9,870',
    dayLabel: '今日盈亏',
    dayValue: '--',
    netValue: '1.2763',
    netValueDate: '最新净值 03-23',
    costLabel: '成本价',
    costValue: '1.2801',
    pnlLabel: '累计盈亏',
    pnlValue: '-29',
    pnlRate: '-0.30',
    allocationLabel: '仓位占比',
    allocationValue: '18.04%',
    progress: 18.04,
  },
  {
    name: '易方达黄金ETF联接C',
    units: 'x 849.59 份',
    tag: '基金',
    marketValue: '2,822',
    dayLabel: '今日盈亏',
    dayValue: '--',
    netValue: '3.3214',
    netValueDate: '最新净值 03-20',
    costLabel: '成本价',
    costValue: '3.4253',
    pnlLabel: '累计盈亏',
    pnlValue: '-88',
    pnlRate: '-3.03',
    allocationLabel: '仓位占比',
    allocationValue: '5.16%',
    progress: 5.16,
  },
]

// 投资详情页静态数据：来自 Pencil「投资详情」页面设计稿。
export const investmentDetailOverview: InvestmentDetailOverview = {
  name: '中银国有企业债A',
  subtitle: '017123 - 移债基金',
  amount: '1.2763',
  updatedAt: '最新净值 2026-03-23',
  todayLabel: '日涨跌',
  todayValue: '-0.30%',
}

// 投资详情-基础数据：顶部“基金详细数据”卡片。
export const investmentDetailBaseStats: InvestmentDetailStat[] = [
  { label: '基金经理', value: '张晨曦' },
  { label: '成立日期', value: '2019-06-18' },
  { label: '基金规模', value: '82.36 亿' },
  { label: '单位净值', value: '1.2748', tone: 'primary' },
  { label: '管理费', value: '0.30%' },
  { label: '托管费', value: '0.10%' },
  { label: '近1月', value: '+1.26%', tone: 'negative' },
  { label: '近1年', value: '+4.83%', tone: 'negative' },
]

// 投资详情-持仓分析：中部“持仓分析”卡片。
export const investmentDetailAnalysisStats: InvestmentDetailStat[] = [
  { label: '累计分红', value: '126.52' },
  { label: '持仓占比', value: '18.04%', tone: 'primary' },
  { label: '分红方式', value: '红利再投' },
  { label: '下次开放日', value: '2026-03-28' },
]

// 投资详情-说明文本：底部说明卡片。
export const investmentDetailDescription =
  '本基金主要投资于高信用等级债券资产，追求稳健收益并控制波动，适合作为长期配置的一部分。'

// 投资详情-交易记录：底部交易列表卡片。
export const investmentDetailTransactions: InvestmentDetailTransaction[] = [
  {
    title: '买入',
    time: '今天 15:20',
    amount: '金额 382.89',
    units: '+ 300 份',
    note: '金额 382.89',
    trend: 'up',
  },
  {
    title: '分红再投',
    time: '03-22 10:15',
    amount: '金额 36.50',
    units: '+ 28.6 份',
    note: '金额 36.50',
    trend: 'up',
  },
  {
    title: '减仓',
    time: '03-18 09:42',
    amount: '金额 128.01',
    units: '- 100 份',
    note: '金额 128.01',
    trend: 'down',
  },
  {
    title: '初始建仓',
    time: '03-01 11:00',
    amount: '金额 9,322.60',
    units: '+ 7,505 份',
    note: '金额 9,322.60',
    trend: 'up',
  },
]

// 黄金账户页静态数据：用于“黄金账户 / 黄金账户持仓 / 清仓记录”页面。
export const goldAccountOverview = {
  totalWeight: '5.00',
  averagePrice: '574.61',
  purchaseTotal: '2873.05',
  estimatedValue: '5088.60',
  estimatedProfit: '2215.55',
  profitRate: '77.1149',
  cumulativeProfit: '2215.55',
}

export const goldAccountHoldings = [
  {
    id: 'icbc-gold',
    tagPrice: '574.61/克',
    accountName: '工商银行',
    amount: '2873.05',
    weight: '5.00g',
    date: '2024.08.05',
    holdingProfit: '+2215.55',
  },
  {
    id: 'ccb-gold',
    tagPrice: '571.20/克',
    accountName: '建设银行',
    amount: '861.15',
    weight: '1.50g',
    date: '2024.09.12',
    holdingProfit: '+126.30',
  },
  {
    id: 'boc-gold',
    tagPrice: '579.80/克',
    accountName: '中国银行',
    amount: '1053.90',
    weight: '1.50g',
    date: '2024.10.03',
    holdingProfit: '+952.30',
  },
]

export const goldLiquidationSummary = {
  cumulativeWeight: '14.50g',
  cumulativeProfit: '+3,294.05',
}

export const goldLiquidationRecords = [
  {
    id: 'record-1',
    time: '2024.09.21 14:30',
    profit: '+1,126.80',
    weight: '5.00g',
    buyPrice: '574.61',
    sellPrice: '799.97',
    fee: '18.20',
  },
  {
    id: 'record-2',
    time: '2024.10.18 10:05',
    profit: '+2,167.25',
    weight: '9.50g',
    buyPrice: '571.20',
    sellPrice: '806.10',
    fee: '34.60',
  },
]
