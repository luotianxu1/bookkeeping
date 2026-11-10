<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { ECharts, EChartsCoreOption } from 'echarts'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'
import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import { useFinanceFamilyView } from '@/business/composables/useFinanceFamilyView'
import { useTheme } from '@/business/utils/theme'
import {
  getLatestAssetAccountSnapshots,
  getAssetTrend,
  type AssetAccountSnapshot,
  type AssetTrend,
  type AssetTrendPoint,
  type AssetTrendRange,
} from '@/business/api/modules/finance'
import { getErrorMessage } from '@/business/api/request'
import {
  buildRangeDetailPoint,
  getRangeDetailChangeAmount,
  getTodayCompareAmount,
  getTrendYAxisBounds,
  mergeAssetTrendResults,
  mergeLatestAssetSnapshots,
  type RangeDetailPoint,
  type SnapshotAccountItemView,
} from './trendMerge'
import {
  formatAxisMoney,
  formatCurrency,
  formatDateTime,
  formatSignedCurrency,
  getAllocationDotStyle,
  getAllocationTrackStyle,
} from './trendFormat'

type TrendRangeLabel = '近7日' | '近30日' | '近60日' | '年内' | '全部'

const route = useRoute()

const rangeOptions: TrendRangeLabel[] = [
  '近7日',
  '近30日',
  '近60日',
  '年内',
  '全部',
]
const rangeMap: Record<TrendRangeLabel, AssetTrendRange> = {
  近7日: '7d',
  近30日: '30d',
  近60日: '60d',
  年内: 'ytd',
  全部: 'all',
}
const reverseRangeMap: Record<AssetTrendRange, TrendRangeLabel> = {
  '7d': '近7日',
  '30d': '近30日',
  '60d': '近60日',
  ytd: '年内',
  all: '全部',
}

const activeRange = ref<TrendRangeLabel>('近7日')
const trend = ref<AssetTrend | null>(null)
const yesterdaySnapshots = ref<SnapshotAccountItemView[]>([])
const yesterdaySnapshotDate = ref('')
const yesterdaySnapshotCurrentTotal = ref<number | null>(null)
const yesterdaySnapshotChangeAmount = ref<number | null>(null)
const isLoading = ref(true)
const pageError = ref('')
const snapshotError = ref('')
const chartRef = ref<HTMLDivElement | null>(null)
const { isDark } = useTheme()
const requestSerial = ref(0)
const isInitialized = ref(false)

let echartsLib: typeof import('@/business/utils/echarts') | null = null
let chartIns: ECharts | null = null

const {
  currentUser,
  familyView,
  familyViewOptions,
  selectedFamilyView,
  canSwitchFamilyView,
  isReadOnlyFamilyView,
  selectedViewerUserIds,
  viewerNameByUserId,
  loadFamilyMembers,
} = useFinanceFamilyView()

const accountId = computed(() => {
  const raw = route.query.accountId
  const normalized = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(normalized)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
})
const showFamilySwitch = computed(
  () => !accountId.value && canSwitchFamilyView.value,
)
const effectiveUserIds = computed(() => {
  if (accountId.value) {
    return currentUser.value ? [currentUser.value.id] : []
  }
  return selectedViewerUserIds.value
})
const familyViewHint = computed(() => {
  if (!showFamilySwitch.value || !isReadOnlyFamilyView.value) {
    return ''
  }

  return selectedFamilyView.value.kind === 'total'
    ? '当前为家庭总计视角，可查看全家资产趋势。'
    : `当前查看 ${selectedFamilyView.value.label} 的资产趋势。`
})
const familyViewSelectOptions = computed<CommonSelectOption[]>(() =>
  familyViewOptions.value.map((option) => ({
    label: option.label,
    value: option.value,
  })),
)
const requestKey = computed(
  () =>
    `${showFamilySwitch.value ? selectedFamilyView.value.value : 'self'}:${accountId.value ?? 'all'}:${activeRange.value}`,
)

const backTo = computed(() =>
  accountId.value
    ? `/finance/accounts/investment/${accountId.value}`
    : '/finance',
)
const backLabel = computed(() =>
  accountId.value ? '返回投资账户' : '返回财务首页',
)
const showYesterdaySnapshotSection = computed(() => !accountId.value)

