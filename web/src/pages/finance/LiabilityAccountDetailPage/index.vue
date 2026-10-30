<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  createLiabilityRecord,
  deleteAccount,
  deleteLiabilityRecord,
  getAccount,
  getLiabilityAccountSummary,
  getLiabilityRecords,
  prepayLiabilityAccount,
  repayLiabilityRecord,
  updateLiabilityRecord,
  type Account,
  type LiabilityAccountSummary,
  type LiabilityRepaymentStatus,
  type LiabilityRecord,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

type InstallmentPreview = {
  amount: number
  totalPeriods: number
  currentPeriod: number
  occurredAt: Date
  error: string
}

const route = useRoute()
const router = useRouter()

const account = ref<Account | null>(null)
const summary = ref<LiabilityAccountSummary>({
  totalAmount: 0,
  accountCount: 0,
  recordCount: 0,
})
const records = ref<LiabilityRecord[]>([])
const isLoading = ref(false)
const pageError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showRecordModal = ref(false)
const showDeleteModal = ref(false)
const showAccountDeleteModal = ref(false)
const showRepayModal = ref(false)
const showPrepayModal = ref(false)
const isSavingRecord = ref(false)
const isDeleting = ref(false)
const isDeletingAccount = ref(false)
const isRepaying = ref(false)
const isPrepaying = ref(false)
const recordFormError = ref('')
const deleteError = ref('')
const accountDeleteError = ref('')
const repayError = ref('')
const prepayError = ref('')
const editingRecord = ref<LiabilityRecord | null>(null)
const deletingRecord = ref<LiabilityRecord | null>(null)
const repayingRecord = ref<LiabilityRecord | null>(null)
const recordAmount = ref('')
const recordInstallmentTotalPeriods = ref('')
const recordInstallmentCurrentPeriod = ref('')
const recordOccurredAt = ref('')
const recordRemark = ref('')
const repayPaidAt = ref('')
const prepayPaidAt = ref('')
const prepayRemark = ref('')
let requestVersion = 0

