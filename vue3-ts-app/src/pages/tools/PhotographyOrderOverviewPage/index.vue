<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { ECharts, EChartsCoreOption } from 'echarts'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import MonthPicker from '@/components/common/MonthPicker/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import YearPicker from '@/components/common/YearPicker/index.vue'
import {
  getPhotographyOrderOverview,
  type PhotographyOrder,
  type PhotographyOrderOverview,
  type PhotographyOrderOverviewBucket,
  type PhotographyOrderOverviewView,
} from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

const route = useRoute()
const router = useRouter()

const viewOptions = [
  { label: '日视图', value: 'calendar', icon: 'calendar-day' },
  { label: '月视图', value: 'month', icon: 'calendar-month' },
  { label: '年视图', value: 'year', icon: 'calendar-year' },
] as const

const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']

const viewMode = ref<PhotographyOrderOverviewView>(resolveInitialView())
const calendarMonth = ref(resolveInitialMonth())
const calendarSelectedDate = ref<string | null>(resolveInitialDate())
const monthYear = ref(resolveInitialYear())
const yearWindowEnd = ref(resolveInitialYear())

const overview = ref<PhotographyOrderOverview | null>(null)
const calendarBuckets = ref<PhotographyOrderOverviewBucket[]>([])
const calendarOrders = ref<PhotographyOrder[]>([])
const calendarActiveDate = ref<string | null>(resolveInitialDate())
const isLoading = ref(false)
const pageError = ref('')

const lineChartRef = ref<HTMLDivElement | null>(null)
const pieChartRef = ref<HTMLDivElement | null>(null)
const revenuePieChartRef = ref<HTMLDivElement | null>(null)
let echartsLib: (typeof import('echarts')) | null = null
let lineChart: ECharts | null = null
let pieChart: ECharts | null = null
let revenuePieChart: ECharts | null = null
let requestSerial = 0

const summary = computed(() => overview.value?.summary ?? null)
const orders = computed(() => (viewMode.value === 'calendar' ? calendarOrders.value : overview.value?.orders ?? []))
const trendPoints = computed(() => overview.value?.trendPoints ?? [])
const typeStats = computed(() => overview.value?.typeStats ?? [])
const buckets = computed(() => (viewMode.value === 'calendar' ? calendarBuckets.value : overview.value?.buckets ?? []))
const selectedCalendarDate = computed(() => (
  viewMode.value === 'calendar'
    ? calendarActiveDate.value
    : overview.value?.selectedValue ?? calendarSelectedDate.value ?? null
))
const showCalendarOrders = computed(() => viewMode.value === 'calendar' && orders.value.length > 0)

const periodTitle = computed(() => {
  if (!overview.value) return '订单列表'
  if (viewMode.value === 'calendar') {
    return selectedCalendarDate.value ? `${formatDateLabel(selectedCalendarDate.value)}订单列表` : '当天订单列表'
  }
  if (viewMode.value === 'month') {
    return `${overview.value.selectedValue}年订单列表`
  }
  return `${overview.value.selectedValue}订单列表`
})

const bucketSectionTitle = computed(() => {
  if (viewMode.value === 'calendar') return '拍摄日历'
  if (viewMode.value === 'month') return '全年月份概览'
  return '近5年年份概览'
})

const trendSeriesName = computed(() => {
  if (viewMode.value === 'calendar') return '当天总收益'
  if (viewMode.value === 'month') return '当月总收益'
  return '当年总收益'
})

const lineHasData = computed(() => trendPoints.value.some((item) => Number(item.contractAmount) > 0 || Number(item.orderCount) > 0))
const quantityPieHasData = computed(() => typeStats.value.some((item) => Number(item.orderCount) > 0))
const revenuePieHasData = computed(() => typeStats.value.some((item) => Number(item.contractAmount) > 0))

onMounted(() => {
  void loadOverview()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  lineChart?.dispose()
  pieChart?.dispose()
  revenuePieChart?.dispose()
  lineChart = null
  pieChart = null
  revenuePieChart = null
})

watch(viewMode, () => {
  void loadOverview()
})

watch(calendarMonth, () => {
  if (viewMode.value === 'calendar') {
    void loadOverview()
  }
})

watch(monthYear, () => {
  if (viewMode.value === 'month') {
    void loadOverview()
  }
})

watch(yearWindowEnd, () => {
  if (viewMode.value === 'year') {
    void loadOverview()
  }
})

watch(
  () => overview.value,
  async () => {
    await nextTick()
    await ensureCharts()
    renderLineChart()
    renderQuantityPieChart()
    renderRevenuePieChart()
  },
  { deep: true },
)