const totalAssets = computed(() => formatCurrency(trend.value?.totalAssets))
const latestTrendPoints = computed(() => trend.value?.trendPoints ?? [])
const previousPointLabel = computed(() => {
  const range = (trend.value?.range ?? rangeMap[activeRange.value]) as
    | AssetTrendRange
    | string
  if (range === '7d' || range === '30d' || range === '60d') return '较上一天'
  if (range === 'ytd') return '较上月'
  if (range === 'all') return '较上年'
  return '较上期'
})
const previousPointAmount = computed<number | null>(() => {
  const points = latestTrendPoints.value
  if (points.length < 2) {
    return null
  }
  const currentValue = Number(points[points.length - 1]?.value ?? 0)
  const previousValue = Number(points[points.length - 2]?.value ?? 0)
  return currentValue - previousValue
})
const previousPointAmountText = computed(() =>
  previousPointAmount.value === null
    ? '--'
    : formatCurrency(previousPointAmount.value),
)
const previousPointRateText = computed(() => {
  const points = latestTrendPoints.value
  if (points.length < 2) {
    return '--'
  }
  const currentValue = Number(points[points.length - 1]?.value ?? 0)
  const previousValue = Number(points[points.length - 2]?.value ?? 0)
  if (previousValue <= 0) {
    return '--'
  }
  const rate = ((currentValue - previousValue) / previousValue) * 100
  return `${rate.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}%`
})
const hasTrendPoints = computed(
  () => (trend.value?.trendPoints.length ?? 0) > 0,
)
const rangeDateText = computed(() => {
  if (!trend.value?.startDate || !trend.value?.endDate) {
    return '--'
  }
  return `${trend.value.startDate} 至 ${trend.value.endDate}`
})
const syncText = computed(() => {
  if (!trend.value?.lastSyncedAt) {
    return `统计区间 ${rangeDateText.value}`
  }
  return `更新于 ${formatDateTime(trend.value.lastSyncedAt)}`
})
const allocations = computed(() => trend.value?.allocations ?? [])
const yesterdaySnapshotDateText = computed(() =>
  yesterdaySnapshotDate.value
    ? `${yesterdaySnapshotDate.value} 快照`
    : '昨日快照',
)
const visibleYesterdaySnapshots = computed(() =>
  yesterdaySnapshots.value.filter(
    (item) => Math.abs(Number(item.changeAmount ?? 0)) > 0,
  ),
)
const yesterdaySnapshotCurrentTotalText = computed(() =>
  yesterdaySnapshotCurrentTotal.value === null
    ? '--'
    : formatCurrency(yesterdaySnapshotCurrentTotal.value),
)
const yesterdaySnapshotChangeAmountText = computed(() =>
  yesterdaySnapshotChangeAmount.value === null
    ? '--'
    : formatCurrency(yesterdaySnapshotChangeAmount.value),
)
const trendRangeKey = computed<AssetTrendRange>(() => {
  const range = trend.value?.range
  return range === '7d' ||
    range === '30d' ||
    range === '60d' ||
    range === 'ytd' ||
    range === 'all'
    ? range
    : rangeMap[activeRange.value]
})
const rangeDetailSummaryText = computed(() => {
  if (trendRangeKey.value === '7d') return '7天总资产'
  if (trendRangeKey.value === '30d') return '30天总资产'
  if (trendRangeKey.value === '60d') return '60天总资产'
  if (trendRangeKey.value === 'ytd') return '12个月总资产'
  return '按年总资产'
})
const rangeDetailCompareText = computed(() => {
  if (
    trendRangeKey.value === '7d' ||
    trendRangeKey.value === '30d' ||
    trendRangeKey.value === '60d'
  )
    return '较上一天'
  if (trendRangeKey.value === 'ytd') return '较上月'
  return '较上年'
})
const rangeDetailPoints = computed<RangeDetailPoint[]>(() => {
  const points = latestTrendPoints.value

  if (trendRangeKey.value !== 'ytd') {
    return points
      .map((item, index) =>
        buildRangeDetailPoint(
          item,
          points[index - 1],
          index === 0 ? trend.value?.previousPeriodValue : null,
        ),
      )
      .reverse()
  }

  const pointMap = new Map<string, AssetTrendPoint>(
    points.map((item) => [item.label, item]),
  )
  const endMonth = trend.value?.endDate
    ? new Date(`${trend.value.endDate}T00:00:00`).getMonth() + 1
    : new Date().getMonth() + 1

  return Array.from({ length: endMonth }, (_, index) => {
    const label = `${index + 1}月`
    const matchedPoint = pointMap.get(label)
    const previousPoint = index > 0 ? pointMap.get(`${index}月`) : undefined
    return {
      key: matchedPoint?.key ?? `ytd-${index + 1}`,
      label,
      value: matchedPoint ? Number(matchedPoint.value ?? 0) : null,
      changeAmount: matchedPoint
        ? getRangeDetailChangeAmount(
            matchedPoint,
            previousPoint,
            index === 0 ? trend.value?.previousPeriodValue : null,
          )
        : null,
    }
  })
    .filter((item) => item.value !== null)
    .reverse()
})

