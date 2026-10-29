// 行情中心 API（/api/finance/market，Java 端 MarketController）：A股/港股/美股/ETF/黄金/全球模块。
// 行情行使用中文键（与后端 LinkedHashMap 字段一一对应），保持与数据源语义一致。
import { financeRequest, requestGet } from '../request'

/** 实时行情快照行结构（部分常用字段） */
export interface SpotRow {
  代码: string
  名称: string
  最新价: number | null
  涨跌幅: number | null
  成交额: number | null
  [key: string]: string | number | null
}

/** 大盘指数实时行情行结构（新浪沪深重要指数） */
export interface IndexSpotRow {
  代码: string
  名称: string
  最新价: number | null
  涨跌幅: number | null
  涨跌额: number | null
  最高: number | null
  最低: number | null
  成交量: number | null
  成交额: number | null
}

/** 大盘汇总：两市总成交额 + 全市场个股涨跌家数 */
export interface MarketSummary {
  total_amount: number | null
  up: number
  down: number
  flat: number
}

/** 行业板块实时行情行结构（新浪行业/概念板块） */
export interface SectorRow {
  代码: string
  名称: string
  公司家数: number
  平均价: number | null
  涨跌幅: number | null
  总成交额: number | null
  领涨股: string
  领涨股涨跌幅: number | null
}

/** 各市场统一行情行结构（全球/港股/美股/ETF/黄金） */
export interface QuoteRow {
  代码: string
  名称: string
  最新价: number | null
  涨跌额: number | null
  涨跌幅: number | null
  成交额: number | null
}

export type MarketGroup = 'global' | 'hk' | 'us' | 'etf' | 'gold'

/** 详情页实时行情行（新浪 A 股格式，指数/个股/ETF 通用） */
export interface DetailQuote {
  代码: string
  名称: string
  最新价: number | null
  涨跌额: number | null
  涨跌幅: number | null
  最高: number | null
  最低: number | null
  成交量: number | null
  成交额: number | null
}

/** K 线单根数据 */
export interface KlinePoint {
  日期: string
  开盘: number
  最高: number
  最低: number
  收盘: number
  成交量: number
  涨跌幅: number | null
}

/** 详情页标的类型：index/stock 为 A 股，global/hk/us 为海外市场，gold 为黄金模块，module 为全球页模块 */
export type DetailKind = 'index' | 'stock' | 'global' | 'hk' | 'us' | 'gold' | 'module'

/** 详情接口响应 */
export interface DetailData {
  kind: DetailKind
  code: string
  symbol: string
  quote: DetailQuote | null
  /** A股五档盘口：bids 买五档 / asks 卖五档，元素 [价格, 挂单量(股)] */
  depth: {
    bids: [number, number][]
    asks: [number, number][]
    date: string
    time: string
  } | null
  /** 估值资料（A股/港股，腾讯 qt），键为指标名 */
  valuation: Record<string, number> | null
  kline: KlinePoint[]
}

/** 资金流向单日（新浪 MoneyFlow，金额单位元） */
export interface MoneyFlowRow {
  date: string
  close: number
  pct: number
  turnover: number
  /** 净流入占成交额比例（%） */
  ratio: number
  net: number
  xl_net: number
  l_net: number
  m_net: number
  s_net: number
}

/** 公司公告条目 */
export interface AnnouncementItem {
  date: string
  title: string
  url: string
}

/** 分红送配记录（每10股口径） */
export interface DividendItem {
  announce: string
  bonus: number
  convert: number
  dividend: number
  progress: string
  ex_date: string | null
  record_date: string | null
}

/** 详情页扩展信息：资金流向/公司公告/分红送配（仅 A 股个股有数据） */
export interface ExtraInfo {
  moneyflow: MoneyFlowRow[]
  announcements: AnnouncementItem[]
  dividends: DividendItem[]
}

/* ---------- F10：财务指标 / 十大流通股东 / 融资融券 ---------- */

/** 财务指标单行（latest=最新报告期，prev=上年同期，yoy=同比%） */
export interface F10FinancialRow {
  name: string
  latest: number | null
  prev: number | null
  yoy: number | null
}

export interface F10Financials {
  latest_date: string
  prev_date: string
  rows: F10FinancialRow[]
}

/** 十大流通股东单行 */
export interface HolderRow {
  rank: number
  name: string
  shares: number
  ratio: number | null
  note: '' | '增持' | '减持' | '新进'
}

