// 图表取色：统一从全局 CSS 令牌读取，颜色随明暗主题自动切换。
// 注意：读取不是响应式的，页面需配合 watch(isDark) 重建图表。
export function readChartToken(name: string, fallback: string): string {
  if (typeof window === 'undefined') {
    return fallback
  }

  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value.length > 0 ? value : fallback
}
