import type {
  AssetAccountSnapshot,
  AssetAccountSnapshotItem,
  AssetTrend,
  AssetTrendAllocation,
  AssetTrendContributor,
  AssetTrendPoint,
  AssetTrendRange,
} from '@/business/api/modules/finance'

export interface RangeDetailPoint {
  key: string
  label: string
  value: number | null
  changeAmount: number | null
}

export interface TrendYAxisBounds {
  min?: number
  max?: number
}

export interface SnapshotAccountItemView extends AssetAccountSnapshotItem {
  ownerLabel?: string
}

export function mergeAssetTrendResults(
  results: AssetTrend[],
  fallbackRange: AssetTrendRange,
) {
  if (results.length === 1) {
    return results[0]
  }

  const first = results[0]
  const pointOrder: string[] = []
  const trendPointMap = new Map<string, AssetTrendPoint>()
  const allocationOrder: string[] = []
  const allocationMap = new Map<string, AssetTrendAllocation>()
  const contributorMap = new Map<string, AssetTrendContributor>()

  let totalAssets = 0
  let cumulativeProfit = 0
  let periodChangeAmount = 0
  let startDate = first?.startDate ?? ''
  let endDate = first?.endDate ?? ''
  let lastSyncedAt = first?.lastSyncedAt ?? null

  results.forEach((result) => {
    totalAssets += Number(result.totalAssets ?? 0)
    cumulativeProfit += Number(result.cumulativeProfit ?? 0)
    periodChangeAmount += Number(result.periodChangeAmount ?? 0)
    startDate = pickEarlierDate(startDate, result.startDate)
    endDate = pickLaterDate(endDate, result.endDate)
    lastSyncedAt = pickLaterDateTime(lastSyncedAt, result.lastSyncedAt)

    result.trendPoints.forEach((item) => {
      if (!trendPointMap.has(item.key)) {
        pointOrder.push(item.key)
        trendPointMap.set(item.key, {
          key: item.key,
          label: item.label,
          value: 0,
        })
      }
      const current = trendPointMap.get(item.key)!
      current.value += Number(item.value ?? 0)
    })

    result.allocations.forEach((item) => {
      const key = `${item.accountTypeCode ?? 'unknown'}:${item.label}`
      if (!allocationMap.has(key)) {
        allocationOrder.push(key)
        allocationMap.set(key, {
          accountTypeCode: item.accountTypeCode,
          label: item.label,
          balance: 0,
          percent: 0,
        })
      }
      const current = allocationMap.get(key)!
      current.balance += Number(item.balance ?? 0)
    })

    result.contributors.forEach((item) => {
      const key = `${item.accountTypeCode ?? 'unknown'}:${item.accountName}`
      const current = contributorMap.get(key) ?? {
        accountId: item.accountId,
        accountName: item.accountName,
        accountTypeCode: item.accountTypeCode,
        accountTypeLabel: item.accountTypeLabel,
        contributionAmount: 0,
        contributionRate: 0,
      }
      current.contributionAmount += Number(item.contributionAmount ?? 0)
      contributorMap.set(key, current)
    })
  })

  const mergedTrendPoints = pointOrder
    .map((key) => trendPointMap.get(key))
    .filter((item): item is AssetTrendPoint => Boolean(item))

  const mergedAllocations = allocationOrder
    .map((key) => allocationMap.get(key))
    .filter((item): item is AssetTrendAllocation => Boolean(item))
    .map((item) => ({
      ...item,
      percent:
        totalAssets > 0 ? (Number(item.balance ?? 0) / totalAssets) * 100 : 0,
    }))

  const mergedContributors = Array.from(contributorMap.values())
    .map((item) => ({
      ...item,
      contributionRate:
        totalAssets > 0
          ? (Number(item.contributionAmount ?? 0) / totalAssets) * 100
          : 0,
    }))
    .sort(
      (left, right) =>
        Number(right.contributionAmount ?? 0) -
        Number(left.contributionAmount ?? 0),
    )

  const hasCompletePreviousPeriodValue = results.every(
    (result) =>
      result.previousPeriodValue !== null &&
      result.previousPeriodValue !== undefined,
  )
  const previousPeriodValue = hasCompletePreviousPeriodValue
    ? results.reduce(
        (total, result) => total + Number(result.previousPeriodValue),
        0,
      )
    : null

  const previousTotal =
    mergedTrendPoints.length > 1
      ? Number(mergedTrendPoints[mergedTrendPoints.length - 2]?.value ?? 0)
      : 0
  const periodChangeRate =
    previousTotal > 0 ? (periodChangeAmount / previousTotal) * 100 : 0
  const cumulativeProfitRate =
    totalAssets > 0 ? (cumulativeProfit / totalAssets) * 100 : 0

  return {
    userId: first?.userId ?? 0,
    accountId: first?.accountId ?? null,
    range: first?.range ?? fallbackRange,
    rangeLabel: first?.rangeLabel ?? '',
    startDate,
    endDate,
    totalAssets,
    cumulativeProfit,
    cumulativeProfitRate,
    periodChangeAmount,
    periodChangeRate,
    previousPeriodValue,
    lastSyncedAt,
    trendPoints: mergedTrendPoints,
    allocations: mergedAllocations,
    contributors: mergedContributors,
  } satisfies AssetTrend
}

