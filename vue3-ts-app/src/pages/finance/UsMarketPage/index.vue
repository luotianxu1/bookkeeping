<script setup lang="ts">
// 美股行情页：通过东财 push2delay 快照 + 分时接口展示美股指数实时行情与当日走势。
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import type { ECharts, EChartsCoreOption } from 'echarts'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getUsPremarket, type UsPremarketData, type UsPremarketIndex } from '@/api/modules/finance'
import { useTheme } from '@/utils/theme'

type IndexConfig = {
  name: string
  secid: string
}

type IndexCard = {
  secid: string
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
  seeded: boolean
  lastMinute: number | null
  error: string
}

// 可自行增减：name 显示名，secid 东财代码（100. 前缀为全球指数）
// 注意东财里 100.NDX 是「纳斯达克综合」，100.NDX100 才是「纳斯达克100」
const INDEX_CONFIGS: IndexConfig[] = [
  { name: '纳斯达克100', secid: '100.NDX100' },
  { name: '标普500', secid: '100.SPX' },
]

const SNAPSHOT_FIELDS = 'f43,f44,f45,f46,f57,f58,f59,f60,f169,f170'
const REFRESH_INTERVAL_MS = 10_000
const MAX_INTRADAY_POINTS = 480
// 美股常规交易时段 9:30–16:00 共 390 分钟，折线横轴按此铺满
const SESSION_MINUTES = 390

const { isDark } = useTheme()

const cards = reactive<IndexCard[]>(
  INDEX_CONFIGS.map((config) => ({
    secid: config.secid,
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
    seeded: false,
    lastMinute: null,
    error: '',
  })),
)

const isRefreshing = ref(false)
const hasLoadedOnce = ref(false)
const updatedAtText = ref('')
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

