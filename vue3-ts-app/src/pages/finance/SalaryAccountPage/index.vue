<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  createSalaryAccountRecord,
  deleteSalaryAccountRecord,
  getSalaryAccountPage,
  saveSalaryInitialBalance,
  updateSalaryAccountRecord,
  type SalaryAccountPage,
  type SalaryAccountRecordItem,
} from '@/api/modules/finance'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getStoredCurrentUser } from '@/utils/current-user'
import {
  createRecentYearOptions,
  dateToMonthInput,
  formatSalaryCurrency,
  monthInputToDate,
  recordCanDelete,
  salaryAccountDisplayName,
} from '../salary-shared'

const route = useRoute()
const pageData = ref<SalaryAccountPage | null>(null)
const isLoading = ref(false)
const isSaving = ref(false)
const pageError = ref('')
const formError = ref('')
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showInitialModal = ref(false)
const showRecordModal = ref(false)
const editingRecord = ref<SalaryAccountRecordItem | null>(null)
const selectedYear = ref(String(new Date().getFullYear()))

const initialForm = reactive({
  amount: '',
  recordMonth: `${new Date().getFullYear()}-01`,
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

const routeAccountType = computed(() => String(route.params.accountType || 'social-security'))
const pageTitle = computed(() => salaryAccountDisplayName(routeAccountType.value))
const canDeleteRecord = computed(() => recordCanDelete(editingRecord.value))

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
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    pageData.value = await getSalaryAccountPage(currentUser.id, routeAccountType.value, Number(selectedYear.value))
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '工资账户加载失败'
    openFeedback(pageError.value, 'error')
  } finally {
    isLoading.value = false
  }
}

function changeYear(value: string) {
  selectedYear.value = value
  void loadPage()
}

function openInitialBalanceModal(record?: SalaryAccountRecordItem) {
  editingRecord.value = record ?? null
  initialForm.amount = record ? String(record.amountValue) : String(pageData.value?.initialBalance ?? 0)
  initialForm.recordMonth = record ? dateToMonthInput(record.monthKey) : `${selectedYear.value}-01`
  initialForm.note = record?.note ?? ''
  formError.value = ''
  showInitialModal.value = true
}

function openRecordModal(record?: SalaryAccountRecordItem) {
  editingRecord.value = record ?? null
  recordForm.amount = record ? String(record.amountValue) : ''
  recordForm.recordMonth = record ? dateToMonthInput(record.monthKey) : `${selectedYear.value}-01`
  recordForm.impactMode = '计入当前余额'
  recordForm.note = record?.note ?? ''
  formError.value = ''
  showRecordModal.value = true
}

function closeInitialModal() {
  if (isSaving.value) {
    return
  }
  showInitialModal.value = false
  editingRecord.value = null
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

async function submitInitialBalance() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    formError.value = '请先登录后保存初始值'
    return
  }

  const amount = Number(initialForm.amount)
  if (!Number.isFinite(amount) || amount < 0) {
    formError.value = '请输入正确的初始金额'
    return
  }
  if (!initialForm.recordMonth) {
    formError.value = '请选择生效月份'
    return
  }

  isSaving.value = true
  formError.value = ''
  try {
    pageData.value = await saveSalaryInitialBalance(routeAccountType.value, {
      userId: currentUser.id,
      amount,
      recordMonth: monthInputToDate(initialForm.recordMonth),
      note: initialForm.note.trim(),
    })
    showInitialModal.value = false
    openFeedback('初始值已保存', 'success')
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '初始值保存失败'
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
      ? await updateSalaryAccountRecord(routeAccountType.value, editingRecord.value.id, payload)
      : await createSalaryAccountRecord(routeAccountType.value, payload)
    showRecordModal.value = false
    openFeedback(editingRecord.value ? '记录已更新' : '记录已新增', 'success')
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '记录保存失败'
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
    pageData.value = await deleteSalaryAccountRecord(routeAccountType.value, editingRecord.value.id, currentUser.id)
    showRecordModal.value = false
    openFeedback('记录已删除', 'success')
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '记录删除失败'
  } finally {
    isSaving.value = false
  }
}

function handleRecordClick(record: SalaryAccountRecordItem) {
  if (!record.editable) {
    return
  }
  if (record.recordType === 'initial') {
    openInitialBalanceModal(record)
    return
  }
  openRecordModal(record)
}

function openFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}
</script>

