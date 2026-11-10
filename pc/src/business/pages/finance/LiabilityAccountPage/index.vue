<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'
import FloatingAddButton from '@/business/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'
import {
  createAccount,
  deleteAccount,
  getAccounts,
  getAccountTypes,
  getLiabilityAccountSummary,
  getLiabilityRecords,
  updateAccount,
  type Account,
  type AccountType,
  type LiabilityAccountSummary,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

type LiabilityCardView = {
  account: Account
  avatarText: string
  avatarClass: string
  secondaryText: string
  noteText: string
  amountText: string
  amountHint: string
}

type DeleteTarget = {
  id: number
  name: string
}

const LIABILITY_ACCOUNT_CODE = 'liability'

const router = useRouter()
const accounts = ref<Account[]>([])
const accountTypes = ref<AccountType[]>([])
const liabilityRecords = ref<
  Array<{
    id: number
    accountId: number
    amount: number
    installmentTotalPeriods?: number | null
    installmentCurrentPeriod?: number | null
    repaymentStatus: 'pending' | 'paid'
    paidAt?: string | null
    occurredAt: string
  }>
>([])
const summary = ref<LiabilityAccountSummary>({
  totalAmount: 0,
  accountCount: 0,
  recordCount: 0,
})
const isLoading = ref(true)
const isManageMode = ref(false)
const pageError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showAccountModal = ref(false)
const showDeleteModal = ref(false)
const isSavingAccount = ref(false)
const isDeleting = ref(false)
const accountFormError = ref('')
const deleteError = ref('')
const editingAccount = ref<Account | null>(null)
const deletingTarget = ref<DeleteTarget | null>(null)
const accountName = ref('')
const accountRemark = ref('')
const loanTotalAmount = ref('')
const loanInterestRate = ref('')
const loanTotalPeriods = ref('')
const loanRepaymentDay = ref('')
const loanStartDate = ref('')
const includeInNetWorth = ref(true)

const liabilityAccountType = computed(
  () =>
    accountTypes.value.find((type) => type.code === LIABILITY_ACCOUNT_CODE) ??
    null,
)
const accountModalTitle = computed(() =>
  editingAccount.value ? '修改负债账户' : '新增负债账户',
)
const accountSubmitLabel = computed(() =>
  editingAccount.value ? '保存账户' : '新增账户',
)

const recordsByAccountId = computed(() => {
  const grouped = new Map<number, typeof liabilityRecords.value>()
  for (const record of liabilityRecords.value) {
    const list = grouped.get(record.accountId) ?? []
    list.push(record)
    grouped.set(record.accountId, list)
  }
  return grouped
})

const liabilityCards = computed<LiabilityCardView[]>(() =>
  accounts.value.map((account, index) => {
    const records = recordsByAccountId.value.get(account.id) ?? []
    const pendingRecords = records.filter(
      (record) => record.repaymentStatus !== 'paid',
    )
    const latestRecord = pendingRecords[0] ?? records[0]
    const currentPendingRecord = getCurrentPendingRecord(records)
    const nextPeriod = getNextPeriod(account, records)
    const displayPeriod =
      currentPendingRecord?.installmentCurrentPeriod ?? nextPeriod
    const scheduledDate = currentPendingRecord
      ? new Date(currentPendingRecord.occurredAt)
      : buildScheduledDate(account, nextPeriod)
    const isSettled =
      Boolean(account.loanSettledAt) || Number(account.currentBalance ?? 0) <= 0

    return {
      account,
      avatarText: (account.name || '负').slice(0, 1),
      avatarClass: `debt-avatar-${index % 4}`,
      secondaryText: isSettled
        ? '已结清'
        : `每月 ${account.loanRepaymentDay ?? '--'} 日 · 第 ${displayPeriod}/${account.loanTotalPeriods ?? '--'} 期`,
      noteText:
        account.remark?.trim() ||
        (isSettled
          ? '该笔贷款已完成结清'
          : scheduledDate
            ? `${currentPendingRecord ? '本期待还' : '下期应还'} ${formatDate(scheduledDate.toISOString())}`
            : '点击查看月供账单'),
      amountText: formatCurrency(Number(account.currentBalance ?? 0)),
      amountHint: latestRecord
        ? latestRecord.repaymentStatus === 'paid' && latestRecord.paidAt
          ? `最近还款 ${formatDate(latestRecord.paidAt)}`
          : `最近更新 ${formatDate(latestRecord.occurredAt)}`
        : isSettled
          ? '合同已结清'
          : `月供约 ${formatCurrency(getMonthlyPayment(account))}`,
    }
  }),
)

const summaryAmountText = computed(() =>
  formatCurrency(summary.value.totalAmount),
)
const currentMonthLiabilityTotal = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  return accounts.value.reduce((total, account) => {
    if (account.loanSettledAt || Number(account.currentBalance ?? 0) <= 0) {
      return total
    }
    const records = recordsByAccountId.value.get(account.id) ?? []
    const pendingAmountThisMonth = records.reduce((recordTotal, record) => {
      if (record.repaymentStatus === 'paid') {
        return recordTotal
      }
      const occurredAt = new Date(record.occurredAt)
      if (Number.isNaN(occurredAt.getTime())) {
        return recordTotal
      }
      return occurredAt.getFullYear() === year &&
        occurredAt.getMonth() === month
        ? recordTotal + Number(record.amount ?? 0)
        : recordTotal
    }, 0)
    if (pendingAmountThisMonth > 0) {
      return total + pendingAmountThisMonth
    }

    const nextPeriod = getNextPeriod(account, records)
    const dueDate = buildScheduledDate(account, nextPeriod)
    if (!dueDate) {
      return total
    }
    return dueDate.getFullYear() === year && dueDate.getMonth() === month
      ? total + getMonthlyPayment(account)
      : total
  }, 0)
})