const chartOption = computed<EChartsCoreOption>(() => {
  const points = trend.value?.trendPoints ?? []
  const pointValues = points.map((item) => Number(item.value ?? 0))
  const yAxisBounds = getTrendYAxisBounds(pointValues)
  const yAxisRange = Math.max(
    Number(yAxisBounds.max ?? 0) - Number(yAxisBounds.min ?? 0),
    0,
  )
  const isSinglePoint = points.length === 1
  const rootStyle = getComputedStyle(document.documentElement)
  const axisText = rootStyle.getPropertyValue('--color-chart-axis').trim()
  const axisLine = rootStyle
    .getPropertyValue('--color-chart-axis-strong')
    .trim()
  const splitLine = rootStyle.getPropertyValue('--color-bg-soft-2').trim()
  const areaStart = rootStyle
    .getPropertyValue('--color-chart-brand-area-start')
    .trim()
  const areaEnd = rootStyle
    .getPropertyValue('--color-chart-brand-area-end')
    .trim()
  const tooltipBg = rootStyle
    .getPropertyValue('--color-chart-tooltip-bg')
    .trim()
  const tooltipBorder = rootStyle
    .getPropertyValue('--color-chart-tooltip-border')
    .trim()
  const tooltipText = rootStyle
    .getPropertyValue('--color-chart-tooltip-text')
    .trim()
  const brandColor = rootStyle.getPropertyValue('--color-brand').trim()
  const positiveColor = rootStyle.getPropertyValue('--color-danger').trim()
  const negativeColor = rootStyle.getPropertyValue('--color-success').trim()

  return {
    animation: false,
    grid: { left: 4, right: 8, top: 14, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipText },
      formatter: (params: any) => {
        const items = Array.isArray(params) ? params : [params]
        const item = items[0]
        const dataIndex = Number(item?.dataIndex)
        const point = Number.isInteger(dataIndex)
          ? points[dataIndex]
          : undefined
        if (!point) {
          return '--'
        }
        const changeAmount = getRangeDetailChangeAmount(
          point,
          points[dataIndex - 1],
          dataIndex === 0 ? trend.value?.previousPeriodValue : null,
        )
        const todayCompareAmount = getTodayCompareAmount(
          point,
          trend.value?.totalAssets,
        )
        const dateLabel = item?.axisValueLabel || item?.axisValue || point.label
        const changeColor =
          changeAmount === null || changeAmount === 0
            ? tooltipText
            : changeAmount > 0
              ? positiveColor
              : negativeColor
        const todayCompareColor =
          todayCompareAmount === null || todayCompareAmount === 0
            ? tooltipText
            : todayCompareAmount > 0
              ? positiveColor
              : negativeColor
        return [
          dateLabel,
          `${item?.marker || ''}总资产 ¥ ${formatCurrency(Number(point.value ?? 0))}`,
          `${rangeDetailCompareText.value} <span style="color:${changeColor};font-weight:700">${formatSignedCurrency(changeAmount)}</span>`,
          `今日较该时间 <span style="color:${todayCompareColor};font-weight:700">${formatSignedCurrency(todayCompareAmount)}</span>`,
        ].join('<br/>')
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((item) => item.label),
      axisLine: { lineStyle: { color: axisLine } },
      axisTick: { show: false },
      axisLabel: {
        color: axisText,
        fontSize: 11,
        interval: points.length > 12 ? 'auto' : 0,
        hideOverlap: false,
      },
    },
    yAxis: {
      type: 'value',
      scale: true,
      min: yAxisBounds.min,
      max: yAxisBounds.max,
      splitNumber: 4,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: axisText,
        fontSize: 11,
        formatter: (value: number) => formatAxisMoney(value, yAxisRange),
      },
      splitLine: { lineStyle: { color: splitLine } },
    },
    series: [
      {
        type: 'line',
        smooth: !isSinglePoint,
        smoothMonotone: 'x',
        showSymbol: points.length <= 8,
        symbol: 'circle',
        symbolSize: isSinglePoint ? 8 : 5,
        data: pointValues,
        lineStyle: {
          width: 2.5,
          color: brandColor,
          cap: 'round',
          join: 'round',
          shadowBlur: 10,
          shadowColor: 'rgba(29, 78, 216, 0.22)',
          shadowOffsetY: 4,
        },
        itemStyle: { color: brandColor },
        emphasis: {
          focus: 'series',
          scale: true,
        },
        label: isSinglePoint
          ? {
              show: true,
              position: 'top',
              color: tooltipText,
              fontSize: 12,
              fontWeight: 700,
              formatter: ({ value }: { value: number | string }) =>
                `¥ ${formatCurrency(Number(value ?? 0))}`,
            }
          : undefined,
        areaStyle: {
          opacity: 0.16,
          shadowBlur: 18,
          shadowColor: 'rgba(29, 78, 216, 0.08)',
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: areaStart },
              { offset: 1, color: areaEnd },
            ],
          },
        },
      },
    ],
  }
})

