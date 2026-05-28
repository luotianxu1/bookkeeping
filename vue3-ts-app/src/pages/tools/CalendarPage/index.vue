<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import {
  getCalendarOverview,
  type CalendarAnniversaryNote,
  type CalendarDay,
  type CalendarMonth,
  type CalendarOverview,
  type CalendarOverviewView,
} from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

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
const isLoading = ref(false)
const pageError = ref('')
let requestSerial = 0

const monthAnniversaries = computed(() => overview.value?.anniversaries ?? [])
const isMonthView = computed(() => viewMode.value === 'month')
const periodLabel = computed(() => overview.value?.title ?? (isMonthView.value ? formatMonthTitle(monthAnchor.value) : yearAnchor.value))

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

    await router.replace({
      query: buildRouteQuery(),
    })
  } catch (error) {
    if (currentRequest !== requestSerial) {
      return
    }
    pageError.value = error instanceof Error ? error.message : '日历加载失败'
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
    monthAnchor.value = selectedDate.value?.slice(0, 7) ?? `${yearAnchor.value}-01`
    selectedDate.value = resolvePreferredSelectedDate(monthAnchor.value)
  }

  viewMode.value = normalized
  void loadOverview()
}

function openYearView() {
  if (!isMonthView.value) {
    return
  }
  switchView('year')
}

function shiftPeriod(offset: number) {
  if (isMonthView.value) {
    const [yearText, monthText] = monthAnchor.value.split('-')
    const date = new Date(Number(yearText), Number(monthText) - 1 + offset, 1)
    monthAnchor.value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
    selectedDate.value = resolvePreferredSelectedDate(monthAnchor.value)
  } else {
    yearAnchor.value = String(Number(yearAnchor.value) + offset)
    const fallbackMonth = selectedDate.value ? selectedDate.value.slice(5, 7) : '01'
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
  const raw = String(route.query.view ?? '').trim().toLowerCase()
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

function noteStatusClass(item: CalendarAnniversaryNote) {
  if (item.daysOffset === 0) {
    return 'is-today'
  }
  return item.daysOffset > 0 ? 'is-upcoming' : 'is-past'
}
</script>

<template>
  <section class="calendar-page" aria-label="日历页面">
    <PageHeader title="日历" back-to="/tools" back-label="返回工具页" />

    <div class="calendar-top-tabs">
      <SegmentedControl
        :model-value="viewMode"
        :options="viewOptions"
        label="日历视图切换"
        variant="surface"
        @update:model-value="switchView"
      />
    </div>

    <div class="calendar-period-bar">
      <button type="button" class="calendar-period-arrow" @click="shiftPeriod(-1)">‹</button>
      <button
        v-if="isMonthView"
        type="button"
        class="calendar-period-title is-button"
        @click="openYearView"
      >
        {{ periodLabel }}
      </button>
      <span v-else class="calendar-period-title">{{ periodLabel }}</span>
      <button type="button" class="calendar-period-arrow" @click="shiftPeriod(1)">›</button>
    </div>

    <CommonLoading v-if="isLoading" text="日历加载中..." />
    <p v-else-if="pageError" class="calendar-message calendar-message-error">{{ pageError }}</p>

    <template v-else>
      <section v-if="isMonthView" class="calendar-month-view">
        <section class="calendar-month-card" aria-label="月历">
          <div class="calendar-weekdays">
            <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
          </div>

          <div class="calendar-grid">
            <button
              v-for="day in overview?.days ?? []"
              :key="day.date"
              type="button"
              :class="[
                'calendar-day-cell',
                {
                  'is-outside': !day.currentMonth,
                  'is-selected': day.selected,
                  'is-weekend': day.weekend,
                  'has-anniversary': day.anniversaryCount > 0,
                },
              ]"
              @click="selectMonthDay(day)"
            >
              <template v-if="day.currentMonth">
                <span class="calendar-day-number">{{ day.day }}</span>
                <span class="calendar-day-lunar">{{ formatLunarLabel(day.date) }}</span>
                <span v-if="day.anniversaryCount > 0" class="calendar-day-dot"></span>
              </template>
            </button>
          </div>
        </section>

        <section class="calendar-notes-card" aria-label="本月纪念日">
          <strong>本月纪念日</strong>
          <p v-if="monthAnniversaries.length === 0" class="calendar-empty">本月还没有纪念日</p>
          <article
            v-for="item in monthAnniversaries"
            :key="item.id"
            class="calendar-note-item"
          >
            <div class="calendar-note-main">
              <strong>{{ item.title }}</strong>
              <p v-if="item.remark">{{ item.remark }}</p>
            </div>
            <div class="calendar-note-side">
              <span>{{ formatMonthDay(item.occurrenceDate) }}</span>
              <b :class="noteStatusClass(item)">{{ item.statusLabel }}</b>
            </div>
          </article>
        </section>
      </section>

      <section v-else class="calendar-year-view" aria-label="年历">
        <div class="calendar-year-grid">
          <button
            v-for="month in overview?.months ?? []"
            :key="month.key"
            :class="['mini-month-card', { active: month.selected }]"
            type="button"
            @click="selectYearMonth(month)"
          >
            <div class="mini-month-head">
              <strong>{{ month.label }}</strong>
              <span>{{ month.daysInMonth }}天</span>
            </div>

            <div class="mini-weekdays">
              <span v-for="label in weekdayLabels" :key="`${month.key}-${label}`">{{ label }}</span>
            </div>

            <div class="mini-month-grid">
              <span
                v-for="day in month.days"
                :key="`${month.key}-${day.date}`"
                :class="[
                  'mini-month-day',
                  {
                    'is-empty': !day.currentMonth,
                    'is-weekend': day.weekend && day.currentMonth,
                    'is-selected': day.selected,
                  },
                ]"
              >
                {{ day.currentMonth ? day.day : '' }}
              </span>
            </div>
          </button>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
