<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'
import { amountTone } from '@/business/utils/amount-tone'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'

import { formatAmount } from '@/business/utils/format'
import { computed, onMounted, ref, watch } from 'vue'

import MonthPicker from '@/business/components/common/MonthPicker/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'
import YearPicker from '@/business/components/common/YearPicker/index.vue'
import { useFinanceFamilyView } from '@/business/composables/useFinanceFamilyView'
import {
  getTransactionAnalysis,
  type TransactionAnalysis,
  type TransactionAnalysisCategoryBreakdownItem,
  type TransactionAnalysisPeriod,
  type TransactionAnalysisPeriodSummary,
} from '@/business/api/modules/finance'
import { getErrorMessage } from '@/business/api/request'
import {
  compareTransactionsDesc,
  mergeTransactionAnalysisResults,
  normalizeAnalysisForView,
} from './analysisMerge'
import {
  buildMonthKey,
  detailAmountText,
  detailMetaText,
  findPreferredDayKey,
  findPreferredSummaryKey,
  formatSignedAmount,
  getBreakdownItemKey,
  isTransactionInBreakdown,
  type SummaryTab,
} from './analysisFormat'
import { useAnalysisCharts } from './useAnalysisCharts'

type PeriodLabel = '月' | '年'

const period = ref<PeriodLabel>('月')
const periodOptions: PeriodLabel[] = ['月', '年']
const DETAIL_PAGE_SIZE = 10
const BREAKDOWN_DETAIL_PAGE_SIZE = 10
const summaryTab = ref<SummaryTab>('支出')
const activeMonth = ref(buildMonthKey(new Date()))
const activeYear = ref(new Date().getFullYear())
const selectedDayKey = ref('')
const selectedYearMonthKey = ref('')
const detailCurrentPage = ref(1)
const expandedBreakdownKey = ref('')
const breakdownDetailCurrentPage = ref(1)
const analysis = ref<TransactionAnalysis | null>(null)
const isLoading = ref(true)
const pageError = ref('')
const requestSerial = ref(0)

const {
  familyView,
  familyViewOptions,
  selectedFamilyView,
  canSwitchFamilyView,
  selectedViewerUserIds,
  viewerNameByUserId,
  isReadOnlyFamilyView,
  loadFamilyMembers,
} = useFinanceFamilyView()

const summaryMetricMap: Record<SummaryTab, 'income' | 'expense' | 'surplus'> = {
  收入: 'income',
  支出: 'expense',
  结余: 'surplus',
}
const emptySummary = {
  income: 0,
  expense: 0,
  surplus: 0,
  incomeCount: 0,
  expenseCount: 0,
  transactionCount: 0,
}

const analysisPeriod = computed<TransactionAnalysisPeriod>(() =>
  period.value === '年' ? 'year' : 'month',
)
const activeMetric = computed(() => summaryMetricMap[summaryTab.value])
const requestKey = computed(
  () =>
    `${selectedFamilyView.value.value}:${analysisPeriod.value}:${analysisPeriod.value === 'month' ? activeMonth.value : activeYear.value}`,
)
const summary = computed(() => analysis.value?.summary ?? emptySummary)
const summaryCards = computed(() => [
  {
    label: '收入' as const,
    amount: formatAmount(summary.value.income),
    tone: amountTone(summary.value.income),
  },
  {
    label: '支出' as const,
    amount: formatAmount(summary.value.expense),
    tone: amountTone(-summary.value.expense),
  },
  {
    label: '结余' as const,
    amount: formatSignedAmount(summary.value.surplus),
    tone: amountTone(summary.value.surplus),
  },
])
const breakdownItems = computed<TransactionAnalysisCategoryBreakdownItem[]>(
  () => {
    if (!analysis.value) return []
    if (summaryTab.value === '收入') return analysis.value.incomeBreakdown
    if (summaryTab.value === '支出') return analysis.value.expenseBreakdown
    return []
  },
)
const periodSummaries = computed(() => analysis.value?.periodSummaries ?? [])
const periodTransactions = computed(() =>
  periodSummaries.value
    .flatMap((item) => item.transactions)
    .filter((transaction) => transaction.type === activeMetric.value)
    .sort(compareTransactionsDesc),
)
const expandedBreakdownItem = computed(
  () =>
    breakdownItems.value.find(
      (item) => getBreakdownItemKey(item) === expandedBreakdownKey.value,
    ) ?? null,
)
const expandedBreakdownTransactions = computed(() => {
  const item = expandedBreakdownItem.value
  if (!item) return []
  return periodTransactions.value.filter((transaction) =>
    isTransactionInBreakdown(transaction, item),
  )
})
const breakdownDetailTotalItems = computed(
  () => expandedBreakdownTransactions.value.length,
)
const breakdownDetailTotalPages = computed(() =>
  Math.max(
    1,
    Math.ceil(breakdownDetailTotalItems.value / BREAKDOWN_DETAIL_PAGE_SIZE),
  ),
)

