<script setup lang="ts">
// 标的详情：实时报价 + K线蜡烛图 + 分时 + 技术指标副图 + 五档盘口/估值 + 资金流向/公告/分红/F10。
// 移植自 investment 项目 Detail 页，浅色移动端主题（涨红 --color-danger / 跌绿 --color-success / 琥珀点缀）。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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
import { amt, dirClass, f2, pct } from '@/utils/marketFormat'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getErrorMessage, isCancelRequest } from '@/api/request'
import { useRequestAbort } from '@/composables/useRequestAbort'
import DepthPanel from './detail/DepthPanel.vue'
import HoldersPanel from './detail/HoldersPanel.vue'
import MarginFinancialsPanel from './detail/MarginFinancialsPanel.vue'
import MoneyflowPanel from './detail/MoneyflowPanel.vue'
import NoticesPanel from './detail/NoticesPanel.vue'
import ProfilePanel from './detail/ProfilePanel.vue'
import StatementsPanel from './detail/StatementsPanel.vue'
import {
  buildCandleChart,
  buildSub,
  indicatorDefs,
  H,
  maSeries,
  MA_DEFS,
  PAD,
  SUB_H,
  SUB_PAD,
  SUB_TABS,
  W,
  type SubKey,
} from './detail/chartGeometry'
import { buildTrendChart, buildTrendSub, TREND_PAD } from './detail/trendGeometry'
import { signed, vol } from './detail/format'
import { useKlineInteraction } from './detail/useKlineInteraction'
import { useTrendInteraction } from './detail/useTrendInteraction'

// 请求取消：切换标的/周期时中止上一组请求，防止慢响应覆盖新数据。
// 三路通道互不干扰：图表（K线/分时，随周期切换）、头部快照、元信息（extra/F10/报表/回看）。
const { signalFor: chartSignal } = useRequestAbort()
const { signalFor: headerSignal } = useRequestAbort()
const { signalFor: metaSignal } = useRequestAbort()

const route = useRoute()

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

/** 全序列均线（含视窗外），图表与单日详情共用 */
const masAll = computed(() => {
  const closes = kline.value.map((p) => p['收盘'])
  return MA_DEFS.map((d) => ({ ...d, vals: maSeries(closes, d.n) }))
})

/** K线分页请求：绑定当前标的/周期，供向前翻页加载更早历史 */
const fetchKline = (count: number) => getKline(kind.value, code.value, scale.value, count)

/* ---------- K线图交互：视窗/翻页/选中（composable，模板绑定同名解构） ---------- */
const {
  chartEl,
  view,
  viewCount,
  endOffset,
  dragging,
  loadingMore,
  exhausted,
  selDate,
  selIndex,
  selInfo,
  onSelect,
  onPointerDown,
  onPointerMove,
  onPointerUp,
  resetSelection: resetKlineSelection,
} = useKlineInteraction({ kline, fetchKline, masAll })

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
    const data = await getDetail(kind.value, code.value, '240', 5, { signal: headerSignal() })
    quote.value = data.quote
    depth.value = data.depth
    valuation.value = data.valuation
    symbol.value = data.symbol
  } catch (e) {
    // 头部快照失败不阻塞图表；分时合成行情会兜底
    if (isCancelRequest(e)) return
  }
}

/** 图表 K 线：只取序列，不携带头部行情 */
async function loadKline(silent = false): Promise<void> {
  if (!silent) loading.value = true
  try {
    const data = await getKline(kind.value, code.value, scale.value, 250, { signal: chartSignal() })
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
    // 被新请求/卸载中止：不清 loading（新请求已接管），也不展示错误
    if (isCancelRequest(e)) return
    // 静默模式下保留旧图表，仅在没有旧数据时才展示错误
    if (!kline.value.length)
      error.value = getErrorMessage(e, '详情加载失败')
  }
  loading.value = false
}

/** 重置图表选中状态：K线单日详情与分时详情/回看日期一并清空 */
function resetSelection(): void {
  resetKlineSelection()
  resetTrendSelection()
}

async function loadTrend(silent = false): Promise<void> {
  if (!silent) loading.value = true
  resetSelection()
  try {
    trendData.value = await getTrend(kind.value, code.value, { signal: chartSignal() })
    if (trendData.value) symbol.value = trendData.value.symbol
    error.value = ''
  } catch (e) {
    // 被新请求/卸载中止：新请求已接管 loading，保持状态即可
    if (isCancelRequest(e)) return
    // 静默模式下保留旧分时图，仅在没有旧数据时才展示错误
    if (!trendData.value)
      error.value = getErrorMessage(e, '分时加载失败')
  }
  loading.value = false
}

