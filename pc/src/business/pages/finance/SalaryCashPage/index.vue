<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'
type CashRow = SalaryCashPage['monthItems'][number]
import { onMounted, ref } from 'vue'
import {
  getSalaryCashPage,
  type SalaryCashPage,
} from '@/business/api/modules/finance'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { createRecentYearOptions, formatSalaryCurrency } from '../salary-shared'
import { getErrorMessage } from '@/business/api/request'

const pageData = ref<SalaryCashPage | null>(null)
const isLoading = ref(true)
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
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    pageData.value = await getSalaryCashPage(
      currentUser.id,
      Number(selectedYear.value),
    )
  } catch (error) {
    pageError.value = getErrorMessage(error, '现金账户加载失败')
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
  <section class="pc-page salary-page" aria-label="现金账户">
    <PageHeader
      title="现金账户"
      back-to="/finance/salary"
      :prefer-back-to="true"
    >
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
      <el-card class="pc-record-card salary-summary-card" shadow="never">
        <div class="salary-summary-head">
          <p class="salary-summary-eyebrow">{{ pageData.year }} 年工资到账</p>
          <div class="salary-summary-main">
            <div class="salary-summary-main-top">
              <strong>
                {{ formatSalaryCurrency(pageData.annualCashIncome) }}
              </strong>
              <span class="salary-pill">
                已发 {{ pageData.paidMonths }} 个月
              </span>
            </div>
            <div class="salary-summary-sub">
              <span>
                本月到手
                {{ formatSalaryCurrency(pageData.currentMonthCashIncome) }}
              </span>
              <span class="highlight">扣款后可支配现金</span>
            </div>
          </div>
        </div>
        <div class="pc-grid salary-metric-grid salary-cash-metric-grid">
          <el-card
            v-for="metric in pageData.metrics"
            :key="metric.label"
            class="pc-record-card salary-metric-card"
            shadow="never"
          >
            <span>{{ metric.label }}</span>
            <strong>{{ formatSalaryCurrency(metric.value) }}</strong>
          </el-card>
        </div>
      </el-card>
      <el-card class="pc-record-card salary-card" shadow="never">
        <div class="salary-card-head">
          <strong>月度到账明细</strong>
          <span class="salary-pill">扣款后</span>
        </div>
        <DesktopTable :rows="pageData.monthItems">
          <el-table-column prop="monthLabel" label="月份" min-width="120" />
          <!-- @vue-generic {CashRow} -->
          <el-table-column label="税前工资" min-width="160" align="right">
            <template #default="{ row }: { row: CashRow }">
              {{ formatSalaryCurrency(row.grossIncome) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {CashRow} -->
          <el-table-column
            label="五险一金个人扣款"
            min-width="160"
            align="right"
          >
            <template #default="{ row }: { row: CashRow }">
              {{ formatSalaryCurrency(row.personalDeduction) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {CashRow} -->
          <el-table-column label="个人所得税" min-width="160" align="right">
            <template #default="{ row }: { row: CashRow }">
              {{ formatSalaryCurrency(row.taxAmount) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {CashRow} -->
          <el-table-column label="实际到账" min-width="160" align="right">
            <template #default="{ row }: { row: CashRow }">
              {{ formatSalaryCurrency(row.cashIncome) }}
            </template>
          </el-table-column>
          <el-table-column prop="statusText" label="状态" min-width="100" />
        </DesktopTable>
      </el-card>
    </template>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