const familyViewSelectOptions = computed<CommonSelectOption[]>(() =>
  familyViewOptions.value.map((option) => ({
    label: option.label,
    value: option.value,
  })),
)

const currentDetailSummary = computed(() => {
  if (analysisPeriod.value === 'month') {
    return (
      periodSummaries.value.find((item) => item.key === selectedDayKey.value) ??
      periodSummaries.value[0] ??
      null
    )
  }

  return (
    periodSummaries.value.find(
      (item) => item.key === selectedYearMonthKey.value,
    ) ??
    periodSummaries.value[0] ??
    null
  )
})
const detailTransactions = computed(() => {
  const source = currentDetailSummary.value?.transactions ?? []
  if (summaryTab.value === '收入') {
    return source.filter((transaction) => transaction.type === 'income')
  }
  if (summaryTab.value === '支出') {
    return source.filter((transaction) => transaction.type === 'expense')
  }
  return source
})
const detailTotalItems = computed(() => detailTransactions.value.length)
const detailTotalPages = computed(() =>
  Math.max(1, Math.ceil(detailTotalItems.value / DETAIL_PAGE_SIZE)),
)

const breakdownSectionTitle = computed(() => {
  if (summaryTab.value === '结余') return '分类占比'
  return `${summaryTab.value}分类占比`
})
const trendSectionTitle = computed(() => {
  const prefix = analysisPeriod.value === 'year' ? '年度' : '月度'
  if (summaryTab.value === '结余') return `${prefix}结余趋势`
  return `${prefix}${summaryTab.value}趋势`
})

const { pieRef, lineRef, syncCharts } = useAnalysisCharts({
  breakdownItems,
  summaryTab,
  activeMetric,
  analysis,
})

watch(requestKey, () => {
  void loadAnalysis()
})

watch(
  [breakdownItems, () => analysis.value?.summary, activeMetric],
  () => {
    void syncCharts()
  },
  { deep: true, flush: 'post' },
)

watch(
  periodSummaries,
  () => {
    syncSelectedSummary()
    void syncCharts()
  },
  { deep: true, flush: 'post' },
)

watch(
  isLoading,
  (loading) => {
    if (!loading && !pageError.value) {
      void syncCharts()
    }
  },
  { flush: 'post' },
)

watch(
  [summaryTab, selectedDayKey, selectedYearMonthKey, analysisPeriod],
  () => {
    detailCurrentPage.value = 1
  },
)

watch([summaryTab, requestKey], () => {
  expandedBreakdownKey.value = ''
  breakdownDetailCurrentPage.value = 1
})

watch(expandedBreakdownKey, () => {
  breakdownDetailCurrentPage.value = 1
})

watch(detailTotalPages, (totalPages) => {
  if (detailCurrentPage.value > totalPages) {
    detailCurrentPage.value = totalPages
  }
})

watch(
  breakdownItems,
  () => {
    if (expandedBreakdownKey.value && !expandedBreakdownItem.value) {
      expandedBreakdownKey.value = ''
    }
  },
  { deep: true },
)

watch(breakdownDetailTotalPages, (totalPages) => {
  if (breakdownDetailCurrentPage.value > totalPages) {
    breakdownDetailCurrentPage.value = totalPages
  }
})

onMounted(() => {
  void initializePage()
})

async function initializePage() {
  await loadFamilyMembers()
  await loadAnalysis()
  void syncCharts()
}

