<script setup lang="ts">
// 标的详情：实时报价 + K线蜡烛图 + 分时 + 技术指标副图 + 五档盘口/估值 + 资金流向/公告/分红/F10。
// 移植自 investment 项目 Detail 页，浅色移动端主题（涨红 --color-danger / 跌绿 --color-success / 琥珀点缀）。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getDetail,
  getExtra,
  getF10,
  getKline,
  getStatements,
  getTrend,
  getTrendHistory,
  type DetailData,
  type DetailKind,
  type DetailQuote,
  type ExtraInfo,
  type F10Data,
  type KlinePoint,
  type StatementsData,
  type TrendData,
  type TrendHistoryDay,
} from '@/api/modules/market'
import { bias as biasCalc, kdj as kdjCalc, macd as macdCalc, rsi as rsiCalc } from '@/utils/marketIndicators'
import { amt, dirClass, f2, pct, signedYi } from '@/utils/marketFormat'
import { detailRoute } from '../routes'
import PageHeader from '@/components/common/PageHeader/index.vue'

const route = useRoute()
const router = useRouter()

const kind = computed(() => route.params.kind as DetailKind)
const code = computed(() => decodeURIComponent(String(route.params.code ?? '')))

/** 类型徽标文案 */
const KIND_LABEL: Record<DetailKind, string> = {
  index: '指数',
  stock: '个股',
  global: '全球指数',
  hk: '港股',
  us: '美股',
  gold: '黄金',
  module: '全球',
}

/* ---------- 状态 ---------- */
const loading = ref(true)
const error = ref('')
const quote = ref<DetailQuote | null>(null)
const kline = ref<KlinePoint[]>([])
const symbol = ref('')
const depth = ref<DetailData['depth']>(null)
const valuation = ref<DetailData['valuation']>(null)
const extra = ref<ExtraInfo | null>(null) // 资金流向/公告/分红（仅A股）
const f10 = ref<F10Data | null>(null) // 财务指标/十大股东/融资融券（仅A股）
const scale = ref('trend') // 周期模式：默认分时，其余为新浪 K 线周期（秒），240 = 日K
const klineScale = ref('240') // 当前 kline 实际加载的周期（分时模式不改动 K 线数据）
const trendData = ref<TrendData | null>(null)

/* ---------- 视窗状态：滚轮/拖拽浏览历史区间 ---------- */
const chartEl = ref<SVGSVGElement | null>(null)
const viewCount = ref(0) // 可见根数；0 = 未初始化（显示全部）
const endOffset = ref(0) // 右端距最新一根的偏移根数；0 = 对齐最新
const dragging = ref(false)

/** 当前视窗内的K线切片 */
const view = computed(() => {
  const pts = kline.value
  if (!pts.length) return []
  const count = viewCount.value > 0 ? Math.min(viewCount.value, pts.length) : pts.length
  const end = pts.length - endOffset.value
  return pts.slice(Math.max(0, end - count), end)
})

/* ---------- 向前翻页：视窗滚到最早时自动加载更早历史 ---------- */
const loadingMore = ref(false)
const exhausted = ref(false) // 数据源已无更早数据
const PAGE = 250 // 每次向前加载的根数

async function loadMore(): Promise<void> {
  if (loadingMore.value || exhausted.value || !kline.value.length) return
  loadingMore.value = true
  try {
    const data = await getKline(kind.value, code.value, scale.value, kline.value.length + PAGE)
    const firstDate = kline.value[0]['日期']
    const older = data.kline.filter((p) => p['日期'] < firstDate)
    if (!older.length) {
      exhausted.value = true
      return
    }
    kline.value = [...older, ...kline.value]
  } catch {
    // 加载失败不打断浏览，滚回最前端会再次尝试
  } finally {
    loadingMore.value = false
  }
}

/** 视窗左端接近最早数据时触发翻页 */
function maybeLoadMore(): void {
  const len = kline.value.length
  if (exhausted.value || loadingMore.value || !len) return
  const count = Math.min(viewCount.value || len, len)
  if (endOffset.value + count >= len - 5) void loadMore()
}

/* ---------- 点击选中单根K线，显示当日详情 ---------- */
const selDate = ref<string | null>(null)

const selIndex = computed(() => {
  if (!selDate.value) return -1
  return view.value.findIndex((p) => p['日期'] === selDate.value)
})

const selInfo = computed(() => {
  const p = selIndex.value >= 0 ? view.value[selIndex.value] : null
  if (!p) return null
  const gi = kline.value.findIndex((q) => q['日期'] === p['日期'])
  return {
    p,
    up: p['收盘'] >= p['开盘'],
    mas: masAll.value.map((m) => ({
      label: m.label,
      color: m.color,
      v: gi >= 0 ? m.vals[gi] : null,
    })),
  }
})

function onSelect(e: MouseEvent): void {
  // 拖拽结束的 click 不算选中
  if (dragMoved) {
    dragMoved = false
    return
  }
  const el = chartEl.value
  const pts = view.value
  if (!el || !pts.length) return
  const rect = el.getBoundingClientRect()
  const xSvg = ((e.clientX - rect.left) * W) / rect.width
  const step = INNER_W / pts.length
  const i = Math.floor((xSvg - PAD.left) / step)
  if (i < 0 || i >= pts.length) {
    selDate.value = null
    return
  }
  // 再点同根K线取消选中
  selDate.value = selDate.value === pts[i]['日期'] ? null : pts[i]['日期']
}

const PERIODS = computed(() => {
  const base = [
    { label: '分时', value: 'trend' },
    { label: '日K', value: '240' },
  ]
  // 分钟K线仅 A 股数据源支持；黄金与全球模块无分钟K线，只有 分时/日K/周K/月K（期货分时为新浪1分钟线）
  if (kind.value === 'index' || kind.value === 'stock') {
    return [
      ...base,
      { label: '周K', value: 'week' },
      { label: '月K', value: 'month' },
      { label: '60分', value: '60' },
      { label: '30分', value: '30' },
      { label: '15分', value: '15' },
      { label: '5分', value: '5' },
    ]
  }
  if (kind.value === 'gold' || kind.value === 'module') {
    // 期货/全球指数有分时；外汇(fx_)与美元指数(DINI)无分钟数据源
    const hasTrend = kind.value === 'gold' || /^(nf_|hf_|int_|rt_hk)/.test(code.value)
    return [
      ...(hasTrend ? [{ label: '分时', value: 'trend' }] : []),
      { label: '日K', value: '240' },
      { label: '周K', value: 'week' },
      { label: '月K', value: 'month' },
    ]
  }
  return [
    ...base,
    { label: '周K', value: 'week' },
    { label: '月K', value: 'month' },
  ]
})

/** 分时无数据的提示：道指/标普500 无分钟数据源 */
const trendEmptyHint = computed(() => {
  if (kind.value === 'module' && code.value.startsWith('int_'))
    return '该指数暂无分钟数据源（数据方未提供）'
  if (kind.value === 'us' && (code.value === 'dji' || code.value === 'sp500'))
    return '道指/标普500 暂无分钟数据源，可查看纳斯达克综合指数分时'
  return '暂无分时数据'
})

/**
 * 加载图表数据。
 * @param silent 静默模式：不触发整页骨架屏，旧图表保留原地等待新数据替换。
 *               仅在切换周期时使用；切换标的/首次进入仍走完整加载。
 */
async function load(silent = false): Promise<void> {
  // 切换标的或初始进入时，当前周期可能在新标的不提供（如外汇无分时），回退分时/日K；
  // 修改 scale 会触发 watch(scale, load) 重新加载，此处直接返回避免重复请求
  if (!PERIODS.value.some((p) => p.value === scale.value)) {
    scale.value = PERIODS.value.some((p) => p.value === 'trend') ? 'trend' : '240'
    return
  }
  if (scale.value === 'trend') {
    await loadTrend(silent)
    return
  }
  await loadKline(silent)
}

/** 头部行情快照：独立于图表周期加载，永远是今日实时数据（含五档/估值） */
async function loadHeader(): Promise<void> {
  try {
    const data = await getDetail(kind.value, code.value, '240', 5)
    quote.value = data.quote
    depth.value = data.depth
    valuation.value = data.valuation
    symbol.value = data.symbol
  } catch {
    // 头部快照失败不阻塞图表；分时合成行情会兜底
  }
}

/** 图表 K 线：只取序列，不携带头部行情 */
async function loadKline(silent = false): Promise<void> {
  if (!silent) loading.value = true
  try {
    const data = await getKline(kind.value, code.value, scale.value, 250)
    kline.value = data.kline
    klineScale.value = scale.value
    // 分钟K默认展示范围：60/30分为最近一周（5 个交易日），15/5分为今天；
    // 日/周/月K默认最近 120 根。更早历史仍可拖拽平移/双指缩放查看
    let vc = Math.min(data.kline.length, 120)
    const minuteDays =
      scale.value === '60' || scale.value === '30' ? 5 : scale.value === '15' || scale.value === '5' ? 1 : 0
    if (minuteDays > 0 && data.kline.length) {
      const seen = new Set<string>()
      let count = 0
      for (let i = data.kline.length - 1; i >= 0; i--) {
        const day = data.kline[i]['日期'].slice(0, 10)
        if (!seen.has(day)) {
          if (seen.size >= minuteDays) break
          seen.add(day)
        }
        count++
      }
      vc = count
    }
    viewCount.value = vc
    endOffset.value = 0
    exhausted.value = false
    selDate.value = null
    error.value = ''
  } catch (e) {
    // 静默模式下保留旧图表，仅在没有旧数据时才展示错误
    if (!kline.value.length)
      error.value = e instanceof Error ? e.message || '详情加载失败' : '详情加载失败'
  } finally {
    loading.value = false
  }
}

