// K线主图 + 技术指标副图的纯几何计算（自 DetailPage 抽出，无响应式依赖）。
import {
  bias as biasCalc,
  kdj as kdjCalc,
  macd as macdCalc,
  rsi as rsiCalc,
} from '@/business/utils/marketIndicators'
import { f2 } from '@/business/utils/marketFormat'
import type { KlinePoint } from '@/business/api/modules/market'

/* 坐标系宽度取 360 ≈ 移动端容器实际像素宽，SVG 文字与线条按 1:1 呈现，不再被 viewBox 缩小 */
export const W = 360
export const H = 230
export const PAD = { top: 12, right: 48, bottom: 22, left: 8 }
export const INNER_W = W - PAD.left - PAD.right
export const INNER_H = H - PAD.top - PAD.bottom

/** 常用均线定义（浅色主题配色：石板灰/琥珀/紫/天蓝） */
export const MA_DEFS = [
  { n: 5, key: 'ma5', label: 'MA5', color: '#94a3b8' },
  { n: 10, key: 'ma10', label: 'MA10', color: '#eab308' },
  { n: 20, key: 'ma20', label: 'MA20', color: '#7c3aed' },
  { n: 60, key: 'ma60', label: 'MA60', color: '#0ea5e9' },
]

/** 收盘价简单移动平均；前 n-1 根为 null */
export function maSeries(closes: number[], n: number): (number | null)[] {
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
export interface Candle {
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

export interface XTick {
  x: number
  label: string
  anchor: string
}

/** 蜡烛主图几何：作用于当前视窗切片（纯函数，自 DetailPage 的 chart computed 抽出） */
export function buildCandleChart(
  pts: KlinePoint[],
  full: KlinePoint[],
  endOffset: number,
  scale: string,
) {
  if (pts.length < 2) return null
  const n = pts.length
  // 视窗在完整序列中的起点，保证均线在窗口首端也有值
  const startIdx = Math.max(0, full.length - endOffset - n)
  const mas = MA_DEFS.map((d) => ({
    ...d,
    vals: maSeries(
      full.map((p) => p['收盘']),
      d.n,
    ),
  }))

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
    return {
      key: m.key,
      label: m.label,
      color: m.color,
      d,
      legend: lastVal === null ? '—' : f2(lastVal),
    }
  })
  const grid = [0, 1, 2, 3].map((t) => {
    const v = min + ((max - min) * t) / 3
    return { y: y(v), label: f2(v) }
  })
  const last = pts[n - 1]
  // 横轴刻度按标签实测宽度配置（10px 字号下数字≈7px/字符，标签 + 间距必须 < 刻度间距）：
  // 分钟K "10-29 14:00"≈71px → 3 个刻度（间距 152px）；周K "24-01-31"≈46px → 4 个（101px）；
  // 月K "22-09"≈30px → 5 个（76px）；日K "10-18"/"26-05"≈30px → 6 个（61px），跨年处缩为 YY-MM
  const sc = scale
  const isMinute = sc === '60' || sc === '30' || sc === '15' || sc === '5'
  const tickN = isMinute ? 3 : sc === 'week' ? 4 : sc === 'month' ? 5 : 6
  let prevYear = ''
  const xTicks: XTick[] = Array.from({ length: tickN }, (_, t) => {
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
}

/* ---------- 技术指标副图（MACD/KDJ/RSI/BIAS 多选叠加） ---------- */
export type SubKey = 'macd' | 'kdj' | 'rsi' | 'bias'
export const SUB_TABS: { key: SubKey; label: string }[] = [
  { key: 'macd', label: 'MACD' },
  { key: 'kdj', label: 'KDJ' },
  { key: 'rsi', label: 'RSI' },
  { key: 'bias', label: 'BIAS' },
]

export const SUB_H = 110
export const SUB_PAD = { top: 8, right: 48, bottom: 6, left: 8 }
export const SUB_INNER_H = SUB_H - SUB_PAD.top - SUB_PAD.bottom

export interface SubLine {
  key: string
  label: string
  color: string
  vals: (number | null)[]
}

export interface SubDef {
  lines: SubLine[]
  bars: (number | null)[] | null
  fixed: boolean
}

/** 由 OHLC 序列构造四个指标的线/柱定义（K线与分时副图共用） */
export function indicatorDefs(
  closes: number[],
  highs: number[],
  lows: number[],
): Record<SubKey, SubDef> {
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
        {
          key: 'r6',
          label: 'RSI6',
          color: '#94a3b8',
          vals: rsiCalc(closes, 6),
        },
        {
          key: 'r12',
          label: 'RSI12',
          color: '#eab308',
          vals: rsiCalc(closes, 12),
        },
        {
          key: 'r24',
          label: 'RSI24',
          color: '#0ea5e9',
          vals: rsiCalc(closes, 24),
        },
      ],
      bars: null,
      fixed: true,
    },
    bias: {
      lines: [
        {
          key: 'b6',
          label: 'BIAS6',
          color: '#94a3b8',
          vals: biasCalc(closes, 6),
        },
        {
          key: 'b12',
          label: 'BIAS12',
          color: '#eab308',
          vals: biasCalc(closes, 12),
        },
        {
          key: 'b24',
          label: 'BIAS24',
          color: '#0ea5e9',
          vals: biasCalc(closes, 24),
        },
      ],
      bars: null,
      fixed: false,
    },
  }
}

export interface SubChartGeom {
  lineDs: { key: string; color: string; d: string }[]
  bars: { key: string; x: number; y: number; h: number; up: boolean }[]
  grid: { y: number; label: string }[]
  zeroY: number
  legend: { label: string; color: string; value: string }[]
  hasBars: boolean
  barW: number
}

/** 单个K线副图几何计算（纯函数；selIndex = 选中K线下标，-1 = 未选中） */
export function buildSub(
  def: SubDef,
  pts: KlinePoint[],
  full: KlinePoint[],
  endOffset: number,
  selIndex: number,
): SubChartGeom {
  const n = pts.length
  const startIdx = Math.max(0, full.length - endOffset - n)

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
  const y = (v: number) =>
    SUB_PAD.top + (1 - (v - min) / (max - min)) * SUB_INNER_H
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
  const bars: { key: string; x: number; y: number; h: number; up: boolean }[] =
    []
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
    : [0, (min + max) / 2, max].map((v) => ({
        y: y(v),
        label: Math.abs(v) >= 1 ? v.toFixed(1) : v.toFixed(2),
      }))

  // 图例取选中K线（未选中取最新），与主图十字线联动
  const idx = selIndex >= 0 && selIndex < n ? selIndex : n - 1
  const legend = def.lines.map((l) => ({
    label: l.label,
    color: l.color,
    value:
      l.vals[startIdx + idx] === null || l.vals[startIdx + idx] === undefined
        ? '—'
        : f2(l.vals[startIdx + idx] as number),
  }))
  if (def.bars) {
    const hv = def.bars[startIdx + idx]
    legend.unshift({
      label: 'MACD',
      color:
        hv === null || hv < 0 ? 'var(--color-success)' : 'var(--color-danger)',
      value: hv === null || hv === undefined ? '—' : f2(hv),
    })
  }
  return { lineDs, bars, grid, zeroY, legend, hasBars: !!def.bars, barW }
}
