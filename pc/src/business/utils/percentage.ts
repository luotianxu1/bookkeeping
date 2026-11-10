const percentageFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
  useGrouping: false,
})

/** 接收百分数（5 表示 5%），只格式化显示，不改变计算或提交精度。 */
export function formatPercentage(
  value: number | string | null | undefined,
  signed = false,
): string {
  if (value == null || String(value).trim() === '') return '—'
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  const rounded = Number(percentageFormatter.format(number))
  return `${signed && rounded > 0 ? '+' : ''}${rounded.toFixed(1)}%`
}

const percentageNumber =
  '[+-]?(?:(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?|\\.\\d+)'
const percentageTextPattern = new RegExp(
  `(${percentageNumber})(?:(\\s*[~～–—至-]\\s*)(${percentageNumber}))?\\s*[%％]`,
  'g',
)

/** 格式化接口百分比和区间（7~10%），保留其余文字与已有正号。 */
export function formatPercentageText<T extends string | number>(
  value: T,
): string | T {
  if (typeof value !== 'string') return value
  return value.replace(
    percentageTextPattern,
    (_, start: string, separator?: string, end?: string) => {
      const first = formatPercentage(
        start.replaceAll(',', ''),
        start.startsWith('+'),
      )
      if (!separator || !end) return first
      return `${first.slice(0, -1)}${separator}${formatPercentage(end.replaceAll(',', ''), end.startsWith('+'))}`
    },
  )
}

/** 输入框失焦时统一显示精度；编辑与提交仍使用原始值。 */
export function formatPercentageInput<
  T extends string | number | null | undefined,
>(value: T): T | string {
  if (
    value == null ||
    String(value).trim() === '' ||
    !Number.isFinite(Number(value))
  )
    return value
  return formatPercentage(value).slice(0, -1)
}
