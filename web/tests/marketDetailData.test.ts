import { expect, test, vi, beforeEach } from 'vitest'
import { computed, defineComponent, ref, nextTick } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { useDetailData } from '@/pages/finance/market/pages/detail/useDetailData'

const pending = vi.hoisted(() => [] as { name: string; code: string; signal: AbortSignal;
  resolve: (value: unknown) => void; reject: (cause: Error) => void }[])
vi.mock('@/api/modules/market', () => {
  const deferred = (name: string, code: string, { signal }: { signal: AbortSignal }) =>
    new Promise((resolve, reject) => { pending.push({ name, code, signal, resolve, reject }) })
  return {
    getDetail: vi.fn(async () => ({ quote: {}, depth: null, valuation: null, symbol: 'test' })),
    getTrend: vi.fn(async () => ({ symbol: 'test' })),
    getKline: vi.fn(async () => ({ kline: [] })),
    getExtra: vi.fn((_kind, code, options) => deferred('extra', code, options)),
    getF10: vi.fn((_kind, code, options) => deferred('f10', code, options)),
    getStatements: vi.fn((_kind, code, _count, options) => deferred('statements', code, options)),
    getTrendHistory: vi.fn((_kind, code, _count, options) => deferred('history', code, options)),
  }
})
beforeEach(() => { pending.length = 0 })

test('all four detail regions complete independently; switching symbols drops stale results', async () => {
  const code = ref('sh600000')
  let data!: ReturnType<typeof useDetailData>
  const wrapper = mount(defineComponent({ setup() {
    data = useDetailData(computed(() => 'stock'), computed(() => code.value))
    return () => null
  } }))
  expect(pending).toHaveLength(4)
  expect(pending.every(request => !request.signal.aborted)).toBe(true)
  const old = pending.slice()
  code.value = 'sh600001'
  await nextTick()
  expect(old.every(request => request.signal.aborted)).toBe(true)
  const fresh = pending.slice(4)
  const results = {
    extra: { moneyflow: [1], announcements: [], dividends: [] },
    f10: { financials: { symbol: 'new' } },
    statements: { income: { symbol: 'new' } },
    history: { days: [{ date: '2026-11-10' }] },
  }
  fresh.forEach(request => request.resolve(results[request.name as keyof typeof results]))
  await flushPromises()
  old.forEach(request => request.resolve({ days: [], financials: { symbol: 'old' } }))
  await flushPromises()
  expect(data.extra.value).toEqual(results.extra)
  expect(data.f10.value).toEqual(results.f10)
  expect(data.statements.value).toEqual(results.statements)
  expect(data.trendHistory.value).toEqual(results.history.days)
  expect(data.secondaryError.value).toBe('')
  data.retrySecondary()
  expect(pending.slice(8).every(request => !request.signal.aborted)).toBe(true)
  wrapper.unmount()
  expect(pending.every(request => request.signal.aborted)).toBe(true)
})

test('one region failure remains visible while other regions load and retry recovers', async () => {
  let data!: ReturnType<typeof useDetailData>
  const wrapper = mount(defineComponent({ setup() {
    data = useDetailData(computed(() => 'stock'), computed(() => 'sh600000'))
    return () => null
  } }))
  pending.find(request => request.name === 'f10')!.reject(new Error('F10 unavailable'))
  pending.filter(request => request.name !== 'f10').forEach(request => request.resolve({ days: [] }))
  await flushPromises()
  expect(data.secondaryError.value).toBe('F10 unavailable')
  data.retrySecondary()
  pending.slice(4).forEach(request => request.resolve({ days: [], financials: {} }))
  await flushPromises()
  expect(data.secondaryError.value).toBe('')
  expect(data.f10.value).toEqual({ days: [], financials: {} })
  wrapper.unmount()
})
