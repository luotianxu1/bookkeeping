<script setup lang="ts">
// 美股行情页：走 market-service /api/market/us/overview（腾讯快照 + SPY/QQQ 分时折算）。
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import { useRouter } from 'vue-router'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonSkeleton from '@/components/common/CommonSkeleton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  getUsOverview,
  getUsPremarket,
  type UsOverviewIndex,
  type UsPremarketData,
} from '@/api/modules/finance'
import { useTheme } from '@/utils/theme'

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
  void router.push(`/finance/global-market/detail/us/${config?.detailCode ?? code}`)
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

const sessionTitle = computed(() => {
  switch (premarketData.value?.sessionStatus) {
    case 'Pre-Market':
      return '盘前行情'
    case 'Market Open':
    case 'Open':
      return '常规行情'
    case 'After-Hours':
      return '盘后行情'
    case 'Market Closed':
    case 'Closed':
      return '最近行情'
    default:
      return 'ETF代理行情'
  }
})

const premarketFootText = computed(() => {
  switch (premarketData.value?.sessionStatus) {
    case 'Pre-Market':
      return 'SPY / QQQ 代理盘前方向，每30秒更新'
    case 'Market Open':
    case 'Open':
      return 'SPY / QQQ 代理常规交易方向，每30秒更新'
    case 'After-Hours':
      return 'SPY / QQQ 代理盘后方向，每30秒更新'
    default:
      return 'SPY / QQQ 代理指数方向，每30秒更新'
  }
})

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
    newYorkDateTimeFormatter.formatToParts(date).map((part) => [part.type, part.value]),
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
let echartsLib: (typeof import('echarts')) | null = null
let refreshTimer: number | null = null

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
  return `${sign}${formatNumber(card.change)}  ${sign}${card.changePercent.toFixed(2)}%`
}

function formatPremarketChange(index: UsPremarketData['indices'][number]) {
  if (index.changePercent == null || !Number.isFinite(index.changePercent)) {
    return '--'
  }
  const sign = index.changePercent >= 0 ? '+' : ''
  return `${sign}${index.changePercent.toFixed(2)}%`
}

function premarketTone(index: UsPremarketData['indices'][number]) {
  if (index.changePercent == null || !Number.isFinite(index.changePercent)) {
    return ''
  }
  return index.changePercent >= 0 ? 'up' : 'down'
}

function formatPremarketTime(value?: string | null) {
  if (!value) {
    return '--:--'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--:--'
  }
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
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
      card.error = card.vals.length > 0 ? '' : error instanceof Error ? error.message : '加载失败'
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
      premarketError.value = error instanceof Error ? error.message : '盘前行情加载失败'
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
  const rangeSource = card.preClose != null && Number.isFinite(card.preClose)
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
        markLine: card.preClose != null && Number.isFinite(card.preClose)
          ? {
            silent: true,
            symbol: 'none',
            lineStyle: { type: 'dashed', color: baselineColor, width: 1, opacity: 0.6 },
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

function onResize() {
  chartInstances.forEach((instance) => instance.resize())
}

function startRefreshTimer() {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer)
  }
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState !== 'visible') {
      return
    }
    void refresh()
  }, REFRESH_INTERVAL_MS)
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void refresh()
  }
}

onMounted(async () => {
  echartsLib = await import('echarts')
  await refresh()
  renderAllCards()
  startRefreshTimer()
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('resize', onResize)
})

// 主题切换时重绘折线，保持涨跌配色与当前主题一致
watch(isDark, () => {
  renderAllCards()
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('resize', onResize)
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
  chartInstances.forEach((instance) => instance.dispose())
  chartInstances.clear()
  chartEls.clear()
})
</script>

<template>
  <section class="us-market-page" aria-label="美股行情">
    <header class="us-market-header">
      <PageHeader title="美股行情" back-label="返回更多功能">
        <template #right>
          <CommonHeaderRefreshButton
            label="刷新行情"
            :loading="isRefreshing"
            @click="refresh"
          />
        </template>
      </PageHeader>
    </header>

    <section v-if="premarketData && showProxyMarketCard" class="premarket-card" aria-label="美股ETF代理行情">
      <header class="premarket-heading">
        <div>
          <h2>{{ sessionTitle }}</h2>
          <span class="premarket-session">{{ premarketData.sessionLabel }}</span>
        </div>
        <time :datetime="premarketData.updatedAt">{{ formatPremarketTime(premarketData.updatedAt) }}</time>
      </header>

      <div class="premarket-index-grid">
        <article
          v-for="index in premarketData.indices"
          :key="index.indexCode"
          :class="premarketTone(index)"
        >
          <header>
            <div>
              <strong>{{ index.proxySymbol }} · {{ index.indexName }}代理</strong>
            </div>
            <span class="premarket-change">{{ formatPremarketChange(index) }}</span>
          </header>
          <p class="premarket-price">{{ formatNumber(index.price) }}</p>
        </article>
      </div>

      <footer>
        <span>{{ premarketFootText }}</span>
        <span>{{ premarketData.source }}</span>
      </footer>
    </section>

    <div v-else-if="!premarketData && premarketLoading" class="premarket-placeholder" aria-live="polite">
      盘前行情加载中…
    </div>
    <div v-else-if="!premarketData && premarketError" class="premarket-error" role="alert">
      <span>{{ premarketError }}</span>
      <button type="button" @click="loadPremarket">重新加载</button>
    </div>

    <CommonSkeleton v-if="isInitialLoading" variant="card" :rows="2" />

    <div v-else class="us-market-grid">
      <article
        v-for="card in cards"
        :key="card.code"
        class="market-card"
        :class="toneClass(card)"
        role="link"
        tabindex="0"
        :aria-label="`查看${card.name}行情详情`"
        @click="goDetail(card.code)"
        @keydown.enter="goDetail(card.code)"
      >
        <header class="market-card-head">
          <span class="market-name">{{ card.name }}</span>
          <span class="market-code">{{ card.code }}</span>
        </header>

        <p class="market-price">{{ formatNumber(card.price) }}</p>
        <p class="market-change">{{ formatChange(card) }}</p>

        <div
          :ref="(el) => setChartRef(card.code, el as Element | null)"
          class="market-chart"
        />

        <p v-if="card.error" class="market-error">加载失败：{{ card.error }}</p>
        <div v-else class="market-detail">
          <span>今开 <b>{{ formatNumber(card.open) }}</b></span>
          <span>昨收 <b>{{ formatNumber(card.preClose) }}</b></span>
          <span>最高 <b>{{ formatNumber(card.high) }}</b></span>
          <span>最低 <b>{{ formatNumber(card.low) }}</b></span>
        </div>
      </article>
    </div>

    <footer class="us-market-foot">
      指数来源：market-service（腾讯快照 + SPY/QQQ 分时折算，纳指100为QQQ代理）；ETF代理行情来源：Nasdaq公开行情。数据可能延迟，不构成投资建议。
    </footer>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
