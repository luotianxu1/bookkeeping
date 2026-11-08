<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import { getFreeEstimate, type FreeEstimate } from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getErrorMessage } from '@/api/request'
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
const chartRanges = [{ label: '10年', value: '10' }, { label: '20年', value: '20' }, { label: '30年', value: '30' }]
const currentUser = getStoredCurrentUser()
const tianjinSelfPayRule = { label: '2026年9月起', base: 5180, pensionRate: 20, medicalRate: 8.5 }
const tianjinMinimumSelfPay = tianjinSelfPayRule.base * (tianjinSelfPayRule.pensionRate + tianjinSelfPayRule.medicalRate) / 100
const includeSocialInsurance = ref(true)
const socialInsuranceMonthly = computed(() => includeSocialInsurance.value ? tianjinMinimumSelfPay : 0)
const currentAssets = computed(() => normalizeMoney(currentAssetsInput.value, estimate.value?.currentNetAssets ?? 0))
const monthlyIncome = computed(() => normalizeMoney(monthlyIncomeInput.value, estimate.value?.monthlyIncome ?? 0))
const monthlyExpense = computed(() => normalizeMoney(monthlyExpenseInput.value, estimate.value?.monthlyExpense ?? 0))
const withdrawalRate = computed(() => normalizeRate(withdrawalRateInput.value, 4, 1, 10))
const expectedReturn = computed(() => normalizeRate(expectedReturnInput.value, 4, 0, 20))
const totalMonthlyExpense = computed(() => monthlyExpense.value + socialInsuranceMonthly.value)
const monthlySurplus = computed(() => monthlyIncome.value - totalMonthlyExpense.value)
const targetAssets = computed(() => totalMonthlyExpense.value * 12 / (withdrawalRate.value / 100))
const remainingAssets = computed(() => Math.max(targetAssets.value - currentAssets.value, 0))
const progress = computed(() => targetAssets.value > 0 ? Math.min(currentAssets.value / targetAssets.value * 100, 100) : 100)
const savingsRate = computed(() => monthlyIncome.value > 0 ? monthlySurplus.value / monthlyIncome.value * 100 : null)
const scenarios = computed(() => buildScenarios(currentAssets.value, targetAssets.value, monthlySurplus.value, expectedReturn.value, Number(chartYears.value)))
const selectedScenario = computed(() => scenarios.value.find(item => item.id === selectedScenarioId.value)!)
const monthlyReturn = computed(() => currentAssets.value * selectedScenario.value.annualReturn / 100 / 12)
const scenarioDifference = computed(() => {
  const base = scenarios.value.find(item => item.id === 'base')!.monthsToFreedom
  const selected = selectedScenario.value.monthsToFreedom
  if (selectedScenarioId.value === 'base') return '按下方填写的收入、支出和回报率测算'
  if (base === null || selected === null) return '收入、支出和提现率相同，仅改变年化回报'
  const difference = selected - base
  return difference === 0 ? '与基准方案预计达标时间相同' : `比基准方案${difference < 0 ? '提前' : '推迟'} ${formatDuration(Math.abs(difference))}`
})
const dataPeriodText = computed(() => estimate.value?.dataStartDate && estimate.value?.dataEndDate
  ? `${estimate.value.dataStartDate} 至 ${estimate.value.dataEndDate}` : '暂无收支日期记录')

function resetAssumptions() {
  if (!estimate.value) return
  currentAssetsInput.value = Number(estimate.value.currentNetAssets ?? 0).toFixed(2)
  monthlyIncomeInput.value = Number(estimate.value.monthlyIncome ?? 0).toFixed(2)
  monthlyExpenseInput.value = Number(estimate.value.monthlyExpense ?? 0).toFixed(2)
  withdrawalRateInput.value = '4'; expectedReturnInput.value = '4'
  includeSocialInsurance.value = true; selectedScenarioId.value = 'base'
}

