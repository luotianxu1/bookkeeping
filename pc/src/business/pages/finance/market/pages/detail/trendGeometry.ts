// 分时折线图 + 分时副图的纯几何计算（自 DetailPage 抽出，无响应式依赖）。
import { f2 } from '@/business/utils/marketFormat'
import {
  H,
  PAD,
  SUB_INNER_H,
  SUB_PAD,
  W,
  type SubDef,
  type XTick,
} from './chartGeometry'

export const TREND_PAD = { top: 12, right: 48, bottom: 22, left: 40 }

export interface TrendInput {
  points: { time: string; price: number; avg: number | null; volume: number }[]
  prev: number | null
}

/** 分时主图几何：价格线 + 均价线 + 昨收基准，涨跌空间以昨收对称（纯函数） */
export function buildTrendChart(av: TrendInput) {
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
  const xTicks: XTick[] = Array.from({ length: 5 }, (_, k) => {
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
}

export interface TrendSubGeom {
  lineDs: { key: string; color: string; d: string }[]
  bars: { key: string; x: number; y: number; h: number; up: boolean }[]
  grid: { y: number; label: string }[]
  zeroY: number
  legend: { label: string; color: string; value: string }[]
  hasBars: boolean
  barW: number
}

/** 分时副图几何：x 与分时主图完全对齐，全量点渲染，无视窗切片；selIdx = 选中分钟下标（-1 = 未选中） */
export function buildTrendSub(
  def: SubDef,
  pts: TrendInput['points'],
  selIdx: number,
): TrendSubGeom {
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
  const y = (v: number) =>
    SUB_PAD.top + (1 - (v - min) / (max - min)) * SUB_INNER_H
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
  const bars: { key: string; x: number; y: number; h: number; up: boolean }[] =
    []
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
    : [0, (min + max) / 2, max].map((v) => ({
        y: y(v),
        label: Math.abs(v) >= 1 ? v.toFixed(1) : v.toFixed(2),
      }))

  // 图例取选中分钟（未选中取最新），与分时十字线联动
  const sel = selIdx !== -1 && selIdx < n ? selIdx : -1
  const idx = sel >= 0 ? sel : n - 1
  const legend = def.lines.map((l) => ({
    label: l.label,
    color: l.color,
    value:
      l.vals[idx] === null || l.vals[idx] === undefined
        ? '—'
        : f2(l.vals[idx] as number),
  }))
  if (def.bars) {
    const hv = def.bars[idx]
    legend.unshift({
      label: 'MACD',
      color:
        hv === null || hv < 0 ? 'var(--color-success)' : 'var(--color-danger)',
      value: hv === null || hv === undefined ? '—' : f2(hv),
    })
  }
  return { lineDs, bars, grid, zeroY, legend, hasBars: !!def.bars, barW }
}
