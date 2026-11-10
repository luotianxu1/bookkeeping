// 在新标签页打开高德网页导航。
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
  const params = new URLSearchParams({
    from: `${origin.longitude},${origin.latitude},${origin.poiName || origin.title}`,
    to: `${destination.longitude},${destination.latitude},${destination.poiName || destination.title}`,
    mode: mode === 'walking' ? 'walk' : mode === 'riding' ? 'ride' : 'car',
    coordinate: 'gaode',
    src: 'life-hub-pc',
    callnative: '0',
  })
  window.open(
    `https://uri.amap.com/navigation?${params}`,
    '_blank',
    'noopener,noreferrer',
  )
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
  openSystemNavigation(
    entry.navigation.origin,
    entry.navigation.destination,
    entry.navigation.mode,
  )
}
