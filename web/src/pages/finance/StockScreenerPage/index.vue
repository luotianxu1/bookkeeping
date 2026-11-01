<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { getErrorMessage } from '@/api/request'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
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
import {
  clampNumber,
  createOptimisticRunningState,
  formatDate,
  formatIndexValue,
  formatKlineNumber,
  formatMarketTime,
  formatPercent,
  formatPrice,
  formatSignedPercent,
  formatTurnover,
  itemChangePercent,
  marketChangeClass,
  marketLabel,
} from './screenerFormat'
import { screeningRules } from './screenerRules'
import { useExpandedKline } from './useExpandedKline'

const activeRuleKey = ref<string>(screeningRules[0].key)
const criteria = reactive({
  minBearishCount: 4,
  minThreeDayDecline: 9,
  minLastDayDecline: 3,
  requireVolumeUp: false,
  requireNoLowerShadow: false,
  includeChiNext: false,
  includeStar: false,
})
const screenPage = ref<StockScreenPage | null>(null)
const latestRun = ref<StockScreenRun | null>(null)
const limitStatistics = ref<LimitUpDownStatistics | null>(null)
const marketStatus = ref<MarketStatus | null>(null)
const activeLimitTab = ref<'up' | 'down'>('up')
const results = ref<StockScreenItem[]>([])
const isLoading = ref(true)
const isLimitStatisticsLoading = ref(false)
const isMarketStatusLoading = ref(false)
const isLoadingMore = ref(false)
const isSubmittingScan = ref(false)
const isStoppingScan = ref(false)
const scanProgressOpen = ref(false)
const scanProgressError = ref('')
const pageError = ref('')
const limitStatisticsError = ref('')
const marketStatusError = ref('')
const actionMessage = ref('')
let statusTimer: number | undefined
let waitingForSubmittedScan = false
let submittedAfterRunId = 0
let statusRequestInFlight = false

const STATUS_POLL_INTERVAL = 2000

const cardKey = (item: StockScreenItem) => `${item.signalDate}-${item.stockCode}`

const {
  expandedKey,
  klineLoadingKey,
  klineErrorKey,
  klineChartError,
  toggleCard,
  loadKline,
  setKlineContainer,
  disposeAllKlineCharts,
} = useExpandedKline({
  cardKey,
  onError: (message) => {
    pageError.value = message
  },
})

const scanIsRunning = computed(() => latestRun.value?.status === 'running')
const scanProcessedStocks = computed(() => Math.max(0, latestRun.value?.processedStocks || 0))
const scanTotalStocks = computed(() => Math.max(0, latestRun.value?.totalStocks || 0))
const scanFailedStocks = computed(() => Math.max(0, latestRun.value?.failedStocks || 0))
const scanProgressIsIndeterminate = computed(() => (
  isSubmittingScan.value || (scanIsRunning.value && scanTotalStocks.value === 0)
))
const scanProgressPercent = computed(() => {
  if (scanProgressError.value) {
    return 0
  }
  if (latestRun.value?.status === 'success') {
    return 100
  }
  if (scanTotalStocks.value <= 0) {
    return 0
  }
  const percent = Math.round((scanProcessedStocks.value / scanTotalStocks.value) * 100)
  return Math.min(scanIsRunning.value ? 99 : 100, Math.max(0, percent))
})
const scanProgressStatus = computed(() => {
  if (scanProgressError.value) return scanProgressError.value
  if (isSubmittingScan.value) return '正在提交全市场扫描任务…'
  if (scanIsRunning.value) {
    return scanTotalStocks.value > 0
      ? `正在读取第 ${scanProcessedStocks.value} / ${scanTotalStocks.value} 只股票的日K数据`
      : '正在获取全市场股票列表…'
  }
  if (latestRun.value?.status === 'success') return actionMessage.value || '全市场扫描完成，最新结果已刷新'
  if (latestRun.value?.status === 'canceled') return '扫描已停止，页面继续显示上一次成功结果'
  if (latestRun.value?.status === 'failed') return latestRun.value.errorMessage || '扫描失败，请稍后重试'
  return actionMessage.value || '准备刷新全市场选股数据'
})
const scanProgressLabel = computed(() => (
  scanProgressIsIndeterminate.value ? '扫描准备中' : `扫描进度 ${scanProgressPercent.value}%`
))
const resultTotal = computed(() => screenPage.value?.total || 0)
const activeLimitStocks = computed(() => (
  activeLimitTab.value === 'up'
    ? limitStatistics.value?.limitUps || []
    : limitStatistics.value?.limitDowns || []
))
// 涨跌停列表默认只展示前 5 只，避免卡片把整页撑得过长
const LIMIT_LIST_PREVIEW_COUNT = 5
const limitListExpanded = ref(false)
const visibleLimitStocks = computed(() => (
  limitListExpanded.value
    ? activeLimitStocks.value
    : activeLimitStocks.value.slice(0, LIMIT_LIST_PREVIEW_COUNT)
))
const limitListHiddenCount = computed(() => (
  Math.max(0, activeLimitStocks.value.length - LIMIT_LIST_PREVIEW_COUNT)
))
watch(activeLimitTab, () => {
  limitListExpanded.value = false
})
const primaryMarketIndices = computed<MarketStatusIndex[]>(() => {
  const primaryCodes = new Set(['000001', '399001', '399006', '899050'])
  return (marketStatus.value?.indices || []).filter((item) => primaryCodes.has(item.code))
})
const activeRule = computed(() => (
  screeningRules.find((rule) => rule.key === activeRuleKey.value) || screeningRules[0]
))
const ruleOptions = computed(() => (
  screeningRules.map((rule) => ({ label: rule.label, value: rule.key }))
))
const hasMore = computed(() => results.value.length < resultTotal.value)
const dataTradeDate = computed(() => screenPage.value?.run?.tradeDate || latestRun.value?.tradeDate || '')

