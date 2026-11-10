<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { useRouter } from 'vue-router'
import AmountText from '@/business/components/common/AmountText/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import type { FundProfitDetail } from '@/business/api/modules/finance'
import {
  formatCurrency,
  formatHoldingQuantity,
  formatRate,
  formatSignedCurrency,
  getDetailAccumulatedProfit,
  getTone,
} from './fundProfitFormat'
import { useFundProfitData } from './useFundProfitData'
import { useFundProfitView } from './useFundProfitView'

const router = useRouter()

const {
  selectedView,
  selectedAccountId,
  selectedSummaryShortcut,
  selectedDetailFilter,
  dayAnchor,
  monthAnchor,
  yearAnchor,
  pageData,
  isLoading,
  isDetailLoading,
  isCalendarLoading,
  isRefreshingPositions,
  pageError,
  shouldShowPositionRefreshButton,
  viewModel,
  refreshPositionData,
  handlePeriodSelect,
  shiftCalendarAnchor,
  showFeedbackModal,
  feedbackMessage,
  feedbackType,
} = useFundProfitData()

const {
  isDayView,
  summaryShortcutOptions,
  summaryTitle,
  summaryPillText,
  summaryMetricProfitText,
  summaryMetricProfitTone,
  summaryMetricRateText,
  summaryMetricRateTone,
  summaryHint,
  trendPoints,
  trendStats,
  trendPositiveRate,
  trendNegativeRate,
  trendFootnote,
  calendarTitle,
  selectionLabel,
  calendarHeadLabel,
  detailTitle,
  dayCalendarCells,
  periodCards,
  yearPeriodCards,
  visibleDetails,
  detailFilterOptions,
  calendarAnchorLabel,
  calendarStatePillText,
  calendarFootnote,
  showCalendarLegend,
  calendarLegendItems,
  getTrendBarStyle,
  getTrendTone,
  getPeriodCardTone,
} = useFundProfitView({
  pageData,
  selectedView,
  selectedAccountId,
  selectedSummaryShortcut,
  selectedDetailFilter,
  dayAnchor,
  monthAnchor,
  yearAnchor,
})

const viewOptions = [
  { label: '日视图', value: 'day' },
  { label: '月视图', value: 'month' },
  { label: '年视图', value: 'year' },
] as const
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']

function openDetail(item: FundProfitDetail) {
  router.push({
    path: '/finance/accounts/investment/detail',
    query: {
      positionId: String(item.positionId),
    },
  })
}

