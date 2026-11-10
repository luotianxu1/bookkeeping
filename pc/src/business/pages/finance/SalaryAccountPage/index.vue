<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  createSalaryAccountRecord,
  deleteSalaryAccountRecord,
  getSalaryAccountPage,
  saveSalaryAccountBalance,
  updateSalaryAccountRecord,
  type SalaryAccountPage,
  type SalaryAccountRecordItem,
} from '@/business/api/modules/finance'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonHeaderActionButton from '@/business/components/common/CommonHeaderActionButton/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import {
  createRecentYearOptions,
  dateToMonthInput,
  formatSalaryCurrency,
  formatSalaryPercent,
  monthInputToDate,
  recordCanDelete,
  salaryAccountDisplayName,
} from '../salary-shared'
import { getErrorMessage } from '@/business/api/request'

const route = useRoute()
const pageData = ref<SalaryAccountPage | null>(null)
const isLoading = ref(true)
const isSaving = ref(false)
const pageError = ref('')
const formError = ref('')
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showBalanceModal = ref(false)
const showRecordModal = ref(false)
const editingRecord = ref<SalaryAccountRecordItem | null>(null)
const selectedYear = ref(String(new Date().getFullYear()))

const balanceForm = reactive({
  amount: '',
  note: '',
})

const recordForm = reactive({
  amount: '',
  recordMonth: `${new Date().getFullYear()}-01`,
  impactMode: '计入当前余额',
  note: '',
})

const yearOptions = createRecentYearOptions(5)
const impactModeOptions = [
  { label: '计入当前余额', value: '计入当前余额' },
  { label: '仅做备注展示', value: '仅做备注展示' },
]

const routeAccountType = computed(() =>
  String(route.params.accountType || 'social-security'),
)
const isCompactSummaryPage = computed(() =>
  ['social-security', 'medical', 'housing-fund'].includes(
    routeAccountType.value,
  ),
)
const pageTitle = computed(() =>
  salaryAccountDisplayName(routeAccountType.value),
)
const canDeleteRecord = computed(() => recordCanDelete(editingRecord.value))
const visibleMetrics = computed(() => pageData.value?.metrics ?? [])

onMounted(() => {
  void loadPage()
})

watch(
  () => route.params.accountType,
  () => {
    void loadPage()
  },
)

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看工资账户'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    pageData.value = await getSalaryAccountPage(
      currentUser.id,
      routeAccountType.value,
      Number(selectedYear.value),
    )
  } catch (error) {
    pageError.value = getErrorMessage(error, '工资账户加载失败')
    openFeedback(pageError.value, 'error')
  } finally {
    isLoading.value = false
  }
}

function changeYear(value: string) {
  selectedYear.value = value
  void loadPage()
}

function openBalanceModal() {
  if (!pageData.value) {
    return
  }
  balanceForm.amount = String(pageData.value?.currentBalance ?? 0)
  balanceForm.note = ''
  formError.value = ''
  showBalanceModal.value = true
}

function openRecordModal(record?: SalaryAccountRecordItem) {
  editingRecord.value = record ?? null
  recordForm.amount = record ? String(record.amountValue) : ''
  recordForm.recordMonth = record
    ? dateToMonthInput(record.monthKey)
    : `${selectedYear.value}-01`
  recordForm.impactMode = '计入当前余额'
  recordForm.note = record?.note ?? ''
  formError.value = ''
  showRecordModal.value = true
}

function closeBalanceModal() {
  if (isSaving.value) {
    return
  }
  showBalanceModal.value = false
  formError.value = ''
}

function closeRecordModal() {
  if (isSaving.value) {
    return
  }
  showRecordModal.value = false
  editingRecord.value = null
  formError.value = ''
}

