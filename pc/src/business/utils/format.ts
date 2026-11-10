// 金额格式化工具：统一各页面重复实现的千分位金额格式化。

const formatterCache = new Map<number, Intl.NumberFormat>()

function getFormatter(digits: number) {
  let formatter = formatterCache.get(digits)
  if (!formatter) {
    formatter = new Intl.NumberFormat('zh-CN', {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    })
    formatterCache.set(digits, formatter)
  }
  return formatter
}

/** 千分位金额（无货币符号），digits 默认 2；null/undefined 按 0 处理 */
export function formatAmount(value: number | null | undefined, digits = 2) {
  return getFormatter(digits).format(Number(value ?? 0))
}

/** 千分位金额（带 ¥ 货币符号），digits 默认 2；null/undefined 按 0 处理 */
export function formatCnyAmount(value: number | null | undefined, digits = 2) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
}
