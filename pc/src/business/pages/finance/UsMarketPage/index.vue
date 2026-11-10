<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

// 美股行情页：走 market-service /api/market/us/overview（腾讯快照 + SPY/QQQ 分时折算）。
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import { useRouter } from 'vue-router'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getUsOverview,
  getUsPremarket,
  type UsOverviewIndex,
  type UsPremarketData,
} from '@/business/api/modules/market'
import { useTheme } from '@/business/utils/theme'
import { getErrorMessage } from '@/business/api/request'
import { useVisibilityPolling } from '@/business/composables/useVisibilityPolling'

type IndexConfig = {
  code: string
  name: string
  // 详情页用（纳指100 以 QQQ 代理呈现）
  detailCode: string
}

type IndexCard = {
  code: string
  fallbackName: string
  name: string
  price: number | null
  change: number | null
  changePercent: number | null
  open: number | null
  high: number | null
  low: number | null
  preClose: number | null
  vals: number[]
  error: string
}

// 可自行增减：与后端 UsOverviewService.INDEX_DEFS 对应
const INDEX_CONFIGS: IndexConfig[] = [
  { code: 'sp500', name: '标普500', detailCode: 'sp500' },
  { code: 'ndx100', name: '纳斯达克100', detailCode: 'qqq' },
]

// 美股常规交易时段 9:30–16:00 共 390 分钟，折线横轴按此铺满
const SESSION_MINUTES = 390
// 后端 overview 接口 15s 缓存，30s 轮询足够
const REFRESH_INTERVAL_MS = 30_000

const { isDark } = useTheme()
const router = useRouter()

// 跳转到全球行情详情页（/finance/global-market/detail/us/:code）
function goDetail(code: string) {
  const config = INDEX_CONFIGS.find((item) => item.code === code)
  void router.push(
    `/finance/global-market/detail/us/${config?.detailCode ?? code}`,
  )
}

const cards = reactive<IndexCard[]>(
  INDEX_CONFIGS.map((config) => ({
    code: config.code,
    fallbackName: config.name,
    name: config.name,
    price: null,
    change: null,
    changePercent: null,
    open: null,
    high: null,
    low: null,
    preClose: null,
    vals: [],
    error: '',
  })),
)

const isRefreshing = ref(false)
const isInitialLoading = ref(true)
const premarketData = ref<UsPremarketData | null>(null)
const premarketLoading = ref(false)
const premarketError = ref('')
const nowTime = ref(Date.now())

const showProxyMarketCard = computed(() => {
  if (isUsRegularMarketTime(new Date(nowTime.value))) {
    return false
  }
  const status = premarketData.value?.sessionStatus
  return status !== 'Market Open' && status !== 'Open'
})

const newYorkDateTimeFormatter = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/New_York',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

function isUsRegularMarketTime(date: Date) {
  const parts = Object.fromEntries(
    newYorkDateTimeFormatter
      .formatToParts(date)
      .map((part) => [part.type, part.value]),
  )
  if (parts.weekday === 'Sat' || parts.weekday === 'Sun') {
    return false
  }
  const hour = Number(parts.hour)
  const minute = Number(parts.minute)
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) {
    return false
  }
  const minutes = hour * 60 + minute
  return minutes >= 9 * 60 + 30 && minutes < 16 * 60
}

function clearProxyMarketState() {
  premarketData.value = null
  premarketLoading.value = false
  premarketError.value = ''
}

const chartEls = new Map<string, HTMLDivElement>()
const chartInstances = new Map<string, ECharts>()
let echartsLib: typeof import('@/business/utils/echarts') | null = null

function isUp(card: IndexCard) {
  return (card.change ?? 0) >= 0
}

function toneClass(card: IndexCard) {
  if (card.change == null) {
    return ''
  }
  return isUp(card) ? 'up' : 'down'
}

