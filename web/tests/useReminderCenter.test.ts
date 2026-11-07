import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, expect, test, vi } from 'vitest'
import { useReminderCenter } from '../src/composables/useReminderCenter'

const api = vi.hoisted(() => ({ getReminders: vi.fn(), actOnReminders: vi.fn() }))
vi.mock('../src/api/modules/reminder', () => api)

afterEach(() => { vi.resetAllMocks() })
const item = { key: 'todo:1:2026-11-07T12:00', title: '测试待办', read: false, snoozedUntil: null }
const overview = (read = false) => ({ items: [{ ...item, read }], unreadCount: read ? 0 : 1, snoozedCount: 0 })

function setup() {
  let state!: ReturnType<typeof useReminderCenter>
  const wrapper = mount(defineComponent({ setup() { state = useReminderCenter(); return () => h('div') } }))
  return { state, wrapper }
}

test('an old polling response cannot overwrite a completed read action', async () => {
  api.getReminders.mockResolvedValueOnce(overview())
  const { state, wrapper } = setup()
  await flushPromises()
  let release!: (value: unknown) => void
  api.getReminders.mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const pending = state.refresh()
  api.actOnReminders.mockResolvedValue(undefined)
  api.getReminders.mockResolvedValueOnce(overview(true))
  expect(await state.act([item.key], 'read')).toBe(true)
  expect(state.unreadCount.value).toBe(0)
  release(overview())
  await pending
  expect(state.items.value[0]?.read).toBe(true)
  wrapper.unmount()
})

test('failed mutation preserves error and fetches the committed server state', async () => {
  api.getReminders.mockResolvedValue(overview())
  const { state, wrapper } = setup()
  await flushPromises()
  api.actOnReminders.mockRejectedValue(new Error('保存失败'))
  expect(await state.act([item.key], 'snooze', 60)).toBe(false)
  expect(state.error.value).toBe('保存失败')
  expect(state.unreadCount.value).toBe(1)
  expect(state.busy.value).toBe(false)
  wrapper.unmount()
})

test('saving settings can reload while an older poll is still running', async () => {
  api.getReminders.mockResolvedValueOnce(overview())
  const { state, wrapper } = setup()
  await flushPromises()
  let release!: (value: unknown) => void
  api.getReminders.mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
  const pending = state.refresh()
  api.getReminders.mockResolvedValueOnce({ items: [], unreadCount: 0, snoozedCount: 0 })
  await state.reload()
  release(overview())
  await pending
  expect(state.items.value).toEqual([])
  expect(state.refreshing.value).toBe(false)
  wrapper.unmount()
})

test('concurrent clicks are ignored and large read batches preserve the snapshot of keys', async () => {
  api.getReminders.mockResolvedValue(overview())
  const { state, wrapper } = setup()
  await flushPromises()
  let release!: () => void
  api.actOnReminders.mockImplementationOnce(() => new Promise<void>(resolve => { release = resolve }))
  const keys = Array.from({ length: 201 }, (_, index) => `todo:${index}`)
  const first = state.act(keys, 'read')
  expect(await state.act([item.key], 'read')).toBe(false)
  release()
  await first
  expect(api.actOnReminders).toHaveBeenCalledTimes(2)
  expect(api.actOnReminders.mock.calls[0]?.[0]).toHaveLength(200)
  expect(api.actOnReminders.mock.calls[1]?.[0]).toEqual(['todo:200'])
  wrapper.unmount()
})
