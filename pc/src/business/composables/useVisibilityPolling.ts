import { onActivated, onBeforeUnmount, onDeactivated } from 'vue'

type PollingOptions = {
  requireVisible?: boolean
  refreshOnVisible?: boolean
}

/** 标签页隐藏或 KeepAlive 页面停用时暂停；恢复时刷新，同一回调最多一个在途请求。 */
export function useVisibilityPolling(
  callback: () => void | Promise<void>,
  intervalMs: number,
  options: PollingOptions = {},
) {
  const requireVisible = options.requireVisible ?? true
  const refreshOnVisible = options.refreshOnVisible ?? true
  let timer: number | null = null
  let active = false
  let stopped = false
  let running = false

  const run = async () => {
    if (
      !active ||
      stopped ||
      running ||
      (requireVisible && document.visibilityState !== 'visible')
    )
      return
    running = true
    try {
      await callback()
    } catch (error) {
      // 页面通常自行显示错误；仍保护定时器免于未处理的 Promise rejection。
      console.error('轮询刷新失败', error)
    } finally {
      running = false
    }
  }
  const handleVisibilityChange = () => {
    if (refreshOnVisible && document.visibilityState === 'visible') void run()
  }
  const pause = () => {
    active = false
    if (timer !== null) window.clearInterval(timer)
    timer = null
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
  const start = (refresh: boolean) => {
    if (stopped || active) return
    active = true
    timer = window.setInterval(() => void run(), intervalMs)
    if (requireVisible && refreshOnVisible)
      document.addEventListener('visibilitychange', handleVisibilityChange)
    if (refresh) void run()
  }
  const stop = () => {
    stopped = true
    pause()
  }

  start(false)
  onActivated(() => start(true))
  onDeactivated(pause)
  onBeforeUnmount(stop)
  return { stop }
}