async function loadOverview() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看订单总览'
    return
  }

  const currentRequest = ++requestSerial
  isLoading.value = true
  pageError.value = ''

  try {
    const response = await getPhotographyOrderOverview(buildQuery(currentUser.id))
    if (currentRequest !== requestSerial) {
      return
    }

    overview.value = response

    if (viewMode.value === 'calendar') {
      calendarBuckets.value = response.buckets ?? []
      calendarOrders.value = response.orders ?? []
      calendarActiveDate.value = response.selectedValue ?? null
      if (calendarMonth.value !== response.anchor) {
        calendarMonth.value = response.anchor
      }
      if (calendarSelectedDate.value !== (response.selectedValue ?? null)) {
        calendarSelectedDate.value = response.selectedValue ?? null
      }
    } else if (viewMode.value === 'month') {
      if (String(monthYear.value) !== response.anchor) {
        monthYear.value = Number(response.anchor)
      }
    } else if (String(yearWindowEnd.value) !== response.anchor) {
      yearWindowEnd.value = Number(response.anchor)
    }

    void router.replace({
      query: buildRouteQuery(),
    })
  } catch (error) {
    if (currentRequest !== requestSerial) {
      return
    }
    pageError.value = error instanceof Error ? error.message : '订单总览加载失败'
  } finally {
    if (currentRequest === requestSerial) {
      isLoading.value = false
    }
  }
}

async function loadCalendarOrders() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || viewMode.value !== 'calendar') {
    return
  }

  const currentRequest = ++requestSerial

  try {
    const response = await getPhotographyOrderOverview({
      userId: currentUser.id,
      view: 'calendar',
      anchor: calendarMonth.value,
      selectedDate: calendarSelectedDate.value ?? undefined,
    })
    if (currentRequest !== requestSerial) {
      return
    }

    calendarBuckets.value = response.buckets ?? []
    calendarOrders.value = response.orders ?? []
    calendarActiveDate.value = response.selectedValue ?? null
    if (calendarSelectedDate.value !== (response.selectedValue ?? null)) {
      calendarSelectedDate.value = response.selectedValue ?? null
    }

    void router.replace({
      query: buildRouteQuery(),
    })
  } catch (error) {
    if (currentRequest !== requestSerial) {
      return
    }
    pageError.value = error instanceof Error ? error.message : '订单列表加载失败'
  }
}

function buildQuery(userId: number) {
  if (viewMode.value === 'calendar') {
    return {
      userId,
      view: 'calendar' as const,
      anchor: calendarMonth.value,
      selectedDate: calendarSelectedDate.value ?? undefined,
    }
  }
  if (viewMode.value === 'month') {
    return {
      userId,
      view: 'month' as const,
      anchor: String(monthYear.value),
    }
  }
  return {
    userId,
    view: 'year' as const,
    anchor: String(yearWindowEnd.value),
  }
}

function buildRouteQuery() {
  if (viewMode.value === 'calendar') {
    return {
      view: 'calendar',
      anchor: calendarMonth.value,
      selectedDate: calendarSelectedDate.value ?? undefined,
    }
  }
  if (viewMode.value === 'month') {
    return {
      view: 'month',
      anchor: String(monthYear.value),
    }
  }
  return {
    view: 'year',
    anchor: String(yearWindowEnd.value),
  }
}

function handleBucketClick(bucket: PhotographyOrderOverviewBucket) {
  if (viewMode.value === 'calendar') {
    if (bucket.orderCount <= 0) {
      return
    }
    const nextMonth = bucket.key.slice(0, 7)
    if (calendarMonth.value !== nextMonth) {
      calendarMonth.value = nextMonth
    }
    if (calendarSelectedDate.value !== bucket.key) {
      calendarSelectedDate.value = bucket.key
    }
    void loadCalendarOrders()
    return
  }

  if (viewMode.value === 'month') {
    viewMode.value = 'calendar'
    calendarMonth.value = bucket.key.slice(0, 7)
    calendarSelectedDate.value = bucket.key
    return
  }

  viewMode.value = 'month'
  monthYear.value = Number(bucket.key)
}

function resolveInitialView(): PhotographyOrderOverviewView {
  const raw = String(route.query.view ?? '').trim().toLowerCase()
  if (raw === 'month' || raw === 'year') {
    return raw
  }
  return 'calendar'
}