watch(requestKey, () => {
  if (!isInitialized.value) {
    return
  }
  void loadTrend()
})

watch(
  [trend, isDark],
  () => {
    renderChart()
  },
  { deep: true },
)

async function ensureEcharts() {
  if (!echartsLib) {
    echartsLib = await import('@/business/utils/echarts')
  }
  return echartsLib
}

function renderChart() {
  if (!chartRef.value || !echartsLib || !hasTrendPoints.value) {
    chartIns?.dispose()
    chartIns = null
    return
  }

  if (!chartIns) {
    chartIns = echartsLib.init(chartRef.value)
  }

  chartIns.setOption(chartOption.value, true)
}

function onResize() {
  chartIns?.resize()
}

onMounted(async () => {
  await ensureEcharts()
  await loadFamilyMembers()
  isInitialized.value = true
  await loadTrend()
  renderChart()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chartIns?.dispose()
  chartIns = null
})

async function loadTrend() {
  if (effectiveUserIds.value.length === 0) {
    pageError.value = '请先登录后查看资产趋势'
    trend.value = null
    resetYesterdaySnapshots()
    isLoading.value = false
    return
  }

  const currentRequest = requestSerial.value + 1
  requestSerial.value = currentRequest
  isLoading.value = true
  pageError.value = ''

  try {
    const [results, snapshotResults] = await Promise.all([
      Promise.all(
        effectiveUserIds.value.map((userId) =>
          getAssetTrend({
            userId,
            accountId: accountId.value,
            range: rangeMap[activeRange.value],
          }),
        ),
      ),
      showYesterdaySnapshotSection.value
        ? Promise.allSettled(
            effectiveUserIds.value.map((userId) =>
              getLatestAssetAccountSnapshots({ userId }),
            ),
          )
        : Promise.resolve([]),
    ])

    if (currentRequest !== requestSerial.value) {
      return
    }

    const merged = mergeAssetTrendResults(results, rangeMap[activeRange.value])
    trend.value = merged
    const normalizedRange =
      reverseRangeMap[(merged.range as AssetTrendRange) ?? 'ytd']
    if (normalizedRange) {
      activeRange.value = normalizedRange
    }
    applyYesterdaySnapshots(snapshotResults)
  } catch (error) {
    if (currentRequest !== requestSerial.value) {
      return
    }
    trend.value = null
    pageError.value = getErrorMessage(error, '资产趋势加载失败')
    resetYesterdaySnapshots()
  } finally {
    if (currentRequest === requestSerial.value) {
      isLoading.value = false
      await nextTick()
      renderChart()
    }
  }
}

