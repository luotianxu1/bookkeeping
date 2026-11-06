import { onBeforeUnmount, ref } from 'vue'
import { getErrorMessage, isCancelRequest } from '@/api/request'

/** 独立区域加载：保留局部错误，取消旧请求，并阻止迟到响应覆盖新状态。 */
export function useAsyncSection<T>(
  fetch: (signal: AbortSignal) => Promise<T>,
  apply: (value: T) => void,
  fallback: string,
) {
  const loading = ref(false)
  const error = ref('')
  const ready = ref(false)
  let serial = 0
  let controller: AbortController | null = null
  const reset = () => {
    serial += 1
    controller?.abort()
    controller = null
    loading.value = false
    error.value = ''
    ready.value = false
  }
  const run = async () => {
    controller?.abort()
    const request = ++serial
    controller = new AbortController()
    const signal = controller.signal
    loading.value = true
    error.value = ''
    try {
      const value = await fetch(signal)
      if (request !== serial || signal.aborted) return
      apply(value)
      ready.value = true
    } catch (cause) {
      if (request === serial && !signal.aborted && !isCancelRequest(cause)) error.value = getErrorMessage(cause, fallback)
    } finally {
      if (request === serial) loading.value = false
    }
  }
  onBeforeUnmount(reset)
  return { loading, error, ready, run, reset }
}
