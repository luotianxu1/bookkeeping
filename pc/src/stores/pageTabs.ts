import { reactive } from 'vue'

export interface PageTab {
  /** 含 query/hash 的完整路径，作为标签唯一标识 */
  key: string
  path: string
  title: string
  fixed?: boolean
}

const STORAGE_KEY = 'life-hub-pc-page-tabs'
const HOME_KEY = '/overview'
const MAX_TABS = 20

function homeTab(): PageTab {
  return { key: HOME_KEY, path: HOME_KEY, title: '功能总览', fixed: true }
}

function isTab(value: unknown): value is PageTab {
  if (!value || typeof value !== 'object') return false
  const tab = value as Partial<PageTab>
  return (
    typeof tab.key === 'string' &&
    tab.key.startsWith('/') &&
    typeof tab.path === 'string' &&
    typeof tab.title === 'string' &&
    tab.title.length > 0
  )
}

function restore(): PageTab[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return [homeTab()]
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return [homeTab()]
    const seen = new Set<string>()
    const restored = parsed.filter(isTab).filter((tab) => {
      if (seen.has(tab.key)) return false
      seen.add(tab.key)
      return true
    })
    // 总览为固定首页，始终排在第一位；其余标签按原顺序保留。
    return [
      homeTab(),
      ...restored.filter((tab) => tab.key !== HOME_KEY).map((tab) => ({
        ...tab,
        fixed: false,
      })),
    ].slice(0, MAX_TABS)
  } catch {
    return [homeTab()]
  }
}

export const pageTabs = reactive<PageTab[]>(restore())

function persist() {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(pageTabs))
  } catch {
    // 隐私模式或存储配额受限时，标签仅在本次会话内存中生效。
  }
}

export function openPageTab(tab: PageTab) {
  if (pageTabs.some((item) => item.key === tab.key)) {
    persist()
    return
  }
  pageTabs.push(tab)
  if (pageTabs.length > MAX_TABS) {
    // 超出上限时淘汰最旧的普通标签，固定首页不动。
    const staleIndex = pageTabs.findIndex((item) => !item.fixed)
    if (staleIndex >= 0) pageTabs.splice(staleIndex, 1)
  }
  persist()
}

export function closePageTab(key: string) {
  const index = pageTabs.findIndex((item) => item.key === key)
  if (index < 0 || pageTabs[index]!.fixed) return
  pageTabs.splice(index, 1)
  persist()
}

export function closeOtherPageTabs(key: string) {
  if (!pageTabs.some((item) => item.key === key)) return
  pageTabs.splice(
    0,
    pageTabs.length,
    ...pageTabs.filter((item) => item.fixed || item.key === key),
  )
  persist()
}

export function closeRightPageTabs(key: string) {
  const index = pageTabs.findIndex((item) => item.key === key)
  if (index < 0) return
  pageTabs.splice(
    0,
    pageTabs.length,
    ...pageTabs.filter((item, i) => item.fixed || i <= index),
  )
  persist()
}

export function closeAllPageTabs() {
  pageTabs.splice(0, pageTabs.length, ...pageTabs.filter((item) => item.fixed))
  persist()
}

export function clearPageTabs() {
  pageTabs.splice(0, pageTabs.length, homeTab())
  persist()
}