async function submitBalance() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    formError.value = '请先登录后保存账户总额'
    return
  }

  const amount = Number(balanceForm.amount)
  if (!Number.isFinite(amount) || amount < 0) {
    formError.value = '请输入正确的账户总额'
    return
  }

  isSaving.value = true
  formError.value = ''
  try {
    pageData.value = await saveSalaryAccountBalance(routeAccountType.value, {
      userId: currentUser.id,
      amount,
      year: Number(selectedYear.value),
      note: balanceForm.note.trim(),
    })
    showBalanceModal.value = false
    openFeedback('账户总额已更新', 'success')
  } catch (error) {
    formError.value = getErrorMessage(error, '账户总额保存失败')
  } finally {
    isSaving.value = false
  }
}

async function submitRecord() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    formError.value = '请先登录后保存记录'
    return
  }

  const amount = Number(recordForm.amount)
  if (!Number.isFinite(amount) || amount < 0) {
    formError.value = '请输入正确的记录金额'
    return
  }
  if (!recordForm.recordMonth) {
    formError.value = '请选择发生月份'
    return
  }

  isSaving.value = true
  formError.value = ''
  try {
    const payload = {
      userId: currentUser.id,
      amount,
      recordMonth: monthInputToDate(recordForm.recordMonth),
      impactMode: recordForm.impactMode,
      note: recordForm.note.trim(),
    }
    pageData.value = editingRecord.value
      ? await updateSalaryAccountRecord(
          routeAccountType.value,
          editingRecord.value.id,
          payload,
        )
      : await createSalaryAccountRecord(routeAccountType.value, payload)
    showRecordModal.value = false
    openFeedback(editingRecord.value ? '记录已更新' : '记录已新增', 'success')
  } catch (error) {
    formError.value = getErrorMessage(error, '记录保存失败')
  } finally {
    isSaving.value = false
  }
}

async function removeRecord() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || !editingRecord.value || !canDeleteRecord.value) {
    return
  }

  isSaving.value = true
  formError.value = ''
  try {
    pageData.value = await deleteSalaryAccountRecord(
      routeAccountType.value,
      editingRecord.value.id,
      currentUser.id,
    )
    showRecordModal.value = false
    openFeedback('记录已删除', 'success')
  } catch (error) {
    formError.value = getErrorMessage(error, '记录删除失败')
  } finally {
    isSaving.value = false
  }
}

function handleRecordClick(record: SalaryAccountRecordItem) {
  if (!record.editable) {
    return
  }
  openRecordModal(record)
}

function openFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}

function formatInterestSettlementDate(value?: string | null) {
  const matched = value?.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!matched) {
    return `${pageData.value?.forecast.forecastYear ?? ''} 年 7 月 1 日`
  }
  return `${matched[1]} 年 ${Number(matched[2])} 月 ${Number(matched[3])} 日`
}
</script>