watch(activeRuleKey, () => {
  disposeAllKlineCharts()
  expandedKey.value = ''
  screenPage.value = null
  results.value = []
  void applyRules()
})

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
  disposeAllKlineCharts()
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
    pageError.value = getErrorMessage(error, '选股数据加载失败')
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
      limitStatisticsError.value = getErrorMessage(error, '涨跌停数据加载失败')
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
      marketStatusError.value = getErrorMessage(error, '大盘状态加载失败')
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
    pageError.value = getErrorMessage(error, '规则筛选失败')
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
    pageError.value = getErrorMessage(error, '加载更多结果失败')
  } finally {
    isLoadingMore.value = false
  }
}

async function startFullMarketScan() {
  if (isSubmittingScan.value || scanIsRunning.value) {
    return
  }
  isSubmittingScan.value = true
  scanProgressOpen.value = true
  scanProgressError.value = ''
  pageError.value = ''
  actionMessage.value = ''
  try {
    submittedAfterRunId = latestRun.value?.id || 0
    const submission = await triggerStockScreenRun(true)
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
    latestRun.value = createOptimisticRunningState(latestRun.value, submittedAfterRunId)
    scheduleStatusPolling(0)
  } catch (error) {
    const message = getErrorMessage(error, '全市场扫描提交失败')
    pageError.value = message
    scanProgressError.value = message
  } finally {
    isSubmittingScan.value = false
  }
}

async function stopFullMarketScan() {
  if (!scanIsRunning.value || isStoppingScan.value) {
    return
  }
  isStoppingScan.value = true
  scanProgressError.value = ''
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
    const message = getErrorMessage(error, '停止扫描失败，请稍后重试')
    pageError.value = message
    scanProgressError.value = message
  }
}