const accountId = computed(() => Number(route.params.accountId))
const detailName = computed(() => account.value?.name || '负债账户')
const detailNote = computed(() => account.value?.remark?.trim() || '')
const summaryAmountText = computed(() => formatCurrency(summary.value.totalAmount))
const hasLoanPlan = computed(() =>
  account.value?.loanTotalAmount != null
  && account.value?.loanTotalPeriods != null
  && account.value?.loanRepaymentDay != null
  && Boolean(account.value?.loanStartDate),
)
const isSettled = computed(() => Boolean(account.value?.loanSettledAt) || Number(summary.value.totalAmount ?? 0) <= 0)
const paidRecords = computed(() => records.value.filter((record) => record.repaymentStatus === 'paid'))
const recordModalTitle = computed(() => editingRecord.value ? '修改月账单' : '新增月账单')
const interestAmount = computed(() => resolveInterestAmount(account.value))
const totalRepaymentAmount = computed(() =>
  Number(account.value?.loanTotalAmount ?? 0) + interestAmount.value,
)
const paidAmount = computed(() => Math.max(totalRepaymentAmount.value - Number(summary.value.totalAmount ?? 0), 0))
const monthlyPaymentAmount = computed(() => {
  const periods = Number(account.value?.loanTotalPeriods ?? 0)
  if (periods <= 0) {
    return 0
  }
  return roundCurrency(totalRepaymentAmount.value / periods)
})
const monthlyPaidPeriods = computed(() => {
  if (!hasLoanPlan.value) {
    return paidRecords.value.length
  }
  if (isSettled.value) {
    return Number(account.value?.loanTotalPeriods ?? 0)
  }
  const periods = new Set(
    paidRecords.value
      .filter((record) => record.repaymentType !== 'prepayment')
      .map((record) => Number(record.installmentCurrentPeriod ?? 0))
      .filter((period) => period > 0),
  )
  return periods.size
})
const remainingInstallmentPeriods = computed(() => {
  if (!hasLoanPlan.value) {
    return records.value.reduce((total, record) => {
      if (record.repaymentStatus === 'paid') {
        return total
      }
      if (!record.installmentTotalPeriods || !record.installmentCurrentPeriod) {
        return total
      }
      return total + Math.max(record.installmentTotalPeriods - record.installmentCurrentPeriod, 0)
    }, 0)
  }
  return Math.max(Number(account.value?.loanTotalPeriods ?? 0) - monthlyPaidPeriods.value, 0)
})
const nextInstallmentPreview = computed<InstallmentPreview | null>(() => {
  const currentAccount = account.value
  if (!currentAccount || !hasLoanPlan.value || isSettled.value) {
    return null
  }
  const totalPeriods = Number(currentAccount.loanTotalPeriods ?? 0)
  const nextPeriod = resolveNextInstallmentPeriod(records.value, totalPeriods)
  if (nextPeriod > totalPeriods) {
    return {
      amount: 0,
      totalPeriods,
      currentPeriod: nextPeriod,
      occurredAt: new Date(),
      error: '该贷款的月账单已全部生成',
    }
  }
  const scheduledDate = recordOccurredAt.value
    ? parseDateTimeLocal(recordOccurredAt.value)
    : buildScheduledDate(currentAccount, nextPeriod)
  if (!scheduledDate) {
    return {
      amount: 0,
      totalPeriods,
      currentPeriod: nextPeriod,
      occurredAt: new Date(),
      error: '无法计算下期账单时间',
    }
  }
  const customAmount = parsePositiveAmount(recordAmount.value)
  return {
    amount: customAmount > 0 ? customAmount : calculateInstallmentAmount(totalRepaymentAmount.value, totalPeriods, nextPeriod),
    totalPeriods,
    currentPeriod: nextPeriod,
    occurredAt: scheduledDate,
    error: '',
  }
})
const nextDueText = computed(() => {
  if (isSettled.value) {
    return '已结清'
  }
  if (!nextInstallmentPreview.value || nextInstallmentPreview.value.error) {
    return '待生成'
  }
  return formatDate(nextInstallmentPreview.value.occurredAt.toISOString())
})
const loanInterestRateText = computed(() => formatRate(account.value))

watch(accountId, () => {
  void loadDetail()
}, { immediate: true })

