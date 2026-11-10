// 旅行详情页的高德地图渲染 composable（自 index.vue 抽出）。
// 负责地图脚本加载、双图（总览/当日路线）实例管理与标记渲染；
// 点位数据由调用方以 getter 注入，保持与页面状态解耦。
import { ref } from 'vue'
import { getErrorMessage } from '@/business/api/request'

export type TravelMapPoint = {
  index: number
  title: string
  longitude: number
  latitude: number
}

type AMapInstance = {
  clearMap: () => void
  add: (overlays: unknown[]) => void
  setFitView: (
    overlays?: unknown[],
    immediately?: boolean,
    avoid?: number[],
  ) => void
  setZoomAndCenter: (zoom: number, center: [number, number]) => void
  destroy: () => void
}

type AMapConstructor = {
  Map: new (
    container: HTMLElement,
    options: Record<string, unknown>,
  ) => AMapInstance
  Marker: new (options: Record<string, unknown>) => unknown
  Polyline: new (options: Record<string, unknown>) => unknown
  Pixel: new (x: number, y: number) => unknown
}

declare global {
  interface Window {
    AMap?: AMapConstructor
  }
}

// 高德 JS Key（地图渲染用）；路线规划/地点搜索的 Key 保存在 tool-service，前端经 /tool-api 代理调用
const amapWebJsKey = String(import.meta.env.VITE_AMAP_WEB_JS_KEY ?? '').trim()

let amapScriptPromise: Promise<AMapConstructor> | null = null

async function ensureAmap(): Promise<AMapConstructor> {
  if (window.AMap) {
    return window.AMap
  }

  if (!amapScriptPromise) {
    amapScriptPromise = new Promise<AMapConstructor>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${amapWebJsKey}`
      script.async = true
      script.onload = () => {
        if (window.AMap) {
          resolve(window.AMap)
          return
        }
        reject(new Error('高德地图初始化失败'))
      }
      script.onerror = () => reject(new Error('高德地图脚本加载失败'))
      document.head.appendChild(script)
    })
  }

  return amapScriptPromise
}

/** 解析高德 location 串（"lng,lat"）为经纬度二元组 */
export function parseAmapLocation(location?: string | null) {
  if (!location) {
    return [null, null] as const
  }

  const [longitudeText, latitudeText] = location.split(',')
  const longitude = Number(longitudeText)
  const latitude = Number(latitudeText)

  return [
    Number.isFinite(longitude) ? longitude : null,
    Number.isFinite(latitude) ? latitude : null,
  ] as const
}

export function useTravelMaps(options: {
  /** 总览图点位 */
  overviewPoints: () => TravelMapPoint[]
  /** 当日路线图点位 */
  routePoints: () => TravelMapPoint[]
  /** 计划是否配置了开始日期（决定无点位时地图视野回落到全国） */
  hasPlanStartDate: () => boolean
}) {
  const overviewMapRef = ref<HTMLDivElement | null>(null)
  const routeMapRef = ref<HTMLDivElement | null>(null)
  const overviewMapMessage = ref('')
  const routeMapMessage = ref('')

  let overviewMapContainer: HTMLDivElement | null = null
  let routeMapContainer: HTMLDivElement | null = null
  let overviewMapInstance: AMapInstance | null = null
  let routeMapInstance: AMapInstance | null = null

  async function renderTravelMap(kind: 'overview' | 'route') {
    const mapRef =
      kind === 'overview' ? overviewMapRef.value : routeMapRef.value
    if (!mapRef) {
      return
    }

    const points =
      kind === 'overview' ? options.overviewPoints() : options.routePoints()
    const messageRef =
      kind === 'overview' ? overviewMapMessage : routeMapMessage

    if (!amapWebJsKey) {
      messageRef.value = '请先配置高德地图 Key'
      return
    }

    try {
      const AMap = await ensureAmap()
      let mapContainer =
        kind === 'overview' ? overviewMapContainer : routeMapContainer
      let mapInstance =
        kind === 'overview' ? overviewMapInstance : routeMapInstance

      if (mapContainer !== mapRef) {
        mapInstance?.destroy()
        mapInstance = null
        mapContainer = mapRef
      }

      if (!mapInstance) {
        mapInstance = new AMap.Map(mapRef, {
          viewMode: '2D',
          zoom: 11,
          center: points[0]
            ? [points[0].longitude, points[0].latitude]
            : [116.397428, 39.90923],
        })
      }

      mapInstance.clearMap()

      if (points.length === 0) {
        messageRef.value =
          kind === 'overview'
            ? '全程暂无可定位的行程点'
            : '当天暂无可定位的行程点'
        if (options.hasPlanStartDate()) {
          mapInstance.setZoomAndCenter(5, [104.066301, 30.572961])
        }
        if (kind === 'overview') {
          overviewMapInstance = mapInstance
          overviewMapContainer = mapContainer
        } else {
          routeMapInstance = mapInstance
          routeMapContainer = mapContainer
        }
        return
      }

      messageRef.value = ''
      const overlays = points.map(
        (point) =>
          new AMap.Marker({
            position: [point.longitude, point.latitude],
            anchor: 'center',
            offset: new AMap.Pixel(0, 0),
            content: `<div class="travel-map-marker">${point.index}</div>`,
            title: point.title,
          }),
      )

      if (points.length > 1) {
        overlays.push(
          new AMap.Polyline({
            path: points.map((point) => [point.longitude, point.latitude]),
            strokeColor: '#2563EB',
            strokeWeight: 4,
            strokeOpacity: 0.9,
            lineJoin: 'round',
            lineCap: 'round',
          }),
        )
      }

      mapInstance.add(overlays)
      mapInstance.setFitView(overlays, true, [28, 52, 28, 28])

      if (kind === 'overview') {
        overviewMapInstance = mapInstance
        overviewMapContainer = mapContainer
      } else {
        routeMapInstance = mapInstance
        routeMapContainer = mapContainer
      }
    } catch (error) {
      messageRef.value = getErrorMessage(error, '高德地图加载失败')
    }
  }

  async function renderOverviewMap() {
    await renderTravelMap('overview')
  }

  async function renderRouteMap() {
    await renderTravelMap('route')
  }

  /** 销毁两图实例（组件卸载时调用） */
  function destroyMaps() {
    overviewMapInstance?.destroy()
    overviewMapInstance = null
    overviewMapContainer = null
    routeMapInstance?.destroy()
    routeMapInstance = null
    routeMapContainer = null
  }

  /** 聚焦到指定点位（行程列表点击导航时用） */
  function focusPoint(
    kind: 'overview' | 'route',
    target: { longitude: number; latitude: number },
  ) {
    const mapInstance =
      kind === 'overview' ? overviewMapInstance : routeMapInstance
    if (!mapInstance) {
      return
    }

    mapInstance.setZoomAndCenter(14, [target.longitude, target.latitude])
  }

  return {
    overviewMapRef,
    routeMapRef,
    overviewMapMessage,
    routeMapMessage,
    renderOverviewMap,
    renderRouteMap,
    destroyMaps,
    focusPoint,
  }
}