export interface HoldersInfo {
  date: string
  announce: string | null
  total_holders: number | null
  /** 各报告期股东总数（从新到旧，筹码集中度观察用） */
  trend: { date: string; total: number }[]
  rows: HolderRow[]
}

/** 融资融券（金额单位元，量单位股） */
export interface MarginInfo {
  date: string
  prev_date: string | null
  balance: number
  delta: number | null
  buy: number
  repay: number | null
  short_vol: number
  short_sell: number | null
}

/** 所属板块：申万行业 + 概念标签 + 同行业个股（可跳转详情） */
export interface F10Sectors {
  industry: string
  concepts: string[]
  siblings: { code: string; name: string }[]
}

/** 公司概况（新浪公司资料页，仅 A 股） */
export interface F10Profile {
  fields: Record<string, string>
  intro: string
  mainbiz: string
}

export interface F10Data {
  financials: F10Financials | null
  holders: HoldersInfo | null
  margin: MarginInfo | null
  profile: F10Profile | null
  sectors: F10Sectors | null
}

/* ---------- 三大财务报表 / 分时回看 ---------- */

/** 三大报表单表：periods 报告期列（从新到旧），rows 科目行（vals 与 periods 对齐，单位万元） */
export interface StatementTable {
  title: string
  unit: string
  periods: string[]
  rows: { label: string; vals: (number | null)[] }[]
}

export interface StatementsData {
  income: StatementTable | null
  balance: StatementTable | null
  cashflow: StatementTable | null
}

/** 分时回看单日（5 分钟粒度） */
export interface TrendHistoryDay {
  date: string
  prev_close: number | null
  points: TrendPoint[]
}

/** 分时单点（1 分钟） */
export interface TrendPoint {
  time: string
  price: number
  avg: number | null
  volume: number
}

/** 分时接口响应（昨日收盘为基准线） */
export interface TrendData {
  symbol: string
  name: string
  date: string
  prev_close: number | null
  points: TrendPoint[]
}

/* ---------- 市场概况 / 榜单 ---------- */

/** 涨跌分布单档 */
export interface ActivityBucket {
  label: string
  count: number
  side: 'down' | 'flat' | 'up'
}

/** 市场概况：涨跌家数 + 11 档涨跌分布（a/hk/us 均为全市场口径） */
export type ActivityMarket = 'a' | 'hk' | 'us'

export interface MarketActivity {
  up: number
  down: number
  flat: number
  dist: ActivityBucket[]
  /** 参与统计的标的数（a/hk/us 均为全市场口径实际取到行情的数量） */
  sample?: number
  /** 涨停/跌停家数（仅 A 股市场返回） */
  limit_up?: number
  limit_down?: number
}

/** 涨跌停个股明细（仅 A 股，各按成交额降序） */
export interface LimitStock {
  /** 完整新浪代码（sh600715） */
  symbol: string
  /** 6 位代码 */
  code: string
  name: string
  price: number
  pct: number
  amount: number
}

export interface MarketLimits {
  limit_up: number
  limit_down: number
  up: LimitStock[]
  down: LimitStock[]
}

/** 港股/美股涨跌幅榜（领涨/领跌 TOP15，带流动性门槛） */
export interface MoverStock {
  /** 详情路由代码：港股 5 位数字 / 美股 ticker */
  code: string
  name: string
  price: number
  pct: number
  amount: number
}

export interface MarketMovers {
  up: MoverStock[]
  down: MoverStock[]
}

/** 市场实时成交额：今日至今 + 上一交易日同时点（prev 为 null 表示暂无同期数据） */
export interface MarketTurnover {
  today: number
  prev: number | null
  currency: 'CNY' | 'HKD' | 'USD'
}

/** 板块分类：industry=行业板块 / concept=概念板块 */
export type SectorKind = 'industry' | 'concept'

/** 全球页模块（A股指数/全球指数/股指期货/汇率指数/债券期货/全球商品） */
export interface GlobalModule {
  key: string
  title: string
  linkable: boolean
  rows: (QuoteRow & { 源代码?: string; linkable?: boolean })[]
}

/** 搜索结果行：类型与详情页 kind 一致，可直接拼路由 */
export interface SearchRow {
  代码: string
  名称: string
  类型: DetailKind
  市场: string
}

/* ---------- 接口函数（requestGet 解包 Result<T>） ---------- */

/** 健康检查 */
export const getHealth = () => requestGet<{ status: string }>(financeRequest, '/api/finance/market/health')