async function loadDetail() {
  const currentRequestVersion = ++requestVersion
  const currentUser = getStoredCurrentUser()

  account.value = null
  records.value = []
  pageError.value = ''

  if (!currentUser) {
    pageError.value = '请先登录后查看负债详情'
    return
  }

  if (!Number.isFinite(accountId.value) || accountId.value <= 0) {
    pageError.value = '负债账户不存在'
    return
  }

  isLoading.value = true

  try {
    const [accountDetail, summaryData, recordList] = await Promise.all([
      getAccount(accountId.value),
      getLiabilityAccountSummary(currentUser.id, accountId.value),
      getLiabilityRecords({ userId: currentUser.id, accountId: accountId.value }),
    ])

    if (currentRequestVersion !== requestVersion) {
      return
    }

    if (accountDetail.accountTypeCode !== 'liability') {
      pageError.value = '当前账户不是负债账户'
      return
    }

    account.value = accountDetail
    summary.value = summaryData
    records.value = recordList
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = error instanceof Error ? error.message : '负债详情加载失败'
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

function openAddRecordModal() {
  if (!account.value) {
    showFeedback('当前负债账户不存在', 'error')
    return
  }
  if (isSettled.value) {
    showFeedback('该负债账户已经结清，无需继续新增月账单', 'error')
    return
  }
  editingRecord.value = null
  resetRecordForm()
  if (nextInstallmentPreview.value && !nextInstallmentPreview.value.error) {
    recordOccurredAt.value = toDateTimeLocalValue(nextInstallmentPreview.value.occurredAt.toISOString())
    recordAmount.value = String(nextInstallmentPreview.value.amount)
  }
  showRecordModal.value = true
}

function openEditRecordModal(record: LiabilityRecord) {
  if (record.repaymentType === 'prepayment') {
    showFeedback('提前还款记录不支持修改', 'error')
    return
  }
  editingRecord.value = record
  recordAmount.value = String(Number(record.amount ?? 0))
  recordInstallmentTotalPeriods.value = record.installmentTotalPeriods ? String(record.installmentTotalPeriods) : ''
  recordInstallmentCurrentPeriod.value = record.installmentCurrentPeriod ? String(record.installmentCurrentPeriod) : ''
  recordOccurredAt.value = toDateTimeLocalValue(record.occurredAt)
  recordRemark.value = record.remark ?? ''
  recordFormError.value = ''
  showRecordModal.value = true
}

function closeRecordModal(force = false) {
  if (isSavingRecord.value && !force) {
    return
  }
  showRecordModal.value = false
  editingRecord.value = null
  resetRecordForm()
}

function resetRecordForm() {
  recordAmount.value = ''
  recordInstallmentTotalPeriods.value = ''
  recordInstallmentCurrentPeriod.value = ''
  recordOccurredAt.value = ''
  recordRemark.value = ''
  recordFormError.value = ''
}

function openDeleteModal(record: LiabilityRecord) {
  if (record.repaymentType === 'prepayment') {
    showFeedback('提前还款记录不支持删除', 'error')
    return
  }
  deletingRecord.value = record
  deleteError.value = ''
  showDeleteModal.value = true
}

function closeDeleteModal(force = false) {
  if (isDeleting.value && !force) {
    return
  }
  showDeleteModal.value = false
  deletingRecord.value = null
  deleteError.value = ''
}

function openRepayModal(record: LiabilityRecord) {
  if (record.repaymentStatus === 'paid' || record.repaymentType === 'prepayment') {
    return
  }
  repayingRecord.value = record
  repayPaidAt.value = toDateTimeLocalValue(new Date().toISOString())
  repayError.value = ''
  showRepayModal.value = true
}

function closeRepayModal(force = false) {
  if (isRepaying.value && !force) {
    return
  }
  showRepayModal.value = false
  repayingRecord.value = null
  repayPaidAt.value = ''
  repayError.value = ''
}

function openPrepayModal() {
  if (!account.value) {
    return
  }
  if (isSettled.value) {
    showFeedback('该负债账户已经结清', 'error')
    return
  }
  prepayPaidAt.value = toDateTimeLocalValue(new Date().toISOString())
  prepayRemark.value = ''
  prepayError.value = ''
  showPrepayModal.value = true
}

function closePrepayModal(force = false) {
  if (isPrepaying.value && !force) {
    return
  }
  showPrepayModal.value = false
  prepayPaidAt.value = ''
  prepayRemark.value = ''
  prepayError.value = ''
}

function openAccountDeleteModal() {
  if (!account.value || isDeletingAccount.value) {
    return
  }
  accountDeleteError.value = ''
  showAccountDeleteModal.value = true
}

function closeAccountDeleteModal(force = false) {
  if (isDeletingAccount.value && !force) {
    return
  }
  showAccountDeleteModal.value = false
  accountDeleteError.value = ''
}

async function saveRecord() {
  const currentUser = getStoredCurrentUser()
  const normalizedAmount = parsePositiveAmount(recordAmount.value)
  const normalizedInstallmentTotalPeriods = normalizePositiveInteger(recordInstallmentTotalPeriods.value)
  const normalizedInstallmentCurrentPeriod = normalizePositiveInteger(recordInstallmentCurrentPeriod.value)
  const isEditing = Boolean(editingRecord.value)

  if (!currentUser || !account.value) {
    recordFormError.value = '负债账户信息不完整'
    return
  }

  if (hasLoanPlan.value) {
    if (editingRecord.value) {
      if (!Number.isFinite(normalizedAmount) || normalizedAmount <= 0) {
        recordFormError.value = '请输入有效的本期金额'
        return
      }
      if (!recordOccurredAt.value) {
        recordFormError.value = '请选择账单时间'
        return
      }
    } else {
      if (!nextInstallmentPreview.value) {
        recordFormError.value = '当前负债账户无法生成下期账单'
        return
      }
      if (nextInstallmentPreview.value.error) {
        recordFormError.value = nextInstallmentPreview.value.error
        return
      }
    }
  } else {
    if (!Number.isFinite(normalizedAmount) || normalizedAmount <= 0) {
      recordFormError.value = '请输入有效的本期待还金额'
      return
    }

    if (recordInstallmentTotalPeriods.value.trim() && Number.isNaN(normalizedInstallmentTotalPeriods)) {
      recordFormError.value = '请输入有效的分期总期数'
      return
    }

    if (recordInstallmentCurrentPeriod.value.trim() && Number.isNaN(normalizedInstallmentCurrentPeriod)) {
      recordFormError.value = '请输入有效的当前期数'
      return
    }

    if (normalizedInstallmentCurrentPeriod !== null && normalizedInstallmentTotalPeriods === null) {
      recordFormError.value = '填写当前期数时，请同时填写分期总期数'
      return
    }

    if (normalizedInstallmentTotalPeriods !== null && normalizedInstallmentTotalPeriods < 2) {
      recordFormError.value = '分期总期数至少为2'
      return
    }

    if (
      normalizedInstallmentTotalPeriods !== null
      && normalizedInstallmentCurrentPeriod !== null
      && normalizedInstallmentCurrentPeriod > normalizedInstallmentTotalPeriods
    ) {
      recordFormError.value = '当前期数不能大于分期总期数'
      return
    }
  }

  isSavingRecord.value = true
  recordFormError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      accountId: account.value.id,
      amount: hasLoanPlan.value
        ? (Number.isFinite(normalizedAmount) && normalizedAmount > 0
          ? normalizedAmount
          : nextInstallmentPreview.value?.amount)
        : normalizedAmount,
      installmentTotalPeriods: hasLoanPlan.value
        ? editingRecord.value?.installmentTotalPeriods ?? nextInstallmentPreview.value?.totalPeriods ?? null
        : normalizedInstallmentTotalPeriods,
      installmentCurrentPeriod: hasLoanPlan.value
        ? editingRecord.value?.installmentCurrentPeriod ?? nextInstallmentPreview.value?.currentPeriod ?? null
        : normalizedInstallmentCurrentPeriod,
      currencyCode: account.value.currencyCode || 'CNY',
      remark: recordRemark.value.trim() || null,
      occurredAt: recordOccurredAt.value ? toIsoLocalString(recordOccurredAt.value) : undefined,
    }

    if (editingRecord.value) {
      await updateLiabilityRecord(editingRecord.value.id, payload)
    } else {
      await createLiabilityRecord(payload)
    }

    closeRecordModal(true)
    showFeedback(isEditing ? '月账单已更新' : '月账单已新增', 'success')
    await loadDetail()
  } catch (error) {
    const message = error instanceof Error ? error.message : '月账单保存失败'
    recordFormError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingRecord.value = false
  }
}

