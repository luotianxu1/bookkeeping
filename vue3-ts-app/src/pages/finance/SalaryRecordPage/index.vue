<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createSalaryMonthRecord,
  deleteSalaryMonthRecord,
  getSalaryMonthPage,
  updateSalaryMonthRecord,
  type SalaryMonthPage,
  type SalaryMonthRecordItem,
} from '@/api/modules/finance'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getStoredCurrentUser } from '@/utils/current-user'
import { createRecentYearOptions, dateToMonthInput, formatSalaryCurrency, monthInputToDate } from '../salary-shared'

const pageData = ref<SalaryMonthPage | null>(null)
const isLoading = ref(false)
const isSaving = ref(false)
const pageError = ref('')
const formError = ref('')
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showRecordModal = ref(false)
const editingRecord = ref<SalaryMonthRecordItem | null>(null)
const selectedYear = ref(String(new Date().getFullYear()))

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
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看工资明细'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    pageData.value = await getSalaryMonthPage(currentUser.id, Number(selectedYear.value))
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '工资明细加载失败'
    openFeedback(pageError.value, 'error')
  } finally {
    isLoading.value = false
  }
}

function changeYear(value: string) {
  selectedYear.value = value
  void loadPage()
}

function openRecordModal(record?: SalaryMonthRecordItem) {
  editingRecord.value = record ?? null
  recordForm.grossSalary = record ? String(record.grossSalary) : ''
  recordForm.salaryMonth = record ? dateToMonthInput(record.monthKey) : `${selectedYear.value}-01`
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
    openFeedback(editingRecord.value ? '工资记录已更新' : '工资记录已新增', 'success')
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '工资记录保存失败'
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
    pageData.value = await deleteSalaryMonthRecord(editingRecord.value.id, currentUser.id)
    showRecordModal.value = false
    openFeedback('工资记录已删除', 'success')
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '工资记录删除失败'
  } finally {
    isSaving.value = false
  }
}

function openFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}
</script>

<template>
  <section class="salary-page" aria-label="工资明细">
    <PageHeader title="工资明细" back-to="/finance/salary" :prefer-back-to="true">
      <CommonSelect
        class="salary-year-select"
        label=""
        :model-value="selectedYear"
        :options="yearOptions"
        @update:model-value="changeYear"
      />
    </PageHeader>

    <div v-if="isLoading" class="salary-loading-wrap">
      <CommonLoading text="工资明细加载中..." />
    </div>

    <p v-else-if="pageError" class="salary-error-text">{{ pageError }}</p>

    <template v-else-if="pageData">
      <section class="salary-summary-card">
        <div class="salary-summary-head">
          <p class="salary-summary-eyebrow">{{ pageData.year }} 年工资明细</p>
          <div class="salary-summary-main">
            <div class="salary-summary-main-top">
              <strong>{{ formatSalaryCurrency(pageData.recordedGrossIncome) }}</strong>
              <span class="salary-pill">已补录 {{ pageData.recordedMonths }} 个月</span>
            </div>
            <div class="salary-summary-sub">
              <span>默认月薪 {{ formatSalaryCurrency(pageData.defaultMonthlyGrossSalary) }}</span>
              <span class="highlight">全年预计 {{ formatSalaryCurrency(pageData.estimatedAnnualGrossIncome) }}</span>
            </div>
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
          <strong>记录维护</strong>
        </div>
        <div class="salary-maintain-panel">
          <div class="salary-row">
            <div>
              <p class="salary-row-label">月度工资记录</p>
              <p class="salary-row-desc">已补录月份优先按实际税前工资参与工资测算。</p>
            </div>
            <div class="salary-row-value">
              <strong>{{ pageData.records.length }}</strong>
            </div>
          </div>
        </div>
        <div class="salary-action-grid">
          <CommonButton @click="openRecordModal()">新增工资记录</CommonButton>
        </div>
      </section>

      <section class="salary-card">
        <div class="salary-card-head">
          <strong>工资记录</strong>
          <span class="salary-pill">按月份维护</span>
        </div>
        <div class="salary-record-list">
          <article
            v-for="record in pageData.records"
            :key="record.id"
            class="salary-record-card"
            @click="openRecordModal(record)"
          >
            <div class="salary-record-top">
              <div>
                <p class="salary-record-title">{{ record.monthLabel }}</p>
                <p class="salary-linked-note">{{ record.note || '无备注' }}</p>
              </div>
              <div class="salary-record-amount">
                <strong>{{ formatSalaryCurrency(record.grossSalary) }}</strong>
                <span>点击修改</span>
              </div>
            </div>
          </article>
        </div>
      </section>
    </template>

    <CommonModal v-model="showRecordModal" title="新增 / 修改工资" :close-on-overlay="!isSaving">
      <div class="salary-form-grid">
        <p class="salary-modal-note">可补录历史工资，也可修改已录入月份的税前工资。</p>
        <CommonInput v-model="recordForm.grossSalary" label="税前工资" input-type="number" input-mode="decimal" />
        <CommonInput v-model="recordForm.salaryMonth" label="工资月份" input-type="month" />
        <CommonInput v-model="recordForm.note" label="备注说明" placeholder="例如：补录实际发薪" />
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
          <CommonButton :disabled="isSaving" @click="submitRecord">{{ isSaving ? '保存中...' : '保存记录' }}</CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonFeedback v-model="feedbackVisible" :message="feedbackMessage" :type="feedbackType" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
