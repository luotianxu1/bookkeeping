<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

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
  formatSignedCurrency,
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

const allocations = computed(() => trend.value?.allocations ?? [])

const visibleYesterdaySnapshots = computed(() =>
  yesterdaySnapshots.value.filter(
    (item) => Math.abs(Number(item.changeAmount ?? 0)) > 0,
  ),
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

type AllocationRow = (typeof allocations.value)[number]
type TrendRow = (typeof rangeDetailPoints.value)[number]
type SnapshotRow = (typeof visibleYesterdaySnapshots.value)[number]
</script>
<template>
  <section class="desktop-workspace">
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
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <template v-if="trend">
      <MetricStrip
        :items="[
          { label: '总资产', value: totalAssets },
          { label: '资产变动', value: previousPointAmountText },
          { label: '变动比例', value: previousPointRateText },
        ]"
      />
      <div class="desktop-columns dashboard-columns">
        <PagePanel title="总资产走势">
          <template #actions>
            <SegmentedControl
              v-model="activeRange"
              :options="rangeOptions"
              label="趋势区间选择"
              variant="surface"
            />
          </template>
          <div v-if="hasTrendPoints" ref="chartRef" class="desktop-chart" />
          <el-empty v-else description="暂无趋势数据" />
        </PagePanel>
        <DesktopTable :rows="allocations" :loading="false" title="资产分布">
          <el-table-column
            prop="label"
            label="类型"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {AllocationRow} -->
          <el-table-column label="余额" min-width="150">
            <template #default="{ row }: { row: AllocationRow }">
              {{ formatCurrency(row.balance) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {AllocationRow} -->
          <el-table-column label="占比" min-width="150">
            <template #default="{ row }: { row: AllocationRow }">
              {{ Number(row.percent ?? 0).toFixed(1) }}%
            </template>
          </el-table-column>
        </DesktopTable>
      </div>
      <DesktopTable
        :rows="rangeDetailPoints"
        :loading="false"
        title="区间资产明细"
      >
        <el-table-column
          prop="label"
          label="日期"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <!-- @vue-generic {TrendRow} -->
        <el-table-column label="资产" min-width="150">
          <template #default="{ row }: { row: TrendRow }">
            {{ row.value === null ? '—' : formatCurrency(row.value) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {TrendRow} -->
        <el-table-column label="变动" min-width="150">
          <template #default="{ row }: { row: TrendRow }">
            <AmountText
              :value="
                row.changeAmount === null
                  ? '—'
                  : formatCurrency(row.changeAmount)
              "
              show-sign
            />
          </template>
        </el-table-column>
      </DesktopTable>
      <template v-if="showYesterdaySnapshotSection">
        <DesktopTable
          :rows="visibleYesterdaySnapshots"
          :loading="false"
          title="账户快照对比"
          search-placeholder="搜索账户"
          searchable
        >
          <el-table-column
            prop="accountName"
            label="账户"
            min-width="200"
            sortable="custom"
            show-overflow-tooltip
          />
          <el-table-column
            prop="accountTypeLabel"
            label="类型"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <el-table-column
            prop="ownerLabel"
            label="成员"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {SnapshotRow} -->
          <el-table-column label="昨日资产" min-width="150">
            <template #default="{ row }: { row: SnapshotRow }">
              {{ formatCurrency(row.totalAssets) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {SnapshotRow} -->
          <el-table-column label="今日资产" min-width="150">
            <template #default="{ row }: { row: SnapshotRow }">
              {{ formatCurrency(row.currentAssets) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {SnapshotRow} -->
          <el-table-column label="资产变动" min-width="150">
            <template #default="{ row }: { row: SnapshotRow }">
              <AmountText :value="formatCurrency(row.changeAmount)" show-sign />
            </template>
          </el-table-column>
        </DesktopTable>
        <el-alert
          v-if="snapshotError"
          :title="snapshotError"
          type="info"
          :closable="false"
          show-icon
        ></el-alert>
      </template>
    </template>
  </section>
</template>