async function confirmDelete() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || !deletingRecord.value) {
    return
  }

  isDeleting.value = true
  deleteError.value = ''

  try {
    await deleteLiabilityRecord(deletingRecord.value.id, currentUser.id)
    closeDeleteModal(true)
    showFeedback('月账单已删除', 'success')
    await loadDetail()
  } catch (error) {
    const message = error instanceof Error ? error.message : '删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

async function confirmRepay() {
  const currentUser = getStoredCurrentUser()
  const record = repayingRecord.value
  if (!currentUser || !record) {
    repayError.value = '待还账单不存在'
    return
  }

  isRepaying.value = true
  repayError.value = ''

  try {
    await repayLiabilityRecord(record.id, {
      userId: currentUser.id,
      paidAt: repayPaidAt.value ? toIsoLocalString(repayPaidAt.value) : undefined,
    })
    closeRepayModal(true)
    showFeedback('该期月账单已还款', 'success')
    await loadDetail()
  } catch (error) {
    const message = error instanceof Error ? error.message : '还款失败'
    repayError.value = message
    showFeedback(message, 'error')
  } finally {
    isRepaying.value = false
  }
}

async function confirmPrepay() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || !account.value) {
    prepayError.value = '负债账户不存在'
    return
  }

  isPrepaying.value = true
  prepayError.value = ''

  try {
    await prepayLiabilityAccount(account.value.id, {
      userId: currentUser.id,
      paidAt: prepayPaidAt.value ? toIsoLocalString(prepayPaidAt.value) : undefined,
      remark: prepayRemark.value.trim() || null,
    })
    closePrepayModal(true)
    showFeedback('该负债账户已提前结清', 'success')
    await loadDetail()
  } catch (error) {
    const message = error instanceof Error ? error.message : '提前还款失败'
    prepayError.value = message
    showFeedback(message, 'error')
  } finally {
    isPrepaying.value = false
  }
}

