<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import CommonBottomSheet from '@/components/common/CommonBottomSheet/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'

interface DateTimeParts {
  year: number
  month: number
  day: number
  hour: number
  minute: number
}

interface CalendarDay extends DateTimeParts {
  key: string
  currentMonth: boolean
  selected: boolean
  today: boolean
}

const props = withDefaults(defineProps<{
  modelValue: string
  label?: string
  disabled?: boolean
}>(), {
  label: '选择日期和时间',
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const triggerRef = ref<HTMLButtonElement | null>(null)
const hourListRef = ref<HTMLDivElement | null>(null)
const minuteListRef = ref<HTMLDivElement | null>(null)
const isOpen = ref(false)
const draftYear = ref(0)
const draftMonth = ref(0)
const draftDay = ref(0)
const draftHour = ref(0)
const draftMinute = ref(0)
const visibleYear = ref(0)
const visibleMonth = ref(0)

const weekDays = ['日', '一', '二', '三', '四', '五', '六']
const hourOptions = Array.from({ length: 24 }, (_, index) => index)
const minuteOptions = Array.from({ length: 60 }, (_, index) => index)

const displayText = computed(() => formatDisplayDateTime(parseDateTime(props.modelValue)))
const visibleMonthText = computed(() => `${visibleYear.value}年${visibleMonth.value}月`)
const selectedTimeText = computed(() => (
  `${padNumber(draftHour.value)}:${padNumber(draftMinute.value)}`
))
const calendarDays = computed<CalendarDay[]>(() => {
  const firstDay = new Date(visibleYear.value, visibleMonth.value - 1, 1)
  const startDate = new Date(visibleYear.value, visibleMonth.value - 1, 1 - firstDay.getDay())
  const today = new Date()

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate() + index)
    const year = date.getFullYear()
    const month = date.getMonth() + 1
    const day = date.getDate()
    return {
      key: `${year}-${padNumber(month)}-${padNumber(day)}`,
      year,
      month,
      day,
      hour: draftHour.value,
      minute: draftMinute.value,
      currentMonth: year === visibleYear.value && month === visibleMonth.value,
      selected: year === draftYear.value && month === draftMonth.value && day === draftDay.value,
      today: year === today.getFullYear()
        && month === today.getMonth() + 1
        && day === today.getDate(),
    }
  })
})

function openPicker() {
  if (props.disabled) {
    return
  }
  applyDraft(parseDateTime(props.modelValue))
  isOpen.value = true
  void scrollSelectedTimeIntoView()
}

function closePicker() {
  isOpen.value = false
  restoreTriggerFocus()
}

function confirmSelection() {
  emit('update:modelValue', formatDateTime({
    year: draftYear.value,
    month: draftMonth.value,
    day: draftDay.value,
    hour: draftHour.value,
    minute: draftMinute.value,
  }))
  closePicker()
}

function applyDraft(parts: DateTimeParts) {
  draftYear.value = parts.year
  draftMonth.value = parts.month
  draftDay.value = parts.day
  draftHour.value = parts.hour
  draftMinute.value = parts.minute
  visibleYear.value = parts.year
  visibleMonth.value = parts.month
}

function selectDay(day: CalendarDay) {
  draftYear.value = day.year
  draftMonth.value = day.month
  draftDay.value = day.day
  visibleYear.value = day.year
  visibleMonth.value = day.month
}

function selectToday() {
  const now = new Date()
  applyDraft({
    year: now.getFullYear(),
    month: now.getMonth() + 1,
    day: now.getDate(),
    hour: now.getHours(),
    minute: now.getMinutes(),
  })
  void scrollSelectedTimeIntoView()
}

function shiftMonth(offset: number) {
  const nextMonth = new Date(visibleYear.value, visibleMonth.value - 1 + offset, 1)
  visibleYear.value = nextMonth.getFullYear()
  visibleMonth.value = nextMonth.getMonth() + 1
}

async function scrollSelectedTimeIntoView() {
  await nextTick()
  centerTimeOption(hourListRef.value, draftHour.value)
  centerTimeOption(minuteListRef.value, draftMinute.value)
}

function centerTimeOption(list: HTMLDivElement | null, value: number) {
  const option = list?.querySelector<HTMLElement>(`[data-time-value="${value}"]`)
  if (!list || !option) {
    return
  }
  const listRect = list.getBoundingClientRect()
  const optionRect = option.getBoundingClientRect()
  list.scrollTop += optionRect.top - listRect.top - (listRect.height - optionRect.height) / 2
}

function restoreTriggerFocus() {
  void nextTick(() => triggerRef.value?.focus())
}