function applyYesterdaySnapshots(
  results: PromiseSettledResult<AssetAccountSnapshot>[],
) {
  if (!showYesterdaySnapshotSection.value) {
    resetYesterdaySnapshots()
    return
  }

  const fulfilled = results
    .filter(
      (item): item is PromiseFulfilledResult<AssetAccountSnapshot> =>
        item.status === 'fulfilled',
    )
    .map((item) => item.value)

  if (fulfilled.length === 0) {
    yesterdaySnapshots.value = []
    yesterdaySnapshotDate.value = ''
    snapshotError.value = '暂无昨日账户快照'
    return
  }

  const merged = mergeLatestAssetSnapshots(fulfilled, viewerNameByUserId.value)
  yesterdaySnapshots.value = merged.accounts
  yesterdaySnapshotDate.value = merged.snapshotDate
  yesterdaySnapshotCurrentTotal.value = merged.currentTotalAssets
  yesterdaySnapshotChangeAmount.value = merged.changeAmount
  snapshotError.value =
    merged.accounts.length > 0 ? '所有账户与今日一致' : '暂无昨日账户快照'
}

function resetYesterdaySnapshots() {
  yesterdaySnapshots.value = []
  yesterdaySnapshotDate.value = ''
  yesterdaySnapshotCurrentTotal.value = null
  yesterdaySnapshotChangeAmount.value = null
  snapshotError.value = ''
}
</script>