async function loadTrend(silent = false): Promise<void> {
  if (!silent) loading.value = true
  selDate.value = null
  selIdx.value = null
  trendDayIdx.value = -1
  try {
    trendData.value = await getTrend(kind.value, code.value)
    if (trendData.value) symbol.value = trendData.value.symbol
    error.value = ''
  } catch (e) {
    // 静默模式下保留旧分时图，仅在没有旧数据时才展示错误
    if (!trendData.value)
      error.value = e instanceof Error ? e.message || '分时加载失败' : '分时加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => [kind.value, code.value], load)
/** 切换周期只静默重渲染图表：不触发整页骨架屏，旧图保留原地替换 */
watch(scale, () => load(true))
/** 头部实时快照：随标的切换加载一次，与图表周期无关 */
watch(() => [kind.value, code.value], loadHeader)
onMounted(load)
onMounted(loadHeader)

/** 扩展信息（资金流向/公告/分红）只在切换标时加载一次，与周期切换无关 */
async function loadExtra(): Promise<void> {
  try {
    extra.value = await getExtra(kind.value, code.value)
  } catch {
    extra.value = { moneyflow: [], announcements: [], dividends: [] }
  }
}
watch(() => [kind.value, code.value], loadExtra)
onMounted(loadExtra)

/** F10 信息（财务指标/十大股东/融资融券/公司概况）与扩展信息一样只在切换标的时加载一次 */
async function loadF10(): Promise<void> {
  try {
    f10.value = await getF10(kind.value, code.value)
  } catch {
    f10.value = { financials: null, holders: null, margin: null, profile: null, sectors: null }
  }
}
watch(() => [kind.value, code.value], loadF10)
onMounted(loadF10)

/** 股东人数趋势（附较上期变化%，从新到旧） */
const holderTrendRows = computed(() => {
  const trend = f10.value?.holders?.trend ?? []
  return trend.slice(0, 8).map((t, i) => {
    const prev = trend[i + 1]?.total
    const delta = prev && prev > 0 ? ((t.total - prev) / prev) * 100 : null
    return { date: t.date, total: t.total, delta }
  })
})

/* ---------- 三大财务报表（利润表/资产负债表/现金流量表，仅 A 股） ---------- */
const statements = ref<StatementsData | null>(null)
type StmtKey = 'income' | 'balance' | 'cashflow'
const STMT_TABS: { key: StmtKey; label: string }[] = [
  { key: 'income', label: '利润表' },
  { key: 'balance', label: '资产负债表' },
  { key: 'cashflow', label: '现金流量表' },
]
const stmtTab = ref<StmtKey>('income')

async function loadStatements(): Promise<void> {
  try {
    statements.value = await getStatements(kind.value, code.value)
    // 当前选中的报表若没有数据，自动切到第一张有数据的报表
    if (statements.value && !statements.value[stmtTab.value]) {
      const first = STMT_TABS.find((t) => statements.value?.[t.key])
      if (first) stmtTab.value = first.key
    }
  } catch {
    statements.value = null
  }
}

/** 三张报表是否至少有一张有数据；没有则整个区块不渲染 */
const hasStatements = computed(
  () => !!statements.value && STMT_TABS.some((t) => !!statements.value?.[t.key]),
)
watch(() => [kind.value, code.value], loadStatements)
onMounted(loadStatements)

/** 重点科目行强调（利润/资产/负债/权益/经营现金流合计行） */
function isKeyRow(label: string): boolean {
  return /营业总收入|净利润|资产总计|负债合计|所有者权益|经营活动产生的现金流量净额/.test(label) && !/增长率|比重|综合收益/.test(label)
}

/* ---------- 分时回看（近 5 个交易日，5 分钟粒度，仅 A 股） ---------- */
const trendHistory = ref<TrendHistoryDay[] | null>(null)
const trendDayIdx = ref(-1) // -1 = 当日实时（1 分钟），其余为 trendHistory 下标

async function loadTrendHistory(): Promise<void> {
  if (kind.value !== 'index' && kind.value !== 'stock') {
    trendHistory.value = null
    return
  }
  try {
    const data = await getTrendHistory(kind.value, code.value, 5)
    trendHistory.value = data?.days ?? null
  } catch {
    trendHistory.value = null
  }
}
watch(() => [kind.value, code.value], loadTrendHistory)
onMounted(loadTrendHistory)

/** 日期切换按钮：实时 + 历史日（最后一日为今日 5 分钟数据，与实时重复，不显示） */
const trendPills = computed(() => {
  const hs = trendHistory.value
  if (!hs || hs.length < 2) return []
  const pills: { idx: number; label: string }[] = [{ idx: -1, label: '实时' }]
  for (let i = 0; i < hs.length - 1; i++) pills.push({ idx: i, label: hs[i].date.slice(5) })
  return pills
})

/** 当前分时显示的数据源：实时（1 分钟）或历史交易日（5 分钟） */
const activeTrend = computed(() => {
  const hs = trendHistory.value
  if (trendDayIdx.value >= 0 && hs && trendDayIdx.value < hs.length - 1) {
    const d = hs[trendDayIdx.value]
    return { points: d.points, prev: d.prev_close, hint: `${d.date} · 5 分钟粒度` }
  }
  const t = trendData.value
  if (!t) return null
  return { points: t.points, prev: t.prev_close, hint: '当日 1 分钟走势' }
})

/* ---------- F10 辅助 ---------- */
/** 金额固定两位小数「亿」（融资余额/财务金额） */
function yi(v: number | null): string {
  if (v === null || v === undefined) return '—'
  return `${(v / 1e8).toFixed(2)}亿`
}
/** 股数转「万股」 */
function wan(v: number): string {
  return `${(v / 1e4).toLocaleString('zh-CN', { maximumFractionDigits: 0 })}万股`
}
/** 财务金额按量级取整显示 */
function finVal(v: number | null): string {
  if (v === null || v === undefined) return '—'
  const abs = Math.abs(v)
  if (abs >= 1e8) return yi(v)
  if (abs >= 1) return v.toFixed(2)
  return v.toFixed(4)
}

/* ---------- 资金流向：双向条形图 ---------- */
const latestFlow = computed(() => extra.value?.moneyflow?.[0] ?? null)
const maxAbsNet = computed(() => {
  const rows = extra.value?.moneyflow ?? []
  return Math.max(...rows.map((r) => Math.abs(r.net)), 1)
})
/** 双向条形图定位：轨道中线为 0，流入向右（红）、流出向左（绿） */
function netBarStyle(net: number): Record<string, string> {
  const half = (Math.abs(net) / maxAbsNet.value) * 50
  return net >= 0
    ? { left: '50%', width: `${half}%` }
    : { right: '50%', width: `${half}%` }
}
/** 连续同向天数：从最新日起净流入（或流出）连续的天数 */
const flowStreak = computed(() => {
  const rows = extra.value?.moneyflow ?? []
  if (!rows.length) return 0
  const inflow = rows[0].net >= 0
  let n = 0
  for (const r of rows) {
    if ((r.net >= 0) === inflow) n++
    else break
  }
  return n
})
/** 区间累计（返回顺序与表格列一致：主力/超大/大/中/小） */
const flowSum = computed(() => {
  const rows = extra.value?.moneyflow ?? []
  const s = { net: 0, xl: 0, l: 0, m: 0, s: 0 }
  for (const r of rows) {
    s.net += r.net
    s.xl += r.xl_net
    s.l += r.l_net
    s.m += r.m_net
    s.s += r.s_net
  }
  return s
})
/** 区间日均净流入率（tfoot 用） */
const flowSumAvg = computed(() => {
  const rows = extra.value?.moneyflow ?? []
  if (!rows.length) return '—'
  const avg = rows.reduce((acc, r) => acc + r.ratio, 0) / rows.length
  return `日均 ${avg >= 0 ? '+' : ''}${avg.toFixed(2)}%`
})

/* ---------- 头部行情：实时数据优先，其次分时合成，缺失回退最后一根K线 ---------- */

/** 分时数据合成的行情兜底：默认周期是分时时 detail 接口不返回 quote */
const trendQuote = computed<Record<string, number | string> | null>(() => {
  const t = trendData.value
  if (!t || !t.points.length || t.prev_close === null) return null
  const prices = t.points.map((p) => p.price)
  const last = prices[prices.length - 1]!
  const prev = t.prev_close
  return {
    '名称': t.name,
    '最新价': last,
    '开盘': prices[0]!,
    '涨跌额': last - prev,
    '涨跌幅': prev !== 0 ? ((last - prev) / prev) * 100 : 0,
    '最高': Math.max(...prices),
    '最低': Math.min(...prices),
    '成交量': t.points.reduce((acc, p) => acc + p.volume, 0),
  }
})

/** 头部行情取数：detail 实时快照 → 分时合成 */
const headQuote = computed(() => quote.value ?? trendQuote.value)

const displayName = computed(() => headQuote.value?.['名称'] || code.value)
const price = computed(() => (headQuote.value?.['最新价'] as number | undefined) ?? null)
const changeAmt = computed(() => (headQuote.value?.['涨跌额'] as number | undefined) ?? null)
const changePct = computed(() => (headQuote.value?.['涨跌幅'] as number | undefined) ?? null)
const suffix = computed(() => {
  if (kind.value === 'index' || kind.value === 'stock') {
    return `.${symbol.value.slice(0, 2).toUpperCase()}`
  }
  if (kind.value === 'hk' && /^\d+$/.test(code.value)) return '.HK'
  return ''
})

/** 带符号涨跌额 */
function signed(v: number | null | undefined): string {
  if (v === null || v === undefined) return '—'
  return `${v > 0 ? '+' : ''}${f2(v)}`
}

/** 成交量自适应：亿 / 万 / 原值 */
function vol(v: number | null | undefined): string {
  if (!v) return '—'
  if (v >= 1e8) return `${(v / 1e8).toFixed(2)}亿`
  if (v >= 1e4) return `${(v / 1e4).toFixed(1)}万`
  return String(v)
}

/* ---------- 关键统计 ---------- */
/** 周期 → 明细表备注 */
const PERIOD_NOTE: Record<string, string> = { '240': '日线', week: '周线', month: '月线' }

const stats = computed(() => {
  // 头部统计永远取今日实时快照（或分时合成），与底部图表周期无关
  const q = headQuote.value
  return [
    { label: '今开', text: f2(q?.['开盘']) },
    { label: '最高', text: f2(q?.['最高']) },
    { label: '最低', text: f2(q?.['最低']) },
    { label: '成交量', text: vol(q?.['成交量']) },
    { label: '成交额', text: amt(q?.['成交额']) },
  ]
})

/* ---------- K线图（SVG 蜡烛 + 均线，作用于当前视窗切片） ---------- */
/* 坐标系宽度取 360 ≈ 移动端容器实际像素宽，SVG 文字与线条按 1:1 呈现，不再被 viewBox 缩小 */
const W = 360
const H = 230
const PAD = { top: 12, right: 48, bottom: 22, left: 8 }
const INNER_W = W - PAD.left - PAD.right
const INNER_H = H - PAD.top - PAD.bottom

/** 常用均线定义（浅色主题配色：石板灰/琥珀/紫/天蓝） */
const MA_DEFS = [
  { n: 5, key: 'ma5', label: 'MA5', color: '#94a3b8' },
  { n: 10, key: 'ma10', label: 'MA10', color: '#eab308' },
  { n: 20, key: 'ma20', label: 'MA20', color: '#7c3aed' },
  { n: 60, key: 'ma60', label: 'MA60', color: '#0ea5e9' },
]

/** 全序列均线（含视窗外），图表与单日详情共用 */
const masAll = computed(() => {
  const closes = kline.value.map((p) => p['收盘'])
  return MA_DEFS.map((d) => ({ ...d, vals: maSeriesLocal(closes, d.n) }))
})

/** 收盘价简单移动平均；前 n-1 根为 null */
function maSeriesLocal(closes: number[], n: number): (number | null)[] {
  const out: (number | null)[] = []
  let sum = 0
  for (let i = 0; i < closes.length; i++) {
    sum += closes[i]
    if (i >= n) sum -= closes[i - n]
    out.push(i >= n - 1 ? sum / n : null)
  }
  return out
}

/** 单根K线的几何信息 */
interface Candle {
  key: string
  cx: number
  bodyX: number
  bodyW: number
  bodyY: number
  bodyH: number
  wickY1: number
  wickY2: number
  up: boolean
}

const chart = computed(() => {
  const pts = view.value
  if (pts.length < 2) return null
  const n = pts.length
  const full = kline.value
  // 视窗在完整序列中的起点，保证均线在窗口首端也有值
  const startIdx = Math.max(0, full.length - endOffset.value - n)
  const mas = masAll.value

  let min = Math.min(...pts.map((p) => p['最低']))
  let max = Math.max(...pts.map((p) => p['最高']))
  // 均线值纳入纵轴范围，避免均线越界
  for (const m of mas) {
    for (let i = 0; i < n; i++) {
      const v = m.vals[startIdx + i]
      if (v !== null) {
        if (v < min) min = v
        if (v > max) max = v
      }
    }
  }
  if (min === max) {
    min *= 0.995
    max *= 1.005
  }
  const pad = (max - min) * 0.04
  min -= pad
  max += pad
  const y = (v: number) => PAD.top + (1 - (v - min) / (max - min)) * INNER_H
  const step = INNER_W / n
  const bodyW = Math.max(1.5, Math.min(14, step * 0.65))
  const candles: Candle[] = pts.map((p, i) => {
    const cx = PAD.left + step * (i + 0.5)
    const yo = y(p['开盘'])
    const yc = y(p['收盘'])
    return {
      key: p['日期'],
      cx,
      bodyX: cx - bodyW / 2,
      bodyW,
      bodyY: Math.min(yo, yc),
      bodyH: Math.max(1, Math.abs(yc - yo)),
      wickY1: y(p['最高']),
      wickY2: y(p['最低']),
      up: p['收盘'] >= p['开盘'],
    }
  })
  // 均线折线：null 处断开分段
  const maLines = mas.map((m) => {
    let d = ''
    let open = false
    for (let i = 0; i < n; i++) {
      const v = m.vals[startIdx + i]
      if (v === null) {
        open = false
        continue
      }
      d += `${open ? 'L' : 'M'}${(PAD.left + step * (i + 0.5)).toFixed(1)},${y(v).toFixed(1)}`
      open = true
    }
    const lastVal = m.vals[startIdx + n - 1]
    return { key: m.key, label: m.label, color: m.color, d, legend: lastVal === null ? '—' : f2(lastVal) }
  })
  const grid = [0, 1, 2, 3].map((t) => {
    const v = min + ((max - min) * t) / 3
    return { y: y(v), label: f2(v) }
  })
  const last = pts[n - 1]
  // 横轴刻度按标签实测宽度配置（10px 字号下数字≈7px/字符，标签 + 间距必须 < 刻度间距）：
  // 分钟K "10-29 14:00"≈71px → 3 个刻度（间距 152px）；周K "24-01-31"≈46px → 4 个（101px）；
  // 月K "22-09"≈30px → 5 个（76px）；日K "10-18"/"26-05"≈30px → 6 个（61px），跨年处缩为 YY-MM
  const sc = scale.value
  const isMinute = sc === '60' || sc === '30' || sc === '15' || sc === '5'
  const tickN = isMinute ? 3 : sc === 'week' ? 4 : sc === 'month' ? 5 : 6
  let prevYear = ''
  const xTicks = Array.from({ length: tickN }, (_, t) => {
    const i = Math.round((t / (tickN - 1)) * (n - 1))
    const day = pts[i]['日期']
    let label: string
    if (isMinute) {
      label = day.slice(5, 16) // "2026-10-29 15:00" → "10-29 15:00"
    } else if (sc === 'week') {
      label = day.slice(2, 10) // "2024-01-31" → "24-01-31"（自带年份）
    } else if (sc === 'month') {
      label = day.slice(2, 7) // "2022-09-06" → "22-09"
    } else {
      const year = day.slice(0, 4)
      const showYear = t === 0 || year !== prevYear
      prevYear = year
      label = showYear ? day.slice(2, 7) : day.slice(5, 10) // "26-05" / "10-18"
    }
    return {
      x: PAD.left + step * (i + 0.5),
      label,
      anchor: t === 0 ? 'start' : t === tickN - 1 ? 'end' : 'middle',
    }
  })
  return {
    candles,
    maLines,
    grid,
    lastY: y(last['收盘']),
    lastLabel: f2(last['收盘']),
    lastUp: last['收盘'] >= last['开盘'],
    xTicks,
  }
})

/* ---------- 技术指标副图（MACD/KDJ/RSI/BIAS 多选叠加，随主图视窗联动） ---------- */
type SubKey = 'macd' | 'kdj' | 'rsi' | 'bias'
const SUB_TABS: { key: SubKey; label: string }[] = [
  { key: 'macd', label: 'MACD' },
  { key: 'kdj', label: 'KDJ' },
  { key: 'rsi', label: 'RSI' },
  { key: 'bias', label: 'BIAS' },
]
/** 多选：点亮的指标各自渲染一个副图，纵向堆叠 */
const subIndicators = ref<SubKey[]>(['macd'])
function toggleSub(key: SubKey) {
  const arr = subIndicators.value
  const i = arr.indexOf(key)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(key)
}
const SUB_H = 110
const SUB_PAD = { top: 8, right: 48, bottom: 6, left: 8 }
const SUB_INNER_H = SUB_H - SUB_PAD.top - SUB_PAD.bottom

interface SubLine {
  key: string
  label: string
  color: string
  vals: (number | null)[]
}

/** 由 OHLC 序列构造四个指标的线/柱定义（K线与分时副图共用） */
function indicatorDefs(
  closes: number[],
  highs: number[],
  lows: number[],
): Record<SubKey, { lines: SubLine[]; bars: (number | null)[] | null; fixed: boolean }> {
  const m = macdCalc(closes)
  const k3 = kdjCalc(highs, lows, closes)
  return {
    macd: {
      lines: [
        { key: 'dif', label: 'DIF', color: '#94a3b8', vals: m.dif },
        { key: 'dea', label: 'DEA', color: '#eab308', vals: m.dea },
      ],
      bars: m.hist,
      fixed: false,
    },
    kdj: {
      lines: [
        { key: 'k', label: 'K', color: '#94a3b8', vals: k3.k },
        { key: 'd', label: 'D', color: '#eab308', vals: k3.d },
        { key: 'j', label: 'J', color: '#7c3aed', vals: k3.j },
      ],
      bars: null,
      fixed: true,
    },
    rsi: {
      lines: [
        { key: 'r6', label: 'RSI6', color: '#94a3b8', vals: rsiCalc(closes, 6) },
        { key: 'r12', label: 'RSI12', color: '#eab308', vals: rsiCalc(closes, 12) },
        { key: 'r24', label: 'RSI24', color: '#0ea5e9', vals: rsiCalc(closes, 24) },
      ],
      bars: null,
      fixed: true,
    },
    bias: {
      lines: [
        { key: 'b6', label: 'BIAS6', color: '#94a3b8', vals: biasCalc(closes, 6) },
        { key: 'b12', label: 'BIAS12', color: '#eab308', vals: biasCalc(closes, 12) },
        { key: 'b24', label: 'BIAS24', color: '#0ea5e9', vals: biasCalc(closes, 24) },
      ],
      bars: null,
      fixed: false,
    },
  }
}

/** 全量技术指标序列（基于完整K线计算，切片显示保证窗口首端有值） */
const subSeries = computed(() => {
  const closes = kline.value.map((p) => p['收盘'])
  const highs = kline.value.map((p) => p['最高'])
  const lows = kline.value.map((p) => p['最低'])
  return indicatorDefs(closes, highs, lows)
})

/** 单个副图几何计算（提取自原 subChart，供多副图复用） */
function buildSub(def: { lines: SubLine[]; bars: (number | null)[] | null; fixed: boolean }) {
  const pts = view.value
  const n = pts.length
  const full = kline.value
  const startIdx = Math.max(0, full.length - endOffset.value - n)

  // 纵轴范围：固定域(KDJ/RSI 0-100，J 超界自适应) 或 窗口自适应(MACD/BIAS 含零轴)
  let min = 0
  let max = def.fixed ? 100 : 0
  for (const l of def.lines) {
    for (let i = 0; i < n; i++) {
      const v = l.vals[startIdx + i]
      if (v === null) continue
      if (v < min) min = v
      if (v > max) max = v
    }
  }
  if (def.bars) {
    for (let i = 0; i < n; i++) {
      const v = def.bars[startIdx + i]
      if (v === null) continue
      if (v < min) min = v
      if (v > max) max = v
    }
  }
  if (min === max) {
    min -= 1
    max += 1
  }
  const pad = (max - min) * 0.08
  min -= pad
  max += pad
  const y = (v: number) => SUB_PAD.top + (1 - (v - min) / (max - min)) * SUB_INNER_H
  const step = INNER_W / n
  const zeroY = y(0)

  // 指标折线（null 断段），与主图 x 完全对齐
  const lineDs = def.lines.map((l) => {
    let d = ''
    let open = false
    for (let i = 0; i < n; i++) {
      const v = l.vals[startIdx + i]
      if (v === null) {
        open = false
        continue
      }
      d += `${open ? 'L' : 'M'}${(PAD.left + step * (i + 0.5)).toFixed(1)},${y(v).toFixed(1)}`
      open = true
    }
    return { key: l.key, color: l.color, d }
  })

  // MACD 柱
  const barW = Math.max(1.5, Math.min(14, step * 0.65))
  const bars: { key: string; x: number; y: number; h: number; up: boolean }[] = []
  if (def.bars) {
    for (let i = 0; i < n; i++) {
      const v = def.bars[startIdx + i]
      if (v === null) continue
      const cy = PAD.left + step * (i + 0.5)
      const vy = y(v)
      bars.push({
        key: pts[i]['日期'],
        x: cy - barW / 2,
        y: Math.min(vy, zeroY),
        h: Math.max(1, Math.abs(vy - zeroY)),
        up: v >= 0,
      })
    }
  }

  // 网格：固定域 20/50/80，自适应域 0/中值/最大值三档
  const grid = def.fixed
    ? [20, 50, 80].map((v) => ({ y: y(v), label: String(v) }))
    : [0, (min + max) / 2, max].map((v) => ({ y: y(v), label: Math.abs(v) >= 1 ? v.toFixed(1) : v.toFixed(2) }))

  // 图例取选中K线（未选中取最新），与主图十字线联动
  const idx = selIndex.value >= 0 && selIndex.value < n ? selIndex.value : n - 1
  const legend = def.lines.map((l) => ({
    label: l.label,
    color: l.color,
    value: l.vals[startIdx + idx] === null || l.vals[startIdx + idx] === undefined ? '—' : f2(l.vals[startIdx + idx] as number),
  }))
  if (def.bars) {
    const hv = def.bars[startIdx + idx]
    legend.unshift({ label: 'MACD', color: hv === null || hv < 0 ? 'var(--color-success)' : 'var(--color-danger)', value: hv === null || hv === undefined ? '—' : f2(hv) })
  }
  return { lineDs, bars, grid, zeroY, legend, hasBars: !!def.bars, barW }
}

/** 多副图：按 SUB_TABS 顺序渲染所有点亮的指标 */
const subCharts = computed(() => {
  if (!chart.value) return []
  return SUB_TABS.filter((t) => subIndicators.value.includes(t.key)).map((t) => ({
    key: t.key,
    label: t.label,
    ...buildSub(subSeries.value[t.key]),
  }))
})

/* ---------- 分时折线图（价格线 + 均价线 + 昨收基准，涨跌空间以昨收对称） ---------- */
const TREND_PAD = { top: 12, right: 48, bottom: 22, left: 40 }

const trendChart = computed(() => {
  const av = activeTrend.value
  if (!av || av.points.length < 2) return null
  const pts = av.points
  const prev = av.prev
  let min = Infinity
  let max = -Infinity
  for (const p of pts) {
    if (p.price < min) min = p.price
    if (p.price > max) max = p.price
    if (p.avg !== null) {
      if (p.avg < min) min = p.avg
      if (p.avg > max) max = p.avg
    }
  }
  if (prev !== null) {
    if (prev < min) min = prev
    if (prev > max) max = prev
  }
  if (!isFinite(min) || min > max) return null
  if (min === max) {
    min *= 0.995
    max *= 1.005
  }
  if (prev !== null) {
    // 以昨收为中心对称展开，保留涨跌空间的观感惯例
    const dev = Math.max(max - prev, prev - min)
    if (dev > 0) {
      max = prev + dev
      min = prev - dev
    }
  }
  const pad = (max - min) * 0.06
  min -= pad
  max += pad

  const top = TREND_PAD.top
  const innerW = W - TREND_PAD.left - PAD.right
  const innerH = H - top - PAD.bottom
  const y = (v: number) => top + (1 - (v - min) / (max - min)) * innerH
  const n = pts.length
  const step = innerW / n
  const x = (i: number) => TREND_PAD.left + step * (i + 0.5)

  let priceD = ''
  let avgD = ''
  let avgOpen = false
  let lastAvg: number | null = null
  pts.forEach((p, i) => {
    priceD += `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.price).toFixed(1)}`
    if (p.avg !== null) {
      avgD += `${avgOpen ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.avg).toFixed(1)}`
      avgOpen = true
      lastAvg = p.avg
    } else {
      avgOpen = false
    }
  })
  const last = pts[n - 1]
  const up = prev !== null ? last.price >= prev : true
  const baseY = (top + innerH).toFixed(1)
  const areaD = `${priceD}L${x(n - 1).toFixed(1)},${baseY}L${x(0).toFixed(1)},${baseY}Z`

  const grid = [0, 1, 2, 3].map((k) => {
    const v = min + ((max - min) * k) / 3
    const pctV = prev ? (v / prev - 1) * 100 : null
    return {
      y: y(v),
      label: f2(v),
      pct: pctV === null ? '' : `${pctV > 0 ? '+' : ''}${pctV.toFixed(1)}%`,
      side: pctV === null || pctV === 0 ? '' : pctV > 0 ? 'up' : 'down',
    }
  })
  // 5 个刻度（首尾 start/end 对齐，中间居中），避免 9 个在 360 宽坐标系里挤成一团
  const xTicks = Array.from({ length: 5 }, (_, k) => {
    const i = Math.round((k / 4) * (n - 1))
    return {
      x: x(i),
      label: pts[i].time,
      anchor: k === 0 ? 'start' : k === 4 ? 'end' : 'middle',
    }
  })
  return {
    priceD,
    avgD,
    areaD,
    grid,
    xTicks,
    prevY: prev !== null ? y(prev) : null,
    prevLabel: prev === null ? '—' : f2(prev),
    lastLabel: f2(last.price),
    avgLabel: lastAvg === null ? '—' : f2(lastAvg),
    lineColor: up ? 'var(--color-danger)' : 'var(--color-success)',
    prev, // 供单点涨跌幅计算
    xOf: x,
    yOf: y,
    step,
  }
})

/* ---------- 分时点击选中：显示该分钟详情 + 十字定位 ---------- */
/** 选中按「点的索引」而非 time 字符串：期货夜盘跨日后同一分钟可能重复出现 */
const selIdx = ref<number | null>(null)

const trendSel = computed(() => {
  const t = activeTrend.value
  const tc = trendChart.value
  if (!t || !tc || selIdx.value === null || selIdx.value >= t.points.length) return null
  const p = t.points[selIdx.value]
  const chg = tc.prev ? (p.price / tc.prev - 1) * 100 : null
  return {
    p,
    chg,
    cx: tc.xOf(selIdx.value),
    cy: tc.yOf(p.price),
  }
})

/* ---------- 分时副图：MACD/KDJ/RSI/BIAS（与K线副图共用开关，基于当日分钟价格序列计算） ---------- */
/** 分时副图几何：x 与分时主图完全对齐（左 44 右 64），全量点渲染，无视窗切片 */
function buildTrendSub(def: { lines: SubLine[]; bars: (number | null)[] | null; fixed: boolean }) {
  const pts = activeTrend.value!.points
  const n = pts.length

  // 纵轴范围：固定域(KDJ/RSI 0-100) 或 窗口自适应(MACD/BIAS 含零轴)
  let min = 0
  let max = def.fixed ? 100 : 0
  for (const l of def.lines) {
    for (const v of l.vals) {
      if (v === null) continue
      if (v < min) min = v
      if (v > max) max = v
    }
  }
  if (def.bars) {
    for (const v of def.bars) {
      if (v === null) continue
      if (v < min) min = v
      if (v > max) max = v
    }
  }
  if (min === max) {
    min -= 1
    max += 1
  }
  const pad = (max - min) * 0.08
  min -= pad
  max += pad
  const y = (v: number) => SUB_PAD.top + (1 - (v - min) / (max - min)) * SUB_INNER_H
  const step = (W - TREND_PAD.left - PAD.right) / n
  const x = (i: number) => TREND_PAD.left + step * (i + 0.5)
  const zeroY = y(0)

  // 指标折线（null 断段），与分时主图 x 完全对齐
  const lineDs = def.lines.map((l) => {
    let d = ''
    let open = false
    for (let i = 0; i < n; i++) {
      const v = l.vals[i]
      if (v === null) {
        open = false
        continue
      }
      d += `${open ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`
      open = true
    }
    return { key: l.key, color: l.color, d }
  })

  // MACD 柱
  const barW = Math.max(1, Math.min(14, step * 0.65))
  const bars: { key: string; x: number; y: number; h: number; up: boolean }[] = []
  if (def.bars) {
    for (let i = 0; i < n; i++) {
      const v = def.bars[i]
      if (v === null) continue
      const vy = y(v)
      bars.push({
        key: pts[i].time,
        x: x(i) - barW / 2,
        y: Math.min(vy, zeroY),
        h: Math.max(1, Math.abs(vy - zeroY)),
        up: v >= 0,
      })
    }
  }

  // 网格：固定域 20/50/80，自适应域 0/中值/最大值三档
  const grid = def.fixed
    ? [20, 50, 80].map((v) => ({ y: y(v), label: String(v) }))
    : [0, (min + max) / 2, max].map((v) => ({ y: y(v), label: Math.abs(v) >= 1 ? v.toFixed(1) : v.toFixed(2) }))

  // 图例取选中分钟（未选中取最新），与分时十字线联动
  const sel = selIdx.value !== null && selIdx.value < n ? selIdx.value : -1
  const idx = sel >= 0 ? sel : n - 1
  const legend = def.lines.map((l) => ({
    label: l.label,
    color: l.color,
    value: l.vals[idx] === null || l.vals[idx] === undefined ? '—' : f2(l.vals[idx] as number),
  }))
  if (def.bars) {
    const hv = def.bars[idx]
    legend.unshift({ label: 'MACD', color: hv === null || hv < 0 ? 'var(--color-success)' : 'var(--color-danger)', value: hv === null || hv === undefined ? '—' : f2(hv) })
  }
  return { lineDs, bars, grid, zeroY, legend, hasBars: !!def.bars, barW }
}

/** 分时副图：按 SUB_TABS 顺序渲染所有点亮的指标 */
const trendSubCharts = computed(() => {
  const av = activeTrend.value
  if (scale.value !== 'trend' || !av || av.points.length < 2) return []
  const pts = av.points
  const defs = indicatorDefs(pts.map((p) => p.price), pts.map((p) => p.price), pts.map((p) => p.price))
  return SUB_TABS.filter((t) => subIndicators.value.includes(t.key)).map((t) => ({
    key: t.key,
    label: t.label,
    ...buildTrendSub(defs[t.key]),
  }))
})

function onTrendSelect(e: MouseEvent): void {
  const t = activeTrend.value
  if (!t || t.points.length < 2) return
  const svg = e.currentTarget as SVGSVGElement
  const rect = svg.getBoundingClientRect()
  const xSvg = ((e.clientX - rect.left) * W) / rect.width
  const step = (W - TREND_PAD.left - PAD.right) / t.points.length
  const i = Math.floor((xSvg - TREND_PAD.left) / step)
  if (i < 0 || i >= t.points.length) {
    selIdx.value = null
    return
  }
  // 再点同一点取消选中
  selIdx.value = selIdx.value === i ? null : i
}

/* ---------- 交互：滚轮滚动历史、拖拽平移、Ctrl+滚轮缩放、双击复位 ---------- */
function clampOffset(v: number): number {
  const len = kline.value.length
  const count = Math.min(viewCount.value || len, len)
  return Math.min(Math.max(0, v), Math.max(0, len - count))
}

function onWheel(e: WheelEvent): void {
  const len = kline.value.length
  if (!len) return
  // 悬停在K线图上时滚轮只作用于图表，不滚动页面
  e.preventDefault()
  if (e.ctrlKey || e.metaKey) {
    // 缩放：放大/缩小可见根数，右端对齐不变
    const factor = e.deltaY > 0 ? 1.15 : 1 / 1.15
    const count = Math.round((viewCount.value || len) * factor)
    viewCount.value = Math.min(len, Math.max(20, count))
    endOffset.value = clampOffset(endOffset.value)
    return
  }
  // 滚动：向下滚看更早，向上滚回到最新
  const step = e.deltaY > 0 ? 4 : -4
  endOffset.value = clampOffset(endOffset.value + step)
  maybeLoadMore()
}

// wheel 需要非 passive 监听才能按需 preventDefault；元素随 v-if 出现，用 watch 挂载
watch(chartEl, (el, _prev, onCleanup) => {
  if (!el) return
  el.addEventListener('wheel', onWheel, { passive: false })
  onCleanup(() => el.removeEventListener('wheel', onWheel))
})

let dragX = 0
let dragOffset = 0
let dragMoved = false

/* 双指捏合缩放（触屏）：双指按下进入缩放，距离拉大=放大（看更少根数） */
const activePointers = new Map<number, number>()
let pinching = false
let pinchBaseDist = 0
let pinchBaseCount = 0

function pinchDistance(): number {
  const xs = [...activePointers.values()]
  return Math.abs(xs[0] - xs[1])
}

function onPointerDown(e: PointerEvent): void {
  activePointers.set(e.pointerId, e.clientX)
  if (activePointers.size === 2 && kline.value.length >= 2) {
    // 第二根手指落下：终止拖拽，以当前距离/根数为基准进入捏合缩放
    pinching = true
    dragging.value = false
    pinchBaseDist = pinchDistance()
    pinchBaseCount = viewCount.value || kline.value.length
    return
  }
  if (activePointers.size > 1 || kline.value.length < 2) return
  dragX = e.clientX
  dragOffset = endOffset.value
  dragMoved = false
  dragging.value = true
  ;(e.currentTarget as Element).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent): void {
  if (activePointers.has(e.pointerId)) activePointers.set(e.pointerId, e.clientX)
  if (pinching && activePointers.size >= 2) {
    const dist = pinchDistance()
    if (dist > 10 && pinchBaseDist > 0) {
      const len = kline.value.length
      const count = Math.round(pinchBaseCount * (pinchBaseDist / dist))
      viewCount.value = Math.min(len, Math.max(20, count))
      endOffset.value = clampOffset(endOffset.value)
    }
    return
  }
  if (!dragging.value || !chartEl.value) return
  if (Math.abs(e.clientX - dragX) > 4) dragMoved = true
  const rect = chartEl.value.getBoundingClientRect()
  const perCandle = (rect.width * INNER_W) / W / viewCount.value
  const shift = Math.round((e.clientX - dragX) / perCandle)
  endOffset.value = clampOffset(dragOffset + shift)
  maybeLoadMore()
}

function onPointerUp(e: PointerEvent): void {
  activePointers.delete(e.pointerId)
  if (activePointers.size < 2) pinching = false
  dragging.value = false
  ;(e.currentTarget as Element).releasePointerCapture?.(e.pointerId)
}

/* ---------- 五档盘口 / 估值资料 ---------- */
/** 卖档自上而下：卖5 → 卖1 */
const asksDesc = computed(() => (depth.value ? [...depth.value.asks].reverse() : []))

function formatVal(k: string, v: number): string {
  if (k === '总市值' || k === '流通市值') return `${f2(v)}亿`
  if (k === '换手率' || k === '振幅') return `${f2(v)}%`
  return f2(v)
}

/* 明细表：最近 12 根，最新在前 */
const tableRows = computed(() => kline.value.slice(-12).reverse())

/** 同行业个股跳详情 */
function openSibling(code2: string): void {
  void router.push(detailRoute('stock', code2))
}
</script>

<template>
  <div class="detail-page">
    <p v-if="error" class="error-box" role="alert">
      详情加载失败：{{ error }}
      <button class="retry" type="button" @click="load">重试</button>
    </p>

    <!-- 骨架屏 -->
    <div v-else-if="loading" class="detail-skel" aria-busy="true">
      <div class="skel-head"></div>
      <div class="skel-chart"></div>
    </div>

    <template v-else>
      <!-- 头部：公共返回栏 + 标的身份，下方实时报价 -->
      <header class="detail-head">
        <PageHeader
          :title="displayName"
          back-to="/finance/global-market/global"
          back-label="返回行情中心"
        >
          <template #right>
            <span class="kind-badge">{{ KIND_LABEL[kind] }}</span>
            <span class="num code">{{ code }}{{ suffix }}</span>
          </template>
        </PageHeader>
        <div class="head-quote">
          <span class="num price" :class="dirClass(changePct)">{{ f2(price) }}</span>
          <span class="num change" :class="dirClass(changePct)">{{ pct(changePct) }}</span>
          <span class="num change" :class="dirClass(changeAmt)">{{ signed(changeAmt) }}</span>
        </div>
      </header>

      <!-- 关键统计 -->
      <dl class="stats">
        <div v-for="s in stats" :key="s.label" class="stat">
          <dt>{{ s.label }}</dt>
          <dd class="num">{{ s.text }}</dd>
        </div>
      </dl>

      <!-- K线图（蜡烛图：影线=最高/最低，实体=开盘/收盘，涨红跌绿） -->
      <section class="chart-card" aria-label="K线走势">
        <div class="chart-head">
          <button
            v-if="endOffset > 0"
            class="reset-btn"
            type="button"
            @click="endOffset = 0"
          >
            回到最新
          </button>
          <!-- 周期切换：分时 / 日K / 分钟K（分钟K仅 A 股） -->
          <div class="periods" role="group" aria-label="K线周期">
            <button
              v-for="p in PERIODS"
              :key="p.value"
              class="period-btn"
              :class="{ active: scale === p.value }"
              :aria-pressed="scale === p.value"
              type="button"
              @click="scale = p.value"
            >
              {{ p.label }}
            </button>
          </div>
        </div>

        <!-- 分时：折线图（价格线/均价线/昨收基准） -->
        <template v-if="scale === 'trend'">
          <!-- 分时回看：日期切换（实时 + 近 5 个交易日） -->
          <div v-if="trendPills.length" class="trend-days" role="group" aria-label="分时日期">
            <button
              v-for="p in trendPills"
              :key="p.idx"
              class="sub-tab"
              :class="{ active: trendDayIdx === p.idx }"
              type="button"
              @click="trendDayIdx = p.idx"
            >
              {{ p.label }}
            </button>
          </div>
          <div v-if="trendChart" class="chart-box">
            <!-- 单点详情面板：点击分时后显示 -->
            <div v-if="trendSel" class="sel-panel">
              <div class="sel-head">
                <span class="num">{{ trendSel.p.time }}</span>
                <button class="sel-close" type="button" aria-label="关闭分时详情" @click.stop="selIdx = null">×</button>
              </div>
              <div class="sel-grid">
                <span class="lbl">价格</span>
                <span class="num" :class="dirClass(trendSel.chg)">{{ f2(trendSel.p.price) }}</span>
                <span class="lbl">涨幅</span>
                <span class="num" :class="dirClass(trendSel.chg)">{{ pct(trendSel.chg) }}</span>
                <span class="lbl">均价</span>
                <span class="num">{{ trendSel.p.avg === null ? '—' : f2(trendSel.p.avg) }}</span>
                <span class="lbl">成交量</span>
                <span class="num">{{ vol(trendSel.p.volume) }}</span>
              </div>
            </div>
            <div class="ma-legend trend-legend" aria-hidden="true">
              <span :style="{ color: trendChart.lineColor }">分时 {{ trendChart.lastLabel }}</span>
              <span style="color: #d97706">均价 {{ trendChart.avgLabel }}</span>
              <span style="color: var(--color-text-muted)">昨收 {{ trendChart.prevLabel }}</span>
            </div>
            <svg class="trend-svg" :viewBox="`0 0 ${W} ${H}`" role="img" aria-label="分时走势图，点击查看某一分钟的详情" @click="onTrendSelect">
              <g class="gridlines">
                <line v-for="g in trendChart.grid" :key="'l' + g.y" :x1="TREND_PAD.left" :x2="W - PAD.right" :y1="g.y" :y2="g.y" />
                <text v-for="g in trendChart.grid" :key="'p' + g.y" class="grid-label num" :x="W - 4" :y="g.y + 4" text-anchor="end">
                  {{ g.label }}
                </text>
                <template v-for="g in trendChart.grid" :key="'c' + g.y">
                  <text v-if="g.pct" class="grid-label num pct-label" :class="g.side" :x="TREND_PAD.left - 6" :y="g.y + 4">
                    {{ g.pct }}
                  </text>
                </template>
              </g>
              <line v-if="trendChart.prevY !== null" class="prev-line" :x1="TREND_PAD.left" :x2="W - PAD.right" :y1="trendChart.prevY" :y2="trendChart.prevY" />
              <path class="area" :d="trendChart.areaD" :fill="trendChart.lineColor" />
              <path v-if="trendChart.avgD" class="avg-path" :d="trendChart.avgD" />
              <path class="price-path" :d="trendChart.priceD" :stroke="trendChart.lineColor" />
              <!-- 选中分钟十字定位线 -->
              <g v-if="trendSel" class="crosshair">
                <line :x1="trendSel.cx" :x2="trendSel.cx" :y1="TREND_PAD.top" :y2="H - PAD.bottom" />
                <line :x1="TREND_PAD.left" :x2="W - PAD.right" :y1="trendSel.cy" :y2="trendSel.cy" />
                <circle class="sel-dot" :cx="trendSel.cx" :cy="trendSel.cy" r="3" />
              </g>
              <g class="x-axis">
                <text v-for="(t, i) in trendChart.xTicks" :key="i" class="date-label" :x="t.x" :y="H - 6" :text-anchor="t.anchor">
                  {{ t.label }}
                </text>
              </g>
            </svg>
            <!-- 分时副图开关：与K线副图共用同一组多选状态 -->
            <div class="sub-head">
              <div class="sub-tabs" role="group" aria-label="分时技术指标多选">
                <button
                  v-for="t in SUB_TABS"
                  :key="t.key"
                  class="sub-tab"
                  :class="{ active: subIndicators.includes(t.key) }"
                  type="button"
                  @click="toggleSub(t.key)"
                >
                  {{ t.label }}
                </button>
              </div>
              <span class="sub-tip">可多选叠加</span>
            </div>
            <!-- 分时副图：与K线副图共用指标开关，基于当日分钟价格序列计算 -->
            <template v-for="sc in trendSubCharts" :key="'t-' + sc.key">
              <div class="sub-head sub-head-chart">
                <span class="sub-name">{{ sc.label }}</span>
                <div class="sub-legend num" aria-hidden="true">
                  <span v-for="l in sc.legend" :key="l.label" :style="{ color: l.color }">
                    {{ l.label }} {{ l.value }}
                  </span>
                </div>
              </div>
              <svg class="sub-svg" :viewBox="`0 0 ${W} ${SUB_H}`" role="img" :aria-label="sc.label + '分时副图'">
                <g class="gridlines">
                  <line v-for="g in sc.grid" :key="'tg' + g.y" :x1="TREND_PAD.left" :x2="W - PAD.right" :y1="g.y" :y2="g.y" />
                  <text v-for="g in sc.grid" :key="'ts' + g.y" class="grid-label num" :x="W - 4" :y="g.y + 4" text-anchor="end">
                    {{ g.label }}
                  </text>
                </g>
                <!-- 零轴（MACD/BIAS） -->
                <line v-if="!sc.grid.some((g) => Math.abs(g.y - sc.zeroY) < 0.5)" class="zero-line" :x1="TREND_PAD.left" :x2="W - PAD.right" :y1="sc.zeroY" :y2="sc.zeroY" />
                <g v-if="sc.hasBars" class="sub-bars">
                  <rect
                    v-for="b in sc.bars"
                    :key="b.key"
                    :class="b.up ? 'up' : 'down'"
                    :x="b.x"
                    :y="b.y"
                    :width="sc.barW"
                    :height="b.h"
                  />
                </g>
                <g class="sub-lines">
                  <path
                    v-for="l in sc.lineDs"
                    :key="l.key"
                    :d="l.d"
                    :stroke="l.color"
                    fill="none"
                    stroke-width="1"
                    stroke-linejoin="round"
                  />
                </g>
                <!-- 选中分钟联动十字线 -->
                <g v-if="trendSel" class="crosshair">
                  <line :x1="trendSel.cx" :x2="trendSel.cx" :y1="SUB_PAD.top" :y2="SUB_H - SUB_PAD.bottom" />
                </g>
              </svg>
            </template>
          </div>
          <p v-else class="empty">{{ trendEmptyHint }}</p>
        </template>
        <!-- K线：蜡烛图（影线=最高/最低，实体=开盘/收盘，涨红跌绿） -->
        <template v-else>
          <div v-if="chart" class="chart-box">
          <!-- 单日详情面板：点击K线后替换均线图例 -->
          <div v-if="selInfo" class="sel-panel">
            <div class="sel-head">
              <span class="num">{{ selInfo.p['日期'] }}</span>
              <button class="sel-close" type="button" aria-label="关闭单日详情" @click.stop="selDate = null">×</button>
            </div>
            <div class="sel-grid">
              <span class="lbl">开</span>
              <span class="num" :class="selInfo.up ? 'up' : 'down'">{{ f2(selInfo.p['开盘']) }}</span>
              <span class="lbl">高</span>
              <span class="num" :class="selInfo.up ? 'up' : 'down'">{{ f2(selInfo.p['最高']) }}</span>
              <span class="lbl">低</span>
              <span class="num" :class="selInfo.up ? 'up' : 'down'">{{ f2(selInfo.p['最低']) }}</span>
              <span class="lbl">收</span>
              <span class="num" :class="selInfo.up ? 'up' : 'down'">{{ f2(selInfo.p['收盘']) }}</span>
              <span class="lbl">涨跌幅</span>
              <span class="num" :class="dirClass(selInfo.p['涨跌幅'])">{{ pct(selInfo.p['涨跌幅']) }}</span>
              <span class="lbl">成交量</span>
              <span class="num">{{ vol(selInfo.p['成交量']) }}</span>
            </div>
            <div class="sel-mas">
              <span v-for="m in selInfo.mas" :key="m.label" :style="{ color: m.color }">
                {{ m.label }} {{ m.v === null ? '—' : f2(m.v) }}
              </span>
            </div>
          </div>
          <!-- 均线图例：显示视窗最后一根的均线值 -->
          <div v-else class="ma-legend" aria-hidden="true">
            <span v-for="l in chart.maLines" :key="l.key" :style="{ color: l.color }">
              {{ l.label }} {{ l.legend }}
            </span>
          </div>
          <!-- 翻页状态 -->
          <div class="page-status" aria-live="polite">
            <template v-if="loadingMore">加载更早数据…</template>
            <template v-else-if="exhausted">已到最早数据</template>
          </div>
          <svg
            ref="chartEl"
            class="kline-svg"
            :class="{ grabbing: dragging }"
            :viewBox="`0 0 ${W} ${H}`"
            role="img"
            aria-label="K线图，可用滚轮滚动查看历史日期，拖拽平移，Ctrl加滚轮缩放"
            @pointerdown="onPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
            @pointercancel="onPointerUp"
            @click="onSelect"
            @dblclick="endOffset = 0"
          >
            <g class="gridlines">
              <line v-for="g in chart.grid" :key="g.y" :x1="PAD.left" :x2="W - PAD.right" :y1="g.y" :y2="g.y" />
              <text v-for="g in chart.grid" :key="'t' + g.y" class="grid-label num" :x="W - 4" :y="g.y + 4" text-anchor="end">
                {{ g.label }}
              </text>
            </g>
            <!-- 最新价虚线 -->
            <g class="last-line" :class="chart.lastUp ? 'up' : 'down'">
              <line :x1="PAD.left" :x2="W - PAD.right" :y1="chart.lastY" :y2="chart.lastY" />
              <text class="num" :x="W - 4" :y="chart.lastY + 4" text-anchor="end">{{ chart.lastLabel }}</text>
            </g>
            <g class="candles">
              <template v-for="c in chart.candles" :key="c.key">
                <line class="wick" :class="c.up ? 'up' : 'down'" :x1="c.cx" :x2="c.cx" :y1="c.wickY1" :y2="c.wickY2" />
                <rect class="body" :class="c.up ? 'up' : 'down'" :x="c.bodyX" :y="c.bodyY" :width="c.bodyW" :height="c.bodyH" />
              </template>
            </g>
            <!-- 均线（MA5/MA10/MA20/MA60） -->
            <g class="ma-lines">
              <path
                v-for="l in chart.maLines"
                :key="l.key"
                :d="l.d"
                :stroke="l.color"
                fill="none"
                stroke-width="1"
                stroke-linejoin="round"
              />
            </g>
            <!-- 选中K线十字定位线 -->
            <g v-if="selIndex >= 0 && chart.candles[selIndex]" class="crosshair">
              <line
                :x1="chart.candles[selIndex].cx"
                :x2="chart.candles[selIndex].cx"
                :y1="PAD.top"
                :y2="H - PAD.bottom"
              />
            </g>
            <g class="x-axis">
              <text
                v-for="(t, i) in chart.xTicks"
                :key="i"
                class="date-label"
                :x="t.x"
                :y="H - 6"
                :text-anchor="t.anchor"
              >
                {{ t.label }}
              </text>
            </g>
          </svg>
          <!-- 技术指标副图：多选叠加（MACD/KDJ/RSI/BIAS） -->
          <div class="sub-head">
            <div class="sub-tabs" role="group" aria-label="技术指标多选">
              <button
                v-for="t in SUB_TABS"
                :key="t.key"
                class="sub-tab"
                :class="{ active: subIndicators.includes(t.key) }"
                type="button"
                @click="toggleSub(t.key)"
              >
                {{ t.label }}
              </button>
            </div>
            <span class="sub-tip">可多选叠加</span>
          </div>
          <template v-for="sc in subCharts" :key="sc.key">
            <div class="sub-head sub-head-chart">
              <span class="sub-name">{{ sc.label }}</span>
              <div class="sub-legend num" aria-hidden="true">
                <span v-for="l in sc.legend" :key="l.label" :style="{ color: l.color }">
                  {{ l.label }} {{ l.value }}
                </span>
              </div>
            </div>
            <svg class="sub-svg" :viewBox="`0 0 ${W} ${SUB_H}`" role="img" :aria-label="sc.label + '副图'">
              <g class="gridlines">
                <line v-for="g in sc.grid" :key="'sg' + g.y" :x1="SUB_PAD.left" :x2="W - SUB_PAD.right" :y1="g.y" :y2="g.y" />
                <text v-for="g in sc.grid" :key="'s' + g.y" class="grid-label num" :x="W - 4" :y="g.y + 4" text-anchor="end">
                  {{ g.label }}
                </text>
              </g>
              <!-- 零轴（MACD/BIAS） -->
              <line v-if="!sc.grid.some((g) => Math.abs(g.y - sc.zeroY) < 0.5)" class="zero-line" :x1="SUB_PAD.left" :x2="W - SUB_PAD.right" :y1="sc.zeroY" :y2="sc.zeroY" />
              <g v-if="sc.hasBars" class="sub-bars">
                <rect
                  v-for="b in sc.bars"
                  :key="b.key"
                  :class="b.up ? 'up' : 'down'"
                  :x="b.x"
                  :y="b.y"
                  :width="sc.barW"
                  :height="b.h"
                />
              </g>
              <g class="sub-lines">
                <path
                  v-for="l in sc.lineDs"
                  :key="l.key"
                  :d="l.d"
                  :stroke="l.color"
                  fill="none"
                  stroke-width="1"
                  stroke-linejoin="round"
                />
              </g>
              <!-- 选中K线联动十字线 -->
              <g v-if="selIndex >= 0 && chart.candles[selIndex]" class="crosshair">
                <line
                  :x1="chart.candles[selIndex].cx"
                  :x2="chart.candles[selIndex].cx"
                  :y1="SUB_PAD.top"
                  :y2="SUB_H - SUB_PAD.bottom"
                />
              </g>
            </svg>
          </template>
          </div>
          <p v-else class="empty">暂无K线数据</p>
        </template>
      </section>

      <!-- 五档盘口 + 估值资料（A股盘口 / A股港股估值） -->
      <section v-if="depth || valuation" class="extra-row">
        <div v-if="depth" class="panel">
          <div class="spot-head">
            <h2>五档盘口</h2>
            <span class="spot-note num">{{ depth.date }} {{ depth.time }}</span>
          </div>
          <table class="depth-table">
            <tbody>
              <tr v-for="(a, i) in asksDesc" :key="'a' + i">
                <td class="side muted">卖{{ asksDesc.length - i }}</td>
                <td class="num ask-price">{{ f2(a[0]) }}</td>
                <td class="num muted">{{ vol(a[1]) }}</td>
              </tr>
              <tr class="now-row">
                <td class="side muted">最新</td>
                <td class="num" :class="dirClass(changePct)">{{ f2(price) }}</td>
                <td class="num muted" :class="dirClass(changePct)">{{ pct(changePct) }}</td>
              </tr>
              <tr v-for="(b, i) in depth.bids" :key="'b' + i">
                <td class="side muted">买{{ i + 1 }}</td>
                <td class="num bid-price">{{ f2(b[0]) }}</td>
                <td class="num muted">{{ vol(b[1]) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="valuation" class="panel">
          <div class="spot-head">
            <h2>估值资料</h2>
            <span class="spot-note">腾讯行情</span>
          </div>
          <dl class="val-grid">
            <div v-for="(v, k) in valuation" :key="k" class="val-item">
              <dt>{{ k }}</dt>
              <dd class="num">{{ formatVal(k, v) }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <!-- 资金流向（A股）：最新日英雄数字 + 双向条形图 -->
      <section v-if="extra?.moneyflow?.length" class="kline-section">
        <div class="spot-head">
          <h2>资金流向</h2>
          <span class="spot-note">近 {{ extra.moneyflow.length }} 日 · 主力 = 超大单 + 大单 · 新浪</span>
        </div>
        <div v-if="latestFlow" class="flow-hero">
          <div class="flow-hero-main">
            <span class="flow-hero-label num">{{ latestFlow.date }} 主力净流入</span>
            <span class="num flow-hero-value" :class="dirClass(latestFlow.net)">{{ signedYi(latestFlow.net) }}</span>
            <span v-if="flowStreak > 1" class="flow-streak num" :class="dirClass(latestFlow.net)">
              {{ latestFlow.net >= 0 ? '连续流入' : '连续流出' }} {{ flowStreak }} 日
            </span>
          </div>
          <div class="flow-hero-sub num">
            <span>净流入率 <b :class="dirClass(latestFlow.net)">{{ latestFlow.ratio.toFixed(2) }}%</b></span>
            <span>超大单 <b :class="dirClass(latestFlow.xl_net)">{{ signedYi(latestFlow.xl_net) }}</b></span>
            <span>大单 <b :class="dirClass(latestFlow.l_net)">{{ signedYi(latestFlow.l_net) }}</b></span>
            <span>中单 <b :class="dirClass(latestFlow.m_net)">{{ signedYi(latestFlow.m_net) }}</b></span>
            <span>小单 <b :class="dirClass(latestFlow.s_net)">{{ signedYi(latestFlow.s_net) }}</b></span>
          </div>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>日期</th>
                <th class="num">收盘价</th>
                <th class="num">涨跌幅</th>
                <th class="num">换手率</th>
                <th class="num">净流入率</th>
                <th class="num">主力净流入</th>
                <th class="flow-bar-head">净流入分布</th>
                <th class="num">超大单</th>
                <th class="num">大单</th>
                <th class="num">中单</th>
                <th class="num">小单</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in extra.moneyflow" :key="r.date">
                <td class="num">{{ r.date }}</td>
                <td class="num">{{ f2(r.close) }}</td>
                <td class="num" :class="dirClass(r.pct)">{{ pct(r.pct) }}</td>
                <td class="num muted">{{ r.turnover.toFixed(2) }}%</td>
                <td class="num muted">{{ r.ratio.toFixed(2) }}%</td>
                <td class="num flow-strong" :class="dirClass(r.net)">{{ signedYi(r.net) }}</td>
                <td class="flow-bar-cell">
                  <div class="flow-bar-track" aria-hidden="true">
                    <div class="flow-bar" :class="r.net >= 0 ? 'in' : 'out'" :style="netBarStyle(r.net)"></div>
                  </div>
                </td>
                <td class="num" :class="dirClass(r.xl_net)">{{ signedYi(r.xl_net) }}</td>
                <td class="num" :class="dirClass(r.l_net)">{{ signedYi(r.l_net) }}</td>
                <td class="num" :class="dirClass(r.m_net)">{{ signedYi(r.m_net) }}</td>
                <td class="num" :class="dirClass(r.s_net)">{{ signedYi(r.s_net) }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td class="flow-foot-label">累计</td>
                <td colspan="3" class="muted">{{ extra.moneyflow.length }} 个交易日</td>
                <td class="num muted">{{ flowSumAvg }}</td>
                <td class="num flow-strong" :class="dirClass(flowSum.net)">{{ signedYi(flowSum.net) }}</td>
                <td></td>
                <td class="num" :class="dirClass(flowSum.xl)">{{ signedYi(flowSum.xl) }}</td>
                <td class="num" :class="dirClass(flowSum.l)">{{ signedYi(flowSum.l) }}</td>
                <td class="num" :class="dirClass(flowSum.m)">{{ signedYi(flowSum.m) }}</td>
                <td class="num" :class="dirClass(flowSum.s)">{{ signedYi(flowSum.s) }}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </section>

      <!-- 公司公告 + 分红送配（A股）：时间轴 + 票根式方案 -->
      <section
        v-if="extra && (extra.announcements.length || extra.dividends.length)"
        class="extra-row"
      >
        <div v-if="extra.announcements.length" class="panel">
          <div class="spot-head">
            <h2>公司公告</h2>
            <span class="spot-note">最新 {{ extra.announcements.length }} 条 · 新浪</span>
          </div>
          <ol class="news-list">
            <li v-for="(a, i) in extra.announcements" :key="a.url" :class="{ lead: i === 0 }">
              <span class="news-date num">{{ a.date }}</span>
              <a :href="a.url" target="_blank" rel="noopener">
                <span class="news-title">{{ a.title }}</span>
                <span class="news-arrow" aria-hidden="true">↗</span>
              </a>
            </li>
          </ol>
        </div>
        <div v-if="extra.dividends.length" class="panel">
          <div class="spot-head">
            <h2>分红送配</h2>
            <span class="spot-note">每10股口径 · 新浪</span>
          </div>
          <table class="div-table">
            <thead>
              <tr>
                <th>除权除息日</th>
                <th>分红方案</th>
                <th>进度</th>
                <th class="num">公告日</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in extra.dividends" :key="d.announce + (d.ex_date ?? '')">
                <td class="num">{{ d.ex_date ?? '—' }}</td>
                <td>
                  <span v-if="d.dividend" class="div-amt num">派 {{ parseFloat(d.dividend.toFixed(2)) }}</span>
                  <span v-if="d.bonus" class="div-tag">送 {{ d.bonus }}</span>
                  <span v-if="d.convert" class="div-tag">转 {{ d.convert }}</span>
                  <span v-if="!d.dividend && !d.bonus && !d.convert" class="muted">—</span>
                </td>
                <td class="muted">{{ d.progress }}</td>
                <td class="num muted">{{ d.announce }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 公司概况（A股）：资料字段 + 简介/主营业务 -->
      <section v-if="f10?.profile" class="kline-section">
        <div class="spot-head">
          <h2>公司概况</h2>
          <span class="spot-note">新浪公司资料</span>
        </div>
        <div class="profile-grid">
          <div v-for="(v, k) in f10.profile.fields" :key="k" class="profile-item">
            <span class="profile-label">{{ k }}</span>
            <span class="profile-value" :class="{ num: /\d/.test(v) }">{{ v }}</span>
          </div>
        </div>
        <div v-if="f10.profile.intro" class="profile-block">
          <h3>公司简介</h3>
          <p>{{ f10.profile.intro }}</p>
        </div>
        <div v-if="f10.profile.mainbiz" class="profile-block">
          <h3>主营业务</h3>
          <p>{{ f10.profile.mainbiz }}</p>
        </div>
        <!-- 所属板块：申万行业 + 概念 -->
        <div v-if="f10.sectors" class="profile-block">
          <h3>所属板块</h3>
          <div class="sector-chips">
            <span v-if="f10.sectors.industry" class="sector-chip industry">{{ f10.sectors.industry }}</span>
            <span v-for="c in f10.sectors.concepts" :key="c" class="sector-chip">{{ c }}</span>
          </div>
          <div v-if="f10.sectors.siblings.length" class="sibling-row">
            <span class="sibling-label">同行业个股</span>
            <div class="sibling-links">
              <button
                v-for="s in f10.sectors.siblings"
                :key="s.code"
                class="sibling-link"
                type="button"
                @click="openSibling(s.code)"
              >{{ s.name }}</button>
            </div>
          </div>
        </div>
      </section>

      <!-- F10：融资融券 + 财务指标（A股） -->
      <section
        v-if="f10 && (f10.margin || f10.financials)"
        class="extra-row"
      >
        <div v-if="f10.margin" class="panel">
          <div class="spot-head">
            <h2>融资融券</h2>
            <span class="spot-note">{{ f10.margin.date }} · 交易所官方</span>
          </div>
          <div class="margin-hero">
            <div class="margin-hero-main">
              <span class="margin-hero-label">融资余额</span>
              <span class="num flow-hero-value">{{ yi(f10.margin.balance) }}</span>
            </div>
            <div v-if="f10.margin.delta !== null" class="margin-hero-sub num">
              较上日 <b :class="dirClass(f10.margin.delta)">{{ signedYi(f10.margin.delta) }}</b>
            </div>
          </div>
          <dl class="val-grid">
            <div class="val-item">
              <dt>融资买入额</dt>
              <dd class="num">{{ yi(f10.margin.buy) }}</dd>
            </div>
            <div class="val-item">
              <dt>融资偿还额</dt>
              <dd class="num">{{ f10.margin.repay === null ? '—' : yi(f10.margin.repay) }}</dd>
            </div>
            <div class="val-item">
              <dt>融券余量</dt>
              <dd class="num">{{ wan(f10.margin.short_vol) }}</dd>
            </div>
            <div class="val-item">
              <dt>融券卖出量</dt>
              <dd class="num">{{ f10.margin.short_sell === null ? '—' : wan(f10.margin.short_sell) }}</dd>
            </div>
          </dl>
        </div>

        <div v-if="f10.financials" class="panel">
          <div class="spot-head">
            <h2>财务指标</h2>
            <span class="spot-note num">{{ f10.financials.latest_date }} vs {{ f10.financials.prev_date }}</span>
          </div>
          <div class="table-wrap">
            <table class="fin-table">
              <thead>
                <tr>
                  <th>指标</th>
                  <th class="num">最新报告期</th>
                  <th class="num">上年同期</th>
                  <th class="num">同比</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in f10.financials.rows" :key="r.name">
                  <td>{{ r.name }}</td>
                  <td class="num">{{ finVal(r.latest) }}</td>
                  <td class="num muted">{{ finVal(r.prev) }}</td>
                  <td class="num" :class="dirClass(r.yoy ?? 0)">{{ r.yoy === null ? '—' : pct(r.yoy) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- 十大流通股东（A股） -->
      <section v-if="f10?.holders?.rows.length" class="kline-section">
        <div class="spot-head">
          <h2>十大流通股东</h2>
          <span class="spot-note num">
            {{ f10.holders.date }} 报告期<template v-if="f10.holders.announce"> · 公告 {{ f10.holders.announce }}</template>
            <template v-if="f10.holders.total_holders"> · 股东总数 {{ f10.holders.total_holders.toLocaleString() }} 户</template>
          </span>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>股东名称</th>
                <th class="num">持股数量</th>
                <th class="num">占总股本</th>
                <th class="num">较上期</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in f10.holders.rows" :key="h.rank">
                <td class="num muted">{{ h.rank }}</td>
                <td>{{ h.name }}</td>
                <td class="num">{{ wan(h.shares) }}</td>
                <td class="num muted">{{ h.ratio === null ? '—' : `${h.ratio}%` }}</td>
                <td class="num">
                  <span v-if="h.note === '增持'" class="holder-up">↑ 增持</span>
                  <span v-else-if="h.note === '减持'" class="holder-down">↓ 减持</span>
                  <span v-else-if="h.note === '新进'" class="holder-new">新进</span>
                  <span v-else class="muted">—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <!-- 股东人数变化：户数减少 = 筹码集中 -->
        <div v-if="holderTrendRows.length" class="holder-trend">
          <h3>股东人数变化</h3>
          <div class="table-wrap">
            <table class="ht-table">
              <thead>
                <tr>
                  <th class="num">报告期</th>
                  <th class="num">股东户数</th>
                  <th class="num">较上期</th>
                  <th>筹码</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="t in holderTrendRows" :key="t.date">
                  <td class="num">{{ t.date }}</td>
                  <td class="num">{{ t.total.toLocaleString() }}</td>
                  <td class="num" :class="t.delta === null ? 'muted' : t.delta <= 0 ? 'holder-up' : 'holder-down'">
                    <template v-if="t.delta === null">—</template>
                    <template v-else>{{ t.delta > 0 ? '+' : '' }}{{ t.delta.toFixed(1) }}%</template>
                  </td>
                  <td>
                    <span v-if="t.delta === null" class="muted">—</span>
                    <span v-else-if="t.delta <= -3" class="holder-up">筹码集中</span>
                    <span v-else-if="t.delta >= 3" class="holder-down">筹码分散</span>
                    <span v-else class="muted">基本持平</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- 三大财务报表（A股）：利润表/资产负债表/现金流量表；无数据整块不显示 -->
      <section v-if="hasStatements" class="kline-section">
        <div class="spot-head">
          <h2>财务报表</h2>
          <span class="spot-note num">单位：万元 · 最近 5 个报告期 · 新浪</span>
        </div>
        <div class="stmt-tabs" role="group" aria-label="报表选择">
          <button
            v-for="t in STMT_TABS"
            :key="t.key"
            class="sub-tab"
            :class="{ active: stmtTab === t.key }"
            type="button"
            @click="stmtTab = t.key"
          >
            {{ t.label }}
          </button>
        </div>
        <div v-if="statements[stmtTab]" class="table-wrap">
          <table class="stmt-table">
            <thead>
              <tr>
                <th class="stmt-label-col">科目</th>
                <th v-for="p in statements[stmtTab]!.periods" :key="p" class="num">{{ p }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in statements[stmtTab]!.rows" :key="r.label" :class="{ 'stmt-key': isKeyRow(r.label) }">
                <td>{{ r.label }}</td>
                <td v-for="(v, i) in r.vals" :key="i" class="num">
                  {{ v === null ? '—' : v.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 行情明细表 -->
      <section class="kline-section" aria-labelledby="kline-title">
        <div class="spot-head">
          <h2 id="kline-title">行情明细</h2>
          <span class="spot-note">
            最近 {{ tableRows.length }} 根 · {{ PERIOD_NOTE[klineScale] ?? `${klineScale}分钟线` }}
          </span>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>时间</th>
                <th class="num">开盘</th>
                <th class="num">最高</th>
                <th class="num">最低</th>
                <th class="num">收盘</th>
                <th class="num">涨跌幅</th>
                <th class="num">成交量</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in tableRows" :key="p['日期']">
                <td class="num muted">{{ p['日期'] }}</td>
                <td class="num">{{ f2(p['开盘']) }}</td>
                <td class="num">{{ f2(p['最高']) }}</td>
                <td class="num">{{ f2(p['最低']) }}</td>
                <td class="num">{{ f2(p['收盘']) }}</td>
                <td class="num" :class="dirClass(p['涨跌幅'])">{{ pct(p['涨跌幅']) }}</td>
                <td class="num muted">{{ vol(p['成交量']) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.detail-page {
  display: grid;
  gap: var(--space-4);
  /* 详情页是独立顶层路由，不在 .market-page 外壳内，
     必须自带继承基准：rem 适配下根字号是动态大值（~39px） */
  font-size: var(--font-size-14);

  /* 表格/盘口等宽内容交给 .table-wrap 内部滚动，不撑爆页面 */
  > * {
    min-width: 0;
  }
}

.num {
  font-variant-numeric: tabular-nums;
}

.muted {
  color: var(--color-text-muted);
}

.up {
  color: var(--color-danger);
}

.down {
  color: var(--color-success);
}

/* ---------- 头部 ---------- */
.detail-head {
  display: grid;
  gap: var(--space-8);
  padding-bottom: var(--space-8);

  /* 类型徽标 + 代码随标题靠左排列，不推到右端 */
  :deep(.page-header) {
    justify-content: flex-start;
  }
}

.kind-badge {
  flex-shrink: 0;
  padding: var(--space-4) var(--space-10);
  border: 1px solid rgba(234, 179, 8, 0.5);
  border-radius: var(--radius-8);
  background: rgba(234, 179, 8, 0.1); /* 琥珀底纹，角标质感 */
  color: #b45309;
  font-size: var(--font-size-11);
  letter-spacing: 0.14em;
}

.code {
  margin-left: var(--space-8); /* 与标题/徽标拉开间距 */
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

.head-quote {
  display: flex;
  align-items: baseline;
  gap: var(--space-12);
}

.price {
  font-size: var(--font-size-30);
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.02em;
}

.change {
  font-size: var(--font-size-16);
  font-weight: 700;
}

/* ---------- 统计带 ---------- */
.stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--space-8);
  margin: 0;
  padding: var(--space-12) 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);

  dt {
    color: var(--color-text-muted);
    font-size: var(--font-size-11);
    letter-spacing: 0.08em;
  }

  dd {
    margin: var(--space-4) 0 0;
    font-size: var(--font-size-13);
    font-weight: 600;
  }
}

/* ---------- 图表卡片 ---------- */
.chart-card {
  margin-top: var(--space-16);
  padding: var(--space-12);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  box-shadow: 0 4px 14px rgba(183, 200, 244, 0.18);
}

.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-8);
  flex-wrap: wrap;
  margin-bottom: var(--space-8);
}

.reset-btn {
  min-height: 32px;
  padding: 0 var(--space-12);
  background: transparent;
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-pill);
  color: var(--color-brand);
  font: inherit;
  font-size: var(--font-size-11);
  cursor: pointer;
  transition:
    background 0.15s ease-out,
    color 0.15s ease-out;

  &:hover {
    background: var(--color-brand);
    color: var(--color-text-inverse);
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }
}

.kline-svg {
  cursor: grab;
  /* none：触摸手势完全交给图表（单指拖拽平移 + 双指捏合缩放），不触发页面滚动/缩放 */
  touch-action: none;
  user-select: none;

  &.grabbing {
    cursor: grabbing;
  }
}

/* ---------- 技术指标副图 ---------- */
.sub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
  flex-wrap: wrap;
  margin-top: var(--space-2);
}

.sub-tabs {
  display: flex;
  gap: var(--space-2);
}

.sub-tab {
  min-height: 24px;
  padding: var(--space-2) var(--space-10);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-8);
  color: var(--color-text-muted);
  font: inherit;
  font-size: var(--font-size-11);
  letter-spacing: 0.04em;
  cursor: pointer;
  transition:
    color 0.15s ease-out,
    border-color 0.15s ease-out;

  &:hover {
    color: var(--color-text-primary);
  }

  &.active {
    border-color: var(--color-border);
    background: var(--color-bg-brand-soft);
    color: var(--color-brand);
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }
}

.sub-tip {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
  letter-spacing: 0.04em;
}

.sub-head-chart {
  margin-top: var(--space-10);
  padding-top: var(--space-8);
  border-top: 1px dashed var(--color-border);
}

.sub-name {
  color: var(--color-brand);
  font-size: var(--font-size-11);
  letter-spacing: 0.1em;
}

/* 分时回看日期切换行 / 三大报表 tab */
.trend-days,
.stmt-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding: var(--space-2) 0 var(--space-8);
}

.stmt-table th,
.stmt-table td {
  white-space: nowrap;
}

.stmt-table .stmt-label-col {
  min-width: 180px;
  text-align: left;
}

.stmt-table tr.stmt-key td {
  color: var(--color-brand);
  font-weight: 600;
}

.stmt-table tr.stmt-key td:first-child {
  border-left: 2px solid rgba(29, 78, 216, 0.35);
}

/* 公司概况 */
.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--space-8) var(--space-16);
}

.profile-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-6) 0;
  border-bottom: 1px solid var(--color-border);
}

.profile-label {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

.profile-value {
  color: var(--color-text-primary);
  font-size: var(--font-size-12);
  word-break: break-all;
}

.profile-block {
  margin-top: var(--space-12);
  padding-top: var(--space-10);
  border-top: 1px dashed var(--color-border);

  h3 {
    margin: 0 0 var(--space-6);
    color: var(--color-brand);
    font-size: var(--font-size-11);
    letter-spacing: 0.08em;
  }

  p {
    margin: 0;
    color: var(--color-text-primary);
    font-size: var(--font-size-12);
    line-height: 1.7;
  }
}

/* 板块 chips：行业琥珀实底，概念描边 */
.sector-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-6);
}

.sector-chip {
  padding: var(--space-2) var(--space-10);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  color: var(--color-text-secondary);
  font-size: var(--font-size-11);

  &.industry {
    border-color: rgba(234, 179, 8, 0.5);
    background: rgba(234, 179, 8, 0.1);
    color: #b45309;
  }
}

.sibling-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-10);
  margin-top: var(--space-10);
}