type ProfitRow = (typeof visibleDetails.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="基金收益">
      <template #right>
        <el-button
          v-if="shouldShowPositionRefreshButton"
          :loading="isRefreshingPositions"
          @click="refreshPositionData"
        >
          刷新持仓
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <template v-if="pageData">
      <div class="desktop-filter-bar">
        <SegmentedControl
          v-model="selectedSummaryShortcut"
          :options="summaryShortcutOptions"
          label="基金收益范围切换"
        />
      </div>
      <MetricStrip
        :items="[
          { label: '区间收益', value: summaryMetricProfitText },
          { label: '收益率', value: summaryMetricRateText },
          { label: '上涨天数', value: trendStats.positiveDays },
          { label: '下跌天数', value: trendStats.negativeDays },
        ]"
      />
      <div class="desktop-columns">
        <PagePanel title="近七日收益">
          <div class="trend-chart">
            <div
              v-for="point in trendPoints"
              :key="point.key"
              class="trend-column"
            >
              <span
                class="trend-profit-text"
                :class="`is-${getTrendTone(point)}`"
              >
                {{
                  point.profit === null || point.profit === undefined
                    ? '--'
                    : formatSignedCurrency(Number(point.profit), 0)
                }}
              </span>
              <span class="trend-bar-track">
                <span
                  class="trend-bar-fill"
                  :class="`is-${getTrendTone(point)}`"
                  :style="getTrendBarStyle(point)"
                ></span>
              </span>
              <span class="trend-axis-label">{{ point.label }}</span>
            </div>
          </div>
          <p class="desktop-note">{{ trendFootnote }}</p>
        </PagePanel>
        <PagePanel title="收益日历">
          <header class="fund-profit-card-head calendar-card-head">
            <strong>{{ calendarTitle }}</strong>
            <span class="card-side-text">{{ calendarHeadLabel }}</span>
          </header>
          <SegmentedControl
            v-model="viewModel"
            :options="viewOptions"
            label="基金收益视图切换"
            class="calendar-view-seg"
            variant="surface"
          />
          <div class="calendar-meta-row">
            <div class="calendar-meta-left">
              <el-button
                native-type="button"
                class="calendar-nav-button"
                @click="shiftCalendarAnchor(-1)"
              >
                ‹
              </el-button>
              <strong>{{ calendarAnchorLabel }}</strong>
              <el-button
                native-type="button"
                class="calendar-nav-button"
                @click="shiftCalendarAnchor(1)"
              >
                ›
              </el-button>
            </div>
            <div class="calendar-status-pill" :class="`is-${selectedView}`">
              <span>{{ calendarStatePillText }}</span>
            </div>
          </div>
          <div v-if="showCalendarLegend" class="calendar-legend-row">
            <span
              v-for="item in calendarLegendItems"
              :key="item.label"
              class="calendar-legend-item"
            >
              <i :class="`is-${item.tone}`"></i>
              <span>{{ item.label }}</span>
            </span>
          </div>
          <p v-if="isCalendarLoading" class="empty-text">日历加载中...</p>
          <template v-else-if="isDayView">
            <div class="calendar-box">
              <div class="weekday-row">
                <span v-for="label in weekdayLabels" :key="label">
                  {{ label }}
                </span>
              </div>
              <div class="pc-grid day-calendar-grid">
                <template
                  v-for="(item, index) in dayCalendarCells"
                  :key="item?.key ?? `blank-${index}`"
                >
                  <span
                    v-if="!item"
                    class="day-calendar-cell day-calendar-cell-blank"
                  ></span>
                  <el-button
                    v-else
                    native-type="button"
                    :class="[
                      'day-calendar-cell',
                      `is-${getPeriodCardTone(item)}`,
                      {
                        selected: item.selected,
                        current: item.current,
                        disabled:
                          item.profit === null || item.profit === undefined,
                      },
                    ]"
                    @click="handlePeriodSelect(item)"
                    class="pc-content-button"
                  >
                    <strong>{{ item.label }}</strong>
                    <span>
                      {{
                        item.profit === null || item.profit === undefined
                          ? '--'
                          : formatCurrency(item.profit, 0)
                      }}
                    </span>
                  </el-button>
                </template>
              </div>
            </div>
          </template>
          <div
            v-else
            class="period-card-grid"
            :class="{ 'period-card-grid-year': selectedView === 'year' }"
          >
            <template
              v-for="(item, index) in selectedView === 'year'
                ? yearPeriodCards
                : periodCards"
              :key="item?.key ?? `spacer-${index}`"
            >
              <span v-if="!item" class="period-card period-card-spacer"></span>
              <el-button
                v-else
                native-type="button"
                :class="[
                  'period-card',
                  `is-${getPeriodCardTone(item)}`,
                  {
                    selected: item.selected,
                    current: item.current,
                    disabled: item.profit === null || item.profit === undefined,
                  },
                ]"
                @click="handlePeriodSelect(item)"
                class="pc-content-button"
              >
                <span class="period-card-label">{{ item.label }}</span>
                <AmountText
                  tag="strong"
                  class="period-card-value"
                  :value="
                    item.profit === null || item.profit === undefined
                      ? '--'
                      : formatCurrency(item.profit)
                  "
                  :tone="getTone(Number(item.profit ?? 0))"
                />
                <span class="period-card-side">
                  {{
                    item.profitRate === null || item.profitRate === undefined
                      ? '--'
                      : formatRate(item.profitRate)
                  }}
                </span>
              </el-button>
            </template>
          </div>
          <p class="calendar-footnote">{{ calendarFootnote }}</p>
        </PagePanel>
      </div>
      <DesktopTable
        :rows="visibleDetails"
        :loading="isDetailLoading"
        title="基金收益明细"
        search-placeholder="搜索基金"
        searchable
      >
        <template #toolbar>
          <el-radio-group v-model="selectedDetailFilter">
            <el-radio-button
              v-for="option in detailFilterOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
        </template>
        <el-table-column
          prop="productName"
          label="基金"
          min-width="240"
          sortable="custom"
          show-overflow-tooltip
        />
        <el-table-column
          prop="productSymbol"
          label="代码"
          min-width="140"
          sortable="custom"
          show-overflow-tooltip
        />
        <!-- @vue-generic {ProfitRow} -->
        <el-table-column label="持有份额" min-width="150">
          <template #default="{ row }: { row: ProfitRow }">
            {{ formatHoldingQuantity(row.holdingQuantity) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {ProfitRow} -->
        <el-table-column label="市值" min-width="150">
          <template #default="{ row }: { row: ProfitRow }">
            {{ formatCurrency(row.holdingAmount) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {ProfitRow} -->
        <el-table-column label="区间收益" min-width="150">
          <template #default="{ row }: { row: ProfitRow }">
            <AmountText
              :value="formatSignedCurrency(row.periodProfit)"
              :tone="getTone(row.periodProfit)"
            />
          </template>
        </el-table-column>
        <!-- @vue-generic {ProfitRow} -->
        <el-table-column label="累计收益" min-width="150">
          <template #default="{ row }: { row: ProfitRow }">
            {{ formatSignedCurrency(getDetailAccumulatedProfit(row)) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {ProfitRow} -->
        <el-table-column fixed="right" label="操作" width="150">
          <template #default="{ row }: { row: ProfitRow }">
            <el-button link type="primary" @click="openDetail(row)">
              查看持仓
            </el-button>
          </template>
        </el-table-column>
      </DesktopTable>
    </template>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
