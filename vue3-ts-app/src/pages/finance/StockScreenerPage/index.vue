<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  getLimitUpDownStatistics,
  getMarketStatus,
  getStockScreenResults,
  getStockScreenStatus,
  stopStockScreenRun,
  triggerStockScreenRun,
  type LimitUpDownStatistics,
  type MarketStatus,
  type MarketStatusIndex,
  type StockScreenItem,
  type StockScreenPage,
  type StockScreenRun,
} from '@/api/modules/finance'

type ScreeningRuleKey = 'sunrise-rise' | 'custom'

type ScreeningRuleOption = {
  value: ScreeningRuleKey
  label: string
  description: string
}

const sunriseRiseRule: ScreeningRuleOption = {
  value: 'sunrise-rise',
  label: '旭日东升',
  description: '连续下跌末端出现缩量阳线实体反包',
}

const screeningRuleOptions: ScreeningRuleOption[] = [
  sunriseRiseRule,
  {
    value: 'custom',
    label: '自定义条件',
    description: '基于旭日东升结构，自行调整阴线数量与跌幅阈值',
  },
]

const activeRuleKey = ref<ScreeningRuleKey>('sunrise-rise')
const criteria = reactive({
  minBearishCount: 4,
  minThreeDayDecline: 9,
  minLastDayDecline: 3,
  requireVolumeUp: false,
  requireNoLowerShadow: false,
})
const screenPage = ref<StockScreenPage | null>(null)
const latestRun = ref<StockScreenRun | null>(null)
const limitStatistics = ref<LimitUpDownStatistics | null>(null)
const marketStatus = ref<MarketStatus | null>(null)
const activeLimitTab = ref<'up' | 'down'>('up')
const results = ref<StockScreenItem[]>([])
const isLoading = ref(false)
const isLimitStatisticsLoading = ref(false)
const isMarketStatusLoading = ref(false)
const isLoadingMore = ref(false)
const isSubmittingScan = ref(false)
const isStoppingScan = ref(false)
const pageError = ref('')
const limitStatisticsError = ref('')
const marketStatusError = ref('')
const actionMessage = ref('')
let statusTimer: number | undefined
let waitingForSubmittedScan = false
let submittedAfterRunId = 0
let statusRequestInFlight = false

const STATUS_POLL_INTERVAL = 2000

const scanIsRunning = computed(() => latestRun.value?.status === 'running')
const resultTotal = computed(() => screenPage.value?.total || 0)
const activeLimitStocks = computed(() => (
  activeLimitTab.value === 'up'
    ? limitStatistics.value?.limitUps || []
    : limitStatistics.value?.limitDowns || []
))
const primaryMarketIndices = computed<MarketStatusIndex[]>(() => {
  const primaryCodes = new Set(['000001', '399001', '399006', '899050'])
  return (marketStatus.value?.indices || []).filter((item) => primaryCodes.has(item.code))
})
const activeRule = computed(() => (
  screeningRuleOptions.find((rule) => rule.value === activeRuleKey.value) || sunriseRiseRule
))
const thresholdInputsDisabled = computed(() => activeRuleKey.value === 'sunrise-rise')
const hasMore = computed(() => results.value.length < resultTotal.value)
const dataTradeDate = computed(() => screenPage.value?.run?.tradeDate || latestRun.value?.tradeDate || '')