function resolveInitialMonth() {
  const raw = String(route.query.anchor ?? '')
  if (resolveInitialView() === 'calendar' && /^\d{4}-\d{2}$/.test(raw)) {
    return raw
  }
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function resolveInitialDate() {
  const raw = String(route.query.selectedDate ?? '')
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    return raw
  }
  return null
}

function resolveInitialYear() {
  const raw = Number(route.query.anchor)
  return Number.isFinite(raw) && raw > 2000 ? raw : new Date().getFullYear()
}

function formatCurrency(value: number | string | null | undefined) {
  const amount = Number(value ?? 0)
  return `¥${amount.toLocaleString('zh-CN', {
    minimumFractionDigits: amount % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  })}`
}

function formatDateLabel(value: string | null | undefined) {
  if (!value) return ''
  const matched = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!matched) return value
  return `${Number(matched[2])}月${Number(matched[3])}日`
}

function formatDateTime(value: string) {
  const [datePart = '', timePart = ''] = value.replace('T', ' ').split(' ')
  return `${datePart.replace(/-/g, '.')} ${timePart.slice(0, 5)}`
}

function orderTypeLabel(type: string) {
  return {
    first_birthday: '周岁',
    hundred_days: '百天',
    engagement: '订婚',
    thanks_banquet: '答谢宴',
    wedding: '婚礼',
    graduation: '毕业照',
  }[type] ?? type
}

function receivedAmount(order: PhotographyOrder) {
  let total = 0
  if (order.depositReceivedAt) {
    total += Number(order.depositAmount ?? 0)
  }
  if (order.finalReceivedAt) {
    total += Number(order.finalAmount ?? 0)
  }
  return total
}

function statusLabel(order: PhotographyOrder) {
  if (isShotByTime(order)) {
    return '已拍摄'
  }

  return Number(order.depositAmount ?? 0) > 0 ? '已付订金' : '未付订金'
}

function statusClass(order: PhotographyOrder) {
  return isShotByTime(order) ? 'overview-order-tag--shot' : 'overview-order-tag--pending'
}

function isShotByTime(order: PhotographyOrder, now = Date.now()) {
  const shootAt = new Date(order.shootAt).getTime()
  return Number.isFinite(shootAt) && shootAt <= now
}

function hasOrderFoot(order: PhotographyOrder) {
  return Boolean(order.address || order.contactInfo || order.remark)
}

function typeAccent(type: string) {
  const rootStyle = getComputedStyle(document.documentElement)
  return {
    wedding: rootStyle.getPropertyValue('--color-warning-strong').trim(),
    graduation: rootStyle.getPropertyValue('--color-brand-strong').trim(),
    first_birthday: rootStyle.getPropertyValue('--color-success-strong').trim(),
    hundred_days: rootStyle.getPropertyValue('--color-teal').trim(),
    engagement: rootStyle.getPropertyValue('--color-danger').trim(),
    thanks_banquet: rootStyle.getPropertyValue('--color-purple').trim(),
  }[type] ?? rootStyle.getPropertyValue('--color-brand').trim()
}

function amountTextClass(value: number | string | null | undefined) {
  return Number(value ?? 0) === 0 ? 'is-zero' : ''
}

function resolveDisplayedTrendPoints() {
  return trendPoints.value
}

async function ensureCharts() {
  if (!echartsLib) {
    echartsLib = await import('echarts')
  }
  if (lineChart && lineChartRef.value && lineChart.getDom() !== lineChartRef.value) {
    lineChart.dispose()
    lineChart = null
  }
  if (pieChart && pieChartRef.value && pieChart.getDom() !== pieChartRef.value) {
    pieChart.dispose()
    pieChart = null
  }
  if (revenuePieChart && revenuePieChartRef.value && revenuePieChart.getDom() !== revenuePieChartRef.value) {
    revenuePieChart.dispose()
    revenuePieChart = null
  }
  if (lineChartRef.value && !lineChart) {
    lineChart = echartsLib.init(lineChartRef.value)
  }
  if (pieChartRef.value && !pieChart) {
    pieChart = echartsLib.init(pieChartRef.value)
  }
  if (revenuePieChartRef.value && !revenuePieChart) {
    revenuePieChart = echartsLib.init(revenuePieChartRef.value)
  }
}