.sibling-label {
  flex-shrink: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

.sibling-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4) var(--space-12);
}

.sibling-link {
  padding: 0;
  background: transparent;
  border: none;
  border-bottom: 1px dashed transparent;
  color: var(--color-text-primary);
  font: inherit;
  font-size: var(--font-size-12);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;

  &:hover {
    color: var(--color-brand);
    border-bottom-color: var(--color-brand);
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }
}

/* 股东人数变化表 */
.holder-trend {
  margin-top: var(--space-14);
  padding-top: var(--space-10);
  border-top: 1px dashed var(--color-border);

  h3 {
    margin: 0 0 var(--space-8);
    color: var(--color-brand);
    font-size: var(--font-size-11);
    letter-spacing: 0.08em;
  }
}

.holder-up {
  color: var(--color-danger);
}

.holder-down {
  color: var(--color-success);
}

.holder-new {
  color: var(--color-chart-gold);
}

.sub-legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-12);
  font-size: var(--font-size-11);
}

.sub-svg {
  display: block;
  width: 100%;
  height: auto;
  user-select: none;

  .gridlines line {
    stroke: var(--color-border);
    stroke-width: 1;
  }

  .grid-label {
    fill: var(--color-text-muted);
    font-size: 10px;
  }
}

.zero-line {
  stroke: var(--color-text-muted);
  stroke-width: 1;
  stroke-dasharray: 3 3;
  opacity: 0.6;
}