onMounted(() => {
  window.addEventListener('focus', handlePageFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  void loadInitialData()
  void loadLimitStatistics()
  void loadMarketStatus()
})

onBeforeUnmount(() => {
  stopStatusPolling()
  window.removeEventListener('focus', handlePageFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

async function loadInitialData() {
  isLoading.value = true
  pageError.value = ''
  try {
    const [status, page] = await Promise.all([
      getStockScreenStatus(),
      getStockScreenResults(buildQuery(1)),
    ])
    latestRun.value = status
    screenPage.value = page
    results.value = page.items || []
    if (status?.status === 'running') {
      scheduleStatusPolling(0)
    }
  } catch (error) {
    pageError.value = toErrorMessage(error, '选股数据加载失败')
  } finally {
    isLoading.value = false
  }
}

async function loadLimitStatistics() {
  if (isLimitStatisticsLoading.value) {
    return
  }
  isLimitStatisticsLoading.value = true
  limitStatisticsError.value = ''
  try {
    limitStatistics.value = await getLimitUpDownStatistics()
  } catch (error) {
    if (!limitStatistics.value) {
      limitStatisticsError.value = toErrorMessage(error, '涨跌停数据加载失败')
    }
  } finally {
    isLimitStatisticsLoading.value = false
  }
}

async function loadMarketStatus() {
  if (isMarketStatusLoading.value) {
    return
  }
  isMarketStatusLoading.value = true
  marketStatusError.value = ''
  try {
    marketStatus.value = await getMarketStatus()
  } catch (error) {
    if (!marketStatus.value) {
      marketStatusError.value = toErrorMessage(error, '大盘状态加载失败')
    }
  } finally {
    isMarketStatusLoading.value = false
  }
}

async function applyRules(options: { preserveMessage?: boolean } = {}) {
  isLoading.value = true
  pageError.value = ''
  if (!options.preserveMessage) {
    actionMessage.value = ''
  }
  try {
    const page = await getStockScreenResults(buildQuery(1))
    screenPage.value = page
    results.value = page.items || []
  } catch (error) {
    pageError.value = toErrorMessage(error, '规则筛选失败')
  } finally {
    isLoading.value = false
  }
}

async function loadMore() {
  if (!screenPage.value || !hasMore.value || isLoadingMore.value) {
    return
  }
  isLoadingMore.value = true
  pageError.value = ''
  try {
    const nextPage = screenPage.value.page + 1
    const page = await getStockScreenResults(buildQuery(nextPage))
    screenPage.value = page
    results.value = [...results.value, ...(page.items || [])]
  } catch (error) {
    pageError.value = toErrorMessage(error, '加载更多结果失败')
  } finally {
    isLoadingMore.value = false
  }
}

async function startFullMarketScan() {
  if (isSubmittingScan.value || scanIsRunning.value) {
    return
  }
  isSubmittingScan.value = true
  pageError.value = ''
  actionMessage.value = ''
  try {
    submittedAfterRunId = latestRun.value?.id || 0
    const submission = await triggerStockScreenRun()
    actionMessage.value = submission.message
    void loadLimitStatistics()
    void loadMarketStatus()
    if (submission.status === 'reused') {
      waitingForSubmittedScan = false
      latestRun.value = await getStockScreenStatus()
      await applyRules({ preserveMessage: true })
      return
    }
    waitingForSubmittedScan = true
    latestRun.value = createOptimisticRunningState(latestRun.value)
    scheduleStatusPolling(0)
  } catch (error) {
    pageError.value = toErrorMessage(error, '全市场扫描提交失败')
  } finally {
    isSubmittingScan.value = false
  }
}

async function stopFullMarketScan() {
  if (!scanIsRunning.value || isStoppingScan.value) {
    return
  }
  isStoppingScan.value = true
  pageError.value = ''
  actionMessage.value = ''
  try {
    const submission = await stopStockScreenRun()
    actionMessage.value = submission.message
    if (submission.status === 'accepted') {
      scheduleStatusPolling(0)
      return
    }
    isStoppingScan.value = false
    latestRun.value = await getStockScreenStatus()
  } catch (error) {
    isStoppingScan.value = false
    pageError.value = toErrorMessage(error, '停止扫描失败，请稍后重试')
  }
}

function handleHeaderScanAction() {
  if (scanIsRunning.value) {
    void stopFullMarketScan()
    return
  }
  void startFullMarketScan()
}

function scheduleStatusPolling(delay = STATUS_POLL_INTERVAL) {
  stopStatusPolling()
  statusTimer = window.setTimeout(() => void pollStatus(), delay)
}

async function pollStatus() {
  statusTimer = undefined
  if (statusRequestInFlight) {
    scheduleStatusPolling()
    return
  }
  statusRequestInFlight = true
  try {
    const wasTrackingScan = waitingForSubmittedScan || latestRun.value?.status === 'running'
    const status = await getStockScreenStatus()
    const waitingForNewRun = waitingForSubmittedScan
      && (!status || status.id <= submittedAfterRunId)
    if (waitingForNewRun) {
      scheduleStatusPolling()
      return
    }

    latestRun.value = status
    if (!status) {
      if (waitingForSubmittedScan) {
        scheduleStatusPolling()
      }
      return
    }
    if (status.status === 'running') {
      scheduleStatusPolling()
      return
    }

    isStoppingScan.value = false
    const hasNewCompletedResult = status.status === 'success'
      && status.id !== screenPage.value?.run?.id
    if (wasTrackingScan || hasNewCompletedResult) {
      waitingForSubmittedScan = false
      actionMessage.value = status.status === 'success'
        ? '全市场扫描完成，已刷新最新结果'
        : status.status === 'canceled'
          ? '全市场扫描已停止，继续显示上一次成功结果'
          : status.errorMessage || '全市场扫描失败，请稍后重试'
      await applyRules({ preserveMessage: true })
      void loadLimitStatistics()
      void loadMarketStatus()
    }
  } catch {
    if (waitingForSubmittedScan || latestRun.value?.status === 'running') {
      scheduleStatusPolling()
    }
  } finally {
    statusRequestInFlight = false
  }
}

function stopStatusPolling() {
  if (statusTimer !== undefined) {
    window.clearTimeout(statusTimer)
    statusTimer = undefined
  }
}

function handlePageFocus() {
  scheduleStatusPolling(0)
  void loadLimitStatistics()
  void loadMarketStatus()
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    scheduleStatusPolling(0)
    void loadLimitStatistics()
    void loadMarketStatus()
  }
}

function createOptimisticRunningState(current: StockScreenRun | null): StockScreenRun {
  return {
    id: current?.id || submittedAfterRunId,
    tradeDate: current?.tradeDate || null,
    triggerName: 'manual-api',
    status: 'running',
    totalStocks: 0,
    processedStocks: 0,
    matchedStocks: 0,
    failedStocks: 0,
    dataSource: current?.dataSource || '公开行情',
    resultMessage: null,
    errorMessage: null,
    startedAt: new Date().toISOString(),
    finishedAt: null,
  }
}

function resetRules() {
  activeRuleKey.value = 'sunrise-rise'
  applySunriseRisePreset()
  void applyRules()
}

function handleRuleSelection() {
  if (activeRuleKey.value === 'sunrise-rise') {
    applySunriseRisePreset()
    void applyRules()
  }
}

function applySunriseRisePreset() {
  criteria.minBearishCount = 4
  criteria.minThreeDayDecline = 9
  criteria.minLastDayDecline = 3
  criteria.requireVolumeUp = false
  criteria.requireNoLowerShadow = false
}

function buildQuery(page: number) {
  return {
    minBearishCount: clampNumber(criteria.minBearishCount, 1, 6, 4),
    minThreeDayDecline: clampNumber(criteria.minThreeDayDecline, 0, 50, 9),
    minLastDayDecline: clampNumber(criteria.minLastDayDecline, 0, 50, 3),
    requireVolumeUp: criteria.requireVolumeUp,
    requireNoLowerShadow: criteria.requireNoLowerShadow,
    page,
    pageSize: 20,
  }
}

function clampNumber(value: number, min: number, max: number, fallback: number) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? Math.min(max, Math.max(min, normalized)) : fallback
}

function formatDate(value?: string | null) {
  if (!value) return '--'
  const parts = value.split('-')
  return parts.length === 3 ? `${parts[0]}.${parts[1]}.${parts[2]}` : value
}

function formatPercent(value?: number | null) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? `${normalized.toFixed(2)}%` : '--'
}

function formatPrice(value?: number | null) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized.toFixed(2) : '--'
}

