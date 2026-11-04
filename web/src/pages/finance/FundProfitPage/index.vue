<script setup lang="ts">
import { useRouter } from 'vue-router'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import type { FundProfitDetail } from '@/api/modules/finance'
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
</script>

<template>
  <section class="fund-profit-page" aria-label="基金收益">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="基金收益" back-to="/finance/more-features" back-label="返回更多功能">
      <CommonHeaderRefreshButton
        v-if="shouldShowPositionRefreshButton"
        label="刷新持仓数据"
        :loading="isRefreshingPositions"
        @click="refreshPositionData"
      />
    </PageHeader>

    <p v-if="pageError" class="fund-profit-message fund-profit-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" text="基金收益加载中..." />

    <template v-else-if="pageData">
      <SegmentedControl
        v-model="selectedSummaryShortcut"
        :options="summaryShortcutOptions"
        label="基金收益范围切换"
        class="fund-profit-range-seg"
      />

      <section class="fund-profit-summary-card" aria-label="基金收益汇总">
        <div class="fund-profit-summary-top">
          <div class="fund-profit-summary-main">
            <p>{{ summaryTitle }}</p>
            <div class="summary-amount-row">
              <AmountText
                tag="strong"
                class="summary-amount"
                :value="summaryMetricProfitText"
                :tone="summaryMetricProfitTone"
              />
              <AmountText
                tag="span"
                class="summary-rate-inline"
                :value="summaryMetricRateText"
                :tone="summaryMetricRateTone"
              />
            </div>
          </div>

          <div class="summary-account-pill">{{ summaryPillText }}</div>
        </div>

        <p class="summary-hint">{{ summaryHint }}</p>
      </section>

      <section class="fund-profit-card" aria-label="近7日收益波动">
        <header class="fund-profit-card-head trend-card-head">
          <strong>近7日收益波动</strong>
          <div class="trend-legend">
            <span class="trend-legend-item">
              <i class="positive"></i>
              <span>上涨</span>
            </span>
            <span class="trend-legend-item">
              <i class="negative"></i>
              <span>下跌</span>
            </span>
          </div>
        </header>

        <div class="trend-chart">
          <div v-for="point in trendPoints" :key="point.key" class="trend-column">
            <span class="trend-profit-text" :class="`is-${getTrendTone(point)}`">
              {{ point.profit === null || point.profit === undefined ? '--' : formatSignedCurrency(Number(point.profit), 0) }}
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

        <p class="card-footnote">{{ trendFootnote }}</p>

        <div class="trend-stats">
          <article class="trend-stat up">
            <span>上涨 {{ trendStats.positiveDays }} 天</span>
            <strong>{{ trendPositiveRate }}</strong>
          </article>
          <article class="trend-stat down">
            <span>下跌 {{ trendStats.negativeDays }} 天</span>
            <strong>{{ trendNegativeRate }}</strong>
          </article>
          <article class="trend-stat neutral">
            <span>最大回撤</span>
            <strong>{{ trendStats.trough ? formatSignedCurrency(Number(trendStats.trough.profit ?? 0), 0) : '--' }}</strong>
          </article>
        </div>
      </section>

      <section class="fund-profit-card" aria-label="收益日历">
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
            <button type="button" class="calendar-nav-button" @click="shiftCalendarAnchor(-1)">‹</button>
            <strong>{{ calendarAnchorLabel }}</strong>
            <button type="button" class="calendar-nav-button" @click="shiftCalendarAnchor(1)">›</button>
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
              <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
            </div>

            <div class="day-calendar-grid">
              <template v-for="(item, index) in dayCalendarCells" :key="item?.key ?? `blank-${index}`">
                <span v-if="!item" class="day-calendar-cell day-calendar-cell-blank"></span>
                <button
                  v-else
                  type="button"
                  :class="[
                    'day-calendar-cell',
                    `is-${getPeriodCardTone(item)}`,
                    { selected: item.selected, current: item.current, disabled: item.profit === null || item.profit === undefined },
                  ]"
                  @click="handlePeriodSelect(item)"
                >
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.profit === null || item.profit === undefined ? '--' : formatCurrency(item.profit, 0) }}</span>
                </button>
              </template>
            </div>
          </div>
        </template>

        <div v-else class="period-card-grid" :class="{ 'period-card-grid-year': selectedView === 'year' }">
          <template v-for="(item, index) in selectedView === 'year' ? yearPeriodCards : periodCards" :key="item?.key ?? `spacer-${index}`">
            <span v-if="!item" class="period-card period-card-spacer"></span>
            <button
              v-else
              type="button"
              :class="[
                'period-card',
                `is-${getPeriodCardTone(item)}`,
                { selected: item.selected, current: item.current, disabled: item.profit === null || item.profit === undefined },
              ]"
              @click="handlePeriodSelect(item)"
            >
              <span class="period-card-label">{{ item.label }}</span>
              <AmountText
                tag="strong"
                class="period-card-value"
                :value="item.profit === null || item.profit === undefined ? '--' : formatCurrency(item.profit)"
                :tone="getTone(Number(item.profit ?? 0))"
              />
              <span class="period-card-side">
                {{ item.profitRate === null || item.profitRate === undefined ? '--' : formatRate(item.profitRate) }}
              </span>
            </button>
          </template>
        </div>

        <p class="calendar-footnote">{{ calendarFootnote }}</p>
      </section>

      <section class="fund-profit-card" aria-label="基金收益明细">
        <header class="fund-profit-card-head detail-card-head">
          <strong>{{ detailTitle }}</strong>
          <span class="card-side-text">跟随 {{ selectionLabel }}</span>
        </header>

        <div class="detail-filter-row">
          <button
            v-for="item in detailFilterOptions"
            :key="item.value"
            type="button"
            :class="['detail-filter', { active: selectedDetailFilter === item.value }]"
            @click="selectedDetailFilter = item.value"
          >
            {{ item.label }}
          </button>
        </div>

        <p v-if="isDetailLoading" class="empty-text">明细加载中...</p>
        <p v-else-if="visibleDetails.length === 0" class="empty-text">当前筛选下暂无基金收益明细</p>

        <template v-else>
          <button
            v-for="item in visibleDetails"
            :key="item.positionId"
            type="button"
            class="detail-row"
            @click="openDetail(item)"
          >
            <div class="detail-main">
              <div class="fund-name-row">
                <strong>{{ item.productName }}</strong>
              </div>
              <div class="fund-detail-meta">
                <span>{{ formatHoldingQuantity(item.holdingQuantity) }} · 市值 {{ formatCurrency(item.holdingAmount) }}</span>
                <span v-if="item.productSymbol" class="fund-code-chip">{{ item.productSymbol }}</span>
              </div>
            </div>
            <div class="detail-side">
              <AmountText
                tag="strong"
                class="detail-profit-text"
                :value="formatSignedCurrency(item.periodProfit)"
                :tone="getTone(item.periodProfit)"
              />
              <AmountText
                tag="span"
                class="detail-accumulated-text"
                :value="`累计 ${formatSignedCurrency(getDetailAccumulatedProfit(item))}`"
                :tone="getTone(getDetailAccumulatedProfit(item))"
              />
            </div>
          </button>
        </template>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