watch(() => [kind.value, code.value], () => load())
/** 切换周期只静默重渲染图表：不触发整页骨架屏，旧图保留原地替换 */
watch(scale, () => load(true))
/** 头部实时快照：随标的切换加载一次，与图表周期无关 */
watch(() => [kind.value, code.value], loadHeader)
onMounted(load)
onMounted(loadHeader)

/** 次级资料（资金流向/公告/F10/报表/分时回看）加载失败标记：面板本体静默隐藏，统一给轻提示与重试 */
const secondaryError = ref('')

/** 扩展信息（资金流向/公告/分红）只在切换标时加载一次，与周期切换无关 */
async function loadExtra(): Promise<void> {
  try {
    extra.value = await getExtra(kind.value, code.value, { signal: metaSignal() })
  } catch (e) {
    if (isCancelRequest(e)) return
    extra.value = { moneyflow: [], announcements: [], dividends: [] }
    secondaryError.value = getErrorMessage(e, '扩展资料加载失败')
  }
}
watch(() => [kind.value, code.value], loadExtra)
onMounted(loadExtra)

/** F10 信息（财务指标/十大股东/融资融券/公司概况）与扩展信息一样只在切换标的时加载一次 */
async function loadF10(): Promise<void> {
  try {
    f10.value = await getF10(kind.value, code.value, { signal: metaSignal() })
  } catch (e) {
    if (isCancelRequest(e)) return
    f10.value = { financials: null, holders: null, margin: null, profile: null, sectors: null }
    secondaryError.value = getErrorMessage(e, 'F10 资料加载失败')
  }
}
watch(() => [kind.value, code.value], loadF10)
onMounted(loadF10)

/** 次级资料统一重试：先清提示，任一再次失败会重新置位 */
function retrySecondary(): void {
  secondaryError.value = ''
  loadExtra()
  loadF10()
  loadStatements()
  loadTrendHistory()
}

/** 股东人数趋势视图已移至 HoldersPanel（ holders.trend → 行内计算） */

/* ---------- 三大财务报表（利润表/资产负债表/现金流量表，仅 A 股） ---------- */
const statements = ref<StatementsData | null>(null)

async function loadStatements(): Promise<void> {
  try {
    statements.value = await getStatements(kind.value, code.value, 5, { signal: metaSignal() })
  } catch (e) {
    if (isCancelRequest(e)) return
    statements.value = null
    secondaryError.value = getErrorMessage(e, '财务报表加载失败')
  }
}

/** 三张报表是否至少有一张有数据；没有则整个区块不渲染 */
const hasStatements = computed(
  () => !!statements.value && ['income', 'balance', 'cashflow'].some((k) => !!statements.value?.[k as keyof StatementsData]),
)
watch(() => [kind.value, code.value], loadStatements)
onMounted(loadStatements)

/* ---------- 分时回看（近 5 个交易日，5 分钟粒度，仅 A 股） ---------- */
const trendHistory = ref<TrendHistoryDay[] | null>(null)
const trendDayIdx = ref(-1) // -1 = 当日实时（1 分钟），其余为 trendHistory 下标

async function loadTrendHistory(): Promise<void> {
  if (kind.value !== 'index' && kind.value !== 'stock') {
    trendHistory.value = null
    return
  }
  try {
    const data = await getTrendHistory(kind.value, code.value, 5, { signal: metaSignal() })
    trendHistory.value = data?.days ?? null
  } catch (e) {
    if (isCancelRequest(e)) return
    trendHistory.value = null
    secondaryError.value = getErrorMessage(e, '分时回看加载失败')
  }
}
watch(() => [kind.value, code.value], loadTrendHistory)
onMounted(loadTrendHistory)

/** 切换标的时清掉上一次的次级资料错误提示 */
watch(() => [kind.value, code.value], () => {
  secondaryError.value = ''
})

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

const displayName = computed(() => String(headQuote.value?.['名称'] || code.value))
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

/** 带符号涨跌额 / 成交量自适应已移至 detail/format（面板与主页面共用） */

/* ---------- 关键统计 ---------- */
/** 周期 → 明细表备注 */
const PERIOD_NOTE: Record<string, string> = { '240': '日线', week: '周线', month: '月线' }

const stats = computed(() => {
  // 头部统计永远取今日实时快照（或分时合成），与底部图表周期无关
  const q = headQuote.value
  return [
    { label: '今开', text: f2(q?.['开盘'] as number | null | undefined) },
    { label: '最高', text: f2(q?.['最高'] as number | null | undefined) },
    { label: '最低', text: f2(q?.['最低'] as number | null | undefined) },
    { label: '成交量', text: vol(q?.['成交量'] as number | null | undefined) },
    { label: '成交额', text: amt(q?.['成交额'] as number | null | undefined) },
  ]
})

