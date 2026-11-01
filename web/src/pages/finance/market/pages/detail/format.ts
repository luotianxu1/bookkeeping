// DetailPage 系列共用的格式化辅助（面板子组件与主页面共用）。
import { f2 } from '@/utils/marketFormat'

/** 金额固定两位小数「亿」（融资余额/财务金额） */
export function yi(v: number | null): string {
  if (v === null || v === undefined) return '—'
  return `${(v / 1e8).toFixed(2)}亿`
}

/** 股数转「万股」 */
export function wan(v: number): string {
  return `${(v / 1e4).toLocaleString('zh-CN', { maximumFractionDigits: 0 })}万股`
}

/** 财务金额按量级取整显示 */
export function finVal(v: number | null): string {
  if (v === null || v === undefined) return '—'
  const abs = Math.abs(v)
  if (abs >= 1e8) return yi(v)
  if (abs >= 1) return v.toFixed(2)
  return v.toFixed(4)
}

/** 带符号涨跌额 */
export function signed(v: number | null | undefined): string {
  if (v === null || v === undefined) return '—'
  return `${v > 0 ? '+' : ''}${f2(v)}`
}

/** 成交量自适应：亿 / 万 / 原值 */
export function vol(v: number | null | undefined): string {
  if (!v) return '—'
  if (v >= 1e8) return `${(v / 1e8).toFixed(2)}亿`
  if (v >= 1e4) return `${(v / 1e4).toFixed(1)}万`
  return String(v)
}