onMounted(() => {
  void loadLiabilityPage()
})

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function openLiabilityDetail(accountId: number) {
  if (isManageMode.value) {
    return
  }
  router.push(`/finance/accounts/liability/${accountId}`)
}

function openEditAccountModal(account: Account) {
  editingAccount.value = account
  accountName.value = account.name
  accountRemark.value = account.remark ?? ''
  loanTotalAmount.value =
    account.loanTotalAmount != null
      ? String(Number(account.loanTotalAmount))
      : ''
  loanInterestRate.value = resolveLoanInterestRateInput(account)
  loanTotalPeriods.value =
    account.loanTotalPeriods != null ? String(account.loanTotalPeriods) : ''
  loanRepaymentDay.value =
    account.loanRepaymentDay != null ? String(account.loanRepaymentDay) : ''
  loanStartDate.value = account.loanStartDate ?? ''
  includeInNetWorth.value = account.includeInNetWorth
  accountFormError.value = ''
  showAccountModal.value = true
}

function openCreateAccountModal() {
  editingAccount.value = null
  accountName.value = ''
  accountRemark.value = ''
  loanTotalAmount.value = ''
  loanInterestRate.value = ''
  loanTotalPeriods.value = ''
  loanRepaymentDay.value = ''
  loanStartDate.value = ''
  includeInNetWorth.value =
    liabilityAccountType.value?.includeInNetWorthDefault ?? true
  accountFormError.value = ''
  showAccountModal.value = true
}

function closeAccountModal(force = false) {
  if (isSavingAccount.value && !force) {
    return
  }
  showAccountModal.value = false
  editingAccount.value = null
  accountName.value = ''
  accountRemark.value = ''
  loanTotalAmount.value = ''
  loanInterestRate.value = ''
  loanTotalPeriods.value = ''
  loanRepaymentDay.value = ''
  loanStartDate.value = ''
  includeInNetWorth.value = true
  accountFormError.value = ''
}

function openDeleteModal(account: Account) {
  deletingTarget.value = {
    id: account.id,
    name: account.name,
  }
  deleteError.value = ''
  showDeleteModal.value = true
}

function closeDeleteModal(force = false) {
  if (isDeleting.value && !force) {
    return
  }
  showDeleteModal.value = false
  deletingTarget.value = null
  deleteError.value = ''
}

async function loadLiabilityPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看负债账户'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList, typeList, summaryData, recordList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }),
      getAccountTypes({ status: 'active' }),
      getLiabilityAccountSummary(currentUser.id),
      getLiabilityRecords({ userId: currentUser.id }),
    ])

    accounts.value = accountList.filter(
      (account) => account.accountTypeCode === LIABILITY_ACCOUNT_CODE,
    )
    accountTypes.value = typeList
    summary.value = summaryData
    liabilityRecords.value = recordList
  } catch (error) {
    pageError.value = getErrorMessage(error, '负债账户加载失败')
  } finally {
    isLoading.value = false
  }
}

