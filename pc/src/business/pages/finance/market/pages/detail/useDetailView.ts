// 标的详情视图派生层：头部行情（实时快照 → 分时合成兜底）、关键统计、
// 分时回看视图状态（日期 pill / 当前展示数据源）、技术指标多选与全量序列、均线全序列、明细表行。
// 纯派生：不发请求，依赖 useDetailData 提供的原始状态。
import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { amt, f2 } from '@/business/utils/marketFormat'
import type {
  DetailKind,
  DetailQuote,
  KlinePoint,
  TrendData,
  TrendHistoryDay,
} from '@/business/api/modules/market'
import { indicatorDefs, maSeries, MA_DEFS, type SubKey } from './chartGeometry'
import { buildTrendChart } from './trendGeometry'
import { vol } from './format'

export type UseDetailViewOptions = {
  kind: ComputedRef<DetailKind>
  code: ComputedRef<string>
  quote: Ref<DetailQuote | null>
  kline: Ref<KlinePoint[]>
  symbol: Ref<string>
  trendData: Ref<TrendData | null>
  trendHistory: Ref<TrendHistoryDay[] | null>
}

export function useDetailView(options: UseDetailViewOptions) {
  const { kind, code, quote, kline, symbol, trendData, trendHistory } = options

  /** 全序列均线（含视窗外），图表与单日详情共用 */
  const masAll = computed(() => {
    const closes = kline.value.map((p) => p['收盘'])
    return MA_DEFS.map((d) => ({ ...d, vals: maSeries(closes, d.n) }))
  })

  /* ---------- 分时回看（近 5 个交易日，5 分钟粒度，仅 A 股） ---------- */
  const trendDayIdx = ref(-1) // -1 = 当日实时（1 分钟），其余为 trendHistory 下标

  /** 日期切换按钮：实时 + 历史日（最后一日为今日 5 分钟数据，与实时重复，不显示） */
  const trendPills = computed(() => {
    const hs = trendHistory.value
    if (!hs || hs.length < 2) return []
    const pills: { idx: number; label: string }[] = [{ idx: -1, label: '实时' }]
    for (let i = 0; i < hs.length - 1; i++)
      pills.push({ idx: i, label: hs[i].date.slice(5) })
    return pills
  })

  /** 当前分时显示的数据源：实时（1 分钟）或历史交易日（5 分钟） */
  const activeTrend = computed(() => {
    const hs = trendHistory.value
    if (trendDayIdx.value >= 0 && hs && trendDayIdx.value < hs.length - 1) {
      const d = hs[trendDayIdx.value]
      return {
        points: d.points,
        prev: d.prev_close,
        hint: `${d.date} · 5 分钟粒度`,
      }
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
      名称: t.name,
      最新价: last,
      开盘: prices[0]!,
      涨跌额: last - prev,
      涨跌幅: prev !== 0 ? ((last - prev) / prev) * 100 : 0,
      最高: Math.max(...prices),
      最低: Math.min(...prices),
      成交量: t.points.reduce((acc, p) => acc + p.volume, 0),
    }
  })

  /** 头部行情取数：detail 实时快照 → 分时合成 */
  const headQuote = computed(() => quote.value ?? trendQuote.value)

  const displayName = computed(() =>
    String(headQuote.value?.['名称'] || code.value),
  )
  const price = computed(
    () => (headQuote.value?.['最新价'] as number | undefined) ?? null,
  )
  const changeAmt = computed(
    () => (headQuote.value?.['涨跌额'] as number | undefined) ?? null,
  )
  const changePct = computed(
    () => (headQuote.value?.['涨跌幅'] as number | undefined) ?? null,
  )
  const suffix = computed(() => {
    if (kind.value === 'index' || kind.value === 'stock') {
      return `.${symbol.value.slice(0, 2).toUpperCase()}`
    }
    if (kind.value === 'hk' && /^\d+$/.test(code.value)) return '.HK'
    return ''
  })

  /* ---------- 关键统计 ---------- */
  /** 周期 → 明细表备注 */
  const PERIOD_NOTE: Record<string, string> = {
    '240': '日线',
    week: '周线',
    month: '月线',
  }

  const stats = computed(() => {
    // 头部统计永远取今日实时快照（或分时合成），与底部图表周期无关
    const q = headQuote.value
    return [
      { label: '今开', text: f2(q?.['开盘'] as number | null | undefined) },
      { label: '最高', text: f2(q?.['最高'] as number | null | undefined) },
      { label: '最低', text: f2(q?.['最低'] as number | null | undefined) },
      {
        label: '成交量',
        text: vol(q?.['成交量'] as number | null | undefined),
      },
      {
        label: '成交额',
        text: amt(q?.['成交额'] as number | null | undefined),
      },
    ]
  })

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

  /* ---------- 分时折线图（价格线 + 均价线 + 昨收基准，涨跌空间以昨收对称） ---------- */
  /* 几何计算在 detail/trendGeometry（纯函数模块） */
  const trendChart = computed(() =>
    buildTrendChart(activeTrend.value ?? { points: [], prev: null }),
  )

  /* 明细表：最近 12 根，最新在前 */
  const tableRows = computed(() => kline.value.slice(-12).reverse())

  return {
    masAll,
    trendDayIdx,
    trendPills,
    activeTrend,
    trendQuote,
    headQuote,
    displayName,
    price,
    changeAmt,
    changePct,
    suffix,
    PERIOD_NOTE,
    stats,
    subIndicators,
    toggleSub,
    subSeries,
    trendChart,
    tableRows,
  }
}
