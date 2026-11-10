<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import {
  getCalendarOverview,
  type CalendarDay,
  type CalendarMonth,
  type CalendarOverview,
  type CalendarOverviewView,
} from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

const route = useRoute()
const router = useRouter()

const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']
const viewOptions = [
  { label: '月视图', value: 'month' },
  { label: '年视图', value: 'year' },
] as const

const viewMode = ref<CalendarOverviewView>(resolveInitialView())
const monthAnchor = ref(resolveInitialMonth())
const yearAnchor = ref(resolveInitialYear())
const selectedDate = ref<string | null>(resolveInitialSelectedDate())
const overview = ref<CalendarOverview | null>(null)
const isLoading = ref(true)
const pageError = ref('')
const hasLoadedOnce = ref(false)
let requestSerial = 0

const monthAnniversaries = computed(() => overview.value?.anniversaries ?? [])
const isMonthView = computed(() => viewMode.value === 'month')
const periodLabel = computed(
  () =>
    overview.value?.title ??
    (isMonthView.value
      ? formatMonthTitle(monthAnchor.value)
      : yearAnchor.value),
)
const showInitialLoading = computed(
  () => isLoading.value && !hasLoadedOnce.value && !overview.value,
)

const lunarFormatter = new Intl.DateTimeFormat('zh-CN-u-ca-chinese', {
  month: 'long',
  day: 'numeric',
})
const lunarLabelCache = new Map<string, string>()

onMounted(() => {
  void loadOverview()
})

async function loadOverview() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看日历'
    isLoading.value = false
    return
  }

  const currentRequest = ++requestSerial
  isLoading.value = true
  pageError.value = ''

  try {
    const response = await getCalendarOverview({
      userId: currentUser.id,
      view: viewMode.value,
      anchor: isMonthView.value ? monthAnchor.value : yearAnchor.value,
      selectedDate: selectedDate.value ?? undefined,
    })

    if (currentRequest !== requestSerial) {
      return
    }

    overview.value = response

    if (response.view === 'month') {
      viewMode.value = 'month'
      monthAnchor.value = response.anchor
    } else {
      viewMode.value = 'year'
      yearAnchor.value = response.anchor
    }

    selectedDate.value = response.selectedDate || null
    hasLoadedOnce.value = true

    await router.replace({
      query: buildRouteQuery(),
    })
  } catch (error) {
    if (currentRequest !== requestSerial) {
      return
    }
    pageError.value = getErrorMessage(error, '日历加载失败')
  } finally {
    if (currentRequest === requestSerial) {
      isLoading.value = false
    }
  }
}

function buildRouteQuery() {
  return {
    view: viewMode.value,
    anchor: isMonthView.value ? monthAnchor.value : yearAnchor.value,
    ...(selectedDate.value ? { selectedDate: selectedDate.value } : {}),
  }
}

function switchView(nextView: string) {
  const normalized = nextView === 'year' ? 'year' : 'month'
  if (normalized === viewMode.value) {
    return
  }

  if (normalized === 'year') {
    yearAnchor.value = monthAnchor.value.slice(0, 4)
  } else {
    monthAnchor.value =
      selectedDate.value?.slice(0, 7) ?? `${yearAnchor.value}-01`
    selectedDate.value = resolvePreferredSelectedDate(monthAnchor.value)
  }

  viewMode.value = normalized
  void loadOverview()
}

function shiftPeriod(offset: number) {
  if (isMonthView.value) {
    const [yearText, monthText] = monthAnchor.value.split('-')
    const date = new Date(Number(yearText), Number(monthText) - 1 + offset, 1)
    monthAnchor.value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
    selectedDate.value = resolvePreferredSelectedDate(monthAnchor.value)
  } else {
    yearAnchor.value = String(Number(yearAnchor.value) + offset)
    const fallbackMonth = selectedDate.value
      ? selectedDate.value.slice(5, 7)
      : '01'
    selectedDate.value = `${yearAnchor.value}-${fallbackMonth}-01`
  }

  void loadOverview()
}

function selectMonthDay(day: CalendarDay) {
  if (!day.currentMonth || !day.date || day.date === selectedDate.value) {
    return
  }
  selectedDate.value = day.date
  void loadOverview()
}

function selectYearMonth(month: CalendarMonth) {
  viewMode.value = 'month'
  monthAnchor.value = month.key
  selectedDate.value = resolvePreferredSelectedDate(month.key)
  void loadOverview()
}

function resolvePreferredSelectedDate(monthKey: string) {
  const today = getTodayDate()
  return today.startsWith(`${monthKey}-`) ? today : `${monthKey}-01`
}

function getTodayDate() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function resolveInitialView(): CalendarOverviewView {
  const raw = String(route.query.view ?? '')
    .trim()
    .toLowerCase()
  return raw === 'year' ? 'year' : 'month'
}

function resolveInitialMonth() {
  const raw = String(route.query.anchor ?? '')
  if (resolveInitialView() === 'month' && /^\d{4}-\d{2}$/.test(raw)) {
    return raw
  }
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function resolveInitialYear() {
  const raw = String(route.query.anchor ?? '')
  if (resolveInitialView() === 'year' && /^\d{4}$/.test(raw)) {
    return raw
  }
  return String(new Date().getFullYear())
}

function resolveInitialSelectedDate() {
  const raw = String(route.query.selectedDate ?? '')
  return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : null
}

function formatMonthTitle(value: string) {
  const matched = /^(\d{4})-(\d{2})$/.exec(value)
  if (!matched) {
    return value
  }
  return `${Number(matched[2])}月`
}

function formatMonthDay(value: string) {
  const matched = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!matched) {
    return value
  }
  return `${matched[2]}-${matched[3]}`
}

