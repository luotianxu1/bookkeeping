<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'

import DesktopRecordList from '@/business/components/DesktopRecordList.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'
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
  <section class="desktop-workspace stagger-in">
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
      <DesktopRecordList
        title="月度到账明细"
        search-placeholder="搜索月份或状态"
        searchable
        :search-keys="['monthLabel', 'statusText']"
        row-key="monthKey"
        empty-title="暂无到账记录"
        empty-hint="发薪月份会在这里出现"
        :rows="pageData.monthItems"
        :loading="isLoading"
      >
        <template #default="{ row }: { row: CashRow }">
          <article class="record-card salary-cash-card">
            <div class="record-card-head">
              <div class="record-card-title">
                <span>{{ row.monthLabel }}</span>
              </div>
              <div class="record-card-aside">
                <el-tag
                  size="small"
                  effect="light"
                  :type="row.cashIncome > 0 ? 'success' : 'info'"
                >
                  {{ row.statusText }}
                </el-tag>
              </div>
            </div>
            <div class="salary-cash-amount">
              <span class="salary-cash-amount-label">实际到账</span>
              <AmountText
                class="salary-cash-amount-value"
                :value="formatSalaryCurrency(row.cashIncome)"
                font-size="26px"
              />
            </div>
            <dl class="record-card-body">
              <div>
                <dt>税前工资</dt>
                <dd>
                  <AmountText :value="formatSalaryCurrency(row.grossIncome)" />
                </dd>
              </div>
              <div>
                <dt>五险一金个人扣款</dt>
                <dd>
                  <AmountText
                    :value="formatSalaryCurrency(row.personalDeduction)"
                    tone="negative"
                    show-sign
                  />
                </dd>
              </div>
              <div>
                <dt>个人所得税</dt>
                <dd>
                  <AmountText
                    :value="formatSalaryCurrency(row.taxAmount)"
                    tone="negative"
                    show-sign
                  />
                </dd>
              </div>
            </dl>
          </article>
        </template>
      </DesktopRecordList>
    </template>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
<style scoped>
/* 到账工资条卡片：实际到账大数字 + 扣款明细行 */
.salary-cash-amount {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.salary-cash-amount-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.salary-cash-amount-value {
  font-weight: 700;
  letter-spacing: -0.02em;
}
</style>
