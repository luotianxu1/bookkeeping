import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { beforeEach, expect, test, vi } from 'vitest'
import { getAccountSummary, getAccountTypes, type AccountSummary } from '@/api/modules/finance'
import { useAccountSummary } from '@/pages/finance/AccountManagementPage/useAccountSummary'

vi.mock('@/api/modules/finance', () => ({ getAccountSummary: vi.fn(), getAccountTypes: vi.fn() }))
beforeEach(() => {
  vi.mocked(getAccountSummary).mockReset()
  vi.mocked(getAccountTypes).mockReset().mockResolvedValue([])
})
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const summary = (totalAssets: number): AccountSummary => ({ accounts: [], totalAssets })

test('family switch ignores late success and failure, keeps current loading, and cancels on unmount', async () => {
  const old = deferred<AccountSummary>(), fresh = deferred<AccountSummary>(), last = deferred<AccountSummary>()
  vi.mocked(getAccountSummary).mockReturnValueOnce(old.promise).mockReturnValueOnce(fresh.promise).mockReturnValueOnce(last.promise)
  const ids = ref([1])
  let state!: ReturnType<typeof useAccountSummary>
  const wrapper = mount(defineComponent({ setup() {
    state = useAccountSummary(ids)
    return () => h('div', String(state.overviewTotalAssets.value))
  } }))
  void state.loadAccounts()
  const signal = vi.mocked(getAccountSummary).mock.calls[0][1]!.signal!
  ids.value = [2]
  void state.loadAccounts()
  expect(signal.aborted).toBe(true)
  old.reject(new Error('old member error'))
  await flushPromises()
  expect(state.isLoadingAccounts.value).toBe(true)
  expect(state.accountListError.value).toBe('')
  fresh.resolve(summary(200))
  await flushPromises()
  expect(state.overviewTotalAssets.value).toBe(200)
  ids.value = [1]
  void state.loadAccounts()
  expect(state.overviewTotalAssets.value).toBeNull()
  const lastSignal = vi.mocked(getAccountSummary).mock.calls[2][1]!.signal!
  wrapper.unmount()
  last.resolve(summary(999))
  await flushPromises()
  expect(lastSignal.aborted).toBe(true)
  expect(state.overviewTotalAssets.value).toBeNull()
  await state.loadAccounts()
  expect(getAccountSummary).toHaveBeenCalledTimes(3)
})

test('each family member needs one summary request; stale success cannot replace latest totals', async () => {
  const old = deferred<AccountSummary>()
  vi.mocked(getAccountSummary).mockReturnValueOnce(old.promise).mockResolvedValueOnce(summary(100)).mockResolvedValueOnce(summary(250))
  const ids = ref([1])
  let state!: ReturnType<typeof useAccountSummary>
  const wrapper = mount(defineComponent({ setup() { state = useAccountSummary(ids); return () => null } }))
  void state.loadAccounts()
  ids.value = [1, 2]
  await state.loadAccounts()
  expect(state.overviewTotalAssets.value).toBe(350)
  expect(vi.mocked(getAccountSummary).mock.calls.map(([id]) => id)).toEqual([1, 1, 2])
  old.resolve(summary(999))
  await flushPromises()
  expect(state.overviewTotalAssets.value).toBe(350)
  expect(state.isLoadingAccounts.value).toBe(false)
  wrapper.unmount()
})