.sub-bars rect.up {
  fill: var(--color-danger);
  opacity: 0.85;
}

.sub-bars rect.down {
  fill: var(--color-success);
  opacity: 0.85;
}

.periods {
  display: flex;
  flex-wrap: nowrap; /* 一行排开，放不下时容器内横滑 */
  gap: var(--space-2);
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.period-btn {
  min-height: 32px;
  flex: 0 0 auto;
  padding: 0 var(--space-8);
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  font: inherit;
  font-size: var(--font-size-11);
  cursor: pointer;
  transition: color 0.15s ease-out;

  &:hover {
    color: var(--color-text-primary);
  }

  /* 选中态只改文字颜色，不加底色/描边 */
  &.active {
    color: var(--color-brand);
    font-weight: 700;
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }
}

.chart-box {
  position: relative;

  svg {
    display: block;
    width: 100%;
    height: auto;
  }
}

.ma-legend {
  position: absolute;
  top: var(--space-8);
  left: var(--space-16);
  z-index: 1;
  display: flex;
  gap: var(--space-8);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  pointer-events: none;
}

.page-status {
  position: absolute;
  bottom: var(--space-32);
  left: var(--space-16);
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
  pointer-events: none;
}

/* 单日详情面板 */
.sel-panel {
  position: absolute;
  top: var(--space-8);
  left: var(--space-16);
  z-index: 2;
  min-width: 172px;
  padding: var(--space-8) var(--space-10);
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--color-border-soft-2);
  border-radius: var(--radius-8);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  font-size: var(--font-size-11);
  pointer-events: auto;
}