export function mergeLatestAssetSnapshots(
  results: AssetAccountSnapshot[],
  viewerNameByUserId: Map<number, string>,
) {
  const accounts: SnapshotAccountItemView[] = []
  let snapshotDate = ''
  let currentTotalAssets = 0
  let changeAmount = 0

  results.forEach((result) => {
    if (!snapshotDate && result.snapshotDate) {
      snapshotDate = result.snapshotDate
    }
    currentTotalAssets += Number(result.currentTotalAssets ?? 0)
    changeAmount += Number(result.changeAmount ?? 0)

    const ownerLabel =
      results.length > 1 ? (viewerNameByUserId.get(result.userId) ?? '') : ''

    result.accounts.forEach((item) => {
      accounts.push({
        ...item,
        ownerLabel,
      })
    })
  })

  return {
    snapshotDate,
    currentTotalAssets,
    changeAmount,
    accounts,
  }
}

export function buildRangeDetailPoint(
  item: AssetTrendPoint,
  previous?: AssetTrendPoint,
  previousPeriodValue?: number | null,
) {
  return {
    key: item.key,
    label: item.label,
    value: Number(item.value ?? 0),
    changeAmount: getRangeDetailChangeAmount(
      item,
      previous,
      previousPeriodValue,
    ),
  } satisfies RangeDetailPoint
}

export function getRangeDetailChangeAmount(
  item: AssetTrendPoint,
  previous?: AssetTrendPoint,
  previousPeriodValue?: number | null,
) {
  const comparisonValue = previous
    ? Number(previous.value ?? 0)
    : previousPeriodValue === null || previousPeriodValue === undefined
      ? null
      : Number(previousPeriodValue)
  if (comparisonValue === null || !Number.isFinite(comparisonValue)) {
    return null
  }
  const currentValue = Number(item.value ?? 0)
  return currentValue - comparisonValue
}

export function getTodayCompareAmount(
  item: AssetTrendPoint,
  todayTotalAssets?: number | null,
) {
  const todayValue = Number(todayTotalAssets)
  if (!Number.isFinite(todayValue)) {
    return null
  }
  return todayValue - Number(item.value ?? 0)
}

export function pickEarlierDate(current: string, candidate?: string | null) {
  if (!candidate) {
    return current
  }
  if (!current) {
    return candidate
  }
  return new Date(candidate).getTime() < new Date(current).getTime()
    ? candidate
    : current
}

export function pickLaterDate(current: string, candidate?: string | null) {
  if (!candidate) {
    return current
  }
  if (!current) {
    return candidate
  }
  return new Date(candidate).getTime() > new Date(current).getTime()
    ? candidate
    : current
}

export function pickLaterDateTime(
  current?: string | null,
  candidate?: string | null,
) {
  if (!candidate) {
    return current ?? null
  }
  if (!current) {
    return candidate
  }
  return new Date(candidate).getTime() > new Date(current).getTime()
    ? candidate
    : current
}

export function getTrendYAxisBounds(values: number[]) {
  const normalized = values.filter((value) => Number.isFinite(value))
  if (normalized.length === 0) {
    return {} satisfies TrendYAxisBounds
  }

  const minValue = Math.min(...normalized)
  const maxValue = Math.max(...normalized)
  const baseRange = maxValue - minValue

  if (baseRange === 0) {
    const padding = Math.max(Math.abs(minValue) * 0.02, 1)
    return {
      min: minValue,
      max: maxValue + padding,
    } satisfies TrendYAxisBounds
  }

  const center = (minValue + maxValue) / 2
  const basePadding = Math.max(baseRange * 0.12, 1)
  const valuePadding = Math.abs(center) * 0.002
  const padding = Math.max(basePadding, valuePadding)

  return {
    min: minValue,
    max: maxValue + padding,
  } satisfies TrendYAxisBounds
}
