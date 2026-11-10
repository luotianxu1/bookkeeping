// 标的详情数据层：头部快照 / 图表K线 / 分时 / 扩展资料 / F10 / 报表 / 分时回看的加载与状态。
// 移植自 investment 项目 Detail 页。
// 图表、头部快照及各个资料区域独立加载，切换标的时取消旧请求并阻止迟到响应。
import { computed, onMounted, ref, watch, type ComputedRef } from 'vue'
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
} from '@/business/api/modules/market'
import { getErrorMessage, isCancelRequest } from '@/business/api/request'
import { useRequestAbort } from '@/business/composables/useRequestAbort'
import { useAsyncSection } from '@/business/composables/useAsyncSection'

export type UseDetailDataOptions = {
  /** 分时加载前回调：清空 K线/分时两套选中状态（由页面接线到交互 composable） */
  onBeforeTrendLoad?: () => void
  /** K线加载成功回调：携带默认视窗根数（分钟K按最近N个交易日，日/周/月K 120 根） */
  onKlineLoaded?: (viewCount: number) => void
}

export function useDetailData(
  kind: ComputedRef<DetailKind>,
  code: ComputedRef<string>,
  options: UseDetailDataOptions = {},
) {
  // 注意：hooks 在调用时才读取（页面接线交互 composable 后才赋值），不能在初始化时解构
  const { signalFor: chartSignal } = useRequestAbort()
  const { signalFor: headerSignal } = useRequestAbort()

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

  /** 分时回看（近 5 个交易日，5 分钟粒度，仅 A 股） */
  const trendHistory = ref<TrendHistoryDay[] | null>(null)

  /* ---------- 三大财务报表（利润表/资产负债表/现金流量表，仅 A 股） ---------- */
  const statements = ref<StatementsData | null>(null)

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
      const hasTrend =
        kind.value === 'gold' || /^(nf_|hf_|int_|rt_hk)/.test(code.value)
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

  /** K线分页请求：绑定当前标的/周期，供向前翻页加载更早历史 */
  const fetchKline = (count: number) =>
    getKline(kind.value, code.value, scale.value, count)

  /**
   * 加载图表数据。
   * @param silent 静默模式：不触发整页骨架屏，旧图表保留原地等待新数据替换。
   *               仅在切换周期时使用；切换标的/首次进入仍走完整加载。
   */
  async function load(silent = false): Promise<void> {
    // 切换标的或初始进入时，当前周期可能在新标的不提供（如外汇无分时），回退分时/日K；
    // 修改 scale 会触发 watch(scale, load) 重新加载，此处直接返回避免重复请求
    if (!PERIODS.value.some((p) => p.value === scale.value)) {
      scale.value = PERIODS.value.some((p) => p.value === 'trend')
        ? 'trend'
        : '240'
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
      const data = await getDetail(kind.value, code.value, '240', 5, {
        signal: headerSignal(),
      })
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
      const data = await getKline(kind.value, code.value, scale.value, 250, {
        signal: chartSignal(),
      })
      kline.value = data.kline
      klineScale.value = scale.value
      // 分钟K默认展示范围：60/30分为最近一周（5 个交易日），15/5分为今天；
      // 日/周/月K默认最近 120 根。更早历史仍可拖拽平移/双指缩放查看
      let vc = Math.min(data.kline.length, 120)
      const minuteDays =
        scale.value === '60' || scale.value === '30'
          ? 5
          : scale.value === '15' || scale.value === '5'
            ? 1
            : 0
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
      options.onKlineLoaded?.(vc)
      error.value = ''
    } catch (e) {
      // 被新请求/卸载中止：不清 loading（新请求已接管），也不展示错误
      if (isCancelRequest(e)) return
      // 静默模式下保留旧图表，仅在没有旧数据时才展示错误
      if (!kline.value.length) error.value = getErrorMessage(e, '详情加载失败')
    }
    loading.value = false
  }

  async function loadTrend(silent = false): Promise<void> {
    if (!silent) loading.value = true
    options.onBeforeTrendLoad?.()
    try {
      trendData.value = await getTrend(kind.value, code.value, {
        signal: chartSignal(),
      })
      if (trendData.value) symbol.value = trendData.value.symbol
      error.value = ''
    } catch (e) {
      // 被新请求/卸载中止：新请求已接管 loading，保持状态即可
      if (isCancelRequest(e)) return
      // 静默模式下保留旧分时图，仅在没有旧数据时才展示错误
      if (!trendData.value) error.value = getErrorMessage(e, '分时加载失败')
    }
    loading.value = false
  }

  const extraSection = useAsyncSection(
    (signal) => getExtra(kind.value, code.value, { signal }),
    (value) => {
      extra.value = value
    },
    '扩展资料加载失败',
  )
  const f10Section = useAsyncSection(
    (signal) => getF10(kind.value, code.value, { signal }),
    (value) => {
      f10.value = value
    },
    'F10 资料加载失败',
  )
  const statementsSection = useAsyncSection(
    (signal) => getStatements(kind.value, code.value, 5, { signal }),
    (value) => {
      statements.value = value
    },
    '财务报表加载失败',
  )
  const historySection = useAsyncSection(
    (signal) =>
      kind.value === 'index' || kind.value === 'stock'
        ? getTrendHistory(kind.value, code.value, 5, { signal })
        : Promise.resolve(null),
    (value) => {
      trendHistory.value = value?.days ?? null
    },
    '分时回看加载失败',
  )
  const secondarySections = [
    extraSection,
    f10Section,
    statementsSection,
    historySection,
  ]
  const secondaryError = computed(
    () =>
      secondarySections.map((section) => section.error.value).find(Boolean) ??
      '',
  )
  const hasStatements = computed(
    () =>
      !!statements.value &&
      ['income', 'balance', 'cashflow'].some(
        (k) => !!statements.value?.[k as keyof StatementsData],
      ),
  )

  function retrySecondary(): void {
    secondarySections.forEach((section) => {
      void section.run()
    })
  }

  function loadSecondary(): void {
    secondarySections.forEach((section) => section.reset())
    extra.value = null
    f10.value = null
    statements.value = null
    trendHistory.value = null
    retrySecondary()
  }

  watch(
    () => [kind.value, code.value],
    () => load(),
  )
  /** 切换周期只静默重渲染图表：不触发整页骨架屏，旧图保留原地替换 */
  watch(scale, () => load(true))
  /** 头部实时快照：随标的切换加载一次，与图表周期无关 */
  watch(() => [kind.value, code.value], loadHeader)
  watch(() => [kind.value, code.value], loadSecondary)
  onMounted(load)
  onMounted(loadHeader)
  onMounted(loadSecondary)

  return {
    // 状态
    loading,
    error,
    quote,
    kline,
    symbol,
    depth,
    valuation,
    extra,
    f10,
    scale,
    klineScale,
    trendData,
    trendHistory,
    secondaryError,
    statements,
    hasStatements,
    // 派生
    PERIODS,
    trendEmptyHint,
    // 请求
    fetchKline,
    load,
    loadHeader,
    retrySecondary,
  }
}
