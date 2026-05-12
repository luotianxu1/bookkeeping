<script setup lang="ts">
// 收支分析（月）页：按选中设计稿实现，并使用 ECharts 绘制饼图与趋势图。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import MonthPicker from '@/components/common/MonthPicker/index.vue'
import YearPicker from '@/components/common/YearPicker/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'

const period = ref('月')
const periodOptions = ['月', '年']
const activeMonth = ref('2026-03')
const activeYear = ref(2026)
const summaryTab = ref('支出')
const summaryCards = [
  { label: '收入', amount: '6,200' },
  { label: '支出', amount: '4,780' },
  { label: '结余', amount: '1,420' },
]
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const categoryBreakdown = [
  { key: 'food', icon: '🍽', name: '餐饮', percent: 48, amount: '2,290', count: '32笔', color: '#2563EB', bar: '#93C5FD' },
  { key: 'daily', icon: '🧴', name: '日用', percent: 24, amount: '1,150', count: '19笔', color: '#0284C7', bar: '#7DD3FC' },
  { key: 'entertainment', icon: '🎬', name: '娱乐', percent: 17, amount: '820', count: '14笔', color: '#0891B2', bar: '#67E8F9' },
  { key: 'others', icon: '🧩', name: '其他', percent: 11, amount: '520', count: '9笔', color: '#64748B', bar: '#CBD5E1' },
]

const selectedDay = ref('21')

const calendarRows = [
  [
    { day: '', amount: '' },
    { day: '', amount: '' },
    { day: '', amount: '' },
    { day: '', amount: '' },
    { day: '', amount: '' },
    { day: '1', amount: '-12', trend: 'expense' },
    { day: '2', amount: '+80', trend: 'income' },
  ],
  [
    { day: '3', amount: '-66', trend: 'expense' },
    { day: '4', amount: '+20', trend: 'income' },
    { day: '5', amount: '-18', trend: 'expense' },
    { day: '6', amount: '+15', trend: 'income' },
    { day: '7', amount: '-34', trend: 'expense' },
    { day: '8', amount: '+0', trend: 'neutral' },
    { day: '9', amount: '-22', trend: 'expense' },
  ],
  [
    { day: '10', amount: '+56', trend: 'income' },
    { day: '11', amount: '-31', trend: 'expense' },
    { day: '12', amount: '+12', trend: 'income' },
    { day: '13', amount: '-49', trend: 'expense' },
    { day: '14', amount: '+0', trend: 'neutral' },
    { day: '15', amount: '-27', trend: 'expense' },
    { day: '16', amount: '+10', trend: 'income' },
  ],
  [
    { day: '17', amount: '-46', trend: 'expense' },
    { day: '18', amount: '+22', trend: 'income' },
    { day: '19', amount: '-30', trend: 'expense' },
    { day: '20', amount: '+0', trend: 'neutral' },
    { day: '21', amount: '-136', trend: 'expense' },
    { day: '22', amount: '+14', trend: 'income' },
    { day: '23', amount: '-12', trend: 'expense' },
  ],
  [
    { day: '24', amount: '+44', trend: 'income' },
    { day: '25', amount: '-18', trend: 'expense' },
    { day: '26', amount: '+8', trend: 'income' },
    { day: '27', amount: '-26', trend: 'expense' },
    { day: '28', amount: '+18', trend: 'income' },
    { day: '29', amount: '-41', trend: 'expense' },
    { day: '30', amount: '+7', trend: 'income' },
  ],
]

const dayDetailMap: Record<string, { totalExpense: string; rows: { title: string; time: string; amount: string }[] }> = {
  '21': {
    totalExpense: '-136',
    rows: [
      { title: '晚饭', time: '餐饮 · 18:42', amount: '-68' },
      { title: '打车', time: '交通 · 13:16', amount: '-42' },
      { title: '奶茶', time: '餐饮 · 09:45', amount: '-26' },
    ],
  },
  '22': {
    totalExpense: '-14',
    rows: [
      { title: '公交', time: '交通 · 09:10', amount: '-2' },
      { title: '午餐', time: '餐饮 · 12:25', amount: '-12' },
    ],
  },
}