function formatVolumeChange(ratio?: number | null) {
  const normalized = Number(ratio)
  if (!Number.isFinite(normalized)) return '--'
  return `缩量 ${Math.max(0, (1 - normalized) * 100).toFixed(1)}%`
}

function formatSignedPercent(value?: number | null) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized)) return '--'
  return `${normalized > 0 ? '+' : ''}${normalized.toFixed(2)}%`
}

function formatIndexValue(value?: number | null) {
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized.toFixed(2) : '--'
}

function formatTurnover(value?: number | null) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized) || normalized <= 0) return '--'
  if (normalized >= 1_000_000_000_000) return `${(normalized / 1_000_000_000_000).toFixed(2)}万亿`
  return `${(normalized / 100_000_000).toFixed(0)}亿`
}

function formatMarketTime(value?: string | null) {
  if (!value) return '--:--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--:--'
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function marketChangeClass(value?: number | null) {
  const normalized = Number(value)
  return normalized > 0 ? 'positive' : normalized < 0 ? 'negative' : 'neutral'
}

function marketLabel(market: string) {
  return market === 'SH' ? '沪市' : market === 'SZ' ? '深市' : market === 'BJ' ? '北交所' : market
}

function toErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
</script>

<template>
  <section class="stock-screener-page" aria-label="A股选股">
    <PageHeader title="A股选股" back-to="/finance/more-features" back-label="返回更多功能">
      <template #right>
        <button
          :class="['header-scan-button', { danger: scanIsRunning }]"
          type="button"
          :disabled="isSubmittingScan || isStoppingScan"
          @click="handleHeaderScanAction"
        >
          {{ scanIsRunning ? (isStoppingScan ? '停止中' : '停止扫描') : isSubmittingScan ? '提交中' : '更新数据' }}
        </button>
      </template>
    </PageHeader>

    <section v-if="marketStatus" class="market-status-card" aria-label="大盘状态">
      <header class="market-status-heading">
        <div>
          <strong>大盘状态</strong>
        </div>
        <span class="market-status-time">{{ formatMarketTime(marketStatus.updatedAt) }}</span>
      </header>

      <div class="market-index-grid" aria-label="主要指数">
        <article v-for="index in primaryMarketIndices" :key="index.code">
          <span>{{ index.name }}</span>
          <strong>{{ formatIndexValue(index.value) }}</strong>
          <b :class="marketChangeClass(index.changePercent)">{{ formatSignedPercent(index.changePercent) }}</b>
        </article>
      </div>

      <div class="market-breadth">
        <div class="market-breadth-counts">
          <span><b class="positive">{{ marketStatus.advanceCount }}</b> 上涨</span>
          <span><b class="negative">{{ marketStatus.declineCount }}</b> 下跌</span>
          <span><b>{{ marketStatus.flatCount }}</b> 平盘</span>
        </div>
        <div
          class="market-breadth-track"
          role="img"
          :aria-label="`上涨股票占比 ${marketStatus.advanceRatio.toFixed(1)}%`"
        >
          <span class="advance" :style="{ width: `${marketStatus.advanceRatio}%` }"></span>
        </div>
      </div>

      <footer class="market-status-footer">
        <span>成交额 {{ formatTurnover(marketStatus.turnover) }}</span>
        <span>{{ marketStatus.source }}</span>
      </footer>
    </section>

    <div v-else-if="isMarketStatusLoading" class="market-status-placeholder" aria-live="polite">
      大盘状态加载中…
    </div>
    <div v-else-if="marketStatusError" class="market-status-error" role="alert">
      <span>{{ marketStatusError }}</span>
      <button type="button" @click="loadMarketStatus">重新加载</button>
    </div>

    <section
      v-if="limitStatistics"
      class="limit-statistics-card"
      aria-label="涨跌停数据"
    >
      <header class="limit-statistics-heading">
        <div>
          <strong>涨跌停数据</strong>
        </div>
        <span class="limit-statistics-time">{{ formatMarketTime(limitStatistics.updatedAt) }}</span>
      </header>

      <div class="limit-summary-grid">
        <article class="limit-up-summary">
          <span>涨停</span>
          <div><strong>{{ limitStatistics.limitUpCount }}</strong><b>只</b></div>
        </article>
        <article class="limit-down-summary">
          <span>跌停</span>
          <div><strong>{{ limitStatistics.limitDownCount }}</strong><b>只</b></div>
        </article>
        <article>
          <span>炸板</span>
          <div><strong>{{ limitStatistics.brokenLimitCount }}</strong><b>只</b></div>
        </article>
        <article>
          <span>封板率</span>
          <div><strong>{{ limitStatistics.sealRate.toFixed(1) }}</strong><b>%</b></div>
        </article>
      </div>

      <div class="limit-stock-tabs" role="tablist" aria-label="涨跌停股票列表">
        <button
          id="limit-tab-up"
          :class="{ active: activeLimitTab === 'up' }"
          type="button"
          role="tab"
          aria-controls="limit-stock-panel"
          :aria-selected="activeLimitTab === 'up'"
          @click="activeLimitTab = 'up'"
        >
          涨停 <b>{{ limitStatistics.limitUpCount }}</b>
        </button>
        <button
          id="limit-tab-down"
          :class="{ active: activeLimitTab === 'down' }"
          type="button"
          role="tab"
          aria-controls="limit-stock-panel"
          :aria-selected="activeLimitTab === 'down'"
          @click="activeLimitTab = 'down'"
        >
          跌停 <b>{{ limitStatistics.limitDownCount }}</b>
        </button>
      </div>

      <section
        id="limit-stock-panel"
        class="limit-stock-panel"
        role="tabpanel"
        :aria-labelledby="activeLimitTab === 'up' ? 'limit-tab-up' : 'limit-tab-down'"
        :aria-label="activeLimitTab === 'up' ? '涨停股票' : '跌停股票'"
      >
        <div v-if="activeLimitStocks.length" class="limit-stock-list">
          <div v-for="stock in activeLimitStocks" :key="`${activeLimitTab}-${stock.code}`">
            <span><b>{{ stock.name }}</b><small>{{ stock.code }} · {{ stock.industry || '其他' }}</small></span>
            <strong :class="activeLimitTab === 'up' ? 'positive' : 'negative'">
              {{ formatSignedPercent(stock.changePercent) }}
            </strong>
          </div>
        </div>
        <p v-else>{{ activeLimitTab === 'up' ? '暂无涨停股票' : '暂无跌停股票' }}</p>
      </section>

      <footer class="limit-statistics-footer">
        <span>盘中触及涨停但未封住计为炸板</span>
        <span>{{ limitStatistics.source }}</span>
      </footer>
    </section>

    <div v-else-if="isLimitStatisticsLoading" class="limit-statistics-placeholder" aria-live="polite">
      涨跌停数据加载中…
    </div>
    <div v-else-if="limitStatisticsError" class="limit-statistics-error" role="alert">
      <span>{{ limitStatisticsError }}</span>
      <button type="button" @click="loadLimitStatistics">重新加载</button>
    </div>

    <p v-if="actionMessage" class="page-message" aria-live="polite">{{ actionMessage }}</p>
    <p v-if="pageError" class="page-message error" role="alert">{{ pageError }}</p>

    <form class="rule-panel" @submit.prevent="applyRules()">
      <div class="rule-panel-heading">
        <div>
          <span>选股规则</span>
          <h2>选择形态策略</h2>
        </div>
        <button class="text-button" type="button" @click="resetRules">恢复默认</button>
      </div>

      <label class="rule-select-field">
        <span>规则名称</span>
        <div class="rule-select-control">
          <select v-model="activeRuleKey" @change="handleRuleSelection">
            <option v-for="rule in screeningRuleOptions" :key="rule.value" :value="rule.value">
              {{ rule.label }}
            </option>
          </select>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="m7 10 5 5 5-5" />
          </svg>
        </div>
        <small>{{ activeRule.description }}</small>
      </label>

      <div class="threshold-grid">
        <label>
          <span>6日阴线至少</span>
          <div :class="['number-input', { disabled: thresholdInputsDisabled }]">
            <input v-model.number="criteria.minBearishCount" type="number" min="1" max="6" step="1" :disabled="thresholdInputsDisabled">
            <b>根</b>
          </div>
        </label>
        <label>
          <span>3日累计跌幅（收盘计）</span>
          <div :class="['number-input', { disabled: thresholdInputsDisabled }]">
            <input v-model.number="criteria.minThreeDayDecline" type="number" min="0" max="50" step="0.5" :disabled="thresholdInputsDisabled">
            <b>%</b>
          </div>
        </label>
        <label>
          <span>第3根阴线单日跌幅</span>
          <div :class="['number-input', { disabled: thresholdInputsDisabled }]">
            <input v-model.number="criteria.minLastDayDecline" type="number" min="0" max="50" step="0.5" :disabled="thresholdInputsDisabled">
            <b>%</b>
          </div>
        </label>
      </div>

      <div class="required-rules" aria-label="固定条件">
        <span>固定条件</span>
        <p>最后3日连续收阴 · 次日阳线实体反包 · 反包阳线缩量</p>
      </div>

      <details class="preference-rules">
        <summary>优选条件 <span>可选</span></summary>
        <label>
          <input v-model="criteria.requireVolumeUp" type="checkbox">
          <span><b>3根阴线连续放量</b><small>成交量逐日增加，强化下跌末端特征</small></span>
        </label>
        <label>
          <input v-model="criteria.requireNoLowerShadow" type="checkbox">
          <span><b>反包阳线近似无下影线</b><small>下影线不超过开盘价的 0.15%</small></span>
        </label>
      </details>

      <button class="apply-button" type="submit" :disabled="isLoading">
        {{ isLoading ? '筛选中…' : '应用规则' }}
      </button>
    </form>

    <CommonLoading v-if="isLoading && results.length === 0" text="正在读取全市场指标..." />

    <section v-else class="result-section" aria-label="选股结果">
      <div class="result-heading">
        <div>
          <span>选股结果</span>
          <h2>{{ resultTotal }} 只股票</h2>
        </div>
        <span v-if="dataTradeDate" class="trade-date">{{ formatDate(dataTradeDate) }}</span>
      </div>

      <article v-for="item in results" :key="`${item.signalDate}-${item.stockCode}`" class="stock-card">
        <header>
          <div class="stock-identity">
            <span class="market-badge">{{ marketLabel(item.market) }}</span>
            <div>
              <h3>{{ item.stockName }}</h3>
              <p>{{ item.stockCode }}</p>
            </div>
          </div>
          <div class="signal-score">
            <strong>{{ item.signalScore }}</strong>
            <span>信号分</span>
          </div>
        </header>

        <div class="stock-metrics">
          <div><span>3日跌幅</span><strong>{{ formatPercent(item.threeDayDeclinePct) }}</strong></div>
          <div><span>末日跌幅</span><strong>{{ formatPercent(item.lastDayDeclinePct) }}</strong></div>
          <div><span>成交量</span><strong>{{ formatVolumeChange(item.volumeRatio) }}</strong></div>
        </div>

        <div class="condition-tags" aria-label="命中条件">
          <span>6日{{ item.bearishCount6 }}阴</span>
          <span>实体反包</span>
          <span>阳线缩量</span>
          <span v-if="item.lastThreeVolumeUp" class="preferred">阴线放量</span>
          <span v-if="item.noLowerShadow" class="preferred">近似光脚</span>
        </div>

        <footer>
          <span>{{ formatDate(item.previousDate) }} 阴线 {{ formatPrice(item.previousOpen) }} → {{ formatPrice(item.previousClose) }}</span>
          <span>{{ formatDate(item.signalDate) }} 阳线 {{ formatPrice(item.signalOpen) }} → {{ formatPrice(item.signalClose) }}</span>
        </footer>
      </article>

      <div v-if="results.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 19V9m5 10V5m5 14v-7m5 7V3M3 19h18" />
        </svg>
        <strong>{{ screenPage?.run ? '暂无符合当前规则的股票' : '还没有可用的扫描数据' }}</strong>
        <p>{{ screenPage?.run ? '可以适当降低跌幅阈值或取消优选条件' : '点击右上角“更新数据”启动首次全市场扫描' }}</p>
      </div>

      <button v-if="hasMore" class="load-more-button" type="button" :disabled="isLoadingMore" @click="loadMore">
        {{ isLoadingMore ? '加载中…' : `加载更多（已显示 ${results.length} / ${resultTotal}）` }}
      </button>
    </section>

    <p class="risk-note">筛选结果仅基于公开历史行情和技术形态，不构成投资建议。</p>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
