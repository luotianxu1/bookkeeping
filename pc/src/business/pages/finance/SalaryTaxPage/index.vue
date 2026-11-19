<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'

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
let loadVersion = 0
const yearOptions = createRecentYearOptions(5)

onMounted(() => {
  void loadPage()
})

async function loadPage() {
  const version = ++loadVersion
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看工资税务'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const result = await getSalaryTaxPage(
      currentUser.id,
      Number(selectedYear.value),
    )
    if (version !== loadVersion) return
    pageData.value = result
  } catch (error) {
    if (version !== loadVersion) return
    pageError.value = getErrorMessage(error, '工资税务加载失败')
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

type TaxMonthRow = NonNullable<typeof pageData.value>['monthItems'][number]
type TaxDeductionRow = NonNullable<typeof pageData.value>['deductions'][number]
</script>
<template>
  <section class="desktop-workspace stagger-in">
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
        <DesktopRecordList
          :rows="pageData.monthItems"
          :loading="false"
          title="月度个税"
          search-placeholder="搜索月份或状态"
          searchable
          :search-keys="['monthLabel', 'statusText']"
          row-key="monthKey"
          empty-title="暂无个税记录"
          empty-hint="已发薪月份的个税会在这里出现"
        >
          <template #default="{ row }: { row: TaxMonthRow }">
            <article class="record-card salary-tax-month-card">
              <div class="record-card-head">
                <div class="record-card-title">
                  <span>{{ row.monthLabel }}</span>
                </div>
                <div class="record-card-aside">
                  <el-tag
                    size="small"
                    effect="light"
                    :type="row.taxAmount > 0 ? 'warning' : 'info'"
                  >
                    {{ row.statusText }}
                  </el-tag>
                </div>
              </div>
              <div class="salary-tax-takehome">
                <span class="salary-tax-takehome-label">税后收入</span>
                <AmountText
                  class="salary-tax-takehome-value"
                  :value="formatSalaryCurrency(row.takeHomeIncome)"
                  font-size="24px"
                />
              </div>
              <dl class="record-card-body">
                <div>
                  <dt>税前收入</dt>
                  <dd>
                    <AmountText
                      :value="formatSalaryCurrency(row.grossIncome)"
                    />
                  </dd>
                </div>
                <div>
                  <dt>个税</dt>
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
        <DesktopRecordList
          :rows="pageData.deductions"
          :loading="false"
          title="专项附加扣除"
          search-placeholder="搜索扣除项目"
          searchable
          :search-keys="['label']"
          empty-title="暂无专项附加扣除"
          empty-hint="在个税申报中配置后会在这里出现"
        >
          <template #default="{ row }: { row: TaxDeductionRow }">
            <article class="record-card salary-tax-deduction-card">
              <div class="record-card-head">
                <div class="record-card-title">
                  <span>{{ row.label }}</span>
                </div>
              </div>
              <dl class="record-card-body">
                <div>
                  <dt>月度扣除</dt>
                  <dd>{{ formatSalaryCurrency(row.monthlyValue) }}</dd>
                </div>
                <div>
                  <dt>年度累计</dt>
                  <dd>{{ formatSalaryCurrency(row.annualValue) }}</dd>
                </div>
              </dl>
            </article>
          </template>
        </DesktopRecordList>
      </div>
    </template>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
<style scoped>
/* 个税月份卡片：税后收入大数字 + 税前/个税明细 */
.salary-tax-takehome {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.salary-tax-takehome-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.salary-tax-takehome-value {
  font-weight: 700;
  letter-spacing: -0.02em;
}
</style>
