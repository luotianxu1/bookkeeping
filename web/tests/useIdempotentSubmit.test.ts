import { describe, expect, it, vi } from 'vitest'
import { useIdempotentSubmit } from '../src/composables/useIdempotentSubmit'

describe('useIdempotentSubmit', () => {
  it('freezes automatic timestamps for retries and generates new timestamps after edits or success', () => {
    const submit = useIdempotentSubmit()
    const generated = vi.fn()
      .mockReturnValueOnce({ tradeAt: '2026-11-06T10:00:00' })
      .mockReturnValueOnce({ tradeAt: '2026-11-06T10:05:00' })
      .mockReturnValueOnce({ tradeAt: '2026-11-06T10:10:00' })
    const first = submit.withRequestId({ amount: 100 }, generated)
    expect(submit.withRequestId({ amount: 100 }, generated)).toEqual(first)
    const edited = submit.withRequestId({ amount: 200 }, generated)
    expect(edited.requestId).not.toBe(first.requestId)
    expect(edited.tradeAt).toBe('2026-11-06T10:05:00')
    submit.reset()
    expect(submit.withRequestId({ amount: 200 }, generated).tradeAt).toBe('2026-11-06T10:10:00')
    expect(generated).toHaveBeenCalledTimes(3)
  })
  it('reuses failed submissions, changes key for a changed payload, and resets after success', () => {
    const submit = useIdempotentSubmit()
    const first = submit.withRequestId({ userId: 1, amount: 10 })
    expect(submit.withRequestId({ userId: 1, amount: 10 }).requestId).toBe(first.requestId)
    expect(submit.withRequestId({ userId: 1, amount: 20 }).requestId).not.toBe(first.requestId)
    const next = submit.withRequestId({ userId: 1, amount: 20 })
    submit.reset()
    expect(submit.withRequestId({ userId: 1, amount: 20 }).requestId).not.toBe(next.requestId)
  })
})