const currentDayDetail = computed(() => dayDetailMap[selectedDay.value] ?? { totalExpense: '-0', rows: [] })
const selectedYearMonth = ref('3月')
const yearGridRows = [
  ['1月', '2月', '3月'],
  ['4月', '5月', '6月'],
  ['7月', '8月', '9月'],
  ['10月', '11月', '12月'],
]

function selectCalendarDay(day: string) {
  if (!day) return
  selectedDay.value = day
}

function selectYearMonth(month: string) {
  selectedYearMonth.value = month
}

const pieRef = ref<HTMLDivElement | null>(null)
const lineRef = ref<HTMLDivElement | null>(null)
let echartsLib: (typeof import('echarts')) | null = null
let pieChart: ECharts | null = null
let lineChart: ECharts | null = null

const pieOption: EChartsCoreOption = {
  animation: false,
  tooltip: { trigger: 'item' },
  color: ['#2563EB', '#64748B', '#CBD5E1', '#A3E635'],
  series: [
    {
      type: 'pie',
      radius: ['46%', '72%'],
      center: ['50%', '50%'],
      label: { show: false },
      data: [
        { value: 36, name: '餐饮' },
        { value: 26, name: '交通' },
        { value: 22, name: '日用' },
        { value: 16, name: '其他' },
      ],
    },
  ],
}

const lineOption: EChartsCoreOption = {
  animation: false,
  grid: { left: 24, right: 12, top: 14, bottom: 22 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: ['1', '5', '10', '15', '20', '25', '30'],
    axisLine: { lineStyle: { color: '#CBD5E1' } },
    axisTick: { show: false },
    axisLabel: { color: '#94A3B8', fontSize: 11 },
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    splitNumber: 3,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { show: false },
    splitLine: { lineStyle: { color: '#E2E8F0' } },
  },
  series: [
    {
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: [72, 66, 74, 63, 71, 58, 68],
      lineStyle: { width: 3, color: '#2563EB' },
    },
  ],
}

const yearLineOption: EChartsCoreOption = {
  animation: false,
  grid: { left: 24, right: 12, top: 14, bottom: 22 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: ['1月', '3月', '5月', '7月', '9月', '11月', '12月'],
    axisLine: { lineStyle: { color: '#CBD5E1' } },
    axisTick: { show: false },
    axisLabel: { color: '#94A3B8', fontSize: 11 },
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    splitNumber: 3,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { show: false },
    splitLine: { lineStyle: { color: '#E2E8F0' } },
  },
  series: [
    {
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: [62, 55, 71, 64, 76, 61, 79],
      lineStyle: { width: 3, color: '#2563EB' },
    },
  ],
}

async function ensureEcharts() {
  if (!echartsLib) {
    echartsLib = await import('echarts')
  }
  return echartsLib
}

function renderPie() {
  if (!pieRef.value) return
  pieChart = echartsLib!.init(pieRef.value)
  pieChart.setOption(pieOption)
}

function renderLine() {
  if (!lineRef.value) return
  lineChart = echartsLib!.init(lineRef.value)
  lineChart.setOption(period.value === '年' ? yearLineOption : lineOption)
}

function handleResize() {
  pieChart?.resize()
  lineChart?.resize()
}

onMounted(async () => {
  await ensureEcharts()
  renderPie()
  renderLine()
  window.addEventListener('resize', handleResize)
})

watch(period, () => {
  if (!lineChart) return
  lineChart.setOption(period.value === '年' ? yearLineOption : lineOption, true)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose()
  lineChart?.dispose()
})
</script>

