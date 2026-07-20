<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getSalaryOverview, type SalaryOverview } from '@/api/modules/finance'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getStoredCurrentUser } from '@/utils/current-user'
import { formatSalaryCurrency, formatSalaryPercent } from '../salary-shared'

const pageData = ref<SalaryOverview | null>(null)
const isLoading = ref(false)
const pageError = ref('')
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

onMounted(() => {
  void loadPage()
})

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看工资管理'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    pageData.value = await getSalaryOverview(currentUser.id)
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '工资管理加载失败'
    openFeedback(pageError.value, 'error')
  } finally {
    isLoading.value = false
  }
}

function openFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}
</script>

<template>
  <section class="salary-page" aria-label="工资管理">
    <PageHeader title="工资管理" back-to="/finance/more-features" :prefer-back-to="true">
      <div class="salary-inline-actions">
        <RouterLink class="salary-page-link" to="/finance/salary/records">工资明细</RouterLink>
        <RouterLink v-slot="{ navigate }" to="/finance/salary/settings" custom>
          <CommonHeaderActionButton label="工资设置" @click="navigate">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 15.2A3.2 3.2 0 1 0 12 8.8A3.2 3.2 0 0 0 12 15.2Z" stroke="currentColor" stroke-width="1.8" />
              <path d="M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 1 1 16.96 19.71L16.9 19.65A1.65 1.65 0 0 0 15.08 19.32A1.65 1.65 0 0 0 14.08 20.83V21A2 2 0 1 1 10.08 21V20.91A1.65 1.65 0 0 0 9 19.4A1.65 1.65 0 0 0 7.18 19.73L7.12 19.79A2 2 0 1 1 4.29 16.96L4.35 16.9A1.65 1.65 0 0 0 4.68 15.08A1.65 1.65 0 0 0 3.17 14.08H3A2 2 0 1 1 3 10.08H3.09A1.65 1.65 0 0 0 4.6 9A1.65 1.65 0 0 0 4.27 7.18L4.21 7.12A2 2 0 1 1 7.04 4.29L7.1 4.35A1.65 1.65 0 0 0 8.92 4.68H9A1.65 1.65 0 0 0 10 3.17V3A2 2 0 1 1 14 3V3.09A1.65 1.65 0 0 0 15 4.6A1.65 1.65 0 0 0 16.82 4.27L16.88 4.21A2 2 0 1 1 19.71 7.04L19.65 7.1A1.65 1.65 0 0 0 19.32 8.92V9A1.65 1.65 0 0 0 20.83 10H21A2 2 0 1 1 21 14H20.91A1.65 1.65 0 0 0 19.4 15Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </CommonHeaderActionButton>
        </RouterLink>
      </div>
    </PageHeader>

    <div v-if="isLoading" class="salary-loading-wrap">
      <CommonLoading text="工资数据加载中..." />
    </div>

    <p v-else-if="pageError" class="salary-error-text">{{ pageError }}</p>

    <template v-else-if="pageData">
      <section class="salary-summary-card">
        <div class="salary-summary-head">
          <div class="salary-summary-eyebrow-row">
            <p class="salary-summary-eyebrow">{{ pageData.monthKey }} 工资测算</p>
            <span class="salary-pill">{{ pageData.payDay }} 日发薪</span>
          </div>
          <div class="salary-summary-main">
            <div class="salary-summary-main-top">
              <strong>{{ formatSalaryCurrency(pageData.netIncome) }}</strong>
              <div class="salary-summary-sub">
                <span class="highlight">到手率 {{ formatSalaryPercent(pageData.netRate) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="salary-summary-banner">
          <div>
            <p class="salary-summary-banner-title">全年总收入</p>
            <p class="salary-summary-banner-desc">已发 {{ pageData.paidMonths }} 个月，按最新配置滚动计算</p>
          </div>
          <div class="salary-linked-amount">
            <strong>{{ formatSalaryCurrency(pageData.annualIncome) }}</strong>
          </div>
        </div>

        <div class="salary-metric-grid">
          <article v-for="metric in pageData.metrics" :key="metric.label" class="salary-metric-card">
            <span>{{ metric.label }}</span>
            <strong>{{ formatSalaryCurrency(metric.value) }}</strong>
          </article>
        </div>
      </section>

      <section class="salary-card">
        <div class="salary-card-head">
          <strong>缴纳明细</strong>
        </div>
        <div class="salary-list">
          <article v-for="item in pageData.details" :key="item.label" class="salary-row">
            <div>
              <p class="salary-row-label">{{ item.label }}</p>
              <p class="salary-row-desc">{{ item.detail }}</p>
            </div>
            <div class="salary-row-value">
              <strong>{{ formatSalaryCurrency(item.value) }}</strong>
            </div>
          </article>
        </div>
      </section>

      <section class="salary-card">
        <div class="salary-card-head">
          <strong>关联账户</strong>
        </div>
        <div class="salary-linked-list">
          <RouterLink
            v-for="account in pageData.linkedAccounts"
            :key="account.accountType"
            :to="account.routePath"
            class="salary-linked-card"
          >
            <div class="salary-linked-top">
              <div>
                <p class="salary-linked-title">{{ account.title }}</p>
              </div>
              <div class="salary-linked-amount">
                <strong>{{ formatSalaryCurrency(account.currentBalance) }}</strong>
              </div>
            </div>
          </RouterLink>
        </div>
      </section>

      <section class="salary-card">
        <div class="salary-card-head">
          <strong>税务服务</strong>
        </div>
        <div class="salary-linked-list">
          <RouterLink class="salary-linked-card" :to="pageData.taxSummary.routePath">
            <div class="salary-linked-top">
              <div>
                <p class="salary-linked-title">工资税务</p>
                <p class="salary-linked-note">年度收入 {{ formatSalaryCurrency(pageData.taxSummary.annualIncome) }}</p>
              </div>
              <div class="salary-linked-amount">
                <strong>{{ formatSalaryCurrency(pageData.taxSummary.annualTax) }}</strong>
                <span>本月 {{ formatSalaryCurrency(pageData.taxSummary.currentMonthTax) }}</span>
              </div>
            </div>
          </RouterLink>
        </div>
      </section>
    </template>

    <CommonFeedback v-model="feedbackVisible" :message="feedbackMessage" :type="feedbackType" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
