<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'

import DesktopTable from '@/business/components/DesktopTable.vue'
type CashRow = SalaryCashPage['monthItems'][number]
import { onMounted, ref } from 'vue'
import {
  getSalaryCashPage,
  type SalaryCashPage,
} from '@/business/api/modules/finance'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

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
let loadVersion = 0
const yearOptions = createRecentYearOptions(5)

onMounted(() => {
  void loadPage()
})

async function loadPage() {
  const version = ++loadVersion
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看现金账户'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const result = await getSalaryCashPage(
      currentUser.id,
      Number(selectedYear.value),
    )
    if (version !== loadVersion) return
    pageData.value = result
  } catch (error) {
    if (version !== loadVersion) return
    pageError.value = getErrorMessage(error, '现金账户加载失败')
    feedbackMessage.value = pageError.value
    feedbackType.value = 'error'
    feedbackVisible.value = true
  } finally {
    if (version === loadVersion) isLoading.value = false
  }
}

function handleYearChange(value: string) {
  selectedYear.value = value
  void loadPage()
}
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="工资到账">
      <template #right>
        <CommonSelect
          label="年份"
          aria-label="年份"
          :model-value="selectedYear"
          :options="yearOptions"
          @update:model-value="handleYearChange"
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
    <template v-if="pageData">
      <MetricStrip
        :items="[
          {
            label: '年度到账',
            value: formatSalaryCurrency(pageData.annualCashIncome),
          },
          {
            label: '本月到账',
            value: formatSalaryCurrency(pageData.currentMonthCashIncome),
          },
          { label: '已发月数', value: pageData.paidMonths },
        ]"
      />
      <MetricStrip
        :items="
          pageData.metrics.map((metric) => ({
            label: metric.label,
            value: formatSalaryCurrency(metric.value),
          }))
        "
      />
      <DesktopTable title="月度到账明细" :rows="pageData.monthItems">
        <el-table-column prop="monthLabel" label="月份" min-width="120" />
        <!-- @vue-generic {CashRow} -->
        <el-table-column label="税前工资" min-width="160" align="right">
          <template #default="{ row }: { row: CashRow }">
            {{ formatSalaryCurrency(row.grossIncome) }}
          </template>
        </el-table-column>
        <!-- @vue-generic {CashRow} -->
        <el-table-column label="五险一金个人扣款" min-width="160" align="right">
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
    </template>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