/** 全球页六大模块行情 */
export const getGlobalModules = () =>
  requestGet<{ count: number; modules: GlobalModule[] }>(financeRequest, '/api/finance/market/global/modules')

/** 市场概况：涨跌家数 + 分布 */
export const getActivity = (market: ActivityMarket = 'a') =>
  requestGet<MarketActivity>(financeRequest, '/api/finance/market/activity', { params: { market } })

/** 涨跌停个股明细（仅 A 股） */
export const getLimits = () => requestGet<MarketLimits>(financeRequest, '/api/finance/market/limits')

/** 港股/美股涨跌幅榜 */
export const getMovers = (market: 'hk' | 'us') =>
  requestGet<MarketMovers>(financeRequest, '/api/finance/market/movers', { params: { market } })

/** 美股行业板块聚合（键结构与 SectorRow 一致，可复用板块组件） */
export const getUsSectors = () =>
  requestGet<{ count: number; data: SectorRow[] }>(financeRequest, '/api/finance/market/sector/us')

/** 今日实时成交额 + 较上一日此时 */
export const getTurnover = (market: ActivityMarket = 'a') =>
  requestGet<MarketTurnover>(financeRequest, '/api/finance/market/turnover', { params: { market } })

/** 大盘核心指数实时行情 */
export const getIndexSpot = () =>
  requestGet<{ count: number; data: IndexSpotRow[]; summary?: MarketSummary }>(
    financeRequest,
    '/api/finance/market/index/spot',
  )

/** 板块实时行情（按涨跌幅降序） */
export const getSectorSpot = (kind: SectorKind = 'industry') =>
  requestGet<{ kind: SectorKind; count: number; data: SectorRow[] }>(financeRequest, '/api/finance/market/sector/spot', {
    params: { kind },
  })

/** 各市场行情（全球/港股/美股/ETF/黄金） */
export const getQuotes = (group: MarketGroup) =>
  requestGet<{ group: string; count: number; data: QuoteRow[] }>(financeRequest, `/api/finance/market/quotes/${group}`)

/** A 股实时行情快照 */
export const getStockSpot = (limit = 50) =>
  requestGet<{ count: number; data: SpotRow[] }>(financeRequest, '/api/finance/market/stock/spot', { params: { limit } })

/** 标的详情：实时行情 + K 线（scale 单位秒，240 = 日K；海外市场仅日K） */
export const getDetail = (kind: DetailKind, code: string, scale = '240', datalen = 120) =>
  requestGet<DetailData>(financeRequest, '/api/finance/market/detail', { params: { kind, code, scale, datalen } })

/** 仅K线序列（详情页滚到最早后向前翻页加载） */
export const getKline = (kind: DetailKind, code: string, scale = '240', datalen = 250) =>
  requestGet<{ count: number; kline: KlinePoint[] }>(financeRequest, '/api/finance/market/kline', {
    params: { kind, code, scale, datalen },
  })

/** 分时（当日 1 分钟线）：A股新浪 / 海外腾讯，美股可能无数据 */
export const getTrend = (kind: DetailKind, code: string) =>
  requestGet<TrendData>(financeRequest, '/api/finance/market/trend', { params: { kind, code } })

/** 近 N 个交易日分时回看（仅 A 股） */
export const getTrendHistory = (kind: DetailKind, code: string, days = 5) =>
  requestGet<{ symbol: string; scale: string; days: TrendHistoryDay[] } | null>(
    financeRequest,
    '/api/finance/market/trend/history',
    { params: { kind, code, days } },
  )

/** 详情页扩展信息：资金流向/公司公告/分红送配（仅 A 股个股有数据） */
export const getExtra = (kind: DetailKind, code: string) =>
  requestGet<ExtraInfo>(financeRequest, '/api/finance/market/extra', { params: { kind, code } })

/** 详情页 F10 信息（仅 A 股个股有数据） */
export const getF10 = (kind: DetailKind, code: string) =>
  requestGet<F10Data>(financeRequest, '/api/finance/market/f10', { params: { kind, code } })

/** 三大财务报表（利润表/资产负债表/现金流量表，仅 A 股） */
export const getStatements = (kind: DetailKind, code: string, periods = 5) =>
  requestGet<StatementsData>(financeRequest, '/api/finance/market/statements', { params: { kind, code, periods } })

/** 股票/指数/港股/美股联想搜索 */
export const searchStocks = (keyword: string) =>
  requestGet<{ count: number; data: SearchRow[] }>(financeRequest, '/api/finance/market/search', { params: { keyword } })
