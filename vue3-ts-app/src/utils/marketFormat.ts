/** 行情中心共享展示格式化工具 */

/** 通用展示（null → —） */
export function fmt(v: number | string | null | undefined): string {
  return v === null || v === undefined ? '—' : String(v)
}

/** 两位小数 */
export function f2(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : v.toFixed(2)
}

/** 涨跌幅（带符号 + 百分号） */
export function pct(v: number | null | undefined): string {
  if (v === null || v === undefined) return '—'
  return `${v > 0 ? '+' : ''}${v.toFixed(2)}%`
}

/** 成交额统一换算为「亿」（快照表用） */
export function toYi(v: number | null | undefined): string {
  if (!v) return '—'
  return (v / 1e8).toFixed(0)
}

/** 成交额固定以「亿」为单位；sign 为币种符号（¥ / HK$ / $），默认无 */
export function amt(v: number | null | undefined, sign = ''): string {
  if (!v) return '—'
  return `${sign}${(v / 1e8).toFixed(0)}亿`
}

/** 带符号金额（亿，两位小数），用于资金流向 */
export function signedYi(v: number | null | undefined): string {
  if (v === null || v === undefined) return '—'
  const yi = Math.round((v / 1e8) * 100) / 100
  if (yi === 0) return '0.00亿' // 极小正负值四舍五入后归零，避免出现 "-0.00亿"
  return `${yi > 0 ? '+' : ''}${yi.toFixed(2)}亿`
}

/** 涨红跌绿 class（0 视为平盘）；对应主题 token：--color-danger 红 / --color-success 绿 */
export function dirClass(v: number | null | undefined): string {
  if (!v) return ''
  return v > 0 ? 'up' : 'down'
}
