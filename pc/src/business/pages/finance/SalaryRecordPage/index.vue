<script setup lang="ts">
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'

import MetricStrip from '@/business/components/MetricStrip.vue'

import { computed, onMounted, reactive, ref } from 'vue'
import {
  createSalaryMonthRecord,
  deleteSalaryMonthRecord,
  getSalaryMonthPage,
  updateSalaryMonthRecord,
  type SalaryMonthPage,
  type SalaryMonthRecordItem,
} from '@/business/api/modules/finance'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import {
  createRecentYearOptions,
  dateToMonthInput,
  formatSalaryCurrency,
  monthInputToDate,
} from '../salary-shared'
import { getErrorMessage } from '@/business/api/request'

const pageData = ref<SalaryMonthPage | null>(null)
const isLoading = ref(true)
const isSaving = ref(false)
const pageError = ref('')
const formError = ref('')
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showRecordModal = ref(false)
const editingRecord = ref<SalaryMonthRecordItem | null>(null)
const selectedYear = ref(String(new Date().getFullYear()))
let loadVersion = 0

const recordForm = reactive({
  grossSalary: '',
  salaryMonth: `${new Date().getFullYear()}-01`,
  note: '',
})

const yearOptions = createRecentYearOptions(5)
const canDeleteRecord = computed(() => Boolean(editingRecord.value))

onMounted(() => {
  void loadPage()
})

async function loadPage() {
  const version = ++loadVersion
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看工资明细'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const result = await getSalaryMonthPage(
      currentUser.id,
      Number(selectedYear.value),
    )
    if (version !== loadVersion) return
    pageData.value = result
  } catch (error) {
    if (version !== loadVersion) return
    pageError.value = getErrorMessage(error, '工资明细加载失败')
    openFeedback(pageError.value, 'error')
  } finally {
    if (version === loadVersion) isLoading.value = false
  }
}

function changeYear(value: string) {
  selectedYear.value = value
  void loadPage()
}

function openRecordModal(record?: SalaryMonthRecordItem) {
  editingRecord.value = record ?? null
  recordForm.grossSalary = record ? String(record.grossSalary) : ''
  recordForm.salaryMonth = record
    ? dateToMonthInput(record.monthKey)
    : `${selectedYear.value}-01`
  recordForm.note = record?.note ?? ''
  formError.value = ''
  showRecordModal.value = true
}

function closeRecordModal() {
  if (isSaving.value) {
    return
  }
  showRecordModal.value = false
  editingRecord.value = null
  formError.value = ''
}

async function submitRecord() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    formError.value = '请先登录后保存工资记录'
    return
  }

  const grossSalary = Number(recordForm.grossSalary)
  if (!Number.isFinite(grossSalary) || grossSalary < 0) {
    formError.value = '请输入正确的税前工资'
    return
  }
  if (!recordForm.salaryMonth) {
    formError.value = '请选择工资月份'
    return
  }

  isSaving.value = true
  formError.value = ''
  try {
    const payload = {
      userId: currentUser.id,
      salaryMonth: monthInputToDate(recordForm.salaryMonth),
      grossSalary,
      note: recordForm.note.trim(),
    }
    pageData.value = editingRecord.value
      ? await updateSalaryMonthRecord(editingRecord.value.id, payload)
      : await createSalaryMonthRecord(payload)
    showRecordModal.value = false
    openFeedback(
      editingRecord.value ? '工资记录已更新' : '工资记录已新增',
      'success',
    )
  } catch (error) {
    formError.value = getErrorMessage(error, '工资记录保存失败')
  } finally {
    isSaving.value = false
  }
}

async function removeRecord() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || !editingRecord.value) {
    return
  }

  isSaving.value = true
  formError.value = ''
  try {
    pageData.value = await deleteSalaryMonthRecord(
      editingRecord.value.id,
      currentUser.id,
    )
    showRecordModal.value = false
    openFeedback('工资记录已删除', 'success')
  } catch (error) {
    formError.value = getErrorMessage(error, '工资记录删除失败')
  } finally {
    isSaving.value = false
  }
}

function openFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}

type DetailTableRow = NonNullable<typeof pageData.value>['records'][number]
</script>
<template>
  <section class="desktop-workspace stagger-in">
    <PageHeader title="工资明细">
      <template #right>
        <CommonSelect
          class="salary-year-select"
          label="年份"
          :model-value="selectedYear"
          :options="yearOptions"
          @update:model-value="changeYear"
        />
        <CommonButton @click="openRecordModal()">新增工资记录</CommonButton>
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
        :items="
          pageData.metrics.map((metric) => ({
            label: metric.label,
            value: formatSalaryCurrency(metric.value),
          }))
        "
      />
      <DesktopRecordList
        :rows="pageData.records"
        :loading="isLoading"
        title="工资记录"
        search-placeholder="搜索月份或备注"
        searchable
        :search-keys="['monthLabel', 'note']"
        row-key="id"
        empty-title="还没有工资记录"
        empty-hint="点击右上角新增第一个月工资"
      >
        <template #default="{ row }: { row: DetailTableRow }">
          <article class="record-card salary-slip-card">
            <div class="record-card-head">
              <div class="record-card-title">
                <span>{{ row.monthLabel }}</span>
              </div>
              <div class="record-card-aside">
                <el-tag size="small" effect="plain">工资条</el-tag>
              </div>
            </div>
            <div class="salary-slip-amount">
              <span class="salary-slip-amount-label">税前工资</span>
              <AmountText
                class="salary-slip-amount-value"
                :value="formatSalaryCurrency(row.grossSalary)"
                font-size="26px"
              />
            </div>
            <p v-if="row.note" class="record-card-note">{{ row.note }}</p>
            <div class="record-card-foot">
              <el-button link type="primary" @click="openRecordModal(row)">
                修改
              </el-button>
            </div>
          </article>
        </template>
      </DesktopRecordList>
    </template>
    <CommonModal
      v-model="showRecordModal"
      title="新增 / 修改工资"
      :close-on-overlay="!isSaving"
    >
      <div class="pc-grid salary-form-grid">
        <p class="salary-modal-note">
          可补录历史工资，也可修改已录入月份的税前工资。
        </p>
        <CommonInput
          v-model="recordForm.grossSalary"
          label="税前工资"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="recordForm.salaryMonth"
          label="工资月份"
          input-type="month"
        />
        <CommonInput
          v-model="recordForm.note"
          label="备注说明"
          placeholder="例如：补录实际发薪"
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
<style scoped>
/* 工资条卡片：月份标题 + 税前工资大数字 + 备注明细 */
.salary-slip-amount {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.salary-slip-amount-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.salary-slip-amount-value {
  font-weight: 700;
  letter-spacing: -0.02em;
}
</style>
