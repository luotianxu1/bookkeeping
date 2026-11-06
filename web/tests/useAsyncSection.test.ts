import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { expect, test } from 'vitest'
import { useAsyncSection } from '../src/composables/useAsyncSection'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

test('local detail renders while optional data is pending or failed, and retry recovers', async () => {
  const optional = deferred<string>()
  const wrapper = mount(defineComponent({ setup() {
    const title = ref('')
    const detail = useAsyncSection(async () => '持仓 100 份', value => { title.value = value }, '详情失败')
    let attempt = 0
    const fees = useAsyncSection(() => ++attempt === 1 ? optional.promise : Promise.resolve('0.5%'), () => {}, '费率失败')
    void detail.run(); void fees.run()
    return () => h('div', [h('strong', title.value), h('p', fees.error.value), h('button', { onClick: fees.run }, '重试')])
  } }))
  await flushPromises()
  expect(wrapper.find('strong').text()).toBe('持仓 100 份')
  optional.reject(new Error('上游超时'))
  await flushPromises()
  expect(wrapper.find('strong').text()).toBe('持仓 100 份')
  expect(wrapper.find('p').text()).toBe('上游超时')
  await wrapper.find('button').trigger('click')
  await flushPromises()
  expect(wrapper.find('p').text()).toBe('')
  wrapper.unmount()
})

test('newer request wins even if old transport ignores cancellation; unmount blocks writes', async () => {
  const old = deferred<string>(); const fresh = deferred<string>(); const last = deferred<string>()
  const signals: AbortSignal[] = []
  let section!: ReturnType<typeof useAsyncSection<string>>
  const values: string[] = []
  const wrapper = mount(defineComponent({ setup() {
    const queue = [old, fresh, last]
    section = useAsyncSection(signal => { signals.push(signal); return queue.shift()!.promise }, value => values.push(value), '失败')
    return () => h('div')
  } }))
  void section.run(); void section.run()
  expect(signals[0].aborted).toBe(true)
  fresh.resolve('new'); await flushPromises()
  old.resolve('old'); await flushPromises()
  expect(values).toEqual(['new'])
  void section.run(); wrapper.unmount(); last.resolve('late'); await flushPromises()
  expect(values).toEqual(['new'])
  expect(signals[2].aborted).toBe(true)
})