function parseDateTime(value: string): DateTimeParts {
  const matched = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value)
  if (matched) {
    const parts = {
      year: Number(matched[1]),
      month: Number(matched[2]),
      day: Number(matched[3]),
      hour: Number(matched[4]),
      minute: Number(matched[5]),
    }
    const date = new Date(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute)
    if (
      date.getFullYear() === parts.year
      && date.getMonth() + 1 === parts.month
      && date.getDate() === parts.day
      && date.getHours() === parts.hour
      && date.getMinutes() === parts.minute
    ) {
      return parts
    }
  }

  const now = new Date()
  return {
    year: now.getFullYear(),
    month: now.getMonth() + 1,
    day: now.getDate(),
    hour: now.getHours(),
    minute: now.getMinutes(),
  }
}

function formatDateTime(parts: DateTimeParts) {
  return `${parts.year}-${padNumber(parts.month)}-${padNumber(parts.day)}T${padNumber(parts.hour)}:${padNumber(parts.minute)}`
}

function formatDisplayDateTime(parts: DateTimeParts) {
  return `${parts.year}/${padNumber(parts.month)}/${padNumber(parts.day)} ${padNumber(parts.hour)}:${padNumber(parts.minute)}`
}

function padNumber(value: number) {
  return String(value).padStart(2, '0')
}
</script>

<template>
  <div class="common-date-time-picker">
    <button
      ref="triggerRef"
      type="button"
      class="common-date-time-picker-trigger"
      :disabled="disabled"
      aria-haspopup="dialog"
      :aria-expanded="isOpen"
      :aria-label="`${label}，当前为${displayText}`"
      @click="openPicker"
    >
      <span>{{ displayText }}</span>
      <i class="common-date-time-picker-chevron" aria-hidden="true"></i>
    </button>

    <CommonBottomSheet
      v-model="isOpen"
      :title="label"
      compact
      @close="restoreTriggerFocus"
    >
      <div class="common-date-time-picker-content">
        <section class="common-date-time-picker-calendar" aria-label="选择日期">
          <header class="common-date-time-picker-month-header">
            <button type="button" aria-label="上一个月" @click="shiftMonth(-1)">
              <i class="common-date-time-picker-nav-icon previous" aria-hidden="true"></i>
            </button>
            <strong>{{ visibleMonthText }}</strong>
            <button type="button" aria-label="下一个月" @click="shiftMonth(1)">
              <i class="common-date-time-picker-nav-icon next" aria-hidden="true"></i>
            </button>
          </header>

          <div class="common-date-time-picker-weekdays" aria-hidden="true">
            <span v-for="weekDay in weekDays" :key="weekDay">{{ weekDay }}</span>
          </div>

          <div class="common-date-time-picker-days">
            <button
              v-for="day in calendarDays"
              :key="day.key"
              type="button"
              :class="[
                'common-date-time-picker-day',
                {
                  muted: !day.currentMonth,
                  selected: day.selected,
                  today: day.today,
                },
              ]"
              :aria-label="`${day.year}年${day.month}月${day.day}日`"
              :aria-pressed="day.selected"
              @click="selectDay(day)"
            >
              {{ day.day }}
            </button>
          </div>
        </section>

        <section class="common-date-time-picker-time" aria-label="选择具体时间">
          <header>
            <div>
              <strong>具体时间</strong>
              <span aria-live="polite">{{ selectedTimeText }}</span>
            </div>
            <button type="button" @click="selectToday">现在</button>
          </header>

          <div class="common-date-time-picker-time-grid">
            <div class="common-date-time-picker-time-column">
              <span>时</span>
              <div ref="hourListRef" class="common-date-time-picker-time-list" role="listbox" aria-label="小时">
                <button
                  v-for="hour in hourOptions"
                  :key="hour"
                  type="button"
                  role="option"
                  :data-time-value="hour"
                  :aria-selected="hour === draftHour"
                  :class="{ selected: hour === draftHour }"
                  @click="draftHour = hour"
                >
                  {{ padNumber(hour) }}
                </button>
              </div>
            </div>

            <span class="common-date-time-picker-time-separator" aria-hidden="true">:</span>

            <div class="common-date-time-picker-time-column">
              <span>分</span>
              <div ref="minuteListRef" class="common-date-time-picker-time-list" role="listbox" aria-label="分钟">
                <button
                  v-for="minute in minuteOptions"
                  :key="minute"
                  type="button"
                  role="option"
                  :data-time-value="minute"
                  :aria-selected="minute === draftMinute"
                  :class="{ selected: minute === draftMinute }"
                  @click="draftMinute = minute"
                >
                  {{ padNumber(minute) }}
                </button>
              </div>
            </div>
          </div>
        </section>
      </div>

      <template #footer>
        <div class="common-date-time-picker-actions">
          <CommonButton variant="secondary" @click="closePicker">取消</CommonButton>
          <CommonButton @click="confirmSelection">确定</CommonButton>
        </div>
      </template>
    </CommonBottomSheet>
  </div>
</template>

<style scoped lang="scss" src="./style.scss"></style>