async function loadAnalysis() {
  if (selectedViewerUserIds.value.length === 0) {
    analysis.value = null
    isLoading.value = false
    pageError.value = '请先登录后查看收支分析'
    return
  }

  const currentRequest = requestSerial.value + 1
  requestSerial.value = currentRequest
  isLoading.value = true
  pageError.value = ''

  try {
    const results = await Promise.all(
      selectedViewerUserIds.value.map(async (userId) => {
        const result = await getTransactionAnalysis({
          userId,
          period: analysisPeriod.value,
          month:
            analysisPeriod.value === 'month' ? activeMonth.value : undefined,
          year: analysisPeriod.value === 'year' ? activeYear.value : undefined,
        })

        return normalizeAnalysisForView(result, userId, {
          isReadOnlyFamilyView: isReadOnlyFamilyView.value,
          selectedFamilyViewKind: selectedFamilyView.value.kind,
          viewerNameByUserId: viewerNameByUserId.value,
        })
      }),
    )

    if (currentRequest !== requestSerial.value) {
      return
    }

    const merged = mergeTransactionAnalysisResults(results, {
      period: analysisPeriod.value,
      activeMonth: activeMonth.value,
      activeYear: activeYear.value,
    })
    analysis.value = merged
    if (merged.month && merged.month !== activeMonth.value) {
      activeMonth.value = merged.month
    }
    if (typeof merged.year === 'number' && merged.year !== activeYear.value) {
      activeYear.value = merged.year
    }
    syncSelectedSummary()
  } catch (error) {
    if (currentRequest !== requestSerial.value) {
      return
    }
    analysis.value = null
    pageError.value = getErrorMessage(error, '收支分析加载失败')
  } finally {
    if (currentRequest === requestSerial.value) {
      isLoading.value = false
    }
  }
}

function syncSelectedSummary() {
  if (analysisPeriod.value === 'month') {
    const available = new Set(periodSummaries.value.map((item) => item.key))
    if (!available.has(selectedDayKey.value)) {
      selectedDayKey.value = findPreferredDayKey(
        periodSummaries.value,
        analysis.value?.month,
      )
    }
    return
  }

  const available = new Set(periodSummaries.value.map((item) => item.key))
  if (!available.has(selectedYearMonthKey.value)) {
    selectedYearMonthKey.value = findPreferredSummaryKey(periodSummaries.value)
  }
}

function toggleBreakdownItem(item: TransactionAnalysisCategoryBreakdownItem) {
  const key = getBreakdownItemKey(item)
  expandedBreakdownKey.value = expandedBreakdownKey.value === key ? '' : key
}

