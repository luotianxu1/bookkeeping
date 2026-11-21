<script setup lang="ts">
import PagePanel from '@/business/components/PagePanel.vue'

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { ECharts, EChartsCoreOption } from 'echarts'

import AmountText from '@/business/components/common/AmountText/index.vue'
import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import { useFinanceFamilyView } from '@/business/composables/useFinanceFamilyView'
import { useTheme } from '@/business/utils/theme'
import { readChartToken } from '@/business/utils/chart-theme'
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

const showYesterdaySnapshotSection = computed(() => !accountId.value)

const totalAssets = computed(() => formatCurrency(trend.value?.totalAssets))
const latestTrendPoints = computed(() => trend.value?.trendPoints ?? [])

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
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  })}%`
})
const hasTrendPoints = computed(
  () => (trend.value?.trendPoints?.length ?? 0) > 0,
)
const previousPointLabel = computed(() => {
  const range = (trend.value?.range ?? rangeMap[activeRange.value]) as
    | AssetTrendRange
    | string
  if (range === '7d' || range === '30d' || range === '60d') return '较上一天'
  if (range === 'ytd') return '较上月'
  if (range === 'all') return '较上年'
  return '较上期'
})
const rangeDateText = computed(() => {
  if (!trend.value?.startDate || !trend.value?.endDate) return '--'
  return `${trend.value.startDate} 至 ${trend.value.endDate}`
})
const syncText = computed(() => {
  if (!trend.value?.lastSyncedAt) return `统计区间 ${rangeDateText.value}`
  return `更新于 ${formatDateTime(trend.value.lastSyncedAt)}`
})
const familyViewHint = computed(() => {
  if (!showFamilySwitch.value || !isReadOnlyFamilyView.value) return ''
  return selectedFamilyView.value.kind === 'total'
    ? '当前为家庭总计视角，可查看全家资产趋势。'
    : `当前查看 ${selectedFamilyView.value.label} 的资产趋势。`
})

const allocations = computed(() => trend.value?.allocations ?? [])

const snapshotKeyword = ref('')
const visibleYesterdaySnapshots = computed(() => {
  const keyword = snapshotKeyword.value.trim().toLowerCase()
  return yesterdaySnapshots.value.filter((item) => {
    if (Math.abs(Number(item.changeAmount ?? 0)) <= 0) return false
    if (!keyword) return true
    return [item.accountName, item.accountTypeLabel, item.ownerLabel]
      .filter(Boolean)
      .some((text) => String(text).toLowerCase().includes(keyword))
  })
})

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
const rangeDetailSummaryText = computed(() => {
  if (trendRangeKey.value === '7d') return '7天总资产'
  if (trendRangeKey.value === '30d') return '30天总资产'
  if (trendRangeKey.value === '60d') return '60天总资产'
  if (trendRangeKey.value === 'ytd') return '12个月总资产'
  return '按年总资产'
})
const snapshotDateText = computed(() =>
  yesterdaySnapshotDate.value
    ? `${yesterdaySnapshotDate.value} 快照`
    : '昨日快照',
)
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
  // 图表取色统一走带兜底的 readChartToken，token 缺失时回退默认值，
  // 避免空字符串传给 echarts 渐变 addColorStop 直接抛 SyntaxError
  const axisText = readChartToken('--color-chart-axis', '#94a3b8')
  const axisLine = readChartToken('--color-chart-axis-strong', '#cbd5e1')
  const splitLine = readChartToken('--color-bg-soft-2', '#eef2f8')
  const areaStart = readChartToken(
    '--color-chart-brand-area-start',
    'rgba(29, 78, 216, 0.2)',
  )
  const areaEnd = readChartToken(
    '--color-chart-brand-area-end',
    'rgba(29, 78, 216, 0.02)',
  )
  const tooltipBg = readChartToken('--color-chart-tooltip-bg', '#ffffff')
  const tooltipBorder = readChartToken('--color-chart-tooltip-border', '#e2e8f0')
  const tooltipText = readChartToken('--color-chart-tooltip-text', '#0f172a')
  const brandColor = readChartToken('--color-brand', '#1d4ed8')
  const positiveColor = readChartToken('--color-danger', '#dc2626')
  const negativeColor = readChartToken('--color-success', '#16a34a')

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
  // 仅当有快照账户但全部账户昨日→今日无变动时提示，
  // 有变动账户时表格已展示对比，不再显示该提示
  const hasChangedAccount = merged.accounts.some(
    (item) => Math.abs(Number(item.changeAmount ?? 0)) > 0,
  )
  snapshotError.value =
    merged.accounts.length > 0 && !hasChangedAccount
      ? '所有账户与今日一致'
      : ''
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
  <section class="desktop-workspace trend-workspace">
    <PageHeader title="资产趋势">
      <template #right>
        <CommonSelect
          v-if="showFamilySwitch"
          v-model="familyView"
          label="家庭视角"
          :options="familyViewSelectOptions"
        />
      </template>
    </PageHeader>
    <p v-if="familyViewHint" class="trend-family-hint">{{ familyViewHint }}</p>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <template v-if="trend">
      <section class="trend-summary" aria-label="资产总览">
        <div class="trend-summary-main">
          <span class="trend-summary-label">总资产</span>
          <AmountText
            tag="strong"
            tone="inherit"
            class="trend-summary-value"
            :value="totalAssets"
            show-unit
          />
          <span class="trend-summary-meta">{{ syncText }}</span>
        </div>
        <div class="trend-summary-change">
          <span class="trend-summary-label">{{ previousPointLabel }}</span>
          <AmountText
            tag="strong"
            class="trend-change-value"
            :value="previousPointAmountText"
            show-sign
            show-unit
          />
          <AmountText
            tag="span"
            class="trend-change-rate"
            :value="previousPointRateText"
            show-sign
          />
        </div>
      </section>

      <div class="desktop-columns dashboard-columns trend-overview-columns">
        <PagePanel title="总资产走势">
          <template #actions>
            <SegmentedControl
              v-model="activeRange"
              :options="rangeOptions"
              label="趋势区间选择"
              variant="surface"
            />
          </template>
          <div
            v-if="hasTrendPoints"
            ref="chartRef"
            class="desktop-chart trend-chart"
          />
          <el-empty v-else description="当前区间暂无可展示的资产走势" />
        </PagePanel>

        <PagePanel title="资产分布">
          <div v-if="allocations.length > 0" class="trend-allocation-list">
            <article
              v-for="item in allocations"
              :key="`${item.accountTypeCode}-${item.label}`"
              class="trend-allocation-item"
            >
              <div class="trend-allocation-row">
                <div class="trend-allocation-name">
                  <span
                    class="trend-allocation-dot"
                    :style="getAllocationDotStyle(item, isDark)"
                  ></span>
                  <strong>{{ item.label }}</strong>
                </div>
                <div class="trend-allocation-values">
                  <AmountText
                    tone="neutral"
                    :value="formatCurrency(item.balance)"
                    show-unit
                  />
                  <span>{{ Number(item.percent ?? 0).toFixed(2) }}%</span>
                </div>
              </div>
              <div
                class="trend-allocation-track"
                :style="getAllocationTrackStyle(item, isDark)"
              >
                <span></span>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无可展示的资产分布" />
        </PagePanel>
      </div>

      <PagePanel title="区间资产明细">
        <template #actions>
          <span class="trend-panel-subtitle">
            {{ rangeDetailSummaryText }} · {{ rangeDetailCompareText }}
          </span>
        </template>
        <div v-if="rangeDetailPoints.length > 0" class="trend-range-grid">
          <article
            v-for="item in rangeDetailPoints"
            :key="item.key"
            class="trend-range-item"
          >
            <div class="trend-range-head">
              <span class="trend-range-date">{{ item.label }}</span>
              <span class="trend-range-index">{{ rangeDetailCompareText }}</span>
            </div>
            <div class="trend-range-value">
              <span class="trend-range-value-label">资产</span>
              <AmountText
                tag="strong"
                tone="inherit"
                :value="item.value === null ? '--' : formatCurrency(item.value)"
                show-unit
              />
            </div>
            <div class="trend-range-change">
              <span>变动</span>
              <AmountText
                :value="
                  item.changeAmount === null
                    ? '--'
                    : formatCurrency(item.changeAmount)
                "
                show-sign
                show-unit
              />
            </div>
          </article>
        </div>
        <el-empty v-else description="当前区间暂无可展示的资产数据" />
      </PagePanel>

      <PagePanel v-if="showYesterdaySnapshotSection" title="账户快照对比">
        <template #actions>
          <div class="trend-snapshot-actions">
            <el-input
              v-model="snapshotKeyword"
              class="trend-snapshot-search"
              placeholder="搜索账户 / 类型 / 成员"
              clearable
            />
            <span class="trend-panel-subtitle">{{ snapshotDateText }}</span>
          </div>
        </template>
        <p class="trend-snapshot-summary">
          今日总资产
          <AmountText
            tone="inherit"
            :value="
              yesterdaySnapshotCurrentTotal === null
                ? '--'
                : formatCurrency(yesterdaySnapshotCurrentTotal)
            "
            show-unit
          />，
          <AmountText
            :value="
              yesterdaySnapshotChangeAmount === null
                ? '--'
                : formatCurrency(yesterdaySnapshotChangeAmount)
            "
            show-sign
            show-unit
          />
        </p>
        <div v-if="visibleYesterdaySnapshots.length > 0" class="trend-snapshot-grid">
          <article
            v-for="item in visibleYesterdaySnapshots"
            :key="`${item.userId}-${item.accountId}`"
            class="trend-snapshot-item"
          >
            <div class="trend-snapshot-head">
              <strong>{{ item.accountName }}</strong>
              <div class="trend-snapshot-tags">
                <span>{{ item.accountTypeLabel }}</span>
                <span v-if="item.ownerLabel">{{ item.ownerLabel }}</span>
              </div>
            </div>
            <div class="trend-snapshot-values">
              <div class="trend-snapshot-value">
                <span>昨日资产</span>
                <AmountText
                  tone="inherit"
                  :value="formatCurrency(item.totalAssets)"
                  show-unit
                />
              </div>
              <div class="trend-snapshot-value">
                <span>今日资产</span>
                <AmountText
                  tone="inherit"
                  :value="formatCurrency(item.currentAssets)"
                  show-unit
                />
              </div>
            </div>
            <div class="trend-snapshot-change">
              <span>资产变动</span>
              <AmountText
                :value="formatCurrency(item.changeAmount)"
                show-sign
                show-unit
              />
            </div>
          </article>
        </div>
        <el-empty v-else :description="snapshotError || '暂无变动账户快照'" />
        <el-alert
          v-if="snapshotError && visibleYesterdaySnapshots.length > 0"
          :title="snapshotError"
          type="info"
          :closable="false"
          show-icon
        ></el-alert>
      </PagePanel>
    </template>
  </section>
</template>

<style scoped>
.trend-workspace {
  display: grid;
  gap: 14px;
}

.trend-family-hint {
  margin: -6px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.trend-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 20px 24px;
  border: 1px solid rgba(29, 78, 216, 0.12);
  border-radius: 16px;
  background:
    radial-gradient(circle at 88% -30%, rgba(59, 130, 246, 0.22), transparent 46%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(234, 242, 255, 0.92));
  box-shadow: 0 12px 30px rgba(87, 120, 190, 0.15);
}

.trend-summary-main,
.trend-summary-change {
  min-width: 0;
  display: grid;
}

.trend-summary-main {
  gap: 8px;
}

.trend-summary-label {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.trend-summary-value {
  font-size: 32px;
  line-height: 1.1;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.trend-summary-meta {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.trend-summary-change {
  justify-items: end;
  gap: 6px;
  min-width: 190px;
  padding: 12px 18px;
  border: 1px solid rgba(29, 78, 216, 0.1);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.7);
}

.trend-change-value {
  font-size: 18px;
  font-weight: 700;
}

.trend-change-rate {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-tertiary);
}

.trend-overview-columns {
  align-items: stretch;
}

.trend-chart {
  height: 280px;
}

.trend-panel-subtitle {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.trend-allocation-list {
  display: grid;
  gap: 16px;
}

.trend-allocation-item {
  display: grid;
  gap: 8px;
}

.trend-allocation-row,
.trend-allocation-name,
.trend-allocation-values {
  min-width: 0;
  display: flex;
  align-items: center;
}

.trend-allocation-row {
  justify-content: space-between;
  gap: 12px;
}

.trend-allocation-name {
  gap: 8px;
}

.trend-allocation-name strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.trend-allocation-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  border-radius: 50%;
}

.trend-allocation-values {
  justify-content: flex-end;
  gap: 10px;
  color: var(--color-text-tertiary);
  font-size: 12px;
  white-space: nowrap;
}

.trend-allocation-values > span:last-child {
  min-width: 52px;
  text-align: right;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.trend-allocation-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--allocation-track);
}

.trend-allocation-track > span {
  display: block;
  width: var(--allocation-width);
  height: 100%;
  border-radius: inherit;
  background: var(--allocation-fill);
}

.trend-range-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.trend-range-item {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  background: var(--color-bg-card);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.trend-range-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(87, 120, 190, 0.12);
}

.trend-range-head,
.trend-range-value,
.trend-range-change,
.trend-snapshot-change {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.trend-range-date {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 700;
}

.trend-range-index,
.trend-range-value-label,
.trend-range-change > span,
.trend-snapshot-change > span,
.trend-snapshot-value > span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.trend-range-value {
  align-items: baseline;
}

.trend-range-value strong {
  font-size: 16px;
  font-weight: 700;
}

.trend-snapshot-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.trend-snapshot-search {
  width: 220px;
}

.trend-snapshot-summary {
  margin: 0 0 14px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.trend-snapshot-summary .amount-text {
  font-weight: 700;
}

.trend-snapshot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
  gap: 12px;
}

.trend-snapshot-item {
  display: grid;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  background: var(--color-bg-card);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.trend-snapshot-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(87, 120, 190, 0.12);
}

.trend-snapshot-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.trend-snapshot-head strong {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 700;
}

.trend-snapshot-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.trend-snapshot-tags span {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 18px;
}

.trend-snapshot-values {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.trend-snapshot-value {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.trend-snapshot-value .amount-text {
  font-size: 15px;
  font-weight: 600;
}

.trend-snapshot-change {
  padding-top: 10px;
  border-top: 1px dashed var(--color-border-soft);
}

:global(html.dark) .trend-summary {
  border-color: rgba(96, 165, 250, 0.22);
  background:
    radial-gradient(circle at 88% -30%, rgba(59, 130, 246, 0.16), transparent 46%),
    linear-gradient(135deg, rgba(30, 41, 59, 0.96), rgba(15, 23, 42, 0.92));
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.22);
}

:global(html.dark) .trend-summary-change {
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.48);
}

:global(html.dark) .trend-range-item,
:global(html.dark) .trend-snapshot-item {
  border-color: rgba(148, 163, 184, 0.18);
  background: rgba(30, 41, 59, 0.55);
}

@media (max-width: 1180px) {
  .trend-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .trend-summary-change {
    justify-items: start;
    width: 100%;
  }

  .trend-overview-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .trend-snapshot-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .trend-snapshot-search {
    width: 100%;
  }

  .trend-snapshot-values {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
