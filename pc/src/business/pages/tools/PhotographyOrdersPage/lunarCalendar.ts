// PhotographyOrderOverviewPage 日历格子的农历标签（纯函数，带模块级缓存）。

const lunarFormatter = new Intl.DateTimeFormat('zh-CN-u-ca-chinese', {
  month: 'long',
  day: 'numeric',
})
const lunarLabelCache = new Map<string, string>()

export function formatLunarLabel(dateText: string) {
  const cached = lunarLabelCache.get(dateText)
  if (cached) {
    return cached
  }

  const lunarText = lunarFormatter.format(new Date(`${dateText}T12:00:00`))
  const matched = /^(.+?)(\d+)日$/.exec(lunarText)
  if (!matched) {
    lunarLabelCache.set(dateText, lunarText)
    return lunarText
  }

  const monthLabel = matched[1]
  const dayValue = Number(matched[2])
  const label = dayValue === 1 ? monthLabel : toChineseDay(dayValue)
  lunarLabelCache.set(dateText, label)
  return label
}

export function toChineseDay(day: number) {
  const digits = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九']
  if (day <= 10) {
    return day === 10 ? '初十' : `初${digits[day]}`
  }
  if (day < 20) {
    return `十${digits[day - 10]}`
  }
  if (day === 20) {
    return '二十'
  }
  if (day < 30) {
    return `廿${digits[day - 20]}`
  }
  return day === 30 ? '三十' : String(day)
}
