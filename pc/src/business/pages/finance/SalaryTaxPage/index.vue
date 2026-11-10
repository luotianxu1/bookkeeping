<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { onMounted, ref } from 'vue'
import {
  getSalaryTaxPage,
  type SalaryTaxPage,
} from '@/business/api/modules/finance'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { createRecentYearOptions, formatSalaryCurrency } from '../salary-shared'
import { getErrorMessage } from '@/business/api/request'

const pageData = ref<SalaryTaxPage | null>(null)
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
    pageError.value = '请先登录后查看工资税务'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    pageData.value = await getSalaryTaxPage(
      currentUser.id,
      Number(selectedYear.value),
    )
  } catch (error) {
    pageError.value = getErrorMessage(error, '工资税务加载失败')
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

type TaxMonthRow = NonNullable<typeof pageData.value>['monthItems'][number]
type TaxDeductionRow = NonNullable<typeof pageData.value>['deductions'][number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="工资税务">
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
            label: '年度个税',
            value: formatSalaryCurrency(pageData.annualTax),
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
      <div class="desktop-columns">
        <DesktopTable
          :rows="pageData.monthItems"
          :loading="false"
          title="月度个税"
        >
          <el-table-column
            prop="monthLabel"
            label="月份"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {TaxMonthRow} -->
          <el-table-column label="税前收入" min-width="150">
            <template #default="{ row }: { row: TaxMonthRow }">
              {{ formatSalaryCurrency(row.grossIncome) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {TaxMonthRow} -->
          <el-table-column label="个税" min-width="150">
            <template #default="{ row }: { row: TaxMonthRow }">
              {{ formatSalaryCurrency(row.taxAmount) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {TaxMonthRow} -->
          <el-table-column label="税后收入" min-width="150">
            <template #default="{ row }: { row: TaxMonthRow }">
              {{ formatSalaryCurrency(row.takeHomeIncome) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="statusText"
            label="状态"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
        </DesktopTable>
        <DesktopTable
          :rows="pageData.deductions"
          :loading="false"
          title="专项附加扣除"
        >
          <el-table-column
            prop="label"
            label="项目"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {TaxDeductionRow} -->
          <el-table-column label="月度扣除" min-width="150">
            <template #default="{ row }: { row: TaxDeductionRow }">
              {{ formatSalaryCurrency(row.monthlyValue) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {TaxDeductionRow} -->
          <el-table-column label="年度累计" min-width="150">
            <template #default="{ row }: { row: TaxDeductionRow }">
              {{ formatSalaryCurrency(row.annualValue) }}
            </template>
          </el-table-column>
        </DesktopTable>
      </div>
    </template>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
