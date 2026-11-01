// K线图交互 composable（自 DetailPage 抽出）：
// 视窗切片 / 滚轮缩放滚动 / 拖拽平移 / 双指捏合 / 向前翻页 / 单根K线选中。
// 纯状态与手势逻辑；几何常量来自 chartGeometry，渲染仍在页面模板。
import { computed, ref, watch, type ComputedRef, type Ref } from 'vue'
import type { KlinePoint } from '@/api/modules/market'
import { INNER_W, PAD, W } from './chartGeometry'

interface Options {
  /** 完整K线序列（页面持有；向前翻页时前插更早数据） */
  kline: Ref<KlinePoint[]>
  /** K线分页请求（页面闭包绑定 kind/code/scale），返回包含更早区间的完整序列 */
  fetchKline: (count: number) => Promise<{ kline: KlinePoint[] }>
  /** 全序列均线（页面 computed），单日详情面板展示用 */
  masAll: ComputedRef<{ label: string; color: string; vals: (number | null)[] }[]>
}

export function useKlineInteraction({ kline, fetchKline, masAll }: Options) {
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
      const data = await fetchKline(kline.value.length + PAGE)
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

  /** 清除K线单日选中（加载新数据前由页面调用） */
  function resetSelection(): void {
    selDate.value = null
  }

  return {
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
    resetSelection,
  }
}
