<script setup lang="ts">
// 标的详情：实时报价 + K线蜡烛图 + 分时 + 技术指标副图 + 五档盘口/估值 + 资金流向/公告/分红/F10。
// 移植自 investment 项目 Detail 页，浅色移动端主题（涨红 --color-danger / 跌绿 --color-success / 琥珀点缀）。
// 数据加载与状态在 useDetailData；行情派生/关键统计/分时回看视图在 useDetailView；
// 本文件只保留交互 composable 接线与依赖交互状态的图表 computed，样式在 detail/style.scss（scoped）。
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { dirClass, f2, pct } from '@/business/utils/marketFormat'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import type { DetailKind } from '@/business/api/modules/market'
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
  H,
  indicatorDefs,
  PAD,
  SUB_H,
  SUB_PAD,
  SUB_TABS,
  W,
} from './detail/chartGeometry'
import { buildTrendSub, TREND_PAD } from './detail/trendGeometry'
import { signed, vol } from './detail/format'
import { useKlineInteraction } from './detail/useKlineInteraction'
import { useTrendInteraction } from './detail/useTrendInteraction'
import { useDetailData } from './detail/useDetailData'
import { useDetailView } from './detail/useDetailView'

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

// 交互 hooks 在接线完 useKlineInteraction / useTrendInteraction 后才赋值（useDetailData 调用时懒读取）：
// - onBeforeTrendLoad：分时加载前清空 K线/分时两套选中状态（原 loadTrend 内 resetSelection 语义）
// - onKlineLoaded：K线加载成功后应用默认视窗（分钟K按最近N个交易日，日/周/月K 120 根）
const dataHooks: {
  onBeforeTrendLoad?: () => void
  onKlineLoaded?: (viewCount: number) => void
} = {}

/* ---------- 数据层：加载状态 / 快照 / K线 / 分时 / 扩展资料 / F10 / 报表 / 分时回看 ---------- */
const {
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
  PERIODS,
  trendEmptyHint,
  fetchKline,
  load,
  retrySecondary,
} = useDetailData(kind, code, dataHooks)

/* ---------- 视图派生层：头部行情 / 关键统计 / 分时回看视图 / 副图多选 / 均线全序列 ---------- */
const {
  masAll,
  trendDayIdx,
  trendPills,
  activeTrend,
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
} = useDetailView({ kind, code, quote, kline, symbol, trendData, trendHistory })

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

/* ---------- 分时图交互：点击选中（composable，模板绑定同名解构） ---------- */
const {
  selIdx,
  trendSel,
  onTrendSelect,
  resetSelection: resetTrendSelection,
} = useTrendInteraction({ activeTrend, trendChart, trendDayIdx })

dataHooks.onBeforeTrendLoad = () => {
  resetKlineSelection()
  resetTrendSelection()
}
dataHooks.onKlineLoaded = (vc) => {
  viewCount.value = vc
  endOffset.value = 0
  exhausted.value = false
  selDate.value = null
}

/* ---------- K线图（SVG 蜡烛 + 均线，作用于当前视窗切片） ---------- */
/* 几何常量与蜡烛/均线计算在 detail/chartGeometry（纯函数模块） */
const chart = computed(() =>
  buildCandleChart(view.value, kline.value, endOffset.value, scale.value),
)

/** 多副图：按 SUB_TABS 顺序渲染所有点亮的指标 */
const subCharts = computed(() => {
  if (!chart.value) return []
  return SUB_TABS.filter((t) => subIndicators.value.includes(t.key)).map(
    (t) => ({
      key: t.key,
      label: t.label,
      ...buildSub(
        subSeries.value[t.key],
        view.value,
        kline.value,
        endOffset.value,
        selIndex.value,
      ),
    }),
  )
})

/* ---------- 分时副图：MACD/KDJ/RSI/BIAS（与K线副图共用开关，基于当日分钟价格序列计算） ---------- */
/** 分时副图：按 SUB_TABS 顺序渲染所有点亮的指标 */
const trendSubCharts = computed(() => {
  const av = activeTrend.value
  if (scale.value !== 'trend' || !av || av.points.length < 2) return []
  const pts = av.points
  const defs = indicatorDefs(
    pts.map((p) => p.price),
    pts.map((p) => p.price),
    pts.map((p) => p.price),
  )
  return SUB_TABS.filter((t) => subIndicators.value.includes(t.key)).map(
    (t) => ({
      key: t.key,
      label: t.label,
      ...buildTrendSub(defs[t.key], pts, selIdx.value ?? -1),
    }),
  )
})
</script>