<template>
  <section class="pc-page salary-page" :aria-label="pageTitle">
    <PageHeader
      :title="pageTitle"
      back-to="/finance/salary"
      :prefer-back-to="true"
    >
      <CommonHeaderActionButton
        v-if="isCompactSummaryPage"
        label="修改账户总额"
        :disabled="isLoading || !pageData"
        @click="openBalanceModal"
      >
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M12 15.2A3.2 3.2 0 1 0 12 8.8A3.2 3.2 0 0 0 12 15.2Z"
            stroke="currentColor"
            stroke-width="1.8"
          />
          <path
            d="M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 1 1 16.96 19.71L16.9 19.65A1.65 1.65 0 0 0 15.08 19.32A1.65 1.65 0 0 0 14.08 20.83V21A2 2 0 1 1 10.08 21V20.91A1.65 1.65 0 0 0 9 19.4A1.65 1.65 0 0 0 7.18 19.73L7.12 19.79A2 2 0 1 1 4.29 16.96L4.35 16.9A1.65 1.65 0 0 0 4.68 15.08A1.65 1.65 0 0 0 3.17 14.08H3A2 2 0 1 1 3 10.08H3.09A1.65 1.65 0 0 0 4.6 9A1.65 1.65 0 0 0 4.27 7.18L4.21 7.12A2 2 0 1 1 7.04 4.29L7.1 4.35A1.65 1.65 0 0 0 8.92 4.68H9A1.65 1.65 0 0 0 10 3.17V3A2 2 0 1 1 14 3V3.09A1.65 1.65 0 0 0 15 4.6A1.65 1.65 0 0 0 16.82 4.27L16.88 4.21A2 2 0 1 1 19.71 7.04L19.65 7.1A1.65 1.65 0 0 0 19.32 8.92V9A1.65 1.65 0 0 0 20.83 10H21A2 2 0 1 1 21 14H20.91A1.65 1.65 0 0 0 19.4 15Z"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </CommonHeaderActionButton>
      <CommonSelect
        v-if="!isCompactSummaryPage"
        class="salary-year-select"
        label=""
        :model-value="selectedYear"
        :options="yearOptions"
        @update:model-value="changeYear"
      />
    </PageHeader>
    <p v-if="pageError" class="salary-error-text">{{ pageError }}</p>
    <div v-else-if="isLoading" class="salary-loading-wrap">
      <CommonLoading text="账户数据加载中..." />
    </div>
    <template v-else-if="pageData">
      <el-card class="pc-record-card salary-summary-card" shadow="never">
        <div class="salary-summary-head">
          <p v-if="!isCompactSummaryPage" class="salary-summary-eyebrow">
            {{ pageData.title }}
          </p>
          <div class="salary-summary-main">
            <div class="salary-summary-main-top">
              <strong>
                {{ formatSalaryCurrency(pageData.currentBalance) }}
              </strong>
              <CommonSelect
                v-if="isCompactSummaryPage"
                class="salary-year-select salary-summary-year-select"
                label=""
                :model-value="selectedYear"
                :options="yearOptions"
                @update:model-value="changeYear"
              />
              <span v-if="!isCompactSummaryPage" class="salary-pill">
                {{ pageData.year }} 年
              </span>
            </div>
            <div v-if="!isCompactSummaryPage" class="salary-summary-sub">
              <span>{{ pageData.subtitle }}</span>
              <span class="highlight">{{ pageData.badgeText }}</span>
            </div>
          </div>
        </div>
        <div class="pc-grid salary-metric-grid">
          <el-card
            v-for="metric in visibleMetrics"
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
        <div class="salary-card-head"><strong>账户明细</strong></div>
        <div class="pc-grid salary-list">
          <el-card
            v-for="item in pageData.details"
            :key="item.label"
            class="pc-record-card salary-row"
            shadow="never"
          >
            <div>
              <p class="salary-row-label">{{ item.label }}</p>
              <p class="salary-row-desc">{{ item.description }}</p>
            </div>
            <div class="salary-row-value">
              <strong>{{ formatSalaryCurrency(item.value) }}</strong>
            </div>
          </el-card>
        </div>
      </el-card>
      <el-card class="pc-record-card salary-card" shadow="never">
        <div class="salary-card-head">
          <strong>账户记录</strong>
          <span class="salary-pill">近 12 条</span>
        </div>
        <DesktopTable :rows="pageData.records">
          <el-table-column prop="monthLabel" label="月份" min-width="140" />
          <el-table-column
            prop="amountLabel"
            label="记录类型"
            min-width="180"
          />
          <!-- @vue-generic {SalaryAccountRecordItem} -->
          <el-table-column label="金额" min-width="160" align="right">
            <template #default="{ row }: { row: SalaryAccountRecordItem }">
              {{ formatSalaryCurrency(row.amountValue) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {SalaryAccountRecordItem} -->
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }: { row: SalaryAccountRecordItem }">
              <el-button
                link
                type="primary"
                :disabled="!row.editable || isSaving"
                @click="handleRecordClick(row)"
              >
                编辑
              </el-button>
            </template>
          </el-table-column>
        </DesktopTable>
      </el-card>
      <el-card
        v-if="pageData.forecast"
        class="pc-record-card salary-card"
        shadow="never"
      >
        <div class="salary-card-head">
          <strong>明年预测</strong>
          <span class="salary-pill">
            {{ pageData.forecast.forecastYear }} 年
          </span>
        </div>
        <el-card class="pc-record-card salary-forecast-panel" shadow="never">
          <div class="salary-row">
            <div><p class="salary-row-label">预测缴费基数</p></div>
            <div class="salary-row-value">
              <strong>
                {{
                  formatSalaryCurrency(pageData.forecast.predictedMonthlyBase)
                }}
              </strong>
            </div>
          </div>
          <div class="pc-grid salary-forecast-grid">
            <div class="salary-forecast-item">
              <span>个人缴存</span>
              <strong>
                {{ formatSalaryCurrency(pageData.forecast.predictedPersonal) }}
              </strong>
            </div>
            <div class="salary-forecast-item">
              <span>单位缴存</span>
              <strong>
                {{ formatSalaryCurrency(pageData.forecast.predictedCompany) }}
              </strong>
            </div>
          </div>
          <div
            v-if="
              pageData.accountType === 'housing_fund' &&
              pageData.forecast.predictedInterest != null
            "
            class="salary-row salary-forecast-interest"
          >
            <div>
              <p class="salary-row-label">预测利息</p>
              <p class="salary-row-desc">
                {{
                  formatInterestSettlementDate(
                    pageData.forecast.interestSettlementDate,
                  )
                }}结息 · 年利率
                {{ formatSalaryPercent(pageData.forecast.interestAnnualRate) }}
              </p>
            </div>
            <div class="salary-row-value">
              <strong>
                {{ formatSalaryCurrency(pageData.forecast.predictedInterest) }}
              </strong>
            </div>
          </div>
        </el-card>
      </el-card>
    </template>
    <CommonModal
      v-model="showBalanceModal"
      title="修改账户总额"
      :close-on-overlay="!isSaving"
    >
      <div class="pc-grid salary-form-grid">
        <p class="salary-modal-note">
          保存后会按当前总额与目标总额的差额生成一条调账记录。
        </p>
        <CommonInput
          v-model="balanceForm.amount"
          label="账户总额"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="balanceForm.note"
          label="备注说明"
          placeholder="例如：手动校准账户总额"
        />
        <p v-if="formError" class="salary-error-text">{{ formError }}</p>
      </div>
      <template #footer>
        <div class="salary-modal-footer salary-balance-modal-footer">
          <CommonButton
            variant="secondary"
            :disabled="isSaving"
            @click="closeBalanceModal"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="isSaving" @click="submitBalance">
            {{ isSaving ? '保存中...' : '确认修改' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showRecordModal"
      title="新增 / 修改记录"
      :close-on-overlay="!isSaving"
    >
      <div class="pc-grid salary-form-grid">
        <p class="salary-modal-note">可补录历史入账，也可修改已有调账记录。</p>
        <CommonInput
          v-model="recordForm.amount"
          label="记录金额"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="recordForm.recordMonth"
          label="发生月份"
          input-type="month"
        />
        <CommonSelect
          v-model="recordForm.impactMode"
          label="影响方式"
          :options="impactModeOptions"
        />
        <CommonInput
          v-model="recordForm.note"
          label="备注说明"
          placeholder="例如：补录开户时结余"
        />
        <p v-if="formError" class="salary-error-text">{{ formError }}</p>
      </div>
      <template #footer>
        <div class="salary-modal-footer salary-record-modal-footer">
          <CommonButton
            v-if="canDeleteRecord"
            variant="secondary"
            class="salary-danger-action"
            :disabled="isSaving"
            @click="removeRecord"
          >
            删除记录
          </CommonButton>
          <CommonButton
            v-else
            variant="secondary"
            :disabled="isSaving"
            @click="closeRecordModal"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="isSaving" @click="submitRecord">
            {{ isSaving ? '保存中...' : '保存记录' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
