import { onBeforeUnmount } from 'vue'

/**
 * 请求取消：防止慢响应覆盖新数据 + 组件卸载后 setState。
 *
 * 用法：
 *   const { signalFor } = useRequestAbort()
 *   const load = async () => {
 *     try {
 *       const data = await requestGet(client, url, { signal: signalFor() })
 *       // signalFor() 会先 abort 上一次请求，再返回新 signal
 *     } catch (error) {
 *       if (isCancelRequest(error)) return  // 被新请求/卸载中止，静默忽略
 *       // ...真实错误处理
 *     }
 *   }
 *
 * 组件卸载时自动 abort 未完成请求。
 */
export function useRequestAbort() {
  let controller: AbortController | null = null

  const signalFor = (): AbortSignal => {
    controller?.abort()
    controller = new AbortController()
    return controller.signal
  }

  onBeforeUnmount(() => {
    controller?.abort()
    controller = null
  })

  return { signalFor }
}
