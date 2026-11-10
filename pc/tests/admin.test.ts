import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../src/api/admin'

afterEach(() => vi.unstubAllGlobals())

describe('user search parameters', () => {
  it.each([undefined, null, ''])('omits cleared filters (%s)', async (empty) => {
    const fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 0, data: { items: [], total: 0 } })),
    )
    vi.stubGlobal('fetch', fetch)

    await api.users({
      keyword: empty,
      status: empty,
      roleCode: empty,
      page: 1,
      pageSize: 20,
    })

    const url = new URL(fetch.mock.calls[0]![0], 'http://localhost')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      page: '1',
      pageSize: '20',
    })
  })

  it('preserves selected filters and pagination', async () => {
    const fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 0, data: { items: [], total: 0 } })),
    )
    vi.stubGlobal('fetch', fetch)

    await api.users({
      keyword: '昵称 & name',
      status: 'active',
      roleCode: 'user',
      page: 2,
      pageSize: 10,
    })

    const url = new URL(fetch.mock.calls[0]![0], 'http://localhost')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      keyword: '昵称 & name',
      status: 'active',
      roleCode: 'user',
      page: '2',
      pageSize: '10',
    })
  })
})