function handleHeaderScanAction() {
  if (scanIsRunning.value) {
    scanProgressOpen.value = true
    return
  }
  scanProgressOpen.value = true
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

function buildQuery(page: number) {
  return {
    ruleKey: activeRuleKey.value,
    minBearishCount: clampNumber(criteria.minBearishCount, 1, 6, 4),
    minThreeDayDecline: clampNumber(criteria.minThreeDayDecline, 0, 50, 9),
    minLastDayDecline: clampNumber(criteria.minLastDayDecline, 0, 50, 3),
    requireVolumeUp: criteria.requireVolumeUp,
    requireNoLowerShadow: criteria.requireNoLowerShadow,
    includeChiNext: criteria.includeChiNext,
    includeStar: criteria.includeStar,
    page,
    pageSize: 20,
  }
}

</script>

<template>
  <section class="stock-screener-page" aria-label="A股选股">
    <PageHeader title="A股选股" back-to="/finance/more-features" back-label="返回更多功能">
      <template #right>
        <button
          v-if="scanIsRunning"
          class="header-scan-button"
          type="button"
          @click="handleHeaderScanAction"
        >
          {{ scanProgressIsIndeterminate ? '扫描中' : `${scanProgressPercent}%` }}
        </button>
        <CommonHeaderRefreshButton
          v-else
          label="刷新选股数据"
          :loading="isSubmittingScan"
          @click="handleHeaderScanAction"
        />
      </template>
    </PageHeader>

    <CommonModal
      v-model="scanProgressOpen"
      title="刷新选股数据"
      size="compact"
    >
      <div class="scan-progress-content" aria-live="polite">
        <div class="scan-progress-heading">
          <span>{{ scanProgressLabel }}</span>
          <strong v-if="!scanProgressIsIndeterminate">{{ scanProgressPercent }}%</strong>
        </div>

        <div
          :class="['scan-progress-track', { indeterminate: scanProgressIsIndeterminate }]"
          role="progressbar"
          aria-label="全市场扫描进度"
          aria-valuemin="0"
          aria-valuemax="100"
          :aria-valuenow="scanProgressIsIndeterminate ? undefined : scanProgressPercent"
        >
          <span :style="scanProgressIsIndeterminate ? undefined : { width: `${scanProgressPercent}%` }"></span>
        </div>

        <p :class="{ error: Boolean(scanProgressError) || latestRun?.status === 'failed' }">
          {{ scanProgressStatus }}
        </p>

        <div class="scan-progress-metrics" aria-label="扫描统计">
          <div>
            <span>已处理</span>
            <strong>{{ scanProcessedStocks }}</strong>
          </div>
          <div>
            <span>总股票</span>
            <strong>{{ scanTotalStocks || '--' }}</strong>
          </div>
          <div>
            <span>失败</span>
            <strong>{{ scanFailedStocks }}</strong>
          </div>
        </div>
      </div>

      <template #footer>
        <button
          v-if="scanIsRunning"
          class="scan-progress-action danger"
          type="button"
          :disabled="isStoppingScan"
          @click="stopFullMarketScan"
        >
          {{ isStoppingScan ? '正在停止…' : '停止扫描' }}
        </button>
        <button
          v-else-if="isSubmittingScan"
          class="scan-progress-action"
          type="button"
          disabled
        >
          正在提交…
        </button>
        <button
          v-else
          class="scan-progress-action"
          type="button"
          @click="scanProgressOpen = false"
        >
          关闭
        </button>
      </template>
    </CommonModal>

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
          <div v-for="stock in visibleLimitStocks" :key="`${activeLimitTab}-${stock.code}`">
            <span><b>{{ stock.name }}</b><small>{{ stock.code }} · {{ stock.industry || '其他' }}</small></span>
            <strong :class="activeLimitTab === 'up' ? 'positive' : 'negative'">
              {{ formatSignedPercent(stock.changePercent) }}
            </strong>
          </div>
        </div>
        <button
          v-if="limitListHiddenCount > 0 || limitListExpanded"
          :class="['limit-list-toggle', { expanded: limitListExpanded }]"
          type="button"
          @click="limitListExpanded = !limitListExpanded"
        >
          {{ limitListExpanded ? '收起列表' : `展开全部 ${activeLimitStocks.length} 只` }}
        </button>
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

    <section class="rule-panel">
      <div class="rule-panel-heading">
        <div>
          <strong>选股规则</strong>
        </div>
      </div>

      <CommonSelect v-model="activeRuleKey" label="规则" :options="ruleOptions" />

      <div class="rule-strategy" :aria-label="`${activeRule.title}策略说明`">
        <h3>{{ activeRule.title }}</h3>
        <p>{{ activeRule.description }}</p>
        <ul>
          <li v-for="point in activeRule.points" :key="point">{{ point }}</li>
        </ul>
      </div>

      <CommonSwitch v-model="criteria.includeChiNext" label="包含创业板（300/301）" />
      <CommonSwitch v-model="criteria.includeStar" label="包含科创板（688）" />

      <button class="apply-button" type="button" :disabled="isLoading" @click="applyRules()">
        {{ isLoading ? '筛选中…' : '搜索匹配股票' }}
      </button>
    </section>

    <CommonLoading v-if="isLoading && results.length === 0" text="正在读取全市场指标..." />

    <section v-else class="result-section" aria-label="选股结果">
      <div class="result-heading">
        <div>
          <span>选股结果</span>
          <h2>{{ resultTotal }} 只股票</h2>
        </div>
        <span v-if="dataTradeDate" class="trade-date">{{ formatDate(dataTradeDate) }}</span>
      </div>

      <article
        v-for="item in results"
        :key="`${item.signalDate}-${item.stockCode}`"
        :class="['stock-card', { expanded: expandedKey === `${item.signalDate}-${item.stockCode}` }]"
      >
        <header
          role="button"
          tabindex="0"
          :aria-expanded="expandedKey === `${item.signalDate}-${item.stockCode}`"
          :aria-label="`${item.stockName} 日K走势`"
          @click="toggleCard(item)"
          @keydown.enter.prevent="toggleCard(item)"
          @keydown.space.prevent="toggleCard(item)"
        >
          <div class="stock-identity">
            <span class="market-badge">{{ marketLabel(item.market) }}</span>
            <div>
              <h3>{{ item.stockName }}</h3>
              <p>
                {{ item.stockCode }}
                <template v-if="activeRuleKey === 'yin-yang-double-bear' && item.yinYangType">
                  · {{ item.yinYangType }} · 回升 {{ formatPercent(item.yinYangPenetrationPct) }}
                </template>
                <template v-else-if="activeRuleKey === 'first-board-high-bear' && item.firstBoardBuyPoint">
                  · {{ item.firstBoardBuyPoint }} · 高阴量比 {{ formatKlineNumber(item.highBearVolumeRatio) }}
                </template>
              </p>
            </div>
          </div>
          <div class="stock-card-trailing">
            <div class="stock-price">
              <strong>{{ formatPrice(item.signalClose) }}</strong>
              <span :class="marketChangeClass(itemChangePercent(item))">{{ formatSignedPercent(itemChangePercent(item)) }}</span>
            </div>
            <svg class="expand-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path d="m7 10 5 5 5-5" />
            </svg>
          </div>
        </header>

        <div v-if="expandedKey === `${item.signalDate}-${item.stockCode}`" class="kline-panel">
          <div
            v-show="klineLoadingKey !== `${item.signalDate}-${item.stockCode}` && klineErrorKey !== `${item.signalDate}-${item.stockCode}`"
            :ref="(el) => setKlineContainer(`${item.signalDate}-${item.stockCode}`, el as Element | null)"
            class="kline-chart"
          ></div>
          <div v-if="klineLoadingKey === `${item.signalDate}-${item.stockCode}`" class="kline-status" aria-live="polite">
            日K加载中…
          </div>
          <div v-else-if="klineErrorKey === `${item.signalDate}-${item.stockCode}`" class="kline-status error" role="alert">
            <span>{{ klineChartError }}</span>
            <button type="button" @click="loadKline(item)">重试</button>
          </div>
        </div>
      </article>

      <div v-if="results.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 19V9m5 10V5m5 14v-7m5 7V3M3 19h18" />
        </svg>
        <strong>{{ screenPage?.run ? '暂无符合当前规则的股票' : '还没有可用的扫描数据' }}</strong>
        <p>{{ screenPage?.run ? '当前交易日没有股票同时满足全部形态条件' : '点击右上角刷新按钮启动首次全市场扫描' }}</p>
      </div>

      <button v-if="hasMore" class="load-more-button" type="button" :disabled="isLoadingMore" @click="loadMore">
        {{ isLoadingMore ? '加载中…' : `加载更多（已显示 ${results.length} / ${resultTotal}）` }}
      </button>
    </section>

    <p class="risk-note">筛选结果仅基于公开历史行情和技术形态，不构成投资建议。</p>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
