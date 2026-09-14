<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getFreeEstimate, type FreeEstimate } from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const estimate = ref<FreeEstimate | null>(null)
const currentAssetsInput = ref('')
const monthlyIncomeInput = ref('')
const monthlyExpenseInput = ref('')
const withdrawalRateInput = ref('4')
const expectedReturnInput = ref('4')
const isLoading = ref(false)
const pageError = ref('')

const currentUser = getStoredCurrentUser()
const hasEstimate = computed(() => Boolean(estimate.value))
const realWithdrawalRate = 4
const realExpectedReturn = 4

const currentAssets = computed(() => normalizeMoney(currentAssetsInput.value, estimate.value?.currentNetAssets ?? 0))
const monthlyIncome = computed(() => normalizeMoney(monthlyIncomeInput.value, estimate.value?.monthlyIncome ?? 0))
const monthlyExpense = computed(() => normalizeMoney(monthlyExpenseInput.value, estimate.value?.monthlyExpense ?? 0))
const withdrawalRate = computed(() => normalizeRate(withdrawalRateInput.value, 4, 1, 10))
const expectedReturn = computed(() => normalizeRate(expectedReturnInput.value, 4, 0, 20))

const calculation = computed(() => {
  if (!estimate.value) {
    return null
  }

  const currentNetAssets = Math.max(Number(estimate.value.currentNetAssets ?? 0), 0)
  const realMonthlyExpense = Math.max(Number(estimate.value.monthlyExpense ?? 0), 0)
  const annualExpense = realMonthlyExpense * 12
  const targetAssets = realWithdrawalRate > 0
    ? annualExpense / (realWithdrawalRate / 100)
    : 0
  const monthlyIncomeValue = Math.max(Number(estimate.value.monthlyIncome ?? 0), 0)
  const monthlySurplus = monthlyIncomeValue - realMonthlyExpense
  const passiveMonthlyIncome = currentNetAssets * (realExpectedReturn / 100) / 12
  const sustainableMonthlyExpense = currentNetAssets * (realWithdrawalRate / 100) / 12
  const progress = targetAssets > 0
    ? Math.min((currentNetAssets / targetAssets) * 100, 100)
    : 100
  const monthsToFreedom = calculateMonthsToTarget(
    currentNetAssets,
    targetAssets,
    Math.max(monthlySurplus, 0),
    realExpectedReturn,
  )

  return {
    currentNetAssets,
    annualExpense,
    targetAssets,
    monthlyIncome: monthlyIncomeValue,
    monthlySurplus,
    passiveMonthlyIncome,
    sustainableMonthlyExpense,
    progress,
    monthsToFreedom,
    remainingAssets: Math.max(targetAssets - currentNetAssets, 0),
    runwayMonths: realMonthlyExpense > 0 ? currentNetAssets / realMonthlyExpense : null,
    savingsRate: monthlyIncomeValue > 0 ? (monthlySurplus / monthlyIncomeValue) * 100 : null,
    isFree: targetAssets <= currentNetAssets,
  }
})

const projection = computed(() => {
  if (!estimate.value) {
    return null
  }

  const currentNetAssets = currentAssets.value
  const annualExpense = monthlyExpense.value * 12
  const targetAssets = withdrawalRate.value > 0
    ? annualExpense / (withdrawalRate.value / 100)
    : 0
  const monthlyIncomeValue = monthlyIncome.value
  const monthlySurplus = monthlyIncomeValue - monthlyExpense.value
  const monthsToFreedom = calculateMonthsToTarget(
    currentNetAssets,
    targetAssets,
    Math.max(monthlySurplus, 0),
    expectedReturn.value,
  )

  return {
    targetAssets,
    remainingAssets: Math.max(targetAssets - currentNetAssets, 0),
    monthlySurplus,
    monthsToFreedom,
    savingsRate: monthlyIncomeValue > 0 ? (monthlySurplus / monthlyIncomeValue) * 100 : null,
  }
})

const statusTitle = computed(() => {
  if (!calculation.value) {
    return '等待数据'
  }
  if (calculation.value.isFree) {
    return '恭喜，当前资产已覆盖目标'
  }
  if (calculation.value.monthsToFreedom === null) {
    return '还需要提高每月结余'
  }
  return '正在靠近财务自由'
})

const statusDescription = computed(() => {
  if (!calculation.value) {
    return '正在读取你的财务记录'
  }
  if (calculation.value.isFree) {
    return '按当前参数，资产理论上可以覆盖年度生活支出。'
  }
  if (calculation.value.monthsToFreedom === null) {
    return '当前月度结余不足以在设定回报率下积累到目标本金。'
  }
  return `按当前收入、支出和预期回报，预计还需 ${formatDuration(calculation.value.monthsToFreedom)}。`
})

