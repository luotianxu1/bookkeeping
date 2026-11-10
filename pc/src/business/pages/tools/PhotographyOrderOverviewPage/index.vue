<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import MonthPicker from '@/business/components/common/MonthPicker/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import YearPicker from '@/business/components/common/YearPicker/index.vue'
import {
  getPhotographyOrderOverview,
  getPhotographyOrders,
  type PhotographyOrder,
  type PhotographyOrderOverview,
  type PhotographyOrderOverviewBucket,
  type PhotographyOrderOverviewView,
} from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'
import {
  amountTextClass,
  formatCurrency,
  isBucketWeekend,
  orderTypeLabel,
  receivedAmount,
  statusLabel,
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
const orders = computed(() =>
  viewMode.value === 'calendar'
    ? calendarOrders.value
    : (overview.value?.orders ?? []),
)
const trendPoints = computed(() => overview.value?.trendPoints ?? [])
const typeStats = computed(() => overview.value?.typeStats ?? [])
const buckets = computed(() =>
  viewMode.value === 'calendar'
    ? calendarBuckets.value
    : (overview.value?.buckets ?? []),
)

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

const lineHasData = computed(() =>
  trendPoints.value.some(
    (item) => Number(item.contractAmount) > 0 || Number(item.orderCount) > 0,
  ),
)
const quantityPieHasData = computed(() =>
  typeStats.value.some((item) => Number(item.orderCount) > 0),
)
const revenuePieHasData = computed(() =>
  typeStats.value.some((item) => Number(item.contractAmount) > 0),
)

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
    const response = await getPhotographyOrderOverview(
      buildQuery(currentUser.id),
    )
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
  if (
    !currentUser ||
    viewMode.value !== 'calendar' ||
    !calendarSelectedDate.value
  ) {
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
  calendarBuckets.value = calendarBuckets.value.map((bucket) =>
    bucket.selected === (bucket.key === date)
      ? bucket
      : { ...bucket, selected: bucket.key === date },
  )
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
  const raw = String(route.query.view ?? '')
    .trim()
    .toLowerCase()
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

type OrderRow = (typeof orders.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-calendar">
    <PageHeader title="摄影订单总览">
      <template #right>
        <SegmentedControl
          v-model="viewMode"
          :options="viewOptions"
          label="订单总览视图切换"
          variant="brand"
        />
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
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[
        { label: '订单总数', value: summary?.totalOrders ?? 0 },
        {
          label: '合同总额',
          value: formatCurrency(summary?.totalContractAmount),
        },
      ]"
    />
    <div class="desktop-columns">
      <PagePanel title="订单分布">
        <div class="pc-toolbar overview-panel-head">
          <strong>{{ bucketSectionTitle }}</strong>
        </div>
        <div v-if="viewMode === 'calendar'" class="calendar-weekdays">
          <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
        </div>
        <div :class="viewMode === 'calendar' ? 'calendar-grid' : 'bucket-grid'">
          <el-button
            v-for="bucket in buckets"
            :key="bucket.key"
            native-type="button"
            :class="[
              'bucket-card',
              `bucket-card--${viewMode}`,
              {
                'is-selected': bucket.selected,
                'is-muted': !bucket.currentScope,
                'is-active': bucket.orderCount > 0,
                'is-weekend':
                  viewMode === 'calendar' &&
                  !bucket.workdayLabel &&
                  isBucketWeekend(bucket.key),
              },
            ]"
            @click="handleBucketClick(bucket)"
            class="pc-content-button"
          >
            <template v-if="viewMode === 'calendar'">
              <template v-if="bucket.currentScope">
                <span
                  v-if="bucket.workdayLabel"
                  class="calendar-day-badge is-workday"
                >
                  班
                </span>
                <span
                  v-if="bucket.holidayLabel"
                  class="calendar-day-badge is-holiday"
                  :title="bucket.holidayLabel"
                >
                  休
                </span>
                <span class="calendar-day-number">{{ bucket.label }}</span>
                <span class="calendar-day-lunar">
                  {{ formatLunarLabel(bucket.key) }}
                </span>
                <span
                  v-if="bucket.orderCount > 0"
                  class="calendar-day-dot"
                  aria-hidden="true"
                ></span>
              </template>
            </template>
            <template v-else>
              <div class="bucket-card-head">
                <strong>{{ bucket.label }}</strong>
              </div>
              <div class="bucket-card-metrics">
                <span>{{ bucket.orderCount }} 单</span>
                <b :class="amountTextClass(bucket.contractAmount)">
                  {{ formatCurrency(bucket.contractAmount) }}
                </b>
              </div>
            </template>
          </el-button>
        </div>
      </PagePanel>
      <PagePanel title="收益趋势">
        <div v-if="lineHasData" ref="lineChartRef" class="desktop-chart" />
        <el-empty v-else description="暂无趋势数据" />
      </PagePanel>
    </div>
    <div class="desktop-columns">
      <PagePanel title="分类分布">
        <div
          v-if="quantityPieHasData"
          ref="pieChartRef"
          class="desktop-chart"
        />
        <el-empty v-else description="暂无分类数据" />
      </PagePanel>
      <PagePanel title="收益类型">
        <div
          v-if="revenuePieHasData"
          ref="revenuePieChartRef"
          class="desktop-chart"
        />
        <el-empty v-else description="暂无收益数据" />
      </PagePanel>
    </div>
    <DesktopTable
      :rows="orders"
      :loading="isLoading"
      title="当前周期订单"
      search-placeholder="搜索地址或联系方式"
      searchable
    >
      <!-- @vue-generic {OrderRow} -->
      <el-table-column label="类型" min-width="150">
        <template #default="{ row }: { row: OrderRow }">
          {{ orderTypeLabel(row.orderType) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {OrderRow} -->
      <el-table-column label="拍摄时间" min-width="200">
        <template #default="{ row }: { row: OrderRow }">
          {{ formatDateTime(row.shootAt) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {OrderRow} -->
      <el-table-column label="状态" min-width="150">
        <template #default="{ row }: { row: OrderRow }">
          {{ statusLabel(row) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {OrderRow} -->
      <el-table-column label="已收收入" min-width="150">
        <template #default="{ row }: { row: OrderRow }">
          {{ formatCurrency(receivedAmount(row)) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="address"
        label="地点"
        min-width="220"
        sortable="custom"
        show-overflow-tooltip
      />
      <el-table-column
        prop="contactInfo"
        label="联系方式"
        min-width="180"
        sortable="custom"
        show-overflow-tooltip
      />
    </DesktopTable>
  </section>
</template>