const proxyLabel = computed(() => {
  switch (premarketData.value?.sessionStatus) {
    case 'Pre-Market':
      return '盘前代理'
    case 'Market Open':
    case 'Open':
      return '实时代理'
    case 'After-Hours':
      return '盘后代理'
    default:
      return 'ETF代理'
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

const statusText = computed(() => {
  if (isRefreshing.value || !hasLoadedOnce.value) {
    return '加载中…'
  }
  return updatedAtText.value ? `更新于 ${updatedAtText.value}` : '暂无行情'
})

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

function formatPremarketChange(index: UsPremarketIndex) {
  if (index.changePercent == null || !Number.isFinite(index.changePercent)) {
    return '--'
  }
  const sign = index.changePercent >= 0 ? '+' : ''
  return `${sign}${index.changePercent.toFixed(2)}%`
}

function premarketTone(index: UsPremarketIndex) {
  if (index.changePercent == null || !Number.isFinite(index.changePercent)) {
    return ''
  }
  return index.changePercent >= 0 ? 'up' : 'down'
}

function formatVolume(value?: number | null) {
  if (value == null || !Number.isFinite(value)) {
    return '--'
  }
  if (value >= 100_000_000) {
    return `${(value / 100_000_000).toFixed(2)}亿`
  }
  if (value >= 10_000) {
    return `${(value / 10_000).toFixed(1)}万`
  }
  return Math.round(value).toLocaleString('zh-CN')
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

// JSONP 请求，绕过跨域，与项目内其它东财行情调用保持一致
function jsonpRequest<T>(url: string, callbackParams: string[], timeoutMs = 8000) {
  return new Promise<T>((resolve, reject) => {
    const callbackName = `__us_market_${Date.now()}_${Math.floor(Math.random() * 100000)}`
    const script = document.createElement('script')
    const globalWindow = window as unknown as Window & Record<string, unknown>
    const timer = window.setTimeout(() => {
      cleanup()
      reject(new Error('行情接口请求超时'))
    }, timeoutMs)

    function cleanup() {
      window.clearTimeout(timer)
      script.remove()
      delete globalWindow[callbackName]
    }

    globalWindow[callbackName] = (data: T) => {
      cleanup()
      resolve(data)
    }

    script.onerror = () => {
      cleanup()
      reject(new Error('行情接口加载失败'))
    }

    const connector = url.includes('?') ? '&' : '?'
    const callbackQuery = callbackParams
      .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(callbackName)}`)
      .join('&')
    script.src = `${url}${connector}${callbackQuery}`
    document.body.appendChild(script)
  })
}

// 首次用分时接口打底，拿到当日已有的走势
async function seedTrends(card: IndexCard) {
  const url = `https://push2delay.eastmoney.com/api/qt/stock/trends2/get?secid=${card.secid}`
    + '&fields1=f1&fields2=f53&iscr=0&ndays=1'
    + `&_=${Date.now()}`
  try {
    const res = await jsonpRequest<{ data?: { trends?: string[] } }>(url, ['cb'])
    const trends = res?.data?.trends
    if (!trends || trends.length === 0) {
      return
    }
    card.vals = trends
      .map((item) => {
        const parts = item.split(',')
        return Number.parseFloat(parts[parts.length - 1])
      })
      .filter((value) => Number.isFinite(value))
  } catch {
    // 折线打底失败不影响主数据
  }
}

// 用最新实时价更新折线：分时粒度为每分钟一个点，同一分钟覆盖，跨分钟追加
function pushLivePoint(card: IndexCard, price: number) {
  const minute = Math.floor(Date.now() / 60_000)
  if (card.lastMinute === minute && card.vals.length > 0) {
    card.vals[card.vals.length - 1] = price
  } else {
    card.vals.push(price)
    card.lastMinute = minute
    if (card.vals.length > MAX_INTRADAY_POINTS) {
      card.vals.shift()
    }
  }
}

async function loadOne(card: IndexCard) {
  const url = `https://push2delay.eastmoney.com/api/qt/stock/get?secid=${card.secid}&fields=${SNAPSHOT_FIELDS}`
  try {
    const res = await jsonpRequest<{ data?: Record<string, number | null> }>(url, ['cb'])
    const data = res?.data
    if (!data || data.f43 == null) {
      throw new Error('暂无行情数据')
    }

    // f59 = 价格精度（小数位），字段值需 / 10^精度
    const decimals = data.f59 != null && Number.isFinite(data.f59) ? Number(data.f59) : 2
    const divisor = 10 ** decimals

    card.error = ''
    card.name = (data.f58 as unknown as string) || card.fallbackName
    card.price = Number(data.f43) / divisor
    card.change = Number(data.f169) / divisor
    card.changePercent = Number(data.f170) / 100
    card.open = Number(data.f46) / divisor
    card.high = Number(data.f44) / divisor
    card.low = Number(data.f45) / divisor
    card.preClose = Number(data.f60) / divisor

    if (!card.seeded) {
      // 首屏：先用分时接口打底，再把当前实时价接上
      card.seeded = true
      await seedTrends(card)
    }
    pushLivePoint(card, card.price)
    renderCard(card)
  } catch (error) {
    card.error = error instanceof Error ? error.message : '加载失败'
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
    await Promise.all([
      ...cards.map((card) => loadOne(card)),
      loadPremarket(),
    ])
    updatedAtText.value = new Date().toLocaleTimeString('zh-CN')
  } finally {
    hasLoadedOnce.value = true
    isRefreshing.value = false
  }
}

function setChartRef(secid: string, el: Element | null) {
  if (el) {
    chartEls.set(secid, el as HTMLDivElement)
  } else {
    chartEls.delete(secid)
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

  // 分时数据每分钟一个点，横轴按美股整段交易时段（390 分钟）铺满，
  // 折线只画到当前已有的分钟数，随真实时间生长，而非拉伸占满整个宽度。
  const points = values.map((value, index) => [index, value])

  return {
    animation: false,
    grid: { left: 2, right: 2, top: 6, bottom: 6 },
    xAxis: {
      type: 'value',
      show: false,
      min: 0,
      max: SESSION_MINUTES,
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
  const el = chartEls.get(card.secid)
  if (!el) {
    return
  }
  let instance = chartInstances.get(card.secid)
  if (!instance) {
    instance = echartsLib.init(el)
    chartInstances.set(card.secid, instance)
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

    <p class="us-market-status">{{ statusText }}</p>

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
              <strong>{{ index.indexName }}</strong>
              <span>{{ proxyLabel }} {{ index.proxySymbol }}</span>
            </div>
            <span class="premarket-change">{{ formatPremarketChange(index) }}</span>
          </header>
          <p class="premarket-price">{{ formatNumber(index.price) }}</p>
          <div class="premarket-index-detail">
            <span>成交量 {{ formatVolume(index.volume) }}</span>
            <span>买 {{ formatNumber(index.bidPrice) }} · 卖 {{ formatNumber(index.askPrice) }}</span>
          </div>
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

    <div class="us-market-grid">
      <article
        v-for="card in cards"
        :key="card.secid"
        class="market-card"
        :class="toneClass(card)"
      >
        <header class="market-card-head">
          <span class="market-name">{{ card.name }}</span>
          <span class="market-code">{{ card.secid }}</span>
        </header>

        <p class="market-price">{{ formatNumber(card.price) }}</p>
        <p class="market-change">{{ formatChange(card) }}</p>

        <div
          :ref="(el) => setChartRef(card.secid, el as Element | null)"
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
      指数来源：东方财富 push2delay 快照 + 分时接口；ETF代理行情来源：Nasdaq公开行情。数据可能延迟，不构成投资建议。
    </footer>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