async function confirmDeleteAccount() {
  const currentUser = getStoredCurrentUser()
  const currentAccount = account.value
  if (!currentUser || !currentAccount) {
    accountDeleteError.value = '负债账户不存在'
    return
  }

  isDeletingAccount.value = true
  accountDeleteError.value = ''

  try {
    await deleteAccount(currentAccount.id)
    closeAccountDeleteModal(true)
    showFeedback('负债账户已删除', 'success')
    await router.push('/finance/accounts/liability')
  } catch (error) {
    const message = error instanceof Error ? error.message : '删除失败'
    accountDeleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeletingAccount.value = false
  }
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(value))
}

function formatCurrency(value: number) {
  return `¥${formatNumber(value)}`
}

function roundCurrency(value: number) {
  return Math.round(value * 100) / 100
}

function resolveInterestAmount(currentAccount: Account | null) {
  if (!currentAccount) {
    return 0
  }
  const principal = Number(currentAccount.loanTotalAmount ?? 0)
  const rate = currentAccount.loanInterestRate
  if (rate != null && Number.isFinite(Number(rate))) {
    return roundCurrency((principal * Number(rate)) / 100)
  }
  return Number(currentAccount.loanInterestAmount ?? 0)
}

function formatRate(currentAccount: Account | null) {
  if (!currentAccount) {
    return '--'
  }
  if (currentAccount.loanInterestRate != null && Number.isFinite(Number(currentAccount.loanInterestRate))) {
    return `${Number(currentAccount.loanInterestRate).toFixed(2)}%`
  }
  const principal = Number(currentAccount.loanTotalAmount ?? 0)
  const interestAmount = Number(currentAccount.loanInterestAmount ?? 0)
  if (principal > 0 && interestAmount > 0) {
    return `${((interestAmount / principal) * 100).toFixed(2)}%`
  }
  return '未设置'
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

function formatRecordAmount(record: LiabilityRecord) {
  return formatCurrency(Number(record.amount ?? 0))
}

function formatInstallmentLabel(record: LiabilityRecord) {
  if (record.repaymentType === 'prepayment') {
    return '提前还款结清'
  }
  if (!record.installmentTotalPeriods || !record.installmentCurrentPeriod) {
    return '单笔待还'
  }
  return `第 ${record.installmentCurrentPeriod}/${record.installmentTotalPeriods} 期`
}

function formatRemainingLabel(record: LiabilityRecord) {
  if (record.repaymentType === 'prepayment') {
    return '结清剩余贷款'
  }
  if (!record.installmentTotalPeriods || !record.installmentCurrentPeriod) {
    return '未设置分期'
  }
  return `剩余 ${Math.max(record.installmentTotalPeriods - record.installmentCurrentPeriod, 0)} 期`
}

function formatRepaymentStatus(status: LiabilityRepaymentStatus, repaymentType?: string | null) {
  if (repaymentType === 'prepayment') {
    return '提前结清'
  }
  return status === 'paid' ? '已还' : '待还'
}

function normalizePositiveInteger(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const parsed = Number(trimmed)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return Number.NaN
  }
  return parsed
}

