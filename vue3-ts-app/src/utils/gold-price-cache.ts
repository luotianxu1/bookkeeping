import { computed, reactive, readonly } from 'vue'
import { getRealtimeGoldPrice, type GoldRealtimePrice } from '@/api/modules/finance'

const AUTO_REFRESH_INTERVAL_MS = 3 * 60 * 1000

type GoldRealtimePriceCacheState = {
  hasStarted: boolean
  isBootstrapping: boolean
  isRefreshingPrimary: boolean
  lastError: string
  price: GoldRealtimePrice | null
}

const state = reactive<GoldRealtimePriceCacheState>({
  hasStarted: false,
  isBootstrapping: false,
  isRefreshingPrimary: false,
  lastError: '',
  price: null,
})

let pendingRequest: Promise<GoldRealtimePrice> | null = null
let refreshTimer: number | null = null

function normalizeError(error: unknown, fallback = '金价加载失败') {
  return error instanceof Error ? error : new Error(fallback)
}

async function requestGoldPrice(
  options: {
    markLoading?: boolean
    force?: boolean
  } = {},
) {
  const { markLoading = false, force = false } = options

  if (!force && pendingRequest) {
    return pendingRequest
  }

  if (!state.price) {
    state.isBootstrapping = true
  }
  if (markLoading) {
    state.isRefreshingPrimary = true
  }

  const task = (async () => {
    try {
      const data = await getRealtimeGoldPrice()
      state.price = data
      state.lastError = ''
      return data
    } catch (error) {
      const normalizedError = normalizeError(error)
      state.lastError = normalizedError.message
      throw normalizedError
    } finally {
      pendingRequest = null
      state.isBootstrapping = false
      if (markLoading) {
        state.isRefreshingPrimary = false
      }
    }
  })()

  pendingRequest = task
  return task
}

function refreshVisiblePrimaryPrice() {
  if (document.visibilityState === 'visible') {
    void requestGoldPrice({ force: true }).catch(() => undefined)
  }
}

export function startGoldPriceAutoRefresh() {
  if (state.hasStarted) {
    return
  }

  state.hasStarted = true
  void requestGoldPrice({ force: true }).catch(() => undefined)
  refreshTimer = window.setInterval(refreshVisiblePrimaryPrice, AUTO_REFRESH_INTERVAL_MS)
  document.addEventListener('visibilitychange', refreshVisiblePrimaryPrice)
}

export async function ensureGoldPriceCache() {
  if (state.price) {
    return state.price
  }
  return requestGoldPrice()
}

export async function refreshGoldPriceCache() {
  return requestGoldPrice({ force: true, markLoading: true })
}

export function useGoldPriceCache() {
  return computed(() => state.price)
}

export const goldPriceCacheState = readonly(state)

export function stopGoldPriceAutoRefresh() {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
}