<template>
  <section class="pc-page investment-trend-page" aria-label="资产趋势">
    <PageHeader title="资产趋势" :back-to="backTo" :back-label="backLabel">
      <template #right>
        <div v-if="showFamilySwitch" class="trend-family-switch">
          <CommonSelect
            v-model="familyView"
            label="切换家庭成员资产趋势视角"
            :options="familyViewSelectOptions"
          />
        </div>
      </template>
    </PageHeader>
    <p v-if="familyViewHint" class="trend-view-hint">{{ familyViewHint }}</p>
    <p
      v-if="pageError"
      class="investment-trend-message investment-trend-message-error"
    >
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />
    <template v-else-if="trend">
      <SegmentedControl
        v-model="activeRange"
        :options="rangeOptions"
        label="趋势区间选择"
        variant="surface"
      />
      <el-card
        class="pc-record-card investment-trend-summary-card"
        aria-label="资产总览"
        shadow="never"
      >
        <div class="investment-trend-summary-top">
          <div class="investment-trend-summary-main">
            <AmountText tag="strong" tone="inherit" :value="totalAssets" />
            <span>{{ syncText }}</span>
          </div>
          <div class="investment-trend-summary-side">
            <span>{{ previousPointLabel }}</span>
            <AmountText
              tag="strong"
              class="summary-profit"
              :value="previousPointAmountText"
              show-sign
              show-unit
            />
            <AmountText
              tag="span"
              class="summary-rate"
              :value="previousPointRateText"
              show-sign
            />
          </div>
        </div>
      </el-card>
      <el-card
        class="pc-record-card investment-trend-card"
        aria-label="总资产走势"
        shadow="never"
      >
        <header class="investment-trend-card-head">
          <div><strong>总资产走势</strong></div>
        </header>
        <div
          v-if="hasTrendPoints"
          ref="chartRef"
          class="pc-chart investment-trend-chart"
        ></div>
        <p v-else class="investment-trend-empty">
          当前区间暂无可展示的资产走势
        </p>
      </el-card>
      <el-card
        class="pc-record-card investment-trend-card"
        aria-label="资产分布"
        shadow="never"
      >
        <header class="investment-trend-card-head">
          <div><strong>资产分布</strong></div>
        </header>
        <div v-if="allocations.length > 0" class="pc-grid allocation-list">
          <el-card
            v-for="item in allocations"
            :key="`${item.accountTypeCode}-${item.label}`"
            class="pc-record-card allocation-item"
            shadow="never"
          >
            <div class="allocation-row">
              <div class="allocation-title">
                <span
                  class="allocation-dot"
                  :style="getAllocationDotStyle(item, isDark)"
                ></span>
                <strong>{{ item.label }}</strong>
              </div>
              <div class="allocation-side">
                <AmountText
                  tag="span"
                  tone="inherit"
                  :value="formatCurrency(item.balance)"
                />
                <span>{{ `${Number(item.percent ?? 0).toFixed(2)}%` }}</span>
              </div>
            </div>
            <div
              class="allocation-track"
              :style="getAllocationTrackStyle(item, isDark)"
            >
              <span class="allocation-fill"></span>
            </div>
          </el-card>
        </div>
        <p v-else class="investment-trend-empty">暂无可展示的资产分布</p>
      </el-card>
      <el-card
        class="pc-record-card investment-trend-card"
        aria-label="区间资产明细"
        shadow="never"
      >
        <header class="investment-trend-card-head">
          <div><strong>区间资产明细</strong></div>
          <span class="range-detail-side-text">
            {{ rangeDetailSummaryText }} · {{ rangeDetailCompareText }}
          </span>
        </header>
        <div
          v-if="rangeDetailPoints.length > 0"
          class="pc-grid range-detail-list"
        >
          <el-card
            v-for="item in rangeDetailPoints"
            :key="item.key"
            class="pc-record-card range-detail-item"
            shadow="never"
          >
            <span class="range-detail-label">{{ item.label }}</span>
            <div class="range-detail-main">
              <AmountText
                v-if="item.value !== null"
                tag="strong"
                tone="inherit"
                class="range-detail-value"
                :value="formatCurrency(item.value)"
                show-unit
              />
              <strong
                v-else
                class="range-detail-value range-detail-value--empty"
              >
                --
              </strong>
              <div class="range-detail-change">
                <span>{{ rangeDetailCompareText }}</span>
                <AmountText
                  tag="span"
                  class="range-detail-change-value"
                  :value="
                    item.changeAmount === null
                      ? '--'
                      : formatCurrency(item.changeAmount)
                  "
                  show-sign
                  show-unit
                />
              </div>
            </div>
          </el-card>
        </div>
        <p v-else class="investment-trend-empty">
          当前区间暂无可展示的资产数据
        </p>
      </el-card>
      <el-card
        v-if="showYesterdaySnapshotSection"
        class="pc-record-card investment-trend-card"
        aria-label="昨日账户快照"
        shadow="never"
      >
        <header class="investment-trend-card-head">
          <div>
            <strong>昨日账户快照</strong>
            <p class="snapshot-total-summary">
              今日总资产
              <AmountText
                tag="span"
                tone="inherit"
                :value="yesterdaySnapshotCurrentTotalText"
                show-unit
              />
              ，
              <AmountText
                tag="span"
                :value="yesterdaySnapshotChangeAmountText"
                show-sign
                show-unit
              />
            </p>
          </div>
          <span class="range-detail-side-text">
            {{ yesterdaySnapshotDateText }}
          </span>
        </header>
        <div
          v-if="visibleYesterdaySnapshots.length > 0"
          class="pc-grid snapshot-account-list"
        >
          <el-card
            v-for="item in visibleYesterdaySnapshots"
            :key="`${item.userId}-${item.accountId}`"
            class="pc-record-card snapshot-account-item"
            shadow="never"
          >
            <div class="snapshot-account-main">
              <div class="snapshot-account-title">
                <strong>{{ item.accountName }}</strong>
                <span class="snapshot-account-type">
                  {{ item.accountTypeLabel }}
                </span>
                <span v-if="item.ownerLabel" class="snapshot-account-owner">
                  {{ item.ownerLabel }}
                </span>
              </div>
              <div class="snapshot-account-compare">
                <span>今日</span>
                <AmountText
                  tag="span"
                  tone="inherit"
                  :value="formatCurrency(item.currentAssets)"
                  show-unit
                />
                <AmountText
                  tag="span"
                  class="snapshot-account-change"
                  :value="formatCurrency(item.changeAmount)"
                  show-sign
                  show-unit
                />
              </div>
            </div>
            <AmountText
              tag="strong"
              tone="inherit"
              class="snapshot-account-value"
              :value="formatCurrency(item.totalAssets)"
              show-unit
            />
          </el-card>
        </div>
        <p v-else class="investment-trend-empty">
          {{ snapshotError || '暂无昨日账户快照' }}
        </p>
      </el-card>
    </template>
  </section>
</template>