.sel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-10);
  margin-bottom: var(--space-6);
}

.sel-close {
  padding: 0 var(--space-4);
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  font-size: var(--font-size-13);
  line-height: 1;
  cursor: pointer;

  &:hover {
    color: var(--color-text-primary);
  }
}

.sel-grid {
  display: grid;
  grid-template-columns: auto 1fr auto 1fr;
  gap: var(--space-2) var(--space-8);
  font-size: var(--font-size-12);

  .lbl {
    color: var(--color-text-muted);
    font-size: var(--font-size-11);
  }
}

.sel-mas {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4) var(--space-10);
  margin-top: var(--space-6);
  padding-top: var(--space-6);
  border-top: 1px solid var(--color-border);
}

.crosshair line {
  stroke: var(--color-text-muted);
  stroke-dasharray: 3 3;
  stroke-width: 1;
}

.gridlines line {
  stroke: var(--color-border);
  stroke-dasharray: 3 5;
}

/* 注意：这里不能写 text-anchor，否则 CSS 会覆盖模板里的 anchor 属性（首尾 start/end、中间 middle） */
.grid-label {
  fill: var(--color-text-muted);
  font-size: 10px;
}

/* 蜡烛：涨红跌绿（A股惯例），每根独立着色 */
.candles .wick.up {
  stroke: var(--color-danger);
}

