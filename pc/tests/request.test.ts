import { afterEach, describe, expect, it, vi } from 'vitest'
import { request } from '../src/api/request'

afterEach(() => vi.unstubAllGlobals())
describe('production API envelope and session errors', () => {
  it('accepts the existing code=0 response and sends only cookie credentials', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ code: 0, data: { id: 1 } })),
      )
    vi.stubGlobal('fetch', fetch)
    expect(await request('/api/admin/session')).toEqual({ id: 1 })
    expect(fetch.mock.calls[0]?.[1]).toMatchObject({ credentials: 'include' })
    expect(fetch.mock.calls[0]?.[1].headers).not.toHaveProperty('Authorization')
  })
  it('reports a logical 401 even if the login endpoint returns HTTP 200', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ code: 401, message: '用户名或密码错误' }),
          ),
        ),
    )
    await expect(
      request('/api/auth/login', { method: 'POST' }),
    ).rejects.toMatchObject({ status: 401, message: '用户名或密码错误' })
  })
  it('invalidates a session on protected 401 but preserves it on 503', async () => {
    const dispatchEvent = vi.fn()
    vi.stubGlobal('window', { dispatchEvent })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 503, message: '服务暂不可用' }), {
          status: 503,
        }),
      ),
    )
    await expect(request('/api/admin/session')).rejects.toMatchObject({
      status: 503,
    })
    expect(dispatchEvent).not.toHaveBeenCalled()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 401, message: '已过期' }), {
          status: 401,
        }),
      ),
    )
    await expect(request('/api/admin/session')).rejects.toMatchObject({
      status: 401,
    })
    expect(dispatchEvent.mock.calls[0]?.[0].type).toBe('session-expired')
  })
})
