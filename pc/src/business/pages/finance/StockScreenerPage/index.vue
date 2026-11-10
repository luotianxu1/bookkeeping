<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { getErrorMessage } from '@/business/api/request'

import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'
import CommonHeaderRefreshButton from '@/business/components/common/CommonHeaderRefreshButton/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getLimitUpDownStatistics,
  getMarketStatus,
  type LimitUpDownStatistics,
  type MarketStatus,
} from '@/business/api/modules/market'
import {
  getStockScreenResults,
  getStockScreenStatus,
  stopStockScreenRun,
  triggerStockScreenRun,
  type StockScreenItem,
  type StockScreenPage,
  type StockScreenRun,
} from '@/business/api/modules/finance'
import {
  clampNumber,
  createOptimisticRunningState,
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

const cardKey = (item: StockScreenItem) =>
  `${item.signalDate}-${item.stockCode}`

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
const scanProcessedStocks = computed(() =>
  Math.max(0, latestRun.value?.processedStocks || 0),
)
const scanTotalStocks = computed(() =>
  Math.max(0, latestRun.value?.totalStocks || 0),
)
const scanFailedStocks = computed(() =>
  Math.max(0, latestRun.value?.failedStocks || 0),
)
const scanProgressIsIndeterminate = computed(
  () =>
    isSubmittingScan.value ||
    (scanIsRunning.value && scanTotalStocks.value === 0),
)
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
  const percent = (scanProcessedStocks.value / scanTotalStocks.value) * 100
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
  if (latestRun.value?.status === 'success')
    return actionMessage.value || '全市场扫描完成，最新结果已刷新'
  if (latestRun.value?.status === 'canceled')
    return '扫描已停止，页面继续显示上一次成功结果'
  if (latestRun.value?.status === 'failed')
    return latestRun.value.errorMessage || '扫描失败，请稍后重试'
  return actionMessage.value || '准备刷新全市场选股数据'
})
const scanProgressLabel = computed(() =>
  scanProgressIsIndeterminate.value
    ? '扫描准备中'
    : `扫描进度 ${formatPercentage(scanProgressPercent.value)}`,
)
const resultTotal = computed(() => screenPage.value?.total || 0)
const activeLimitStocks = computed(() =>
  activeLimitTab.value === 'up'
    ? limitStatistics.value?.limitUps || []
    : limitStatistics.value?.limitDowns || [],
)
// 涨跌停列表默认只展示前 5 只，避免卡片把整页撑得过长

const limitListExpanded = ref(false)

watch(activeLimitTab, () => {
  limitListExpanded.value = false
})