<template>
  <section class="salary-page" :aria-label="pageTitle">
    <PageHeader :title="pageTitle" back-to="/finance/salary" :prefer-back-to="true">
      <CommonSelect
        class="salary-year-select"
        label=""
        :model-value="selectedYear"
        :options="yearOptions"
        @update:model-value="changeYear"
      />
    </PageHeader>

    <div v-if="isLoading" class="salary-loading-wrap">
      <CommonLoading text="账户数据加载中..." />
    </div>

    <p v-else-if="pageError" class="salary-error-text">{{ pageError }}</p>

    <template v-else-if="pageData">
      <section class="salary-summary-card">
        <div class="salary-summary-head">
          <p class="salary-summary-eyebrow">{{ pageData.title }}</p>
          <div class="salary-summary-main">
            <div class="salary-summary-main-top">
              <strong>{{ formatSalaryCurrency(pageData.currentBalance) }}</strong>
              <span class="salary-pill">{{ pageData.year }} 年</span>
            </div>
            <div class="salary-summary-sub">
              <span>{{ pageData.subtitle }}</span>
              <span class="highlight">{{ pageData.badgeText }}</span>
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
          <strong>账户明细</strong>
        </div>
        <div class="salary-list">
          <article v-for="item in pageData.details" :key="item.label" class="salary-row">
            <div>
              <p class="salary-row-label">{{ item.label }}</p>
              <p class="salary-row-desc">{{ item.description }}</p>
            </div>
            <div class="salary-row-value">
              <strong>{{ formatSalaryCurrency(item.value) }}</strong>
            </div>
          </article>
        </div>
      </section>

      <section class="salary-card">
        <div class="salary-card-head">
          <strong>账户维护</strong>
        </div>
        <div class="salary-maintain-panel">
          <div class="salary-row">
            <div>
              <p class="salary-row-label">已设初始值</p>
              <p class="salary-row-desc">首次录入后会参与余额累计</p>
            </div>
            <div class="salary-row-value">
              <strong>{{ formatSalaryCurrency(pageData.initialBalance) }}</strong>
            </div>
          </div>
        </div>
        <div class="salary-action-grid">
          <CommonButton variant="secondary" @click="openRecordModal()">新增 / 修改记录</CommonButton>
          <CommonButton @click="openInitialBalanceModal()">设置初始值</CommonButton>
        </div>
      </section>

      <section class="salary-card">
        <div class="salary-card-head">
          <strong>账户记录</strong>
          <span class="salary-pill">近 12 条</span>
        </div>
        <div class="salary-record-list">
          <article
            v-for="record in pageData.records"
            :key="record.id"
            class="salary-record-card"
            @click="handleRecordClick(record)"
          >
            <div class="salary-record-top">
              <div>
                <p class="salary-record-title">{{ record.monthLabel }}</p>
                <p class="salary-linked-note">{{ record.amountLabel }}</p>
              </div>
              <div class="salary-record-amount">
                <strong>{{ formatSalaryCurrency(record.amountValue) }}</strong>
                <span v-if="record.editable">点击修改</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section v-if="pageData.forecast" class="salary-card">
        <div class="salary-card-head">
          <strong>明年预测</strong>
          <span class="salary-pill">{{ pageData.forecast.forecastYear }} 年</span>
        </div>
        <div class="salary-forecast-panel">
          <div class="salary-row">
            <div>
              <p class="salary-row-label">预测缴费基数</p>
            </div>
            <div class="salary-row-value">
              <strong>{{ formatSalaryCurrency(pageData.forecast.predictedMonthlyBase) }}</strong>
            </div>
          </div>
          <div class="salary-forecast-grid">
            <div class="salary-forecast-item">
              <span>个人缴存</span>
              <strong>{{ formatSalaryCurrency(pageData.forecast.predictedPersonal) }}</strong>
            </div>
            <div class="salary-forecast-item">
              <span>单位缴存</span>
              <strong>{{ formatSalaryCurrency(pageData.forecast.predictedCompany) }}</strong>
            </div>
          </div>
        </div>
      </section>
    </template>

    <CommonModal v-model="showInitialModal" title="设置初始值" :close-on-overlay="!isSaving">
      <div class="salary-form-grid">
        <p class="salary-modal-note">初始值会作为第一笔手动记录写入，并参与后续自动累计。</p>
        <CommonInput v-model="initialForm.amount" label="初始金额" input-type="number" input-mode="decimal" />
        <CommonInput v-model="initialForm.recordMonth" label="生效月份" input-type="month" />
        <CommonInput v-model="initialForm.note" label="备注说明" placeholder="例如：开户时历史余额" />
        <p v-if="formError" class="salary-error-text">{{ formError }}</p>
      </div>
      <template #footer>
        <div class="salary-modal-footer">
          <CommonButton variant="secondary" :disabled="isSaving" @click="closeInitialModal">取消</CommonButton>
          <CommonButton :disabled="isSaving" @click="submitInitialBalance">{{ isSaving ? '保存中...' : '保存初始值' }}</CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal v-model="showRecordModal" title="新增 / 修改记录" :close-on-overlay="!isSaving">
      <div class="salary-form-grid">
        <p class="salary-modal-note">可补录历史入账，也可修改已有调账记录。</p>
        <CommonInput v-model="recordForm.amount" label="记录金额" input-type="number" input-mode="decimal" />
        <CommonInput v-model="recordForm.recordMonth" label="发生月份" input-type="month" />
        <CommonSelect v-model="recordForm.impactMode" label="影响方式" :options="impactModeOptions" />
        <CommonInput v-model="recordForm.note" label="备注说明" placeholder="例如：补录开户时结余" />
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
