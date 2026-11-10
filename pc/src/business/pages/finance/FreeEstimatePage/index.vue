<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed, onMounted, ref } from 'vue'

import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import {
  getFreeEstimate,
  type FreeEstimate,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'
import ProjectionChart from './ProjectionChart.vue'
import { buildScenarios, type ScenarioId } from './projection'

const estimate = ref<FreeEstimate | null>(null)
const currentAssetsInput = ref('')
const monthlyIncomeInput = ref('')
const monthlyExpenseInput = ref('')
const withdrawalRateInput = ref('4')
const expectedReturnInput = ref('4')
const isLoading = ref(true)
const pageError = ref('')
const selectedScenarioId = ref<ScenarioId>('base')
const chartYears = ref('30')
const chartRanges = [
  { label: '10年', value: '10' },
  { label: '20年', value: '20' },
  { label: '30年', value: '30' },
]
const currentUser = getStoredCurrentUser()
const tianjinSelfPayRule = {
  label: '2026年9月起',
  base: 5180,
  pensionRate: 20,
  medicalRate: 8.5,
}
const tianjinMinimumSelfPay =
  (tianjinSelfPayRule.base *
    (tianjinSelfPayRule.pensionRate + tianjinSelfPayRule.medicalRate)) /
  100
const includeSocialInsurance = ref(true)
const socialInsuranceMonthly = computed(() =>
  includeSocialInsurance.value ? tianjinMinimumSelfPay : 0,
)
const currentAssets = computed(() =>
  normalizeMoney(
    currentAssetsInput.value,
    estimate.value?.currentNetAssets ?? 0,
  ),
)
const monthlyIncome = computed(() =>
  normalizeMoney(monthlyIncomeInput.value, estimate.value?.monthlyIncome ?? 0),
)
const monthlyExpense = computed(() =>
  normalizeMoney(
    monthlyExpenseInput.value,
    estimate.value?.monthlyExpense ?? 0,
  ),
)
const withdrawalRate = computed(() =>
  normalizeRate(withdrawalRateInput.value, 4, 1, 10),
)
const expectedReturn = computed(() =>
  normalizeRate(expectedReturnInput.value, 4, 0, 20),
)
const totalMonthlyExpense = computed(
  () => monthlyExpense.value + socialInsuranceMonthly.value,
)
const monthlySurplus = computed(
  () => monthlyIncome.value - totalMonthlyExpense.value,
)
const targetAssets = computed(
  () => (totalMonthlyExpense.value * 12) / (withdrawalRate.value / 100),
)
const remainingAssets = computed(() =>
  Math.max(targetAssets.value - currentAssets.value, 0),
)

const savingsRate = computed(() =>
  monthlyIncome.value > 0
    ? (monthlySurplus.value / monthlyIncome.value) * 100
    : null,
)
const scenarios = computed(() =>
  buildScenarios(
    currentAssets.value,
    targetAssets.value,
    monthlySurplus.value,
    expectedReturn.value,
    Number(chartYears.value),
  ),
)
const selectedScenario = computed(
  () => scenarios.value.find((item) => item.id === selectedScenarioId.value)!,
)

function resetAssumptions() {
  if (!estimate.value) return
  currentAssetsInput.value = Number(
    estimate.value.currentNetAssets ?? 0,
  ).toFixed(2)
  monthlyIncomeInput.value = Number(estimate.value.monthlyIncome ?? 0).toFixed(
    2,
  )
  monthlyExpenseInput.value = Number(
    estimate.value.monthlyExpense ?? 0,
  ).toFixed(2)
  withdrawalRateInput.value = '4'
  expectedReturnInput.value = '4'
  includeSocialInsurance.value = true
  selectedScenarioId.value = 'base'
}

async function loadEstimate() {
  if (!currentUser) {
    pageError.value = '请先登录后使用财务自由测算'
    isLoading.value = false
    return
  }
  isLoading.value = true
  pageError.value = ''
  try {
    estimate.value = await getFreeEstimate({ userId: currentUser.id })
    resetAssumptions()
  } catch (error) {
    estimate.value = null
    pageError.value = getErrorMessage(error, '财务自由测算数据加载失败')
  } finally {
    isLoading.value = false
  }
}

function normalizeMoney(value: string, fallback: number) {
  const parsed = Number(String(value ?? '').replace(/[,\s，]/g, ''))
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
}
function normalizeRate(
  value: string,
  fallback: number,
  min: number,
  max: number,
) {
  const parsed = Number(value)
  return Number.isFinite(parsed)
    ? Math.min(Math.max(parsed, min), max)
    : fallback
}
function formatCurrency(value: number | null | undefined) {
  return `¥ ${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
function formatPercent(value: number | null) {
  return formatPercentage(value)
}
function formatDuration(months: number | null): string {
  if (months === null) return '100年内未达标'
  if (months === 0) return '已达到目标'
  if (months < 12) return `${months}个月`
  const years = Math.floor(months / 12),
    rest = months % 12
  return `${years}年${rest ? `${rest}个月` : ''}`
}
onMounted(loadEstimate)

type ScenarioRow = (typeof scenarios.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="财务自由测算">
      <template #right>
        <el-button @click="resetAssumptions">恢复默认</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="loadEstimate">重试</el-button>
    </el-alert>
    <template v-if="estimate">
      <MetricStrip
        :items="[
          {
            label: '预计达标时间',
            value: formatDuration(selectedScenario.monthsToFreedom),
          },
          { label: '目标本金', value: formatCurrency(targetAssets) },
          { label: '还需积累', value: formatCurrency(remainingAssets) },
          { label: '储蓄率', value: formatPercent(savingsRate) },
        ]"
      />
      <div class="desktop-calculator">
        <PagePanel title="测算参数">
          <div class="pc-form-grid free-form">
            <label class="pc-field free-field free-field-wide">
              <span>当前净资产</span>
              <div class="free-input-wrap">
                <b>¥</b>
                <DesktopInput
                  v-model="currentAssetsInput"
                  type="number"
                  inputmode="decimal"
                  min="0"
                  step="1000"
                  aria-label="当前净资产"
                />
              </div>
            </label>
            <label class="pc-field free-field">
              <span>每月收入</span>
              <div class="free-input-wrap">
                <b>¥</b>
                <DesktopInput
                  v-model="monthlyIncomeInput"
                  type="number"
                  inputmode="decimal"
                  min="0"
                  step="100"
                  aria-label="每月收入"
                />
              </div>
            </label>
            <label class="pc-field free-field">
              <span>每月生活费</span>
              <div class="free-input-wrap">
                <b>¥</b>
                <DesktopInput
                  v-model="monthlyExpenseInput"
                  type="number"
                  inputmode="decimal"
                  min="0"
                  step="100"
                  aria-label="每月生活费"
                />
              </div>
            </label>
            <label class="pc-field free-field">
              <span>安全提现率</span>
              <div class="free-input-wrap">
                <DesktopInput
                  percentage
                  v-model="withdrawalRateInput"
                  type="number"
                  inputmode="decimal"
                  min="1"
                  max="10"
                  step="0.1"
                  aria-label="安全提现率"
                />
                <b>%</b>
              </div>
            </label>
            <label class="pc-field free-field">
              <span>基准年化回报</span>
              <div class="free-input-wrap">
                <DesktopInput
                  percentage
                  v-model="expectedReturnInput"
                  type="number"
                  inputmode="decimal"
                  min="0"
                  max="20"
                  step="0.1"
                  aria-label="基准年化回报"
                />
                <b>%</b>
              </div>
            </label>
          </div>
          <CommonSwitch
            v-model="includeSocialInsurance"
            :label="`计入社保医保 ${formatCurrency(tianjinMinimumSelfPay)}/月`"
          />
          <p class="desktop-note">
            月总支出 {{ formatCurrency(totalMonthlyExpense) }}
          </p>
        </PagePanel>
        <div class="desktop-results">
          <DesktopTable :rows="scenarios" :loading="false" title="方案对比">
            <el-table-column
              prop="label"
              label="方案"
              min-width="140"
              sortable="custom"
              show-overflow-tooltip
            />
            <!-- @vue-generic {ScenarioRow} -->
            <el-table-column label="年化回报" min-width="150">
              <template #default="{ row }: { row: ScenarioRow }">
                {{ formatPercent(row.annualReturn) }}
              </template>
            </el-table-column>
            <!-- @vue-generic {ScenarioRow} -->
            <el-table-column label="达标时间" min-width="150">
              <template #default="{ row }: { row: ScenarioRow }">
                {{ formatDuration(row.monthsToFreedom) }}
              </template>
            </el-table-column>
            <!-- @vue-generic {ScenarioRow} -->
            <el-table-column fixed="right" label="操作" width="150">
              <template #default="{ row }: { row: ScenarioRow }">
                <el-button
                  link
                  type="primary"
                  :aria-label="row.label + '方案'"
                  @click="selectedScenarioId = row.id"
                >
                  {{ row.id === selectedScenarioId ? '已选择' : '选择方案' }}
                </el-button>
              </template>
            </el-table-column>
          </DesktopTable>
          <PagePanel title="资产与目标趋势">
            <template #actions>
              <SegmentedControl
                v-model="chartYears"
                :options="chartRanges"
                label="趋势年限"
              />
            </template>
            <ProjectionChart
              :scenarios="scenarios"
              :target="targetAssets"
              :selected="selectedScenarioId"
              :years="Number(chartYears)"
            />
          </PagePanel>
          <PagePanel title="历史收支参考">
            <dl class="desktop-record-details">
              <div>
                <dt>当前净资产</dt>
                <dd>{{ formatCurrency(estimate.currentNetAssets) }}</dd>
              </div>
              <div>
                <dt>历史月均收入</dt>
                <dd>{{ formatCurrency(estimate.historicalMonthlyIncome) }}</dd>
              </div>
              <div>
                <dt>历史月均支出</dt>
                <dd>{{ formatCurrency(estimate.historicalMonthlyExpense) }}</dd>
              </div>
              <div>
                <dt>收支记录</dt>
                <dd>
                  {{ estimate.incomeTransactionCount }} 笔收入 /
                  {{ estimate.expenseTransactionCount }} 笔支出
                </dd>
              </div>
              <div>
                <dt>数据截至</dt>
                <dd>{{ estimate.asOfDate }}</dd>
              </div>
            </dl>
          </PagePanel>
        </div>
      </div>
      <p class="free-footnote">
        社保医保采用天津灵活就业最低养老及职工医保标准（{{
          tianjinSelfPayRule.label
        }}）。测算按固定支出和回报率计算，未计通胀、税费及收益波动，仅供规划参考。
      </p>
    </template>
  </section>
</template>
