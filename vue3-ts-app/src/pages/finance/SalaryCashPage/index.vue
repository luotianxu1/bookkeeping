<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getSalaryCashPage, type SalaryCashPage } from '@/api/modules/finance'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getStoredCurrentUser } from '@/utils/current-user'
import { createRecentYearOptions, formatSalaryCurrency } from '../salary-shared'

const pageData = ref<SalaryCashPage | null>(null)
const isLoading = ref(false)
const pageError = ref('')
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const selectedYear = ref(String(new Date().getFullYear()))
const yearOptions = createRecentYearOptions(5)

onMounted(() => {
  void loadPage()
})

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看现金账户'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    pageData.value = await getSalaryCashPage(currentUser.id, Number(selectedYear.value))
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '现金账户加载失败'
    feedbackMessage.value = pageError.value
    feedbackType.value = 'error'
    feedbackVisible.value = true
  } finally {
    isLoading.value = false
  }
}

function handleYearChange(value: string) {
  selectedYear.value = value
  void loadPage()
}
</script>

<template>
  <section class="salary-page" aria-label="现金账户">
    <PageHeader title="现金账户" back-to="/finance/salary" :prefer-back-to="true">
      <CommonSelect
        class="salary-year-select"
        label=""
        :model-value="selectedYear"
        :options="yearOptions"
        @update:model-value="handleYearChange"
      />
    </PageHeader>

    <div v-if="isLoading" class="salary-loading-wrap">
      <CommonLoading text="现金账户加载中..." />
    </div>

    <p v-else-if="pageError" class="salary-error-text">{{ pageError }}</p>

    <template v-else-if="pageData">
      <section class="salary-summary-card">
        <div class="salary-summary-head">
          <p class="salary-summary-eyebrow">{{ pageData.year }} 年工资到账</p>
          <div class="salary-summary-main">
            <div class="salary-summary-main-top">
              <strong>{{ formatSalaryCurrency(pageData.annualCashIncome) }}</strong>
              <span class="salary-pill">已发 {{ pageData.paidMonths }} 个月</span>
            </div>
            <div class="salary-summary-sub">
              <span>本月到手 {{ formatSalaryCurrency(pageData.currentMonthCashIncome) }}</span>
              <span class="highlight">扣款后可支配现金</span>
            </div>
          </div>
        </div>

        <div class="salary-metric-grid salary-cash-metric-grid">
          <article v-for="metric in pageData.metrics" :key="metric.label" class="salary-metric-card">
            <span>{{ metric.label }}</span>
            <strong>{{ formatSalaryCurrency(metric.value) }}</strong>
          </article>
        </div>
      </section>

      <section class="salary-card">
        <div class="salary-card-head">
          <strong>月度到账明细</strong>
          <span class="salary-pill">扣款后</span>
        </div>
        <div class="salary-month-list">
          <article v-for="item in pageData.monthItems" :key="item.monthKey" class="salary-month-card">
            <div class="salary-month-top">
              <div>
                <p class="salary-month-title">{{ item.monthLabel }}</p>
                <p class="salary-month-note">税前工资 {{ formatSalaryCurrency(item.grossIncome) }}</p>
              </div>
              <div class="salary-month-amount">
                <strong>{{ formatSalaryCurrency(item.cashIncome) }}</strong>
                <span>{{ item.statusText }}</span>
              </div>
            </div>
            <div class="salary-cash-deductions">
              <div class="salary-row">
                <p class="salary-row-desc">五险一金个人扣款</p>
                <strong>{{ formatSalaryCurrency(item.personalDeduction) }}</strong>
              </div>
              <div class="salary-row">
                <p class="salary-row-desc">个人所得税</p>
                <strong>{{ formatSalaryCurrency(item.taxAmount) }}</strong>
              </div>
            </div>
          </article>
        </div>
      </section>
    </template>

    <CommonFeedback v-model="feedbackVisible" :message="feedbackMessage" :type="feedbackType" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