<template>
  <div class="pc-page detail-page">
    <p v-if="error" class="error-box" role="alert">
      详情加载失败：{{ error }}
      <el-button class="retry" native-type="button" @click="load()">
        重试
      </el-button>
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
          <span class="num price" :class="dirClass(changePct)">
            {{ f2(price) }}
          </span>
          <span class="num change" :class="dirClass(changePct)">
            {{ pct(changePct) }}
          </span>
          <span class="num change" :class="dirClass(changeAmt)">
            {{ signed(changeAmt) }}
          </span>
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
      <el-card
        class="pc-record-card chart-card"
        aria-label="K线走势"
        shadow="never"
      >
        <div class="chart-head">
          <el-button
            v-if="endOffset > 0"
            class="reset-btn"
            native-type="button"
            @click="endOffset = 0"
          >
            回到最新
          </el-button>
          <!-- 周期切换：分时 / 日K / 分钟K（分钟K仅 A 股） -->
          <div class="periods" role="group" aria-label="K线周期">
            <el-button
              v-for="p in PERIODS"
              :key="p.value"
              class="period-btn"
              :class="{ active: scale === p.value }"
              :aria-pressed="scale === p.value"
              native-type="button"
              @click="scale = p.value"
            >
              {{ p.label }}
            </el-button>
          </div>
        </div>
        <!-- 分时：折线图（价格线/均价线/昨收基准） -->
        <template v-if="scale === 'trend'">
          <!-- 分时回看：日期切换（实时 + 近 5 个交易日） -->
          <div
            v-if="trendPills.length"
            class="trend-days"
            role="group"
            aria-label="分时日期"
          >
            <el-button
              v-for="p in trendPills"
              :key="p.idx"
              class="sub-tab"
              :class="{ active: trendDayIdx === p.idx }"
              native-type="button"
              @click="trendDayIdx = p.idx"
            >
              {{ p.label }}
            </el-button>
          </div>
          <div v-if="trendChart" class="chart-box">
            <!-- 单点详情面板：点击分时后显示 -->
            <el-card
              v-if="trendSel"
              class="pc-record-card sel-panel"
              shadow="never"
            >
              <div class="sel-head">
                <span class="num">{{ trendSel.p.time }}</span>
                <el-button
                  class="sel-close"
                  native-type="button"
                  aria-label="关闭分时详情"
                  @click.stop="selIdx = null"
                >
                  ×
                </el-button>
              </div>
              <div class="pc-grid sel-grid">
                <span class="lbl">价格</span>
                <span class="num" :class="dirClass(trendSel.chg)">
                  {{ f2(trendSel.p.price) }}
                </span>
                <span class="lbl">涨幅</span>
                <span class="num" :class="dirClass(trendSel.chg)">
                  {{ pct(trendSel.chg) }}
                </span>
                <span class="lbl">均价</span>
                <span class="num">
                  {{ trendSel.p.avg === null ? '—' : f2(trendSel.p.avg) }}
                </span>
                <span class="lbl">成交量</span>
                <span class="num">{{ vol(trendSel.p.volume) }}</span>
              </div>
            </el-card>
            <div class="ma-legend trend-legend" aria-hidden="true">
              <span :style="{ color: trendChart.lineColor }">
                分时 {{ trendChart.lastLabel }}
              </span>
              <span style="color: #d97706">均价 {{ trendChart.avgLabel }}</span>
              <span style="color: var(--color-text-muted)">
                昨收 {{ trendChart.prevLabel }}
              </span>
            </div>
            <svg
              class="trend-svg"
              :viewBox="`0 0 ${W} ${H}`"
              role="img"
              aria-label="分时走势图，点击查看某一分钟的详情"
              @click="onTrendSelect"
            >
              <g class="gridlines">
                <line
                  v-for="g in trendChart.grid"
                  :key="'l' + g.y"
                  :x1="TREND_PAD.left"
                  :x2="W - PAD.right"
                  :y1="g.y"
                  :y2="g.y"
                />
                <text
                  v-for="g in trendChart.grid"
                  :key="'p' + g.y"
                  class="grid-label num"
                  :x="W - 4"
                  :y="g.y + 4"
                  text-anchor="end"
                >
                  {{ g.label }}
                </text>
                <template v-for="g in trendChart.grid" :key="'c' + g.y">
                  <text
                    v-if="g.pct"
                    class="grid-label num pct-label"
                    :class="g.side"
                    :x="TREND_PAD.left - 6"
                    :y="g.y + 4"
                  >
                    {{ g.pct }}
                  </text>
                </template>
              </g>
              <line
                v-if="trendChart.prevY !== null"
                class="prev-line"
                :x1="TREND_PAD.left"
                :x2="W - PAD.right"
                :y1="trendChart.prevY"
                :y2="trendChart.prevY"
              />
              <path
                class="area"
                :d="trendChart.areaD"
                :fill="trendChart.lineColor"
              />
              <path
                v-if="trendChart.avgD"
                class="avg-path"
                :d="trendChart.avgD"
              />
              <path
                class="price-path"
                :d="trendChart.priceD"
                :stroke="trendChart.lineColor"
              />
              <!-- 选中分钟十字定位线 -->
              <g v-if="trendSel" class="crosshair">
                <line
                  :x1="trendSel.cx"
                  :x2="trendSel.cx"
                  :y1="TREND_PAD.top"
                  :y2="H - PAD.bottom"
                />
                <line
                  :x1="TREND_PAD.left"
                  :x2="W - PAD.right"
                  :y1="trendSel.cy"
                  :y2="trendSel.cy"
                />
                <circle
                  class="sel-dot"
                  :cx="trendSel.cx"
                  :cy="trendSel.cy"
                  r="3"
                />
              </g>
              <g class="x-axis">
                <text
                  v-for="(t, i) in trendChart.xTicks"
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
            <!-- 分时副图开关：与K线副图共用同一组多选状态 -->
            <div class="sub-head">
              <div
                class="pc-toolbar sub-tabs"
                role="group"
                aria-label="分时技术指标多选"
              >
                <el-button
                  v-for="t in SUB_TABS"
                  :key="t.key"
                  class="sub-tab"
                  :class="{ active: subIndicators.includes(t.key) }"
                  native-type="button"
                  @click="toggleSub(t.key)"
                >
                  {{ t.label }}
                </el-button>
              </div>
              <span class="sub-tip">可多选叠加</span>
            </div>
            <!-- 分时副图：与K线副图共用指标开关，基于当日分钟价格序列计算 -->
            <template v-for="sc in trendSubCharts" :key="'t-' + sc.key">
              <div class="sub-head sub-head-chart">
                <span class="sub-name">{{ sc.label }}</span>
                <div class="sub-legend num" aria-hidden="true">
                  <span
                    v-for="l in sc.legend"
                    :key="l.label"
                    :style="{ color: l.color }"
                  >
                    {{ l.label }} {{ l.value }}
                  </span>
                </div>
              </div>
              <svg
                class="sub-svg"
                :viewBox="`0 0 ${W} ${SUB_H}`"
                role="img"
                :aria-label="sc.label + '分时副图'"
              >
                <g class="gridlines">
                  <line
                    v-for="g in sc.grid"
                    :key="'tg' + g.y"
                    :x1="TREND_PAD.left"
                    :x2="W - PAD.right"
                    :y1="g.y"
                    :y2="g.y"
                  />
                  <text
                    v-for="g in sc.grid"
                    :key="'ts' + g.y"
                    class="grid-label num"
                    :x="W - 4"
                    :y="g.y + 4"
                    text-anchor="end"
                  >
                    {{ g.label }}
                  </text>
                </g>
                <!-- 零轴（MACD/BIAS） -->
                <line
                  v-if="!sc.grid.some((g) => Math.abs(g.y - sc.zeroY) < 0.5)"
                  class="zero-line"
                  :x1="TREND_PAD.left"
                  :x2="W - PAD.right"
                  :y1="sc.zeroY"
                  :y2="sc.zeroY"
                />
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
                  <line
                    :x1="trendSel.cx"
                    :x2="trendSel.cx"
                    :y1="SUB_PAD.top"
                    :y2="SUB_H - SUB_PAD.bottom"
                  />
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
            <el-card
              v-if="selInfo"
              class="pc-record-card sel-panel"
              shadow="never"
            >
              <div class="sel-head">
                <span class="num">{{ selInfo.p['日期'] }}</span>
                <el-button
                  class="sel-close"
                  native-type="button"
                  aria-label="关闭单日详情"
                  @click.stop="selDate = null"
                >
                  ×
                </el-button>
              </div>
              <div class="pc-grid sel-grid">
                <span class="lbl">开</span>
                <span class="num" :class="selInfo.up ? 'up' : 'down'">
                  {{ f2(selInfo.p['开盘']) }}
                </span>
                <span class="lbl">高</span>
                <span class="num" :class="selInfo.up ? 'up' : 'down'">
                  {{ f2(selInfo.p['最高']) }}
                </span>
                <span class="lbl">低</span>
                <span class="num" :class="selInfo.up ? 'up' : 'down'">
                  {{ f2(selInfo.p['最低']) }}
                </span>
                <span class="lbl">收</span>
                <span class="num" :class="selInfo.up ? 'up' : 'down'">
                  {{ f2(selInfo.p['收盘']) }}
                </span>
                <span class="lbl">涨跌幅</span>
                <span class="num" :class="dirClass(selInfo.p['涨跌幅'])">
                  {{ pct(selInfo.p['涨跌幅']) }}
                </span>
                <span class="lbl">成交量</span>
                <span class="num">{{ vol(selInfo.p['成交量']) }}</span>
              </div>
              <div class="sel-mas">
                <span
                  v-for="m in selInfo.mas"
                  :key="m.label"
                  :style="{ color: m.color }"
                >
                  {{ m.label }} {{ m.v === null ? '—' : f2(m.v) }}
                </span>
              </div>
            </el-card>
            <!-- 均线图例：显示视窗最后一根的均线值 -->
            <div v-else class="ma-legend" aria-hidden="true">
              <span
                v-for="l in chart.maLines"
                :key="l.key"
                :style="{ color: l.color }"
              >
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
                <line
                  v-for="g in chart.grid"
                  :key="g.y"
                  :x1="PAD.left"
                  :x2="W - PAD.right"
                  :y1="g.y"
                  :y2="g.y"
                />
                <text
                  v-for="g in chart.grid"
                  :key="'t' + g.y"
                  class="grid-label num"
                  :x="W - 4"
                  :y="g.y + 4"
                  text-anchor="end"
                >
                  {{ g.label }}
                </text>
              </g>
              <!-- 最新价虚线 -->
              <g class="last-line" :class="chart.lastUp ? 'up' : 'down'">
                <line
                  :x1="PAD.left"
                  :x2="W - PAD.right"
                  :y1="chart.lastY"
                  :y2="chart.lastY"
                />
                <text
                  class="num"
                  :x="W - 4"
                  :y="chart.lastY + 4"
                  text-anchor="end"
                >
                  {{ chart.lastLabel }}
                </text>
              </g>
              <g class="candles">
                <template v-for="c in chart.candles" :key="c.key">
                  <line
                    class="wick"
                    :class="c.up ? 'up' : 'down'"
                    :x1="c.cx"
                    :x2="c.cx"
                    :y1="c.wickY1"
                    :y2="c.wickY2"
                  />
                  <rect
                    class="body"
                    :class="c.up ? 'up' : 'down'"
                    :x="c.bodyX"
                    :y="c.bodyY"
                    :width="c.bodyW"
                    :height="c.bodyH"
                  />
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
              <g
                v-if="selIndex >= 0 && chart.candles[selIndex]"
                class="crosshair"
              >
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
              <div
                class="pc-toolbar sub-tabs"
                role="group"
                aria-label="技术指标多选"
              >
                <el-button
                  v-for="t in SUB_TABS"
                  :key="t.key"
                  class="sub-tab"
                  :class="{ active: subIndicators.includes(t.key) }"
                  native-type="button"
                  @click="toggleSub(t.key)"
                >
                  {{ t.label }}
                </el-button>
              </div>
              <span class="sub-tip">可多选叠加</span>
            </div>
            <template v-for="sc in subCharts" :key="sc.key">
              <div class="sub-head sub-head-chart">
                <span class="sub-name">{{ sc.label }}</span>
                <div class="sub-legend num" aria-hidden="true">
                  <span
                    v-for="l in sc.legend"
                    :key="l.label"
                    :style="{ color: l.color }"
                  >
                    {{ l.label }} {{ l.value }}
                  </span>
                </div>
              </div>
              <svg
                class="sub-svg"
                :viewBox="`0 0 ${W} ${SUB_H}`"
                role="img"
                :aria-label="sc.label + '副图'"
              >
                <g class="gridlines">
                  <line
                    v-for="g in sc.grid"
                    :key="'sg' + g.y"
                    :x1="SUB_PAD.left"
                    :x2="W - SUB_PAD.right"
                    :y1="g.y"
                    :y2="g.y"
                  />
                  <text
                    v-for="g in sc.grid"
                    :key="'s' + g.y"
                    class="grid-label num"
                    :x="W - 4"
                    :y="g.y + 4"
                    text-anchor="end"
                  >
                    {{ g.label }}
                  </text>
                </g>
                <!-- 零轴（MACD/BIAS） -->
                <line
                  v-if="!sc.grid.some((g) => Math.abs(g.y - sc.zeroY) < 0.5)"
                  class="zero-line"
                  :x1="SUB_PAD.left"
                  :x2="W - SUB_PAD.right"
                  :y1="sc.zeroY"
                  :y2="sc.zeroY"
                />
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
                <g
                  v-if="selIndex >= 0 && chart.candles[selIndex]"
                  class="crosshair"
                >
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
      </el-card>
      <!-- 五档盘口 / 估值 / 资金流向 / 公告分红 / 公司概况 / F10 / 股东 / 报表：面板子组件 -->
      <DepthPanel
        v-if="depth || valuation"
        :depth="depth"
        :valuation="valuation"
        :price="price"
        :change-pct="changePct"
      />
      <MoneyflowPanel v-if="extra?.moneyflow?.length" :rows="extra.moneyflow" />
      <NoticesPanel v-if="extra" :extra="extra" />
      <ProfilePanel
        v-if="f10?.profile"
        :profile="f10.profile"
        :sectors="f10.sectors"
      />
      <MarginFinancialsPanel v-if="f10" :f10="f10" />
      <HoldersPanel v-if="f10?.holders" :holders="f10.holders" />
      <StatementsPanel v-if="hasStatements" :statements="statements" />
      <!-- 次级资料（资金流向/公告/F10/报表/分时回看）加载失败：面板静默隐藏，统一给轻提示 -->
      <p v-if="secondaryError" class="error-box" role="alert">
        {{ secondaryError }}
        <el-button class="retry" native-type="button" @click="retrySecondary">
          重试
        </el-button>
      </p>
      <!-- 行情明细表 -->
      <section class="kline-section" aria-labelledby="kline-title">
        <div class="spot-head">
          <h2 id="kline-title">行情明细</h2>
          <span class="spot-note">
            最近 {{ tableRows.length }} 根 ·
            {{ PERIOD_NOTE[klineScale] ?? `${klineScale}分钟线` }}
          </span>
        </div>
        <div class="table-wrap">
          <el-table :data="tableRows" stripe empty-text="暂无数据">
            <el-table-column label="时间" min-width="140">
              <template #default="{ row: p }">{{ p['日期'] }}</template>
            </el-table-column>
            <el-table-column label="开盘" min-width="120">
              <template #default="{ row: p }">{{ f2(p['开盘']) }}</template>
            </el-table-column>
            <el-table-column label="最高" min-width="120">
              <template #default="{ row: p }">{{ f2(p['最高']) }}</template>
            </el-table-column>
            <el-table-column label="最低" min-width="120">
              <template #default="{ row: p }">{{ f2(p['最低']) }}</template>
            </el-table-column>
            <el-table-column label="收盘" min-width="120">
              <template #default="{ row: p }">{{ f2(p['收盘']) }}</template>
            </el-table-column>
            <el-table-column label="涨跌幅" min-width="120">
              <template #default="{ row: p }">
                <span :class="dirClass(p['涨跌幅'])">
                  {{ pct(p['涨跌幅']) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="成交量" min-width="120">
              <template #default="{ row: p }">{{ vol(p['成交量']) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </template>
  </div>
</template>
