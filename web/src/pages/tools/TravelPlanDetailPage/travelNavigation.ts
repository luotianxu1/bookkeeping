// 系统级导航（高德 App 唤起）：纯函数，无组件状态。
export type TransportMode = 'driving' | 'walking' | 'riding'

export type NavigationPoint = {
  title: string
  longitude: number
  latitude: number
  poiId?: string | null
  poiName?: string | null
}

export function openSystemNavigation(
  origin: NavigationPoint,
  destination: NavigationPoint,
  mode: TransportMode = 'driving',
) {
  const travelMode = mode === 'walking'
    ? 'walking'
    : mode === 'riding'
      ? 'transit'
      : 'driving'
  const isIOS = /iPhone|iPad|iPod/i.test(window.navigator.userAgent)
  const destinationName = destination.poiName?.trim() || destination.title.trim()

  if (isIOS) {
    const params = new URLSearchParams({
      sourceApplication: 'vue3-ts-app',
      poiname: destinationName,
      lat: String(destination.latitude),
      lon: String(destination.longitude),
      dev: '1',
    })
    window.location.href = `iosamap://navi?${params.toString()}`
    return
  }

  if (destinationName) {
    const keywordParams = new URLSearchParams({
      sourceApplication: 'vue3-ts-app',
      keyword: destinationName,
      style: travelMode === 'walking' ? '2' : '0',
    })
    window.location.href = `androidamap://keywordNavi?${keywordParams.toString()}`
    return
  }

  const params = new URLSearchParams({
    sourceApplication: 'vue3-ts-app',
    poiname: destinationName || '目的地',
    poiid: destination.poiId?.trim() || '',
    lat: String(destination.latitude),
    lon: String(destination.longitude),
    dev: '1',
    style: travelMode === 'walking' ? '2' : '0',
  })

  void origin
  window.location.href = `androidamap://navi?${params.toString()}`
}

export function openOverviewNavigation(entry: {
  navigation?: {
    origin: NavigationPoint
    destination: NavigationPoint
    mode: TransportMode
  } | null
}) {
  if (!entry.navigation) {
    return
  }
  openSystemNavigation(entry.navigation.origin, entry.navigation.destination, entry.navigation.mode)
}