/* ---------- K线图（SVG 蜡烛 + 均线，作用于当前视窗切片） ---------- */
/* 几何常量与蜡烛/均线计算在 detail/chartGeometry（纯函数模块） */

const chart = computed(() =>
  buildCandleChart(view.value, kline.value, endOffset.value, scale.value),
)

/* ---------- 技术指标副图（MACD/KDJ/RSI/BIAS 多选叠加，随主图视窗联动） ---------- */
/** 多选：点亮的指标各自渲染一个副图，纵向堆叠 */
const subIndicators = ref<SubKey[]>(['macd'])
function toggleSub(key: SubKey) {
  const arr = subIndicators.value
  const i = arr.indexOf(key)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(key)
}

/** 全量技术指标序列（基于完整K线计算，切片显示保证窗口首端有值） */
const subSeries = computed(() => {
  const closes = kline.value.map((p) => p['收盘'])
  const highs = kline.value.map((p) => p['最高'])
  const lows = kline.value.map((p) => p['最低'])
  return indicatorDefs(closes, highs, lows)
})

/** 多副图：按 SUB_TABS 顺序渲染所有点亮的指标 */
const subCharts = computed(() => {
  if (!chart.value) return []
  return SUB_TABS.filter((t) => subIndicators.value.includes(t.key)).map((t) => ({
    key: t.key,
    label: t.label,
    ...buildSub(subSeries.value[t.key], view.value, kline.value, endOffset.value, selIndex.value),
  }))
})

/* ---------- 分时折线图（价格线 + 均价线 + 昨收基准，涨跌空间以昨收对称） ---------- */
/* 几何计算在 detail/trendGeometry（纯函数模块） */

const trendChart = computed(() => buildTrendChart(activeTrend.value ?? { points: [], prev: null }))

/* ---------- 分时图交互：点击选中（composable，模板绑定同名解构） ---------- */
const {
  selIdx,
  trendSel,
  onTrendSelect,
  resetSelection: resetTrendSelection,
} = useTrendInteraction({ activeTrend, trendChart, trendDayIdx })

/* ---------- 分时副图：MACD/KDJ/RSI/BIAS（与K线副图共用开关，基于当日分钟价格序列计算） ---------- */
/** 分时副图：按 SUB_TABS 顺序渲染所有点亮的指标 */
const trendSubCharts = computed(() => {
  const av = activeTrend.value
  if (scale.value !== 'trend' || !av || av.points.length < 2) return []
  const pts = av.points
  const defs = indicatorDefs(pts.map((p) => p.price), pts.map((p) => p.price), pts.map((p) => p.price))
  return SUB_TABS.filter((t) => subIndicators.value.includes(t.key)).map((t) => ({
    key: t.key,
    label: t.label,
    ...buildTrendSub(defs[t.key], pts, selIdx.value ?? -1),
  }))
})

/* ---------- 五档盘口 / 估值 / 同行业跳转：已移至 detail/DepthPanel、ProfilePanel ---------- */

/* 明细表：最近 12 根，最新在前 */
const tableRows = computed(() => kline.value.slice(-12).reverse())
</script>

<template>
  <div class="detail-page">
    <p v-if="error" class="error-box" role="alert">
      详情加载失败：{{ error }}
      <button class="retry" type="button" @click="load()">重试</button>
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

      <!-- 五档盘口 / 估值 / 资金流向 / 公告分红 / 公司概况 / F10 / 股东 / 报表：面板子组件 -->
      <DepthPanel v-if="depth || valuation" :depth="depth" :valuation="valuation" :price="price" :change-pct="changePct" />
      <MoneyflowPanel v-if="extra?.moneyflow?.length" :rows="extra.moneyflow" />
      <NoticesPanel v-if="extra" :extra="extra" />
      <ProfilePanel v-if="f10?.profile" :profile="f10.profile" :sectors="f10.sectors" />
      <MarginFinancialsPanel v-if="f10" :f10="f10" />
      <HoldersPanel v-if="f10?.holders" :holders="f10.holders" />
      <StatementsPanel v-if="hasStatements" :statements="statements" />

      <!-- 次级资料（资金流向/公告/F10/报表/分时回看）加载失败：面板静默隐藏，统一给轻提示 -->
      <p v-if="secondaryError" class="error-box" role="alert">
        {{ secondaryError }}
        <button class="retry" type="button" @click="retrySecondary">重试</button>
      </p>

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
.trend-days {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding: var(--space-2) 0 var(--space-8);
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
