<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import MonthPicker from '@/components/common/MonthPicker/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import YearPicker from '@/components/common/YearPicker/index.vue'
import {
  getPhotographyOrderOverview,
  getPhotographyOrders,
  type PhotographyOrder,
  type PhotographyOrderOverview,
  type PhotographyOrderOverviewBucket,
  type PhotographyOrderOverviewView,
} from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getErrorMessage } from '@/api/request'
import {
  amountTextClass,
  formatCurrency,
  formatDateLabel,
  hasOrderFoot,
  isBucketWeekend,
  orderTypeLabel,
  receivedAmount,
  statusClass,
  statusLabel,
  typeAccent,
} from '../PhotographyOrdersPage/photographyOrderFormat'
import { formatLunarLabel } from '../PhotographyOrdersPage/lunarCalendar'
import { useOverviewCharts } from './useOverviewCharts'

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
const isLoading = ref(true)
const pageError = ref('')

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
  if (viewMode.value === 'calendar') return '当天总金额'
  if (viewMode.value === 'month') return '当月总金额'
  return '当年总金额'
})

const lineHasData = computed(() => trendPoints.value.some((item) => Number(item.contractAmount) > 0 || Number(item.orderCount) > 0))
const quantityPieHasData = computed(() => typeStats.value.some((item) => Number(item.orderCount) > 0))
const revenuePieHasData = computed(() => typeStats.value.some((item) => Number(item.contractAmount) > 0))

const {
  lineChartRef,
  pieChartRef,
  revenuePieChartRef,
  ensureCharts,
  renderLineChart,
  renderQuantityPieChart,
  renderRevenuePieChart,
} = useOverviewCharts({ trendPoints, typeStats, viewMode, trendSeriesName })

onMounted(() => {
  void loadOverview()
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
    isLoading.value = false
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
    pageError.value = getErrorMessage(error, '订单总览加载失败')
  } finally {
    if (currentRequest === requestSerial) {
      isLoading.value = false
    }
  }
}

async function loadCalendarOrders() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || viewMode.value !== 'calendar' || !calendarSelectedDate.value) {
    return
  }

  const currentRequest = ++requestSerial

  try {
    const response = await getPhotographyOrders({
      userId: currentUser.id,
      status: 'all',
      shootDate: calendarSelectedDate.value,
    })
    if (currentRequest !== requestSerial) {
      return
    }

    calendarOrders.value = response

    void router.replace({
      query: buildRouteQuery(),
    })
  } catch (error) {
    if (currentRequest !== requestSerial) {
      return
    }
    pageError.value = getErrorMessage(error, '订单列表加载失败')
  }
}

function applyCalendarSelection(date: string) {
  calendarActiveDate.value = date
  calendarBuckets.value = calendarBuckets.value.map((bucket) => (
    bucket.selected === (bucket.key === date)
      ? bucket
      : { ...bucket, selected: bucket.key === date }
  ))
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
    if (!bucket.currentScope || bucket.orderCount <= 0) {
      return
    }
    if (bucket.key === calendarSelectedDate.value) {
      return
    }
    const nextMonth = bucket.key.slice(0, 7)
    if (calendarMonth.value !== nextMonth) {
      calendarMonth.value = nextMonth
    }
    calendarSelectedDate.value = bucket.key
    applyCalendarSelection(bucket.key)
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

function formatDateTime(value: string) {
  const [datePart = '', timePart = ''] = value.replace('T', ' ').split(' ')
  return `${datePart.replace(/-/g, '.')} ${timePart.slice(0, 5)}`
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
      </div>

      <div class="overview-stats-grid">
        <article class="overview-stat-card">
          <span>总单数</span>
          <strong>{{ summary?.totalOrders ?? 0 }}</strong>
        </article>
        <article class="overview-stat-card">
          <span>总金额</span>
          <strong :class="amountTextClass(summary?.totalContractAmount)">{{ formatCurrency(summary?.totalContractAmount) }}</strong>
        </article>
      </div>
    </section>

    <p v-if="pageError" class="overview-page-error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" text="总览加载中..." />

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
                'is-weekend': viewMode === 'calendar' && !bucket.workdayLabel && isBucketWeekend(bucket.key),
              },
            ]"
            @click="handleBucketClick(bucket)"
          >
            <template v-if="viewMode === 'calendar'">
              <template v-if="bucket.currentScope">
                <span v-if="bucket.workdayLabel" class="calendar-day-badge is-workday">班</span>
                <span v-if="bucket.holidayLabel" class="calendar-day-badge is-holiday" :title="bucket.holidayLabel">休</span>
                <span class="calendar-day-number">{{ bucket.label }}</span>
                <span class="calendar-day-lunar">{{ formatLunarLabel(bucket.key) }}</span>
                <span v-if="bucket.orderCount > 0" class="calendar-day-dot" aria-hidden="true"></span>
              </template>
            </template>
            <template v-else>
              <div class="bucket-card-head">
                <strong>{{ bucket.label }}</strong>
              </div>
              <div class="bucket-card-metrics">
                <span>{{ bucket.orderCount }} 单</span>
                <b :class="amountTextClass(bucket.contractAmount)">{{ formatCurrency(bucket.contractAmount) }}</b>
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