function parsePositiveAmount(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return Number.NaN
  }
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? parsed : Number.NaN
}

function parseDateTimeLocal(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function resolveNextInstallmentPeriod(recordList: LiabilityRecord[], totalPeriods: number) {
  const maxPeriod = recordList.reduce((max, record) => Math.max(max, Number(record.installmentCurrentPeriod ?? 0)), 0)
  return Math.min(maxPeriod + 1, totalPeriods + 1)
}

function buildScheduledDate(currentAccount: Account, period: number) {
  if (!currentAccount.loanStartDate) {
    return null
  }
  const baseDate = new Date(`${currentAccount.loanStartDate}T09:00:00`)
  if (Number.isNaN(baseDate.getTime())) {
    return null
  }
  const scheduleMonth = new Date(baseDate)
  scheduleMonth.setMonth(scheduleMonth.getMonth() + Math.max(period - 1, 0))
  const repaymentDay = Number(currentAccount.loanRepaymentDay ?? baseDate.getDate())
  const monthEnd = new Date(scheduleMonth.getFullYear(), scheduleMonth.getMonth() + 1, 0).getDate()
  scheduleMonth.setDate(Math.min(repaymentDay, monthEnd))
  return scheduleMonth
}

function calculateInstallmentAmount(totalAmount: number, totalPeriods: number, currentPeriod: number) {
  const averageAmount = roundCurrency(totalAmount / totalPeriods)
  if (currentPeriod >= totalPeriods) {
    return roundCurrency(totalAmount - averageAmount * (totalPeriods - 1))
  }
  return averageAmount
}

function toDateTimeLocalValue(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function toIsoLocalString(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toISOString().slice(0, 19)
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}
</script>

<template>
  <section class="debt-account-detail-page" aria-label="负债详情">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="debt-detail-header">
      <PageHeader :title="`${detailName}账单`" back-to="/finance/accounts/liability" back-label="返回负债账户">
        <template #right>
          <button
            type="button"
            class="debt-detail-delete-button"
            :disabled="isDeletingAccount || !account"
            @click="openAccountDeleteModal"
          >
            删除账户
          </button>
        </template>
      </PageHeader>
    </header>

    <p v-if="pageError" class="debt-detail-message debt-detail-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="debt-detail-summary-card" aria-label="负债详情总览">
        <div class="debt-detail-summary-top">
          <div class="debt-detail-summary-title">
            <strong>贷款合同</strong>
            <span>{{ detailName }} · 每月 {{ account?.loanRepaymentDay ?? '--' }} 日还款</span>
          </div>
          <span class="debt-detail-summary-badge">{{ isSettled ? '已结清' : '还款中' }}</span>
        </div>

        <AmountText tag="p" class="debt-detail-summary-amount" :value="summaryAmountText" tone="inherit" />
        <p class="debt-detail-summary-sub">下期账单 {{ nextDueText }}</p>
        <p v-if="detailNote" class="debt-detail-summary-note">{{ detailNote }}</p>

        <div class="debt-detail-metrics">
          <div class="debt-detail-metric">
            <span>合同总额</span>
            <strong>{{ formatCurrency(totalRepaymentAmount) }}</strong>
          </div>
          <div class="debt-detail-metric">
            <span>已还金额</span>
            <strong>{{ formatCurrency(paidAmount) }}</strong>
          </div>
          <div class="debt-detail-metric">
            <span>月供参考</span>
            <strong>{{ formatCurrency(monthlyPaymentAmount) }}</strong>
          </div>
          <div class="debt-detail-metric">
            <span>贷款本金</span>
            <strong>{{ formatCurrency(Number(account?.loanTotalAmount ?? 0)) }}</strong>
          </div>
          <div class="debt-detail-metric">
            <span>贷款利率</span>
            <strong>{{ loanInterestRateText }}</strong>
          </div>
          <div class="debt-detail-metric">
            <span>剩余期数</span>
            <strong>{{ remainingInstallmentPeriods }} 期</strong>
          </div>
        </div>

        <div class="debt-detail-summary-actions">
          <CommonButton variant="secondary" :disabled="isSettled" @click="openAddRecordModal">
            新增月账单
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSettled" @click="openPrepayModal">
            提前还款
          </CommonButton>
        </div>
      </section>

      <section class="debt-record-history" aria-label="还款记录">
        <div class="debt-record-history-head">
          <div class="debt-record-history-title">
            <strong>月账单列表</strong>
            <span>支持按月还款、修改账单，也支持整笔提前结清</span>
          </div>
          <span class="debt-record-history-badge">{{ records.length }} 条</span>
        </div>

        <div class="debt-record-history-list">
          <p v-if="records.length === 0" class="debt-record-empty">
            暂无月账单
          </p>

          <article
            v-for="record in records"
            v-else
            :key="record.id"
            class="debt-record-card"
          >
            <div class="debt-record-card-main">
              <div class="debt-record-card-top">
                <span class="debt-record-chip" :class="{ 'is-payable': record.repaymentStatus !== 'paid' }">
                  {{ formatRepaymentStatus(record.repaymentStatus, record.repaymentType) }}
                </span>
                <span class="debt-record-date">{{ formatDate(record.occurredAt) }}</span>
              </div>
              <p class="debt-record-funding-account">
                {{ formatInstallmentLabel(record) }}
                · {{ record.repaymentStatus === 'paid' && record.paidAt ? `还款于 ${formatDate(record.paidAt)}` : formatRemainingLabel(record) }}
              </p>
              <p class="debt-record-remark">{{ record.remark || '未填写还款说明' }}</p>
            </div>

            <div class="debt-record-card-side">
              <strong class="debt-record-amount" :class="{ 'is-negative': record.repaymentStatus !== 'paid' }">
                {{ formatRecordAmount(record) }}
              </strong>
              <div v-if="record.repaymentType !== 'prepayment'" class="debt-record-actions">
                <button
                  v-if="record.repaymentStatus !== 'paid'"
                  type="button"
                  class="debt-record-action"
                  @click="openRepayModal(record)"
                >
                  去还款
                </button>
                <button type="button" class="debt-record-action" @click="openEditRecordModal(record)">
                  修改
                </button>
                <button type="button" class="debt-record-action is-danger" @click="openDeleteModal(record)">
                  删除
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <FloatingAddButton
        v-if="!isSettled"
        aria-label="新增月账单"
        storage-key="liability-record-create"
        @click="openAddRecordModal"
      />
    </template>

    <CommonModal
      v-model="showRepayModal"
      title="确认月供还款"
    >
      <div class="debt-record-form">
        <CommonInput
          :model-value="repayingRecord ? repayingRecord.accountName || detailName : detailName"
          label="贷款账户"
          :disabled="true"
        />
        <CommonInput
          :model-value="repayingRecord ? formatInstallmentLabel(repayingRecord) : ''"
          label="当前账单"
          :disabled="true"
        />
        <CommonInput
          :model-value="repayingRecord ? formatRecordAmount(repayingRecord) : ''"
          label="本期金额"
          :disabled="true"
        />
        <CommonInput
          v-model="repayPaidAt"
          label="还款时间"
          input-type="datetime-local"
        />
        <p v-if="repayError" class="debt-record-form-error">
          {{ repayError }}
        </p>
      </div>

      <template #footer>
        <div class="debt-record-modal-actions">
          <CommonButton variant="secondary" :disabled="isRepaying" @click="closeRepayModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isRepaying" @click="confirmRepay">
            {{ isRepaying ? '还款中...' : '确认还款' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showPrepayModal"
      title="提前还款"
    >
      <div class="debt-record-form">
        <CommonInput :model-value="detailName" label="贷款账户" :disabled="true" />
        <CommonInput :model-value="summaryAmountText" label="剩余应还" :disabled="true" />
        <CommonInput
          v-model="prepayPaidAt"
          label="结清时间"
          input-type="datetime-local"
        />
        <CommonInput v-model="prepayRemark" label="备注" placeholder="例如：提前结清房贷" />
        <p v-if="prepayError" class="debt-record-form-error">
          {{ prepayError }}
        </p>
      </div>

      <template #footer>
        <div class="debt-record-modal-actions">
          <CommonButton variant="secondary" :disabled="isPrepaying" @click="closePrepayModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isPrepaying" @click="confirmPrepay">
            {{ isPrepaying ? '结清中...' : '确认提前还款' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showRecordModal"
      :title="recordModalTitle"
    >
      <div class="debt-record-form">
        <template v-if="hasLoanPlan">
          <CommonInput
            v-if="!editingRecord && nextInstallmentPreview"
            :model-value="`第 ${nextInstallmentPreview.currentPeriod}/${nextInstallmentPreview.totalPeriods} 期`"
            label="计划账期"
            :disabled="true"
          />
          <CommonInput
            v-else-if="editingRecord"
            :model-value="formatInstallmentLabel(editingRecord)"
            label="计划账期"
            :disabled="true"
          />
          <CommonInput
            v-model="recordOccurredAt"
            label="账单时间"
            input-type="datetime-local"
          />
          <CommonInput
            v-model="recordAmount"
            label="本期金额"
            placeholder="允许按实际月供调整"
            input-type="number"
            input-mode="decimal"
          />
          <p class="debt-record-form-hint">
            月账单会按贷款合同顺序生成，默认带出下期账期、还款日和参考月供。
          </p>
          <p v-if="nextInstallmentPreview?.error && !editingRecord" class="debt-record-form-error">
            {{ nextInstallmentPreview.error }}
          </p>
        </template>
        <template v-else>
          <CommonInput
            v-model="recordAmount"
            label="本期待还金额"
            placeholder="输入本期应还金额"
            input-type="number"
            input-mode="decimal"
          />
          <CommonInput
            v-model="recordInstallmentTotalPeriods"
            label="贷款总期数"
            placeholder="可选，例如 360"
            input-type="number"
            input-mode="numeric"
          />
          <CommonInput
            v-model="recordInstallmentCurrentPeriod"
            label="当前还款期数"
            placeholder="可选，默认第1期"
            input-type="number"
            input-mode="numeric"
          />
          <CommonInput
            v-model="recordOccurredAt"
            label="账单时间"
            input-type="datetime-local"
          />
          <p class="debt-record-form-hint">
            当前账户未配置完整贷款合同，暂按手工账单模式处理。
          </p>
        </template>
        <CommonInput v-model="recordRemark" label="备注" placeholder="输入本期房贷、车贷说明" />
        <p v-if="recordFormError" class="debt-record-form-error">
          {{ recordFormError }}
        </p>
      </div>

      <template #footer>
        <div class="debt-record-modal-actions">
          <CommonButton variant="secondary" :disabled="isSavingRecord" @click="closeRecordModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSavingRecord" @click="saveRecord">
            {{ editingRecord ? '保存修改' : '保存账单' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showAccountDeleteModal"
      title="删除负债账户"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="debt-record-delete-message">
        删除后该账户下的全部月账单会一并移除，确认继续吗？
      </p>
      <p v-if="accountDeleteError" class="debt-record-delete-error">
        {{ accountDeleteError }}
      </p>

      <template #footer>
        <div class="debt-record-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeletingAccount" @click="closeAccountDeleteModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isDeletingAccount" @click="confirmDeleteAccount">
            {{ isDeletingAccount ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showDeleteModal"
      title="删除月账单"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="debt-record-delete-message">
        删除后无法恢复，确认删除这条月账单吗？
      </p>
      <p v-if="deleteError" class="debt-record-delete-error">
        {{ deleteError }}
      </p>

      <template #footer>
        <div class="debt-record-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeleting" @click="closeDeleteModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isDeleting" @click="confirmDelete">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
