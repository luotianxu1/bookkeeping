import { computed, reactive, readonly } from 'vue'
import { getGoldPrices, type GoldPrice, type GoldPriceRange } from '@/api/modules/finance'

const DEFAULT_RANGE: GoldPriceRange = '1d'

type GoldPriceDetailCacheState = {
  isBootstrapping: boolean
  isRefreshingPrimary: boolean
  lastError: string
  prices: Partial<Record<GoldPriceRange, GoldPrice>>
}

const state = reactive<GoldPriceDetailCacheState>({
  isBootstrapping: false,
  isRefreshingPrimary: false,
  lastError: '',
  prices: {},
})

const pendingRequests = new Map<GoldPriceRange, Promise<GoldPrice>>()

function normalizeError(error: unknown, fallback = '金价加载失败') {
  return error instanceof Error ? error : new Error(fallback)
}

async function requestGoldPriceDetail(
  range: GoldPriceRange,
  options: {
    markLoading?: boolean
    force?: boolean
  } = {},
) {
  const { markLoading = false, force = false } = options

  if (!force && pendingRequests.has(range)) {
    return pendingRequests.get(range)!
  }

  if (range === DEFAULT_RANGE && !state.prices[DEFAULT_RANGE]) {
    state.isBootstrapping = true
  }
  if (markLoading) {
    state.isRefreshingPrimary = true
  }

  const task = (async () => {
    try {
      const data = await getGoldPrices(range)
      state.prices[range] = data
      state.lastError = ''
      return data
    } catch (error) {
      const normalizedError = normalizeError(error)
      state.lastError = normalizedError.message
      throw normalizedError
    } finally {
      pendingRequests.delete(range)
      if (range === DEFAULT_RANGE) {
        state.isBootstrapping = false
      }
      if (markLoading) {
        state.isRefreshingPrimary = false
      }
    }
  })()

  pendingRequests.set(range, task)
  return task
}

export async function ensureGoldPriceCache(range: GoldPriceRange = DEFAULT_RANGE) {
  const cached = state.prices[range]
  if (cached) {
    return cached
  }
  return requestGoldPriceDetail(range)
}

export async function refreshGoldPriceCache(range: GoldPriceRange = DEFAULT_RANGE) {
  return requestGoldPriceDetail(range, { force: true, markLoading: range === DEFAULT_RANGE })
}

export function getCachedGoldPrice(range: GoldPriceRange = DEFAULT_RANGE) {
  return state.prices[range] ?? null
}

export function useGoldPriceCache(range: GoldPriceRange = DEFAULT_RANGE) {
  return computed(() => state.prices[range] ?? null)
}

export const goldPriceCacheState = readonly(state)