type AnalysisRow = (typeof detailTransactions.value)[number]
function selectDesktopPeriod(key: string) {
  expandedBreakdownKey.value = ''
  if (period.value === '月') selectedDayKey.value = key
  else selectedYearMonthKey.value = key
}
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="收支分析" />
    <div class="desktop-filter-bar">
      <SegmentedControl
        v-model="period"
        :options="periodOptions"
        label="统计周期"
      />
      <MonthPicker v-if="period === '月'" v-model="activeMonth" />
      <YearPicker v-else v-model="activeYear" />
      <CommonSelect
        v-if="canSwitchFamilyView"
        v-model="familyView"
        label="账本范围"
        :options="familyViewSelectOptions"
      />
      <SegmentedControl
        v-model="summaryTab"
        :options="['收入', '支出', '结余']"
        label="分析指标"
      />
    </div>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="
        summaryCards.map((item) => ({
          label: item.label,
          value: item.amount,
          tone: item.tone,
        }))
      "
    />
    <el-skeleton v-if="isLoading" :rows="8" animated />
    <template v-else-if="!pageError">
      <div class="desktop-columns">
        <PagePanel :title="trendSectionTitle">
          <div ref="lineRef" class="desktop-chart" />
        </PagePanel>
        <PagePanel v-if="summaryTab !== '结余'" :title="breakdownSectionTitle">
          <div ref="pieRef" class="desktop-chart" />
        </PagePanel>
      </div>
      <div class="desktop-columns">
        <DesktopRecordList
          :rows="breakdownItems"
          :loading="false"
          title="分类明细"
          :paginate="false"
          empty-title="该指标暂无分类数据"
          empty-hint="切换收入 / 支出指标后可查看分类占比"
        >
          <template
            #default="{
              row,
            }: {
              row: TransactionAnalysisCategoryBreakdownItem
            }"
          >
            <article class="record-card analysis-category-card">
              <div class="record-card-head">
                <div class="record-card-title">
                  <span>{{ row.categoryName }}</span>
                </div>
                <div class="record-card-aside">
                  <span class="analysis-category-count">
                    {{ row.transactionCount }} 笔
                  </span>
                </div>
              </div>
              <AmountText
                class="analysis-category-amount"
                :value="formatAmount(row.amount)"
                :tone="amountTone(summaryTab === '支出' ? -row.amount : row.amount)"
                font-size="24px"
              />
              <el-progress
                :format="formatPercentage"
                :percentage="Math.max(0, Math.min(100, row.percent))"
              />
              <div class="record-card-foot">
                <el-button link type="primary" @click="toggleBreakdownItem(row)">
                  查看流水
                </el-button>
              </div>
            </article>
          </template>
        </DesktopRecordList>
        <DesktopRecordList
          :rows="periodSummaries"
          :loading="false"
          title="期间汇总"
          :paginate="false"
          empty-title="暂无期间数据"
          empty-hint="选择月份或年份后可查看汇总"
        >
          <template
            #default="{ row }: { row: TransactionAnalysisPeriodSummary }"
          >
            <article class="record-card analysis-period-card">
              <div class="record-card-head">
                <div class="record-card-title">
                  <span>{{ row.label }}</span>
                </div>
              </div>
              <dl class="record-card-body analysis-period-body">
                <div>
                  <dt>收入</dt>
                  <dd>
                    <AmountText
                      :value="formatAmount(row.income)"
                      :tone="amountTone(row.income)"
                    />
                  </dd>
                </div>
                <div>
                  <dt>支出</dt>
                  <dd>
                    <AmountText
                      :value="formatAmount(row.expense)"
                      :tone="amountTone(-row.expense)"
                    />
                  </dd>
                </div>
                <div>
                  <dt>结余</dt>
                  <dd>
                    <AmountText
                      :value="formatAmount(row.surplus)"
                      :tone="amountTone(row.surplus)"
                    />
                  </dd>
                </div>
              </dl>
              <div class="record-card-foot">
                <el-button
                  link
                  type="primary"
                  @click="selectDesktopPeriod(row.key)"
                >
                  查看
                </el-button>
              </div>
            </article>
          </template>
        </DesktopRecordList>
      </div>
      <DesktopTable
        :rows="
          expandedBreakdownItem
            ? expandedBreakdownTransactions
            : detailTransactions
        "
        :loading="false"
        title="选中期间 / 分类流水"
        search-placeholder="搜索选中范围内的流水"
        searchable
      >
        <el-table-column
          prop="title"
          label="名称"
          min-width="200"
          sortable="custom"
          show-overflow-tooltip
        />
        <!-- @vue-generic {AnalysisRow} -->
        <el-table-column label="账户与日期" min-width="220">
          <template #default="{ row }: { row: AnalysisRow }">
            {{ detailMetaText(row) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {AnalysisRow} -->
        <el-table-column label="金额" min-width="150">
          <template #default="{ row }: { row: AnalysisRow }">
            <AmountText
              :value="detailAmountText(row)"
              :tone="
                row.type === 'transfer'
                  ? 'neutral'
                  : amountTone(row.type === 'income' ? row.amount : -row.amount)
              "
            />
          </template>
        </el-table-column>
      </DesktopTable>
    </template>
  </section>
</template>
<style scoped>
/* 分类占比卡：分类名 + 笔数 + 方向色金额大数字 + 占比进度条 */
.analysis-category-card .analysis-category-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.analysis-category-card .analysis-category-amount {
  font-weight: 700;
  letter-spacing: -0.02em;
}

/* 期间汇总卡：收入红 / 支出绿 / 结余按方向着色 */
.analysis-period-card .analysis-period-body {
  font-size: 14px;
}
</style>