function formatNumber(value?: number | null) {
  if (value == null || !Number.isFinite(value)) {
    return '--'
  }
  return value.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatChange(card: IndexCard) {
  if (card.change == null || card.changePercent == null) {
    return '--'
  }
  const sign = card.change >= 0 ? '+' : ''
  return `${sign}${formatNumber(card.change)}  ${sign}${card.changePercent.toFixed(1)}%`
}

function formatPremarketChange(index: UsPremarketData['indices'][number]) {
  if (index.changePercent == null || !Number.isFinite(index.changePercent)) {
    return '--'
  }
  const sign = index.changePercent >= 0 ? '+' : ''
  return `${sign}${index.changePercent.toFixed(1)}%`
}

// 把后端聚合数据写入卡片：分时整段替换（每分钟一个点，含盘前时段）
function applyOverview(indices: UsOverviewIndex[]) {
  for (const index of indices) {
    const card = cards.find((item) => item.code === index.code)
    if (!card) {
      continue
    }
    card.error = ''
    card.name = index.name || card.fallbackName
    card.price = index.price ?? null
    card.change = index.change ?? null
    card.changePercent = index.changePercent ?? null
    card.open = index.open ?? null
    card.high = index.high ?? null
    card.low = index.low ?? null
    card.preClose = index.preClose ?? null
    card.vals = (index.points ?? [])
      .map((point) => point.price)
      .filter((value) => Number.isFinite(value))
    renderCard(card)
  }
  for (const card of cards) {
    if (!indices.some((index) => index.code === card.code)) {
      card.error = '暂无行情数据'
    }
  }
}

async function loadOverview() {
  try {
    const data = await getUsOverview()
    applyOverview(data.indices ?? [])
  } catch (error) {
    for (const card of cards) {
      card.error =
        card.vals.length > 0 ? '' : getErrorMessage(error, '加载失败')
    }
  }
}

async function loadPremarket() {
  if (isUsRegularMarketTime(new Date(nowTime.value))) {
    clearProxyMarketState()
    return
  }
  if (premarketLoading.value) {
    return
  }
  premarketLoading.value = true
  premarketError.value = ''
  try {
    premarketData.value = await getUsPremarket()
  } catch (error) {
    if (!premarketData.value) {
      premarketError.value = getErrorMessage(error, '盘前行情加载失败')
    }
  } finally {
    premarketLoading.value = false
  }
}

async function refresh() {
  if (isRefreshing.value) {
    return
  }
  nowTime.value = Date.now()
  isRefreshing.value = true
  try {
    await Promise.all([loadOverview(), loadPremarket()])
  } finally {
    isRefreshing.value = false
    isInitialLoading.value = false
  }
}

function setChartRef(code: string, el: Element | null) {
  if (el) {
    chartEls.set(code, el as HTMLDivElement)
  } else {
    chartEls.delete(code)
  }
}

function buildChartOption(card: IndexCard): EChartsCoreOption {
  const rootStyle = getComputedStyle(document.documentElement)
  const baselineColor = rootStyle.getPropertyValue('--color-chart-axis').trim()
  const dangerColor = rootStyle.getPropertyValue('--color-danger').trim()
  const successColor = rootStyle.getPropertyValue('--color-success').trim()
  const lineColor = isUp(card) ? dangerColor : successColor

  const values = card.vals
  const rangeSource =
    card.preClose != null && Number.isFinite(card.preClose)
      ? [...values, card.preClose]
      : values
  let min = rangeSource.length > 0 ? Math.min(...rangeSource) : 0
  let max = rangeSource.length > 0 ? Math.max(...rangeSource) : 1
  if (min === max) {
    min -= 1
    max += 1
  }

  // 分时每分钟一个点，横轴按美股整段交易时段（390 分钟）铺满；
  // QQQ 代理含盘前点，超出 390 分钟时扩展横轴。
  const points = values.map((value, index) => [index, value])

  return {
    animation: false,
    grid: { left: 2, right: 2, top: 6, bottom: 6 },
    xAxis: {
      type: 'value',
      show: false,
      min: 0,
      max: Math.max(SESSION_MINUTES, values.length),
    },
    yAxis: {
      type: 'value',
      show: false,
      min,
      max,
    },
    series: [
      {
        type: 'line',
        showSymbol: false,
        data: points,
        lineStyle: { width: 1.5, color: lineColor },
        areaStyle: { color: lineColor, opacity: 0.12 },
        markLine:
          card.preClose != null && Number.isFinite(card.preClose)
            ? {
                silent: true,
                symbol: 'none',
                lineStyle: {
                  type: 'dashed',
                  color: baselineColor,
                  width: 1,
                  opacity: 0.6,
                },
                label: { show: false },
                data: [{ yAxis: card.preClose }],
              }
            : undefined,
      },
    ],
  }
}

function renderCard(card: IndexCard) {
  if (!echartsLib || card.vals.length < 2) {
    return
  }
  const el = chartEls.get(card.code)
  if (!el) {
    return
  }
  let instance = chartInstances.get(card.code)
  if (!instance) {
    instance = echartsLib.init(el)
    chartInstances.set(card.code, instance)
  }
  instance.setOption(buildChartOption(card), true)
}

function renderAllCards() {
  cards.forEach((card) => renderCard(card))
}

// 可见性感知轮询：隐藏时跳过，恢复可见立即刷新
useVisibilityPolling(() => refresh(), REFRESH_INTERVAL_MS)

function onResize() {
  chartInstances.forEach((instance) => instance.resize())
}

onMounted(async () => {
  echartsLib = await import('@/business/utils/echarts')
  await refresh()
  renderAllCards()
  window.addEventListener('resize', onResize)
})

// 主题切换时重绘折线，保持涨跌配色与当前主题一致
watch(isDark, () => {
  renderAllCards()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chartInstances.forEach((instance) => instance.dispose())
  chartInstances.clear()
  chartEls.clear()
})

type ProxyRow = NonNullable<typeof premarketData.value>['indices'][number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="美股行情">
      <template #right>
        <el-button :loading="isRefreshing" @click="refresh">刷新行情</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="premarketError"
      :title="premarketError"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="loadPremarket">重试</el-button>
    </el-alert>
    <template v-if="premarketData && showProxyMarketCard">
      <DesktopTable
        :rows="premarketData.indices"
        :loading="false"
        title="ETF 代理行情"
      >
        <el-table-column
          prop="indexName"
          label="指数"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <el-table-column
          prop="proxySymbol"
          label="代理 ETF"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <!-- @vue-generic {ProxyRow} -->
        <el-table-column label="价格" min-width="150">
          <template #default="{ row }: { row: ProxyRow }">
            {{ formatNumber(row.price) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {ProxyRow} -->
        <el-table-column label="涨跌" min-width="150">
          <template #default="{ row }: { row: ProxyRow }">
            {{ formatPremarketChange(row) }}
          </template>
        </el-table-column>
      </DesktopTable>
    </template>
    <div class="desktop-market-charts">
      <el-card
        v-for="card in cards"
        :key="card.code"
        class="pc-record-card market-card"
        :class="toneClass(card)"
        role="link"
        tabindex="0"
        :aria-label="`查看${card.name}行情详情`"
        @click="goDetail(card.code)"
        @keydown.enter="goDetail(card.code)"
        shadow="never"
      >
        <header class="market-card-head">
          <span class="market-name">{{ card.name }}</span>
          <span class="market-code">{{ card.code }}</span>
        </header>
        <p class="market-price">{{ formatNumber(card.price) }}</p>
        <p class="market-change">{{ formatChange(card) }}</p>
        <div
          :ref="(el) => setChartRef(card.code, el as Element | null)"
          class="pc-chart market-chart"
        />
        <p v-if="card.error" class="market-error">加载失败：{{ card.error }}</p>
        <div v-else class="market-detail">
          <span>
            今开
            <b>{{ formatNumber(card.open) }}</b>
          </span>
          <span>
            昨收
            <b>{{ formatNumber(card.preClose) }}</b>
          </span>
          <span>
            最高
            <b>{{ formatNumber(card.high) }}</b>
          </span>
          <span>
            最低
            <b>{{ formatNumber(card.low) }}</b>
          </span>
        </div>
      </el-card>
    </div>
  </section>
</template>