.candles .wick.down {
  stroke: var(--color-success);
}

.candles .body.up {
  fill: var(--color-danger);
}

.candles .body.down {
  fill: var(--color-success);
}

/* 最新价虚线 + 右侧价格标签 */
.last-line line {
  stroke-width: 1;
  stroke-dasharray: 4 4;
}

.last-line.up line {
  stroke: var(--color-danger);
}

.last-line.up text {
  fill: var(--color-danger);
}

.last-line.down line {
  stroke: var(--color-success);
}

.last-line.down text {
  fill: var(--color-success);
}

.last-line text {
  font-size: 10px;
  fill: var(--color-text-muted);
}

.date-label {
  fill: var(--color-text-muted);
  font-size: 10px;
}

/* 分时折线图 */
.trend-legend {
  left: 40px; /* 避开左侧百分比刻度 */
}

.trend-svg {
  display: block;
  width: 100%;
  height: auto;
  user-select: none;
  cursor: crosshair;
}

.sel-dot {
  fill: var(--color-brand);
  stroke: var(--color-bg-card);
  stroke-width: 1.5;
}

.prev-line {
  stroke: var(--color-text-muted);
  stroke-width: 1;
  stroke-dasharray: 4 4;
}

.avg-path {
  stroke: #d97706;
  stroke-width: 1;
  fill: none;
  stroke-linejoin: round;
}