function formatLunarLabel(dateText: string) {
  const cached = lunarLabelCache.get(dateText)
  if (cached) {
    return cached
  }

  const lunarText = lunarFormatter.format(new Date(`${dateText}T12:00:00`))
  const matched = /^(.+?)(\d+)日$/.exec(lunarText)
  if (!matched) {
    lunarLabelCache.set(dateText, lunarText)
    return lunarText
  }

  const monthLabel = matched[1]
  const dayValue = Number(matched[2])
  const label = dayValue === 1 ? monthLabel : toChineseDay(dayValue)
  lunarLabelCache.set(dateText, label)
  return label
}

function miniDayTitle(day: CalendarDay) {
  return day.holidayLabel || day.workdayLabel || undefined
}

function toChineseDay(day: number) {
  const digits = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九']
  if (day <= 10) {
    return day === 10 ? '初十' : `初${digits[day]}`
  }
  if (day < 20) {
    return `十${digits[day - 10]}`
  }
  if (day === 20) {
    return '二十'
  }
  if (day < 30) {
    return `廿${digits[day - 20]}`
  }
  return day === 30 ? '三十' : String(day)
}

type AnniversaryRow = (typeof monthAnniversaries.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-calendar calendar-page">
    <PageHeader title="日历">
      <template #right>
        <SegmentedControl
          :model-value="viewMode"
          :options="viewOptions"
          label="日历视图切换"
          variant="surface"
          @update:model-value="switchView"
        />
        <el-button @click="shiftPeriod(-1)">上一周期</el-button>
        <strong>{{ periodLabel }}</strong>
        <el-button @click="shiftPeriod(1)">下一周期</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <CommonLoading v-if="showInitialLoading" />
    <template v-else>
      <div v-if="isMonthView" class="desktop-main-aside">
        <el-card
          class="pc-record-card calendar-month-card"
          aria-label="月历"
          shadow="never"
        >
          <div class="calendar-weekdays">
            <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
          </div>
          <div class="pc-grid calendar-grid">
            <el-button
              v-for="day in overview?.days ?? []"
              :key="day.date"
              native-type="button"
              :class="[
                'calendar-day-cell',
                {
                  'is-outside': !day.currentMonth,
                  'is-selected': day.selected,
                  'is-weekend': day.weekend && !day.workdayLabel,
                  'is-workday': !!day.workdayLabel,
                  'has-anniversary': day.anniversaryCount > 0,
                },
              ]"
              @click="selectMonthDay(day)"
            >
              <template v-if="day.currentMonth">
                <span
                  v-if="day.workdayLabel"
                  class="calendar-day-badge is-workday"
                >
                  班
                </span>
                <span
                  v-if="day.holidayLabel"
                  class="calendar-day-badge is-holiday"
                  :title="day.holidayLabel"
                >
                  休
                </span>
                <span class="calendar-day-number">{{ day.day }}</span>
                <span class="calendar-day-lunar">
                  {{ formatLunarLabel(day.date) }}
                </span>
                <span
                  v-if="day.anniversaryCount > 0"
                  class="calendar-day-dot"
                ></span>
              </template>
            </el-button>
          </div>
        </el-card>
        <DesktopTable
          :rows="monthAnniversaries"
          :loading="false"
          title="本月纪念日"
        >
          <el-table-column
            prop="title"
            label="纪念日"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {AnniversaryRow} -->
          <el-table-column label="日期" min-width="150">
            <template #default="{ row }: { row: AnniversaryRow }">
              {{ formatMonthDay(row.occurrenceDate) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="statusLabel"
            label="状态"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
        </DesktopTable>
      </div>
      <section v-if="!isMonthView" class="calendar-year-view" aria-label="年历">
        <div class="pc-grid calendar-year-grid">
          <el-button
            v-for="month in overview?.months ?? []"
            :key="month.key"
            :class="['mini-month-card', { active: month.selected }]"
            native-type="button"
            @click="selectYearMonth(month)"
            class="pc-content-button"
          >
            <div class="mini-month-head">
              <strong>{{ month.label }}</strong>
              <span>{{ month.daysInMonth }}天</span>
            </div>
            <div class="mini-weekdays">
              <span
                v-for="label in weekdayLabels"
                :key="`${month.key}-${label}`"
              >
                {{ label }}
              </span>
            </div>
            <div class="pc-grid mini-month-grid">
              <span
                v-for="day in month.days"
                :key="`${month.key}-${day.date}`"
                :title="day.currentMonth ? miniDayTitle(day) : undefined"
                :class="[
                  'mini-month-day',
                  {
                    'is-empty': !day.currentMonth,
                    'is-weekend':
                      day.weekend && day.currentMonth && !day.workdayLabel,
                    'is-holiday': !!day.holidayLabel && day.currentMonth,
                    'is-workday': !!day.workdayLabel && day.currentMonth,
                    'is-selected': day.selected && day.currentMonth,
                  },
                ]"
              >
                <span class="mini-month-number">
                  {{ day.currentMonth ? day.day : '' }}
                </span>
                <span
                  v-if="day.currentMonth && day.workdayLabel"
                  class="mini-month-mark-dot is-workday"
                  aria-hidden="true"
                ></span>
                <span
                  v-else-if="day.currentMonth && day.holidayLabel"
                  :class="[
                    'mini-month-mark-dot',
                    {
                      'is-holiday': !!day.holidayLabel,
                    },
                  ]"
                  aria-hidden="true"
                ></span>
              </span>
            </div>
          </el-button>
        </div>
      </section>
    </template>
  </section>
</template>
