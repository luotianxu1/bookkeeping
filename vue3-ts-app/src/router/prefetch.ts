// 路由 chunk 空闲预取：懒加载保证首屏轻量，但首次点击页面要等 chunk 下载。
// 在浏览器空闲时按序预取所有路由组件，点击时即已就绪，兼顾两者。
import type { Router } from 'vue-router'

const IDLE_FALLBACK_DELAY = 300
const BOOT_DELAY = 1500

function requestIdle(callback: () => void): void {
  if (typeof window.requestIdleCallback === 'function') {
    window.requestIdleCallback(() => callback(), { timeout: 3000 })
    return
  }

  window.setTimeout(callback, IDLE_FALLBACK_DELAY)
}

export function setupRoutePrefetch(router: Router): void {
  const start = () => {
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
