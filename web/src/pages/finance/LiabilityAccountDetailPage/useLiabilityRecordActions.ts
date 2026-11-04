// 负债账户详情页的弹窗与操作逻辑：月账单新增/编辑/删除、还款、提前结清、账户删除。
import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createLiabilityRecord,
  deleteAccount,
  deleteLiabilityRecord,
  prepayLiabilityAccount,
  repayLiabilityRecord,
  updateLiabilityRecord,
  type Account,
  type LiabilityRecord,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getErrorMessage } from '@/api/request'
import type { FeedbackType } from '@/composables/useFeedback'
import {
  formatDate,
  normalizePositiveInteger,
  parseDateTimeLocal,
  parsePositiveAmount,
  toDateTimeLocalValue,
  toIsoLocalString,
} from './liabilityFormat'
import {
  buildScheduledDate,
  calculateInstallmentAmount,
  resolveNextInstallmentPeriod,
} from './liabilityLoanPlan'

type InstallmentPreview = {
  amount: number
  totalPeriods: number
  currentPeriod: number
  occurredAt: Date
  error: string
}

export type UseLiabilityRecordActionsOptions = {
  account: Ref<Account | null>
  records: Ref<LiabilityRecord[]>
  hasLoanPlan: ComputedRef<boolean>
  isSettled: ComputedRef<boolean>
  totalRepaymentAmount: ComputedRef<number>
  loadDetail: () => Promise<void>
  showFeedback: (message: string, type: FeedbackType) => void
}

export function useLiabilityRecordActions(options: UseLiabilityRecordActionsOptions) {
  const {
    account,
    records,
    hasLoanPlan,
    isSettled,
    totalRepaymentAmount,
    loadDetail,
    showFeedback,
  } = options
  const router = useRouter()

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

  const recordModalTitle = computed(() => editingRecord.value ? '修改月账单' : '新增月账单')
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
      const message = getErrorMessage(error, '月账单保存失败')
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
      const message = getErrorMessage(error, '删除失败')
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
      const message = getErrorMessage(error, '还款失败')
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
      const message = getErrorMessage(error, '提前还款失败')
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
      const message = getErrorMessage(error, '删除失败')
      accountDeleteError.value = message
      showFeedback(message, 'error')
    } finally {
      isDeletingAccount.value = false
    }
  }

  return {
    showRecordModal,
    showDeleteModal,
    showAccountDeleteModal,
    showRepayModal,
    showPrepayModal,
    isSavingRecord,
    isDeleting,
    isDeletingAccount,
    isRepaying,
    isPrepaying,
    recordFormError,
    deleteError,
    accountDeleteError,
    repayError,
    prepayError,
    editingRecord,
    deletingRecord,
    repayingRecord,
    recordAmount,
    recordInstallmentTotalPeriods,
    recordInstallmentCurrentPeriod,
    recordOccurredAt,
    recordRemark,
    repayPaidAt,
    prepayPaidAt,
    prepayRemark,
    recordModalTitle,
    nextInstallmentPreview,
    nextDueText,
    openAddRecordModal,
    openEditRecordModal,
    closeRecordModal,
    openDeleteModal,
    closeDeleteModal,
    confirmDelete,
    openRepayModal,
    closeRepayModal,
    confirmRepay,
    openPrepayModal,
    closePrepayModal,
    confirmPrepay,
    openAccountDeleteModal,
    closeAccountDeleteModal,
    confirmDeleteAccount,
    saveRecord,
  }
}
