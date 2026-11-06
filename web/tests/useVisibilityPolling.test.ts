import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, KeepAlive, ref } from 'vue'
import { afterEach, expect, test, vi } from 'vitest'
import { useVisibilityPolling } from '../src/composables/useVisibilityPolling'

afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks() })

test('pauses cached pages, refreshes on activation, and stops after unmount', async () => {
  vi.useFakeTimers()
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
  const refresh = vi.fn()
  const visible = ref(true)
  const Page = defineComponent({ setup() { useVisibilityPolling(refresh, 1000); return () => h('div') } })
  const Other = defineComponent({ setup: () => () => h('span') })
  const wrapper = mount(defineComponent({ setup: () => () => h(KeepAlive, {}, { default: () => h(visible.value ? Page : Other) }) }))
  await vi.advanceTimersByTimeAsync(1000)
  expect(refresh).toHaveBeenCalledTimes(1)
  visible.value = false
  await flushPromises()
  await vi.advanceTimersByTimeAsync(5000)
  document.dispatchEvent(new Event('visibilitychange'))
  expect(refresh).toHaveBeenCalledTimes(1)
  visible.value = true
  await flushPromises()
  expect(refresh).toHaveBeenCalledTimes(2)
  wrapper.unmount()
  await vi.advanceTimersByTimeAsync(2000)
  expect(refresh).toHaveBeenCalledTimes(2)
})

test('does not overlap slow requests and skips hidden browser tabs', async () => {
  vi.useFakeTimers()
  const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
  let finish!: () => void
  const refresh = vi.fn(() => new Promise<void>((resolve) => { finish = resolve }))
  const wrapper = mount(defineComponent({ setup() { useVisibilityPolling(refresh, 1000); return () => h('div') } }))
  await vi.advanceTimersByTimeAsync(2000)
  expect(refresh).not.toHaveBeenCalled()
  visibility.mockReturnValue('visible')
  document.dispatchEvent(new Event('visibilitychange'))
  await vi.advanceTimersByTimeAsync(5000)
  expect(refresh).toHaveBeenCalledTimes(1)
  finish()
  await flushPromises()
  await vi.advanceTimersByTimeAsync(1000)
  expect(refresh).toHaveBeenCalledTimes(2)
  wrapper.unmount()
  finish()
})