async function saveAccount() {
  const currentUser = getStoredCurrentUser()
  const account = editingAccount.value
  const targetAccountType = account
    ? (accountTypes.value.find((type) => type.id === account.accountTypeId) ??
      null)
    : liabilityAccountType.value
  const trimmedName = accountName.value.trim()
  const normalizedLoanTotalAmount = normalizePositiveAmount(
    loanTotalAmount.value,
  )
  const normalizedLoanInterestRate = normalizeNullableAmount(
    loanInterestRate.value,
  )
  const normalizedLoanTotalPeriods = normalizePositiveInteger(
    loanTotalPeriods.value,
  )
  const normalizedLoanRepaymentDay = normalizePositiveInteger(
    loanRepaymentDay.value,
  )
  const normalizedLoanStartDate = loanStartDate.value.trim()

  if (!currentUser) {
    accountFormError.value = '负债账户信息不完整'
    return
  }

  if (!trimmedName) {
    accountFormError.value = '请输入负债账户名称'
    return
  }

  if (!targetAccountType) {
    accountFormError.value = '负债账户类型不存在'
    return
  }

  if (
    !Number.isFinite(normalizedLoanTotalAmount) ||
    normalizedLoanTotalAmount <= 0
  ) {
    accountFormError.value = '请输入有效的贷款总额'
    return
  }

  if (
    !Number.isInteger(normalizedLoanTotalPeriods) ||
    normalizedLoanTotalPeriods < 2
  ) {
    accountFormError.value = '贷款总期数至少为2'
    return
  }

  if (
    Number.isNaN(normalizedLoanInterestRate) ||
    normalizedLoanInterestRate < 0
  ) {
    accountFormError.value = '请输入有效的贷款利率'
    return
  }

  if (
    !Number.isInteger(normalizedLoanRepaymentDay) ||
    normalizedLoanRepaymentDay < 1 ||
    normalizedLoanRepaymentDay > 31
  ) {
    accountFormError.value = '每月还款日必须在1到31之间'
    return
  }

  if (!normalizedLoanStartDate) {
    accountFormError.value = '请选择首期账单日期'
    return
  }

  isSavingAccount.value = true
  accountFormError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      accountTypeId: targetAccountType.id,
      contactId: null,
      name: trimmedName,
      icon: targetAccountType.code ?? LIABILITY_ACCOUNT_CODE,
      currencyCode: account?.currencyCode || 'CNY',
      currentBalance: 0,
      loanTotalAmount: normalizedLoanTotalAmount,
      loanInterestRate: normalizedLoanInterestRate,
      loanTotalPeriods: normalizedLoanTotalPeriods,
      loanRepaymentDay: normalizedLoanRepaymentDay,
      loanStartDate: normalizedLoanStartDate,
      includeInNetWorth: includeInNetWorth.value,
      status: 'active',
      remark: accountRemark.value.trim() || null,
    }

    if (account) {
      await updateAccount(account.id, payload)
      showFeedback('负债账户已更新', 'success')
    } else {
      await createAccount(payload)
      showFeedback('负债账户已新增', 'success')
    }
    closeAccountModal(true)
    await loadLiabilityPage()
  } catch (error) {
    const message = getErrorMessage(error, '负债账户保存失败')
    accountFormError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingAccount.value = false
  }
}

async function confirmDelete() {
  const target = deletingTarget.value
  if (!target) {
    return
  }

  isDeleting.value = true
  deleteError.value = ''

  try {
    await deleteAccount(target.id)
    closeDeleteModal(true)
    showFeedback('负债账户已删除', 'success')
    await loadLiabilityPage()
  } catch (error) {
    const message = getErrorMessage(error, '删除失败')
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
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

function normalizePositiveAmount(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return Number.NaN
  }
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? parsed : Number.NaN
}

function normalizeNullableAmount(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return 0
  }
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? parsed : Number.NaN
}

function normalizePositiveInteger(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return Number.NaN
  }
  const parsed = Number(trimmed)
  return Number.isInteger(parsed) ? parsed : Number.NaN
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function resolveInterestAmount(account: Account) {
  const principal = Number(account.loanTotalAmount ?? 0)
  const rate = account.loanInterestRate
  if (rate != null && Number.isFinite(Number(rate))) {
    return Math.round(principal * Number(rate)) / 100
  }
  return Number(account.loanInterestAmount ?? 0)
}

function resolveLoanInterestRateInput(account: Account) {
  if (account.loanInterestRate != null) {
    return String(Number(account.loanInterestRate))
  }
  const principal = Number(account.loanTotalAmount ?? 0)
  const interestAmount = Number(account.loanInterestAmount ?? 0)
  if (principal > 0 && interestAmount > 0) {
    return String(Math.round((interestAmount / principal) * 10000) / 100)
  }
  return ''
}

function getTotalRepaymentAmount(account: Account) {
  return Number(account.loanTotalAmount ?? 0) + resolveInterestAmount(account)
}

function getMonthlyPayment(account: Account) {
  const totalPeriods = Number(account.loanTotalPeriods ?? 0)
  if (totalPeriods <= 0) {
    return 0
  }
  const totalRepaymentAmount = getTotalRepaymentAmount(account)
  const average = Math.round((totalRepaymentAmount / totalPeriods) * 100) / 100
  return average
}