const activeRule = computed(
  () =>
    screeningRules.find((rule) => rule.key === activeRuleKey.value) ||
    screeningRules[0],
)
const ruleOptions = computed(() =>
  screeningRules.map((rule) => ({ label: rule.label, value: rule.key })),
)
const hasMore = computed(() => results.value.length < resultTotal.value)

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
    latestRun.value = createOptimisticRunningState(
      latestRun.value,
      submittedAfterRunId,
    )
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
    const wasTrackingScan =
      waitingForSubmittedScan || latestRun.value?.status === 'running'
    const status = await getStockScreenStatus()
    const waitingForNewRun =
      waitingForSubmittedScan && (!status || status.id <= submittedAfterRunId)
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
    const hasNewCompletedResult =
      status.status === 'success' && status.id !== screenPage.value?.run?.id
    if (wasTrackingScan || hasNewCompletedResult) {
      waitingForSubmittedScan = false
      actionMessage.value =
        status.status === 'success'
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

type LimitRow = (typeof activeLimitStocks.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader
      title="A股选股"
      back-to="/finance/more-features"
      back-label="返回更多功能"
    >
      <template #right>
        <el-button
          v-if="scanIsRunning"
          class="header-scan-button"
          native-type="button"
          @click="handleHeaderScanAction"
        >
          {{
            scanProgressIsIndeterminate
              ? '扫描中'
              : formatPercentage(scanProgressPercent)
          }}
        </el-button>
        <CommonHeaderRefreshButton
          v-else
          label="刷新选股数据"
          :loading="isSubmittingScan"
          @click="handleHeaderScanAction"
        />
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <el-alert
      v-if="marketStatusError"
      :title="marketStatusError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <el-alert
      v-if="limitStatisticsError"
      :title="limitStatisticsError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <template v-if="marketStatus">
      <MetricStrip
        :items="[
          { label: '上涨', value: marketStatus.advanceCount },
          { label: '下跌', value: marketStatus.declineCount },
          { label: '平盘', value: marketStatus.flatCount },
          { label: '成交额', value: formatTurnover(marketStatus.turnover) },
        ]"
      />
    </template>
    <div class="desktop-aside-main">
      <el-card class="pc-record-card rule-panel" shadow="never">
        <div class="pc-toolbar rule-panel-heading">
          <div><strong>选股规则</strong></div>
        </div>
        <CommonSelect
          v-model="activeRuleKey"
          label="规则"
          :options="ruleOptions"
        />
        <div class="rule-strategy" :aria-label="`${activeRule.title}策略说明`">
          <h3>{{ activeRule.title }}</h3>
          <p>{{ activeRule.description }}</p>
          <ul>
            <li v-for="point in activeRule.points" :key="point">{{ point }}</li>
          </ul>
        </div>
        <CommonSwitch
          v-model="criteria.includeChiNext"
          label="包含创业板（300/301）"
        />
        <CommonSwitch
          v-model="criteria.includeStar"
          label="包含科创板（688）"
        />
        <el-button
          class="apply-button"
          native-type="button"
          :disabled="isLoading"
          @click="applyRules()"
        >
          {{ isLoading ? '筛选中…' : '搜索匹配股票' }}
        </el-button>
      </el-card>
      <div class="desktop-results">
        <DesktopTable
          :paginate="false"
          :rows="results"
          :loading="isLoading"
          title="选股结果"
        >
          <el-table-column
            prop="stockCode"
            label="代码"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <el-table-column
            prop="stockName"
            label="股票"
            min-width="180"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {StockScreenItem} -->
          <el-table-column label="市场" min-width="150">
            <template #default="{ row }: { row: StockScreenItem }">
              {{ marketLabel(row.market) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {StockScreenItem} -->
          <el-table-column label="收盘价" min-width="150">
            <template #default="{ row }: { row: StockScreenItem }">
              {{ formatPrice(row.signalClose) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {StockScreenItem} -->
          <el-table-column label="涨跌幅" min-width="150">
            <template #default="{ row }: { row: StockScreenItem }">
              <span :class="marketChangeClass(itemChangePercent(row))">
                {{ formatSignedPercent(itemChangePercent(row)) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            prop="signalDate"
            label="信号日"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {StockScreenItem} -->
          <el-table-column label="走势" min-width="150">
            <template #default="{ row }: { row: StockScreenItem }">
              <el-button link type="primary" @click="toggleCard(row)">
                查看日 K
              </el-button>
            </template>
          </el-table-column>
        </DesktopTable>
        <div class="desktop-pagination">
          <span>已显示 {{ results.length }} / {{ resultTotal }} 只</span>
          <el-button v-if="hasMore" :loading="isLoadingMore" @click="loadMore">
            加载更多
          </el-button>
        </div>
        <template
          v-for="item in results.filter(
            (item) => cardKey(item) === expandedKey,
          )"
          :key="cardKey(item)"
        >
          <el-card
            v-if="expandedKey === `${item.signalDate}-${item.stockCode}`"
            class="pc-record-card kline-panel"
            shadow="never"
          >
            <div
              v-show="
                klineLoadingKey !== `${item.signalDate}-${item.stockCode}` &&
                klineErrorKey !== `${item.signalDate}-${item.stockCode}`
              "
              :ref="
                (el) =>
                  setKlineContainer(
                    `${item.signalDate}-${item.stockCode}`,
                    el as Element | null,
                  )
              "
              class="pc-chart kline-chart"
            ></div>
            <div
              v-if="klineLoadingKey === `${item.signalDate}-${item.stockCode}`"
              class="kline-status"
              aria-live="polite"
            >
              日K加载中…
            </div>
            <div
              v-else-if="
                klineErrorKey === `${item.signalDate}-${item.stockCode}`
              "
              class="kline-status error"
              role="alert"
            >
              <span>{{ klineChartError }}</span>
              <el-button native-type="button" @click="loadKline(item)">
                重试
              </el-button>
            </div>
          </el-card>
        </template>
        <el-collapse>
          <el-collapse-item title="市场涨跌停" name="limits">
            <DesktopTable
              :rows="activeLimitStocks"
              :loading="false"
              title="涨跌停股票"
            >
              <template #toolbar>
                <el-radio-group v-model="activeLimitTab">
                  <el-radio-button value="up">涨停</el-radio-button>
                  <el-radio-button value="down">跌停</el-radio-button>
                </el-radio-group>
              </template>
              <el-table-column
                prop="name"
                label="名称"
                min-width="140"
                sortable="custom"
                show-overflow-tooltip
              />
              <el-table-column
                prop="code"
                label="代码"
                min-width="140"
                sortable="custom"
                show-overflow-tooltip
              />
              <el-table-column
                prop="industry"
                label="行业"
                min-width="140"
                sortable="custom"
                show-overflow-tooltip
              />
              <!-- @vue-generic {LimitRow} -->
              <el-table-column label="涨跌幅" min-width="150">
                <template #default="{ row }: { row: LimitRow }">
                  {{ formatSignedPercent(row.changePercent) }}
                </template>
              </el-table-column>
            </DesktopTable>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>
    <CommonModal v-model="scanProgressOpen" title="刷新选股数据" size="compact">
      <div class="scan-progress-content" aria-live="polite">
        <div class="scan-progress-heading">
          <span>{{ scanProgressLabel }}</span>
          <strong v-if="!scanProgressIsIndeterminate">
            {{ formatPercentage(scanProgressPercent) }}
          </strong>
        </div>
        <div
          :class="[
            'scan-progress-track',
            { indeterminate: scanProgressIsIndeterminate },
          ]"
          role="progressbar"
          aria-label="全市场扫描进度"
          aria-valuemin="0"
          aria-valuemax="100"
          :aria-valuenow="
            scanProgressIsIndeterminate ? undefined : scanProgressPercent
          "
        >
          <span
            :style="
              scanProgressIsIndeterminate
                ? undefined
                : { width: `${scanProgressPercent}%` }
            "
          ></span>
        </div>
        <p
          :class="{
            error: Boolean(scanProgressError) || latestRun?.status === 'failed',
          }"
        >
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
        <el-button
          v-if="scanIsRunning"
          class="scan-progress-action danger"
          native-type="button"
          :disabled="isStoppingScan"
          @click="stopFullMarketScan"
          type="danger"
          plain
        >
          {{ isStoppingScan ? '正在停止…' : '停止扫描' }}
        </el-button>
        <el-button
          v-else-if="isSubmittingScan"
          class="scan-progress-action"
          native-type="button"
          disabled
        >
          正在提交…
        </el-button>
        <el-button
          v-else
          class="scan-progress-action"
          native-type="button"
          @click="scanProgressOpen = false"
        >
          关闭
        </el-button>
      </template>
    </CommonModal>
  </section>
</template>