const dataPeriodText = computed(() => {
  if (!estimate.value?.dataStartDate || !estimate.value?.dataEndDate) {
    return '暂无收支日期记录'
  }
  return `${estimate.value.dataStartDate} 至 ${estimate.value.dataEndDate}`
})

async function loadEstimate() {
  if (!currentUser) {
    pageError.value = '请先登录后使用 Free 测算'
    return
  }

  isLoading.value = true
  pageError.value = ''
  try {
    const result = await getFreeEstimate({ userId: currentUser.id })
    estimate.value = result
    currentAssetsInput.value = formatInputNumber(result.currentNetAssets)
    monthlyIncomeInput.value = formatInputNumber(result.monthlyIncome)
    monthlyExpenseInput.value = formatInputNumber(result.monthlyExpense)
  } catch (error) {
    estimate.value = null
    pageError.value = error instanceof Error ? error.message : 'Free 测算数据加载失败'
  } finally {
    isLoading.value = false
  }
}

function calculateMonthsToTarget(
  currentAssets: number,
  targetAssets: number,
  monthlySurplus: number,
  annualReturn: number,
) {
  if (targetAssets <= currentAssets) {
    return 0
  }
  if (monthlySurplus <= 0 && annualReturn <= 0) {
    return null
  }

  const monthlyReturn = annualReturn / 100 / 12
  let balance = currentAssets
  for (let month = 1; month <= 1200; month += 1) {
    balance = balance * (1 + monthlyReturn) + monthlySurplus
    if (balance >= targetAssets) {
      return month
    }
  }
  return null
}

function normalizeMoney(value: string, fallback: number) {
  const parsed = Number(String(value ?? '').replace(/[,\s，]/g, ''))
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
}

function normalizeRate(value: string, fallback: number, min: number, max: number) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return fallback
  }
  return Math.min(Math.max(parsed, min), max)
}

function formatInputNumber(value: number | null | undefined) {
  return Number(value ?? 0).toFixed(2)
}