function getNextPeriod(
  account: Account,
  records: typeof liabilityRecords.value,
) {
  const maxPeriod = records.reduce(
    (max, record) =>
      Math.max(max, Number(record.installmentCurrentPeriod ?? 0)),
    0,
  )
  return Math.min(
    maxPeriod + 1,
    Number(account.loanTotalPeriods ?? maxPeriod + 1),
  )
}

function getCurrentPendingRecord(records: typeof liabilityRecords.value) {
  return records.reduce<(typeof liabilityRecords.value)[number] | null>(
    (selectedRecord, record) => {
      if (record.repaymentStatus === 'paid') {
        return selectedRecord
      }
      if (selectedRecord === null) {
        return record
      }
      const currentPeriod = Number(
        record.installmentCurrentPeriod ?? Number.MAX_SAFE_INTEGER,
      )
      const selectedPeriod = Number(
        selectedRecord.installmentCurrentPeriod ?? Number.MAX_SAFE_INTEGER,
      )
      if (currentPeriod !== selectedPeriod) {
        return currentPeriod < selectedPeriod ? record : selectedRecord
      }
      return new Date(record.occurredAt).getTime() <
        new Date(selectedRecord.occurredAt).getTime()
        ? record
        : selectedRecord
    },
    null,
  )
}

function buildScheduledDate(account: Account, period: number) {
  if (!account.loanStartDate) {
    return null
  }
  const baseDate = new Date(`${account.loanStartDate}T09:00:00`)
  if (Number.isNaN(baseDate.getTime())) {
    return null
  }
  const result = new Date(baseDate)
  result.setMonth(result.getMonth() + Math.max(period - 1, 0))
  const repaymentDay = Number(account.loanRepaymentDay ?? baseDate.getDate())
  const monthEnd = new Date(
    result.getFullYear(),
    result.getMonth() + 1,
    0,
  ).getDate()
  result.setDate(Math.min(repaymentDay, monthEnd))
  return result
}

type DesktopRow = (typeof liabilityCards.value)[number]
</script>
<template>
  <section class="pc-page">
    <PageHeader title="负债账户">
      <template #right>
        <el-button type="primary" @click="openCreateAccountModal">
          新增账户
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    />
    <DesktopTable :rows="liabilityCards" :loading="isLoading">
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="账户"
        prop="account.name"
        min-width="180"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="待还金额"
        prop="amountText"
        min-width="140"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="还款周期"
        prop="secondaryText"
        min-width="220"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="还款信息"
        prop="noteText"
        min-width="220"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button
              link
              type="primary"
              @click="openLiabilityDetail(row.account.id)"
            >
              明细
            </el-button>
            <el-button
              link
              type="primary"
              @click="openEditAccountModal(row.account)"
            >
              编辑
            </el-button>
            <el-button link type="danger" @click="openDeleteModal(row.account)">
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal v-model="showAccountModal" :title="accountModalTitle">
      <div class="pc-form-grid debt-form">
        <CommonInput
          v-model="accountName"
          label="账户名称"
          placeholder="例如：招行房贷、比亚迪车贷、公积金贷"
        />
        <CommonInput
          v-model="loanTotalAmount"
          label="贷款总额"
          placeholder="例如：680000"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="loanInterestRate"
          label="贷款利率（%）"
          placeholder="例如：3.25"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="loanTotalPeriods"
          label="贷款总期数"
          placeholder="例如：360"
          input-type="number"
          input-mode="numeric"
        />
        <CommonInput
          v-model="loanRepaymentDay"
          label="每月还款日"
          placeholder="例如：20"
          input-type="number"
          input-mode="numeric"
        />
        <CommonInput
          v-model="loanStartDate"
          label="首期账单日期"
          input-type="date"
        />
        <CommonInput
          v-model="accountRemark"
          label="备注"
          placeholder="输入贷款机构、还款日等说明"
        />
        <CommonSwitch v-model="includeInNetWorth" label="是否计入总资产" />
        <p v-if="accountFormError" class="debt-form-error">
          {{ accountFormError }}
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar debt-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSavingAccount"
            @click="closeAccountModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSavingAccount"
            @click="saveAccount"
          >
            {{ accountSubmitLabel }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showDeleteModal"
      title="删除负债账户"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="debt-delete-message">
        删除后该账户下的负债账单会一并移除，确认继续吗？
      </p>
      <p v-if="deleteError" class="debt-delete-error">{{ deleteError }}</p>
      <template #footer>
        <div class="pc-toolbar debt-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isDeleting"
            @click="confirmDelete"
          >
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
