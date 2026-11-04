// 路由 chunk 空闲预取：懒加载保证首屏轻量，但首次点击页面要等 chunk 下载。
// 全量预取约 80+ 个 chunk（1MB+）对移动端流量与带宽都不友好，因此按 section 渐进预取：
// 进入某个 section（finance/tools/food/profile）后，只在该 section 内部的空闲时段预取其组件，
// 跨 section 首次点击仍走正常懒加载。
import type { Router } from 'vue-router'

const IDLE_FALLBACK_DELAY = 300
const BOOT_DELAY = 1500

/** 省流模式（浏览器「省流/低数据」开关）或慢速蜂窝网络时跳过预取 */
function shouldSkipPrefetch(): boolean {
  const connection = (navigator as Navigator & {
    connection?: { saveData?: boolean; effectiveType?: string }
  }).connection
  if (!connection) {
    return false
  }
  if (connection.saveData) {
    return true
  }
  // effectiveType: slow-2g / 2g / 3g / 4g
  return connection.effectiveType === 'slow-2g' || connection.effectiveType === '2g'
}

function requestIdle(callback: () => void): void {
  if (typeof window.requestIdleCallback === 'function') {
    window.requestIdleCallback(() => callback(), { timeout: 3000 })
    return
  }

  window.setTimeout(callback, IDLE_FALLBACK_DELAY)
}

/** 已预取过的组件（同一组件可能被多条路由/多个 section 复用，全局去重） */
const loadedComponents = new Set<() => Promise<unknown>>()
/** 已启动预取的 section，避免重复收集 */
const prefetchedSections = new Set<string>()

function collectSectionLoads(router: Router, section: string): Array<() => Promise<unknown>> {
  const loads: Array<() => Promise<unknown>> = []
  for (const record of router.getRoutes()) {
    if (record.meta?.section !== section) {
      continue
    }
    const component = record.components?.default
    if (typeof component === 'function') {
      // 懒加载组件是 () => Promise<Component>；函数式组件虽同为 function 但不会出现在路由懒加载里
      const load = component as unknown as () => Promise<unknown>
      if (!loadedComponents.has(load)) {
        loadedComponents.add(load)
        loads.push(load)
      }
    }
  }
  return loads
}

function drainPrefetchQueue(queue: Array<() => Promise<unknown>>): void {
  const prefetchNext = () => {
    const load = queue.shift()
    if (!load) {
      return
    }

    Promise.resolve(load())
      .catch(() => {
        // 预取失败不打扰用户，点击时路由会正常再加载
      })
      .finally(() => requestIdle(prefetchNext))
  }

  requestIdle(prefetchNext)
}

export function setupRoutePrefetch(router: Router): void {
  router.afterEach((to) => {
    const section = to.meta?.section
    if (!section || prefetchedSections.has(section) || shouldSkipPrefetch()) {
      return
    }
    prefetchedSections.add(section)
    // 首个 section 推迟启动，避开首屏关键请求；后续 section 切换后立即排队
    const delay = prefetchedSections.size === 1 ? BOOT_DELAY : 0
    window.setTimeout(() => drainPrefetchQueue(collectSectionLoads(router, section)), delay)
  })
}
