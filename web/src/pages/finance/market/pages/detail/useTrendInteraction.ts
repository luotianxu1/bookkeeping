// 分时图交互 composable（自 DetailPage 抽出）：点击选中某分钟 + 十字定位。
import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { PAD, W } from './chartGeometry'
import { TREND_PAD, buildTrendChart, type TrendInput } from './trendGeometry'

interface Options {
  /** 当前分时数据源（实时/历史日），页面 computed */
  activeTrend: ComputedRef<TrendInput | null>
  /** 分时主图几何（依赖 activeTrend），页面 computed */
  trendChart: ComputedRef<ReturnType<typeof buildTrendChart>>
  /** 分时回看日期下标（-1 = 实时），重置选中时一并复位 */
  trendDayIdx: Ref<number>
}

export function useTrendInteraction({ activeTrend, trendChart, trendDayIdx }: Options) {
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

  /** 清除分时选中并复位回看日期（加载新数据前由页面调用） */
  function resetSelection(): void {
    selIdx.value = null
    trendDayIdx.value = -1
  }

  return { selIdx, trendSel, onTrendSelect, resetSelection }
}
