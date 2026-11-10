// 行程点位的纯计算：前晚住宿延续、概览时间线条目构建。
import {
  itineraryDescription,
  routeTransferSummary,
  typeTone,
  type TravelTone,
} from './travelFormat'
import type { TransportMode, NavigationPoint } from './travelNavigation'
import type { TravelPlanDetail } from '@/business/api/modules/tool'

type DayEntry = TravelPlanDetail['days'][number]
type ItineraryItem = DayEntry['itineraries'][number]

export function prependPreviousAccommodation(
  currentItems: DayEntry['itineraries'],
  orderedDays: DayEntry[],
  dayIndex: number,
): ItineraryItem[] {
  const previousDay = orderedDays.find((item) => item.dayIndex === dayIndex - 1)
  const previousLastItem =
    previousDay?.itineraries?.[previousDay.itineraries.length - 1]

  if (!previousLastItem || previousLastItem.type !== 'accommodation') {
    return currentItems
  }

  const alreadyStartsWithSameAccommodation = currentItems.some(
    (item, index) => {
      if (index !== 0) {
        return false
      }
      return (
        item.id === previousLastItem.id ||
        (item.type === 'accommodation' &&
          item.title === previousLastItem.title &&
          item.poiId === previousLastItem.poiId)
      )
    },
  )

  if (alreadyStartsWithSameAccommodation) {
    return currentItems
  }

  return [
    {
      ...previousLastItem,
      id: -previousLastItem.id,
      remark:
        previousLastItem.remark ||
        previousLastItem.address ||
        previousLastItem.poiName ||
        '延续前一晚住宿',
    },
    ...currentItems,
  ]
}

export function buildOverviewEntries(itineraries: DayEntry['itineraries']) {
  return itineraries.flatMap((item, index) => {
    const nextItem = itineraries[index + 1]
    const tone = typeTone(item.type)
    const entries: Array<
      | { kind: 'item'; tone: TravelTone; title: string; description: string }
      | {
          kind: 'transfer'
          text: string
          navigation?: {
            origin: NavigationPoint
            destination: NavigationPoint
            mode: TransportMode
          } | null
        }
    > = [
      {
        kind: 'item',
        tone,
        title: item.title,
        description: itineraryDescription(item),
      },
    ]

    if (nextItem) {
      const hasOrigin =
        typeof item.longitude === 'number' &&
        Number.isFinite(item.longitude) &&
        typeof item.latitude === 'number' &&
        Number.isFinite(item.latitude)
      const hasDestination =
        typeof nextItem.longitude === 'number' &&
        Number.isFinite(nextItem.longitude) &&
        typeof nextItem.latitude === 'number' &&
        Number.isFinite(nextItem.latitude)
      entries.push({
        kind: 'transfer',
        text: routeTransferSummary(nextItem),
        navigation:
          hasOrigin && hasDestination
            ? {
                origin: {
                  title: item.title,
                  longitude: item.longitude as number,
                  latitude: item.latitude as number,
                  poiId: item.poiId,
                  poiName: item.poiName,
                },
                destination: {
                  title: nextItem.title,
                  longitude: nextItem.longitude as number,
                  latitude: nextItem.latitude as number,
                  poiId: nextItem.poiId,
                  poiName: nextItem.poiName,
                },
                mode: (item.transportMode as TransportMode | null) || 'driving',
              }
            : null,
      })
    }

    return entries
  })
}