function renderLineChart() {
  if (!lineChart) return

  const displayedTrendPoints = resolveDisplayedTrendPoints()
  const labels = displayedTrendPoints.map((item) => item.label)
  const incomeValues = displayedTrendPoints.map((item) => Number(item.contractAmount ?? 0))
  const rootStyle = getComputedStyle(document.documentElement)
  const tooltipBg = rootStyle.getPropertyValue('--color-chart-tooltip-bg').trim()
  const tooltipBorder = rootStyle.getPropertyValue('--color-chart-tooltip-border').trim()
  const tooltipText = rootStyle.getPropertyValue('--color-chart-tooltip-text').trim()
  const chartAxis = rootStyle.getPropertyValue('--color-chart-axis').trim()
  const chartSplit = rootStyle.getPropertyValue('--color-chart-split').trim()
  const incomeColor = rootStyle.getPropertyValue('--color-danger').trim()

  const option: EChartsCoreOption = {
    grid: { left: 28, right: 18, top: 28, bottom: 28, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: tooltipText },
    },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: chartAxis, fontSize: 11 },
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: chartSplit } },
      axisLabel: { color: chartAxis, fontSize: 11 },
    },
    yAxis: [
      {
        type: 'value',
        axisLine: { show: false },
        splitLine: { lineStyle: { color: chartSplit } },
        axisLabel: {
          color: chartAxis,
          fontSize: 11,
          formatter: (value: number) => `¥${value}`,
        },
      },
    ],
    series: [
      {
        name: trendSeriesName.value,
        type: 'bar',
        data: incomeValues,
        barMaxWidth: 18,
        itemStyle: { color: incomeColor, borderRadius: [8, 8, 0, 0] },
        emphasis: { itemStyle: { color: incomeColor } },
      },
    ],
  }

  lineChart.setOption(option, true)
}

function buildPieOption(valueGetter: (item: (typeof typeStats.value)[number]) => number): EChartsCoreOption {
  return {
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['58%', '78%'],
        center: ['50%', '48%'],
        avoidLabelOverlap: false,
        label: { show: false },
        emphasis: { scale: true },
        data: typeStats.value.map((item) => ({
          name: item.label,
          value: valueGetter(item),
          itemStyle: { color: typeAccent(item.type) },
        })),
      },
    ],
  }
}

function renderQuantityPieChart() {
  if (!pieChart) return

  pieChart.setOption(buildPieOption((item) => Number(item.orderCount || 0)), true)
}

function renderRevenuePieChart() {
  if (!revenuePieChart) return

  revenuePieChart.setOption(buildPieOption((item) => Number(item.contractAmount || 0)), true)
}

function handleResize() {
  lineChart?.resize()
  pieChart?.resize()
  revenuePieChart?.resize()
}
</script>