function formatCurrency(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return `¥ ${amount.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function formatPercent(value: number | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) {
    return '--'
  }
  return `${Number(value).toFixed(1)}%`
}

function formatDuration(months: number | null) {
  if (months === null) {
    return '--'
  }
  if (months < 12) {
    return `${months} 个月`
  }
  const years = Math.floor(months / 12)
  const remainingMonths = months % 12
  return remainingMonths > 0 ? `${years} 年 ${remainingMonths} 个月` : `${years} 年`
}

onMounted(() => {
  void loadEstimate()
})
</script>

<template>
  <section class="free-estimate-page" aria-label="Free测算">
    <PageHeader title="Free测算" back-to="/finance/more-features" back-label="返回行情工具" :prefer-back-to="true" />

    <CommonLoading v-if="isLoading && !hasEstimate" />

    <section v-else-if="pageError" class="free-message free-message-error" role="alert">
      <strong>{{ pageError }}</strong>
      <CommonButton v-if="currentUser" variant="secondary" size="sm" @click="loadEstimate">重新加载</CommonButton>
    </section>

    <template v-else-if="calculation && estimate">
      <section class="free-hero-card">
        <div class="free-hero-copy">
          <span class="free-eyebrow">FINANCIAL INDEPENDENCE</span>
          <h1>{{ statusTitle }}</h1>
          <p class="free-hero-description">{{ statusDescription }}</p>
        </div>
        <div class="free-hero-target">
          <span>财富自由总金额</span>
          <strong>{{ formatCurrency(calculation.targetAssets) }}</strong>
          <small>年度支出 ÷ {{ formatPercent(realWithdrawalRate) }} 提现率</small>
        </div>
        <div class="free-progress">
          <div class="free-progress-head">
            <span>当前进度</span>
            <strong>{{ formatPercent(calculation.progress) }}</strong>
          </div>
          <div class="free-progress-track" role="progressbar" :aria-valuenow="calculation.progress" aria-valuemin="0" aria-valuemax="100">
            <span :style="{ width: `${calculation.progress}%` }"></span>
          </div>
          <div class="free-progress-foot">
            <span>当前净资产 {{ formatCurrency(calculation.currentNetAssets) }}</span>
            <span v-if="calculation.isFree">已达到目标</span>
            <span v-else>还差 {{ formatCurrency(calculation.remainingAssets) }}</span>
          </div>
        </div>
      </section>

      <section class="free-layout">
        <div class="free-main-column">
          <section class="free-card free-result-card">
            <header class="free-card-head">
              <div>
                <span class="free-kicker">RESULT</span>
                <h2>这笔资产能带来什么</h2>
              </div>
            </header>
            <div class="free-result-grid">
              <article class="free-result-item free-result-item-primary">
                <span>按预期年化可产生</span>
                <AmountText :value="formatCurrency(calculation.passiveMonthlyIncome)" :tone="'positive'" class="free-result-value" />
                <small>每月被动收入</small>
              </article>
              <article class="free-result-item">
                <span>当前资产可覆盖</span>
                <strong>{{ formatDuration(calculation.runwayMonths === null ? null : Math.floor(calculation.runwayMonths)) }}</strong>
                <small>不考虑投资收益</small>
              </article>
              <article class="free-result-item">
                <span>预计达到目标</span>
                <strong>{{ formatDuration(calculation.monthsToFreedom) }}</strong>
                <small>持续投入当前月度结余</small>
              </article>
            </div>
          </section>

          <section class="free-card">
            <header class="free-card-head">
              <div>
                <span class="free-kicker">DATABASE INSIGHT</span>
                <h2>数据库里的财务画像</h2>
              </div>
              <span class="free-card-caption">{{ dataPeriodText }}</span>
            </header>
            <dl class="free-data-list">
              <div>
                <dt>历史月均收入</dt>
                <dd>{{ formatCurrency(estimate.historicalMonthlyIncome) }}</dd>
              </div>
              <div>
                <dt>历史月均支出</dt>
                <dd>{{ formatCurrency(estimate.historicalMonthlyExpense) }}</dd>
              </div>
              <div>
                <dt>有效固定支出</dt>
                <dd>{{ formatCurrency(estimate.recurringMonthlyExpense) }}</dd>
              </div>
              <div>
                <dt>纳入统计月份</dt>
                <dd>{{ estimate.dataMonths }} 个月</dd>
              </div>
              <div>
                <dt>收支记录</dt>
                <dd>{{ estimate.incomeTransactionCount }} 笔收入 / {{ estimate.expenseTransactionCount }} 笔支出</dd>
              </div>
            </dl>
          </section>
        </div>

        <aside class="free-card free-settings-card">
          <header class="free-card-head">
            <div>
              <span class="free-kicker">WHAT IF</span>
              <h2>调整你的假设</h2>
            </div>
          </header>
          <div class="free-form">
            <label class="free-field">
              <span>当前净资产</span>
              <div class="free-input-wrap">
                <b>¥</b>
                <input v-model="currentAssetsInput" inputmode="decimal" type="number" min="0" step="1000" aria-label="当前净资产">
              </div>
              <small>默认取账户中的当前净资产</small>
            </label>
            <label class="free-field">
              <span>每月收入</span>
              <div class="free-input-wrap">
                <b>¥</b>
                <input v-model="monthlyIncomeInput" inputmode="decimal" type="number" min="0" step="100" aria-label="每月收入">
              </div>
              <small>默认取历史月均收入</small>
            </label>
            <label class="free-field">
              <span>每月生活费</span>
              <div class="free-input-wrap">
                <b>¥</b>
                <input v-model="monthlyExpenseInput" inputmode="decimal" type="number" min="0" step="100" aria-label="每月生活费">
              </div>
              <small>默认取历史月均支出</small>
            </label>
            <label class="free-field">
              <span>安全提现率</span>
              <div class="free-input-wrap">
                <input v-model="withdrawalRateInput" inputmode="decimal" type="number" min="1" max="10" step="0.1" aria-label="安全提现率">
                <b>%</b>
              </div>
              <small>常见长期测算区间为 3%～4%</small>
            </label>
            <label class="free-field">
              <span>预期年化回报</span>
              <div class="free-input-wrap">
                <input v-model="expectedReturnInput" inputmode="decimal" type="number" min="0" max="20" step="0.1" aria-label="预期年化回报">
                <b>%</b>
              </div>
              <small>只用于推演，不代表实际收益承诺</small>
            </label>
          </div>
          <div class="free-assumption">
            <span>当前月度结余</span>
            <AmountText
              :value="formatCurrency(projection?.monthlySurplus)"
              :tone="projection && projection.monthlySurplus >= 0 ? 'positive' : 'negative'"
              class="free-assumption-value"
            />
            <small>预计剩余时间 {{ formatDuration(projection?.monthsToFreedom ?? null) }} · 储蓄率 {{ formatPercent(projection?.savingsRate) }}</small>
            <div class="free-assumption-summary">
              <span>预测财富自由总金额 <strong>{{ formatCurrency(projection?.targetAssets) }}</strong></span>
              <span>还需积累 <strong>{{ formatCurrency(projection?.remainingAssets) }}</strong></span>
            </div>
          </div>
        </aside>
      </section>

      <p class="free-footnote">
        说明：本页用当前净资产、现金收支和历史月均支出做估算；目标本金采用“年度生活费 ÷ 安全提现率”，结果仅用于规划参考。
      </p>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
