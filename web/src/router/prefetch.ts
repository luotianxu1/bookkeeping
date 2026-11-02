// 路由 chunk 空闲预取：懒加载保证首屏轻量，但首次点击页面要等 chunk 下载。
// 在浏览器空闲时按序预取所有路由组件，点击时即已就绪，兼顾两者。
import type { Router } from 'vue-router'

const IDLE_FALLBACK_DELAY = 300
const BOOT_DELAY = 1500

/** 省流模式（浏览器「省流/低数据」开关）或慢速蜂窝网络时跳过全量预取 */
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

export function setupRoutePrefetch(router: Router): void {
  const start = () => {
    if (shouldSkipPrefetch()) {
      return
    }

    // 同一组件可能被多条路由复用，去重避免重复请求
    const pending = new Set<() => Promise<unknown>>()
    for (const record of router.getRoutes()) {
      const component = record.components?.default
      if (typeof component === 'function') {
        pending.add(component as () => Promise<unknown>)
      }
    }

    const queue = [...pending]
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

  if (document.readyState === 'complete') {
    window.setTimeout(start, BOOT_DELAY)
    return
  }

  window.addEventListener('load', () => window.setTimeout(start, BOOT_DELAY), { once: true })
}