.price-path {
  stroke-width: 1.4;
  fill: none;
  stroke-linejoin: round;
}

.area {
  fill-opacity: 0.08;
  stroke: none;
}

.pct-label {
  fill: var(--color-text-muted);
  text-anchor: end;

  &.up {
    fill: var(--color-danger);
  }

  &.down {
    fill: var(--color-success);
  }
}

/* ---------- 五档盘口 / 估值资料 ---------- */
.extra-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--space-12);
  margin-top: var(--space-16);
}

.panel {
  padding: var(--space-12);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  box-shadow: 0 4px 14px rgba(183, 200, 244, 0.18);
}

.spot-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-10);
  margin-bottom: var(--space-10);

  h2 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: var(--font-size-14);
    font-weight: 700;
  }
}

.spot-note {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

.depth-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-12);

  td {
    padding: var(--space-4) var(--space-10);
    border-bottom: 1px solid var(--color-border);
    white-space: nowrap;
  }

  tr:last-child td {
    border-bottom: none;
  }

  .side {
    width: 3em;
  }

  .now-row td {
    border-top: 1px solid var(--color-border-soft-2);
    border-bottom: 1px solid var(--color-border-soft-2);
    font-weight: 700;
  }
}

/* A股盘口惯例：卖档绿、买档红 */
.ask-price {
  color: var(--color-success);
}

