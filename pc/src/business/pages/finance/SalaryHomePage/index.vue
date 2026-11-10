<script setup lang="ts">
import { formatPercentageText } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  getSalaryOverview,
  type SalaryOverview,
} from '@/business/api/modules/finance'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { formatSalaryCurrency, formatSalaryPercent } from '../salary-shared'
import { getErrorMessage } from '@/business/api/request'

const pageData = ref<SalaryOverview | null>(null)
const isLoading = ref(true)
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
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    pageData.value = await getSalaryOverview(currentUser.id)
  } catch (error) {
    pageError.value = getErrorMessage(error, '工资管理加载失败')
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

type SalaryDetailRow = NonNullable<typeof pageData.value>['details'][number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="工资管理">
      <template #right>
        <RouterLink to="/finance/salary/records">
          <el-button>工资明细</el-button>
        </RouterLink>
        <RouterLink to="/finance/salary/settings">
          <el-button type="primary">工资设置</el-button>
        </RouterLink>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <el-skeleton v-if="isLoading" :rows="8" animated />
    <template v-if="pageData">
      <MetricStrip
        :items="[
          {
            label: '本月到手',
            value: formatSalaryCurrency(pageData.netIncome),
          },
          {
            label: '全年总收入',
            value: formatSalaryCurrency(pageData.annualIncome),
          },
          { label: '到手率', value: formatSalaryPercent(pageData.netRate) },
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
      <div class="desktop-main-aside">
        <DesktopTable
          :rows="pageData.details"
          :loading="false"
          title="缴纳明细"
        >
          <el-table-column
            prop="label"
            label="项目"
            min-width="160"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {SalaryDetailRow} -->
          <el-table-column label="金额" min-width="150">
            <template #default="{ row }: { row: SalaryDetailRow }">
              {{ formatSalaryCurrency(row.value) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="detail"
            :formatter="
              (row: SalaryDetailRow) => formatPercentageText(row.detail ?? '')
            "
            label="说明"
            min-width="200"
            sortable="custom"
            show-overflow-tooltip
          />
        </DesktopTable>
        <PagePanel title="关联账户">
          <div class="desktop-link-list">
            <RouterLink
              v-for="account in pageData.linkedAccounts"
              :key="account.accountType"
              :to="account.routePath"
            >
              <span>{{ account.title }}</span>
              <strong>
                {{ formatSalaryCurrency(account.currentBalance) }}
              </strong>
            </RouterLink>
            <RouterLink :to="pageData.taxSummary.routePath">
              <span>年度个税</span>
              <strong>
                {{ formatSalaryCurrency(pageData.taxSummary.annualTax) }}
              </strong>
            </RouterLink>
          </div>
        </PagePanel>
      </div>
    </template>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
