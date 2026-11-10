import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'

export function useResource<T>(loader: () => Promise<T>, initial: T) {
  const data = shallowRef(initial)
  const loading = ref(false)
  const error = ref('')
  let version = 0
  async function reload() {
    const current = ++version
    loading.value = true
    error.value = ''
    try {
      const result = await loader()
      if (current === version) data.value = result
    } catch (cause) {
      if (current === version)
        error.value =
          cause instanceof Error ? cause.message : '加载失败，请重试'
    } finally {
      if (current === version) loading.value = false
    }
  }
  onMounted(reload)
  onBeforeUnmount(() => {
    version++
  })
  return { data, loading, error, reload }
}
