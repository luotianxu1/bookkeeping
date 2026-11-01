import { onBeforeUnmount } from 'vue'

type PollingOptions = {
  /** 页面不可见时跳过回调（默认 true） */
  requireVisible?: boolean
  /** 从不可见恢复可见时立即执行一次（默认 true，仅 requireVisible 时生效） */
  refreshOnVisible?: boolean
}

/**
 * 组件级可见性感知轮询：统一「setInterval + visibility 检查 + 恢复可见立即刷新 + 卸载清理」模板。
 *
 * - 页面隐藏时跳过回调（requireVisible）
 * - 恢复可见时立即执行一次，避免回到页面要等一个完整周期才刷新（refreshOnVisible）
 * - 组件卸载自动清理定时器与 visibilitychange 监听
 *
 * 须在 setup() 中同步调用；返回的 stop() 可提前停止（幂等）。
 */
export function useVisibilityPolling(
  callback: () => void,
  intervalMs: number,
  options: PollingOptions = {},
) {
  const requireVisible = options.requireVisible ?? true
  const refreshOnVisible = options.refreshOnVisible ?? true
  let timer: number | null = null

  const run = () => {
    if (!requireVisible || document.visibilityState === 'visible') {
      callback()
    }
  }

  const handleVisibilityChange = () => {
    if (refreshOnVisible && document.visibilityState === 'visible') {
      callback()
    }
  }

  const stop = () => {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }

  timer = window.setInterval(run, intervalMs)
  if (requireVisible && refreshOnVisible) {
    document.addEventListener('visibilitychange', handleVisibilityChange)
  }
  onBeforeUnmount(stop)

  return { stop }
}