.bid-price {
  color: var(--color-danger);
}

.val-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: var(--space-8);
  margin: 0;

  dt {
    color: var(--color-text-muted);
    font-size: var(--font-size-11);
    letter-spacing: 0.08em;
  }

  dd {
    margin: var(--space-4) 0 0;
    font-size: var(--font-size-14);
    font-weight: 600;
  }
}

/* ---------- F10：融资融券 / 财务指标 / 十大股东 ---------- */
.margin-hero {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--space-8) var(--space-16);
  padding: var(--space-2) 0 var(--space-16);
}

.margin-hero-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-10);
}

.margin-hero-label {
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

.margin-hero-sub {
  color: var(--color-text-muted);
  font-size: var(--font-size-12);

  b {
    font-weight: 600;
  }
}

.fin-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-12);

  th,
  td {
    padding: var(--space-8) var(--space-10);
    border-bottom: 1px solid var(--color-border);
    text-align: left;
    white-space: nowrap;
  }

  th:first-child,
  td:first-child {
    padding-left: 0;
  }

  tr:last-child td {
    border-bottom: none;
  }
}

/* ---------- 资金流向：英雄数字 + 双向条形图 ---------- */
.flow-hero {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--space-8) var(--space-20);
  margin-bottom: var(--space-12);
  padding: var(--space-12) var(--space-16);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  /* 琥珀金自上而下渗光，区分于普通表格面板 */
  background:
    linear-gradient(180deg, rgba(234, 179, 8, 0.08), transparent 72%),
    var(--color-bg-card);
}

.flow-hero-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-12);
}

.flow-hero-label {
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
  letter-spacing: 0.06em;
}

.flow-hero-value {
  font-size: var(--font-size-26);
  font-weight: 700;
  white-space: nowrap;
}

.flow-hero-sub {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-6) var(--space-14);
  color: var(--color-text-muted);
  font-size: var(--font-size-12);

  b {
    font-weight: 600;
    white-space: nowrap;
  }
}

/* 连续同向天数徽标 */
.flow-streak {
  padding: var(--space-2) var(--space-8);
  border: 1px solid currentColor;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-11);
  font-weight: 600;
  opacity: 0.9;
}

.flow-strong {
  font-weight: 700;
}

.flow-bar-head {
  min-width: 150px;
}

/* 累计行：双线压顶 + 淡琥珀底，与数据行区分 */
.flow-foot-label {
  font-weight: 700;
  letter-spacing: 0.1em;
}

tfoot td {
  border-top: 2px solid var(--color-border);
  border-bottom: none;
  background: rgba(234, 179, 8, 0.05);
  font-weight: 600;
}

.flow-bar-cell {
  min-width: 150px;
  padding-top: var(--space-12);
  padding-bottom: var(--space-12);
}

/* 发丝中线为 0 轴，条形自中线向两侧生长 */
.flow-bar-track {
  position: relative;
  height: 12px;

  &::before {
    content: '';
    position: absolute;
    left: 50%;
    top: -5px;
    bottom: -5px;
    width: 1px;
    background: var(--color-border);
  }
}

.flow-bar {
  position: absolute;
  top: 50%;
  height: 8px;
  border-radius: 2px;
  transform: translateY(-50%);

  &.in {
    background: linear-gradient(90deg, rgba(220, 38, 38, 0.2), var(--color-danger));
  }

  &.out {
    background: linear-gradient(270deg, rgba(22, 163, 74, 0.2), var(--color-success));
  }
}

/* ---------- 公司公告：时间轴 ---------- */
.news-list {
  position: relative;
  margin: 0;
  padding: 0 0 0 var(--space-16);
  list-style: none;

  /* 左侧规则线，串起所有节点 */
  &::before {
    content: '';
    position: absolute;
    left: 3px;
    top: var(--space-10);
    bottom: var(--space-10);
    width: 1px;
    background: var(--color-border);
  }

  li {
    position: relative;
    border-bottom: 1px solid var(--color-border);

    &:last-child {
      border-bottom: none;
    }

    /* 节点圆点：首条用强调色实心 */
    &::before {
      content: '';
      position: absolute;
      left: calc(-1 * var(--space-16));
      top: 15px;
      width: 7px;
      height: 7px;
      border: 1px solid var(--color-border);
      border-radius: 50%;
      background: var(--color-bg-card);
    }

    &.lead::before {
      border-color: var(--color-brand);
      background: var(--color-brand);
    }
  }

  a {
    display: flex;
    gap: var(--space-12);
    align-items: baseline;
    padding: var(--space-8) var(--space-2);
    color: inherit;
    text-decoration: none;
  }
}

.news-date {
  flex-shrink: 0;
  width: 76px;
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

.news-title {
  flex: 1;
  overflow: hidden;
  font-size: var(--font-size-13);
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color 0.15s ease-out;
}

/* 首条公告破格：换行全文 + 加重 */
.news-list li.lead .news-title {
  font-weight: 600;
  line-height: 1.55;
  white-space: normal;
}

.news-arrow {
  flex-shrink: 0;
  color: var(--color-brand);
  opacity: 0;
  transform: translate(-4px, 2px);
  transition:
    opacity 0.15s ease-out,
    transform 0.15s ease-out;
}

.news-list a:hover .news-title,
.news-list a:focus-visible .news-title {
  color: var(--color-brand);
}

.news-list a:hover .news-arrow,
.news-list a:focus-visible .news-arrow {
  opacity: 1;
  transform: none;
}

/* ---------- 分红送配：派息金额强调 ---------- */
.div-table th,
.div-table td {
  padding: var(--space-8) var(--space-10);
}

.div-amt {
  color: var(--color-chart-gold);
  font-weight: 700;
}

.div-tag {
  display: inline-block;
  margin-left: var(--space-6);
  padding: 1px var(--space-8);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

/* ---------- 明细表 ---------- */
.kline-section {
  margin-top: var(--space-20);
}

.table-wrap {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  background: var(--color-bg-card);
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-12);
}

th,
td {
  padding: var(--space-10) var(--space-14);
  border-bottom: 1px solid var(--color-border);
  text-align: left;
  white-space: nowrap;
}

th {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
  font-weight: 500;
  letter-spacing: 0.08em;
  border-bottom-color: var(--color-border-soft-2);
}

tbody tr:last-child td {
  border-bottom: none;
}

td.up {
  color: var(--color-danger);
}

td.down {
  color: var(--color-success);
}

/* ---------- 错误/骨架 ---------- */
.error-box {
  margin: 0;
  padding: var(--space-12) var(--space-16);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  background: var(--color-bg-card);
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

.retry {
  min-height: 36px;
  margin-left: var(--space-10);
  padding: 0 var(--space-16);
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-brand);
  font: inherit;
  font-size: var(--font-size-12);
  font-weight: 700;
  cursor: pointer;
}

.detail-skel {
  display: grid;
  gap: var(--space-12);
  padding-top: var(--space-16);
}

.skel-head {
  height: 56px;
  width: 40%;
}

.skel-chart {
  height: 220px; /* 与真实图表渲染高度（360×230 viewBox）匹配 */
}

.skel-head,
.skel-chart {
  border-radius: var(--radius-10);
  background: linear-gradient(90deg, var(--color-bg-soft) 25%, var(--color-bg-card) 50%, var(--color-bg-soft) 75%);
  background-size: 200% 100%;
  animation: skeleton-flow 1.4s ease infinite;
}

@keyframes skeleton-flow {
  from {
    background-position: 200% 0;
  }

  to {
    background-position: -200% 0;
  }
}

/* ---------- 响应式 ---------- */
@media (max-width: 640px) {
  .price {
    font-size: var(--font-size-24);
  }

  .stats {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (prefers-reduced-motion: reduce) {
  .skel-head,
  .skel-chart {
    animation: none;
  }
}
</style>