async function loadEstimate() {
  if (!currentUser) { pageError.value = '请先登录后使用财务自由测算'; isLoading.value = false; return }
  isLoading.value = true; pageError.value = ''
  try {
    estimate.value = await getFreeEstimate({ userId: currentUser.id })
    resetAssumptions()
  } catch (error) {
    estimate.value = null
    pageError.value = getErrorMessage(error, '财务自由测算数据加载失败')
  } finally { isLoading.value = false }
}

function normalizeMoney(value: string, fallback: number) {
  const parsed = Number(String(value ?? '').replace(/[,\s，]/g, ''))
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
}
function normalizeRate(value: string, fallback: number, min: number, max: number) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? Math.min(Math.max(parsed, min), max) : fallback
}
function formatCurrency(value: number | null | undefined) {
  return `¥ ${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
function formatPercent(value: number | null) { return value === null ? '--' : `${value.toFixed(1)}%` }
function formatDuration(months: number | null): string {
  if (months === null) return '100年内未达标'
  if (months === 0) return '已达到目标'
  if (months < 12) return `${months}个月`
  const years = Math.floor(months / 12), rest = months % 12
  return `${years}年${rest ? `${rest}个月` : ''}`
}
onMounted(loadEstimate)
</script>

<template>
  <section class="free-estimate-page" aria-label="财务自由测算">
    <PageHeader title="财务自由测算" back-to="/finance/more-features" back-label="返回更多功能" :prefer-back-to="true" />
    <div v-if="pageError" class="free-message" role="alert">
      <span>{{ pageError }}</span><CommonButton v-if="currentUser" variant="secondary" size="sm" @click="loadEstimate">重新加载</CommonButton>
    </div>
    <CommonLoading v-else-if="isLoading" />
    <template v-else-if="estimate">
      <section class="free-card free-plan" aria-label="方案测算">
        <div class="free-result" role="region" aria-label="方案结果">
          <div class="free-result-title"><span>{{ selectedScenario.label }}方案 · {{ selectedScenario.monthsToFreedom === 0 ? '当前进度' : '预计达到目标' }}</span><span class="free-badge">年化 {{ formatPercent(selectedScenario.annualReturn) }}</span></div>
          <strong class="free-duration" aria-live="polite">{{ formatDuration(selectedScenario.monthsToFreedom) }}</strong>
          <p class="free-muted">{{ scenarioDifference }}</p>
          <dl class="free-result-metrics">
            <div><dt>目标本金</dt><dd>{{ formatCurrency(targetAssets) }}</dd></div>
            <div><dt>还需积累</dt><dd>{{ formatCurrency(remainingAssets) }}</dd></div>
          </dl>
          <div class="free-progress-heading"><span>当前进度</span><strong>{{ formatPercent(progress) }}</strong></div>
          <div class="free-progress-track" role="progressbar" aria-label="当前方案目标进度" :aria-valuenow="progress" :aria-valuemin="0" :aria-valuemax="100"><span :style="{ width: `${progress}%` }" /></div>
          <dl class="free-result-details">
            <div><dt>每月结余</dt><dd><AmountText :value="formatCurrency(monthlySurplus)" :tone="monthlySurplus >= 0 ? 'positive' : 'negative'" /></dd></div>
            <div><dt>储蓄率</dt><dd>{{ formatPercent(savingsRate) }}</dd></div>
            <div><dt>月均预期收益</dt><dd>{{ formatCurrency(monthlyReturn) }}</dd></div>
          </dl>
        </div>
        <div class="free-settings">
          <header class="free-card-head"><h2>测算参数</h2><CommonButton variant="secondary" size="sm" @click="resetAssumptions">恢复默认</CommonButton></header>
          <div class="free-form">
            <label class="free-field free-field-wide"><span>当前净资产</span><div class="free-input-wrap"><b>¥</b><input v-model="currentAssetsInput" type="number" inputmode="decimal" min="0" step="1000" aria-label="当前净资产"></div></label>
            <label class="free-field"><span>每月收入</span><div class="free-input-wrap"><b>¥</b><input v-model="monthlyIncomeInput" type="number" inputmode="decimal" min="0" step="100" aria-label="每月收入"></div></label>
            <label class="free-field"><span>每月生活费</span><div class="free-input-wrap"><b>¥</b><input v-model="monthlyExpenseInput" type="number" inputmode="decimal" min="0" step="100" aria-label="每月生活费"></div></label>
            <label class="free-field"><span>安全提现率</span><div class="free-input-wrap"><input v-model="withdrawalRateInput" type="number" inputmode="decimal" min="1" max="10" step="0.1" aria-label="安全提现率"><b>%</b></div></label>
            <label class="free-field"><span>基准年化回报</span><div class="free-input-wrap"><input v-model="expectedReturnInput" type="number" inputmode="decimal" min="0" max="20" step="0.1" aria-label="基准年化回报"><b>%</b></div></label>
          </div>
          <CommonSwitch v-model="includeSocialInsurance" :label="`计入社保医保 ${formatCurrency(tianjinMinimumSelfPay)}/月`" />
          <p class="free-muted">月总支出 {{ formatCurrency(totalMonthlyExpense) }} · 目标本金 = 年支出 ÷ {{ formatPercent(withdrawalRate) }}</p>
        </div>
      </section>

      <section class="free-card" aria-label="方案与趋势对比">
        <header class="free-card-head"><h2>方案对比</h2><span class="free-muted">点击方案查看结果</span></header>
        <div class="free-scenarios" role="group" aria-label="测算方案">
          <button v-for="scenario in scenarios" :key="scenario.id" type="button" class="free-scenario" :class="{ active: scenario.id === selectedScenarioId }" :aria-label="`${scenario.label}方案`" :aria-pressed="scenario.id === selectedScenarioId" @click="selectedScenarioId = scenario.id">
            <span class="free-scenario-name">{{ scenario.label }}</span><strong>{{ formatPercent(scenario.annualReturn) }}</strong><span>预期年化回报</span><b>{{ formatDuration(scenario.monthsToFreedom) }}</b>
          </button>
        </div>
        <p class="free-muted">保守／乐观按基准回报率 −2／+2 个百分点（限 0–20%），其余参数相同。</p>
        <header class="free-chart-head"><h3>资产与目标趋势</h3><SegmentedControl v-model="chartYears" :options="chartRanges" label="趋势年限" variant="surface" /></header>
        <ProjectionChart :scenarios="scenarios" :target="targetAssets" :selected="selectedScenarioId" :years="Number(chartYears)" />
        <p class="free-muted">虚线为目标本金，实线为每月投入结余后的预计资产。图中展示未来 {{ chartYears }} 年，达标时间最长测算 100 年。</p>
        <p v-if="monthlySurplus < 0" class="free-muted">当前每月结余为负，本页沿用零投入的预测假设。</p>
      </section>

      <details class="free-card free-history">
        <summary>
          <span>历史收支参考</span>
          <span class="free-muted">{{ estimate.dataMonths }} 个月</span>
          <svg class="free-history-chevron" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
            <path d="m6 9 6 6 6-6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </summary>
        <p class="free-muted">{{ dataPeriodText }}</p>
        <dl class="free-data-list">
          <div><dt>当前净资产</dt><dd>{{ formatCurrency(estimate.currentNetAssets) }}</dd></div>
          <div><dt>历史月均收入</dt><dd>{{ formatCurrency(estimate.historicalMonthlyIncome) }}</dd></div>
          <div><dt>历史月均支出</dt><dd>{{ formatCurrency(estimate.historicalMonthlyExpense) }}</dd></div>
          <div><dt>收支记录</dt><dd>{{ estimate.incomeTransactionCount }} 笔收入 / {{ estimate.expenseTransactionCount }} 笔支出</dd></div>
          <div><dt>数据截至</dt><dd>{{ estimate.asOfDate }}</dd></div>
        </dl>
        <p class="free-muted">恢复默认会重新填入历史数据，提现率与基准回报率均恢复为 4%，并计入社保医保。</p>
      </details>
      <p class="free-footnote">社保医保采用天津灵活就业最低养老及职工医保标准（{{ tianjinSelfPayRule.label }}）。测算按固定支出和回报率计算，未计通胀、税费及收益波动，仅供规划参考。</p>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