<template>
  <section class="photography-order-overview-page" aria-label="摄影订单总览">
    <PageHeader title="订单总览" back-to="/tools/photography-orders" back-label="返回订单页" />

    <SegmentedControl v-model="viewMode" :options="viewOptions" label="订单总览视图切换" variant="brand" />

    <section class="overview-hero">
      <div class="overview-picker-row">
        <MonthPicker
          v-if="viewMode === 'calendar'"
          :model-value="calendarMonth"
          @update:model-value="calendarMonth = $event"
        />
        <YearPicker
          v-else-if="viewMode === 'month'"
          :model-value="monthYear"
          @update:model-value="monthYear = $event"
        />
        <YearPicker
          v-else
          :model-value="yearWindowEnd"
          @update:model-value="yearWindowEnd = $event"
        />
      </div>

      <div class="overview-stats-grid">
        <article class="overview-stat-card">
          <span>总单数</span>
          <strong>{{ summary?.totalOrders ?? 0 }}</strong>
          <p>已拍摄 {{ summary?.shotOrders ?? 0 }} · 未拍摄 {{ summary?.pendingOrders ?? 0 }}</p>
        </article>
        <article class="overview-stat-card">
          <span>已收金额</span>
          <strong :class="amountTextClass(summary?.totalReceivedAmount)">{{ formatCurrency(summary?.totalReceivedAmount) }}</strong>
          <p>订金 {{ formatCurrency(summary?.depositIncome) }} · 尾款 {{ formatCurrency(summary?.finalIncome) }}</p>
        </article>
      </div>
    </section>

    <CommonLoading v-if="isLoading" text="总览加载中..." />
    <p v-else-if="pageError" class="overview-page-error">{{ pageError }}</p>

    <template v-else>
      <section class="overview-panel">
        <div class="overview-panel-head">
          <strong>{{ bucketSectionTitle }}</strong>
        </div>

        <div v-if="viewMode === 'calendar'" class="calendar-weekdays">
          <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
        </div>

        <div :class="viewMode === 'calendar' ? 'calendar-grid' : 'bucket-grid'">
          <button
            v-for="bucket in buckets"
            :key="bucket.key"
            type="button"
            :class="[
              'bucket-card',
              `bucket-card--${viewMode}`,
              {
                'is-selected': bucket.selected,
                'is-muted': !bucket.currentScope,
                'is-active': bucket.orderCount > 0,
              },
            ]"
            @click="handleBucketClick(bucket)"
          >
            <template v-if="viewMode === 'calendar'">
              <div class="calendar-card-center">
                <strong>{{ bucket.label }}</strong>
                <span v-if="bucket.orderCount > 0" class="calendar-card-dot" aria-hidden="true"></span>
              </div>
            </template>
            <template v-else>
              <div class="bucket-card-head">
                <strong>{{ bucket.label }}</strong>
              </div>
              <div class="bucket-card-metrics">
                <span>{{ bucket.orderCount }} 单</span>
                <b :class="amountTextClass(bucket.totalIncome)">{{ formatCurrency(bucket.totalIncome) }}</b>
              </div>
            </template>
          </button>
        </div>
      </section>

      <section v-if="showCalendarOrders" class="overview-orders-panel">
        <div class="overview-panel-head">
          <strong>{{ periodTitle }}</strong>
          <span>{{ orders.length }} 单</span>
        </div>

        <p v-if="orders.length === 0" class="overview-empty">当前周期暂无订单</p>

        <div v-else class="overview-order-list">
          <article v-for="order in orders" :key="order.id" class="overview-order-card">
            <div class="overview-order-head">
              <div>
                <strong>{{ orderTypeLabel(order.orderType) }}</strong>
                <p>{{ formatDateTime(order.shootAt) }}</p>
              </div>
              <div class="overview-order-tags">
                <span class="overview-order-tag" :style="{ color: typeAccent(order.orderType), backgroundColor: `${typeAccent(order.orderType)}14` }">
                  {{ orderTypeLabel(order.orderType) }}
                </span>
                <span :class="['overview-order-tag', 'overview-order-tag--status', statusClass(order)]">
                  {{ statusLabel(order) }}
                </span>
              </div>
            </div>

            <div class="overview-order-metrics">
              <div>
                <span>已收收入</span>
                <strong :class="['income', amountTextClass(receivedAmount(order))]">{{ formatCurrency(receivedAmount(order)) }}</strong>
              </div>
            </div>

            <div v-if="hasOrderFoot(order)" class="overview-order-foot">
              <p v-if="order.address">地址：{{ order.address }}</p>
              <p v-if="order.contactInfo">联系方式：{{ order.contactInfo }}</p>
              <p v-if="order.remark">备注：{{ order.remark }}</p>
            </div>
          </article>
        </div>
      </section>

      <section class="overview-charts-grid">
        <article class="chart-card chart-card--wide">
          <div class="chart-card-head">
            <div>
              <strong>收益趋势</strong>
            </div>
          </div>
          <div v-if="!lineHasData" class="chart-empty">当前周期还没有可展示的趋势数据</div>
          <div v-else ref="lineChartRef" class="chart-surface"></div>
        </article>

        <article class="chart-card">
          <div class="chart-card-head">
            <div>
              <strong>分类分布</strong>
            </div>
          </div>
          <div v-if="!quantityPieHasData" class="chart-empty">当前周期暂无分类分布数据</div>
          <div v-else ref="pieChartRef" class="chart-surface chart-surface--pie"></div>
          <div v-if="quantityPieHasData" class="type-legend">
            <article v-for="item in typeStats" :key="item.type" class="type-legend-item">
              <span class="type-dot" :style="{ backgroundColor: typeAccent(item.type) }"></span>
              <div>
                <strong>{{ item.label }}</strong>
                <p>{{ item.orderCount }} 单</p>
              </div>
            </article>
          </div>
        </article>

        <article class="chart-card">
          <div class="chart-card-head">
            <div>
              <strong>收益类型</strong>
            </div>
          </div>
          <div v-if="!revenuePieHasData" class="chart-empty">当前周期暂无收益类型数据</div>
          <div v-else ref="revenuePieChartRef" class="chart-surface chart-surface--pie"></div>
          <div v-if="revenuePieHasData" class="type-legend">
            <article v-for="item in typeStats" :key="`revenue-${item.type}`" class="type-legend-item">
              <span class="type-dot" :style="{ backgroundColor: typeAccent(item.type) }"></span>
              <div>
                <strong>{{ item.label }}</strong>
                <p>{{ formatCurrency(item.contractAmount) }}</p>
              </div>
            </article>
          </div>
        </article>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