<template>
  <section class="analysis-page" aria-label="收支分析">
    <PageHeader title="收支分析" back-to="/finance" back-label="返回财务首页" />

    <SegmentedControl v-model="period" :options="periodOptions" label="月年切换" />

    <MonthPicker v-if="period === '月'" v-model="activeMonth" />
    <YearPicker v-else v-model="activeYear" />

    <section class="summary-switch" aria-label="收支筛选">
      <button
        v-for="item in summaryCards"
        :key="item.label"
        type="button"
        :class="['summary-item', { active: summaryTab === item.label }]"
        @click="summaryTab = item.label"
      >
        <strong>{{ item.label }}</strong>
        <AmountText tag="span" :value="item.amount" />
      </button>
    </section>

    <section class="card">
      <header class="card-head">
        <strong>分类占比</strong>
      </header>
      <div ref="pieRef" class="pie-chart"></div>
      <div class="breakdown-list">
        <article v-for="item in categoryBreakdown" :key="item.key" class="breakdown-item">
          <div class="breakdown-head">
            <div class="breakdown-left" :style="{ color: item.color }">
              <span>{{ item.icon }}</span>
              <strong>{{ item.name }}</strong>
            </div>
            <span class="breakdown-right" :style="{ color: item.color }">
              {{ item.percent }}%&nbsp;
              <AmountText tag="span" :value="item.amount" />
              &nbsp;{{ item.count }}
            </span>
          </div>
          <div class="breakdown-track">
            <span :style="{ width: `${item.percent}%`, background: item.bar }"></span>
          </div>
        </article>
      </div>
    </section>

    <section class="card">
      <header class="card-head">
        <strong>{{ period === '年' ? '年度趋势' : '月度趋势' }}</strong>
      </header>
      <div ref="lineRef" class="line-chart"></div>
    </section>

    <section v-if="period === '月'" class="card">
      <header class="card-head split">
        <strong>月度汇总</strong>
        <span>2026年3月</span>
      </header>

      <div class="calendar-week">
        <span v-for="day in weekdays" :key="day">{{ day }}</span>
      </div>

      <div class="calendar-grid">
        <div v-for="(week, weekIndex) in calendarRows" :key="`w-${weekIndex}`" class="calendar-row">
          <button
            v-for="cell in week"
            :key="`${weekIndex}-${cell.day || 'blank'}`"
            type="button"
            :class="[
              'calendar-cell',
              `trend-${cell.trend ?? 'neutral'}`,
              { active: cell.day === selectedDay, empty: !cell.day },
            ]"
            @click="selectCalendarDay(cell.day)"
          >
            <span class="day">{{ cell.day }}</span>
            <AmountText tag="span" class="amount" :value="cell.amount" />
          </button>
        </div>
      </div>

      <div class="day-detail">
        <header>
          <strong>3月{{ selectedDay }}日</strong>
          <span>总支出 {{ currentDayDetail.totalExpense }}</span>
        </header>
        <article v-for="row in currentDayDetail.rows" :key="`${row.title}-${row.time}`" class="day-detail-row">
          <div>
            <strong>{{ row.title }}</strong>
            <span>{{ row.time }}</span>
          </div>
          <AmountText tag="strong" class="expense" :value="row.amount" />
        </article>
      </div>
    </section>

    <section v-else class="card">
      <header class="card-head split">
        <strong>年度汇总</strong>
        <span>{{ activeYear }}年</span>
      </header>

      <div class="year-grid">
        <div v-for="(row, rowIndex) in yearGridRows" :key="`yr-${rowIndex}`" class="year-grid-row">
          <button
            v-for="month in row"
            :key="month"
            type="button"
            :class="['year-grid-item', { active: selectedYearMonth === month }]"
            @click="selectYearMonth(month)"
          >
            <strong>{{ month }}</strong>
            <span>支出 {{ month === '3月' ? '-136' : '-0' }}</span>
          </button>
        </div>
      </div>

      <div class="day-detail">
        <header>
          <strong>{{ selectedYearMonth }}</strong>
          <span>总支出 -136</span>
        </header>
        <article v-for="row in dayDetailMap['21'].rows" :key="`${row.title}-${row.time}`" class="day-detail-row">
          <div>
            <strong>{{ row.title }}</strong>
            <span>{{ row.time }}</span>
          </div>
          <AmountText tag="strong" class="expense" :value="row.amount" />
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
