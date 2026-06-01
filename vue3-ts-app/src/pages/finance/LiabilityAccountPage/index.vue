<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
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
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

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
const liabilityRecords = ref<Array<{
  id: number
  accountId: number
  amount: number
  installmentTotalPeriods?: number | null
  installmentCurrentPeriod?: number | null
  repaymentStatus: 'pending' | 'paid'
  paidAt?: string | null
  occurredAt: string
}>>([])
const summary = ref<LiabilityAccountSummary>({
  totalAmount: 0,
  accountCount: 0,
  recordCount: 0,
})
const isLoading = ref(false)
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

const liabilityAccountType = computed(() => accountTypes.value.find((type) => type.code === LIABILITY_ACCOUNT_CODE) ?? null)
const accountModalTitle = computed(() => (editingAccount.value ? '修改负债账户' : '新增负债账户'))
const accountSubmitLabel = computed(() => (editingAccount.value ? '保存账户' : '新增账户'))

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
    const pendingRecords = records.filter((record) => record.repaymentStatus !== 'paid')
    const latestRecord = pendingRecords[0] ?? records[0]
    const currentPendingRecord = getCurrentPendingRecord(records)
    const nextPeriod = getNextPeriod(account, records)
    const displayPeriod = currentPendingRecord?.installmentCurrentPeriod ?? nextPeriod
    const scheduledDate = currentPendingRecord
      ? new Date(currentPendingRecord.occurredAt)
      : buildScheduledDate(account, nextPeriod)
    const isSettled = Boolean(account.loanSettledAt) || Number(account.currentBalance ?? 0) <= 0

    return {
      account,
      avatarText: (account.name || '负').slice(0, 1),
      avatarClass: `debt-avatar-${index % 4}`,
      secondaryText: isSettled
        ? '已结清'
        : `每月 ${account.loanRepaymentDay ?? '--'} 日 · 第 ${displayPeriod}/${account.loanTotalPeriods ?? '--'} 期`,
      noteText: account.remark?.trim()
        || (isSettled
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

const summaryAmountText = computed(() => formatCurrency(summary.value.totalAmount))
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
      return occurredAt.getFullYear() === year && occurredAt.getMonth() === month
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
  loanTotalAmount.value = account.loanTotalAmount != null ? String(Number(account.loanTotalAmount)) : ''
  loanInterestRate.value = resolveLoanInterestRateInput(account)
  loanTotalPeriods.value = account.loanTotalPeriods != null ? String(account.loanTotalPeriods) : ''
  loanRepaymentDay.value = account.loanRepaymentDay != null ? String(account.loanRepaymentDay) : ''
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
  includeInNetWorth.value = liabilityAccountType.value?.includeInNetWorthDefault ?? true
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

    accounts.value = accountList.filter((account) => account.accountTypeCode === LIABILITY_ACCOUNT_CODE)
    accountTypes.value = typeList
    summary.value = summaryData
    liabilityRecords.value = recordList
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '负债账户加载失败'
  } finally {
    isLoading.value = false
  }
}

async function saveAccount() {
  const currentUser = getStoredCurrentUser()
  const account = editingAccount.value
  const targetAccountType = account ? accountTypes.value.find((type) => type.id === account.accountTypeId) ?? null : liabilityAccountType.value
  const trimmedName = accountName.value.trim()
  const normalizedLoanTotalAmount = normalizePositiveAmount(loanTotalAmount.value)
  const normalizedLoanInterestRate = normalizeNullableAmount(loanInterestRate.value)
  const normalizedLoanTotalPeriods = normalizePositiveInteger(loanTotalPeriods.value)
  const normalizedLoanRepaymentDay = normalizePositiveInteger(loanRepaymentDay.value)
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

  if (!Number.isFinite(normalizedLoanTotalAmount) || normalizedLoanTotalAmount <= 0) {
    accountFormError.value = '请输入有效的贷款总额'
    return
  }

  if (!Number.isInteger(normalizedLoanTotalPeriods) || normalizedLoanTotalPeriods < 2) {
    accountFormError.value = '贷款总期数至少为2'
    return
  }

  if (Number.isNaN(normalizedLoanInterestRate) || normalizedLoanInterestRate < 0) {
    accountFormError.value = '请输入有效的贷款利率'
    return
  }

  if (!Number.isInteger(normalizedLoanRepaymentDay) || normalizedLoanRepaymentDay < 1 || normalizedLoanRepaymentDay > 31) {
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
    const message = error instanceof Error ? error.message : '负债账户保存失败'
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
    const message = error instanceof Error ? error.message : '删除失败'
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

function getNextPeriod(account: Account, records: typeof liabilityRecords.value) {
  const maxPeriod = records.reduce((max, record) => Math.max(max, Number(record.installmentCurrentPeriod ?? 0)), 0)
  return Math.min(maxPeriod + 1, Number(account.loanTotalPeriods ?? maxPeriod + 1))
}

function getCurrentPendingRecord(records: typeof liabilityRecords.value) {
  return records.reduce<typeof liabilityRecords.value[number] | null>((selectedRecord, record) => {
    if (record.repaymentStatus === 'paid') {
      return selectedRecord
    }
    if (selectedRecord === null) {
      return record
    }
    const currentPeriod = Number(record.installmentCurrentPeriod ?? Number.MAX_SAFE_INTEGER)
    const selectedPeriod = Number(selectedRecord.installmentCurrentPeriod ?? Number.MAX_SAFE_INTEGER)
    if (currentPeriod !== selectedPeriod) {
      return currentPeriod < selectedPeriod ? record : selectedRecord
    }
    return new Date(record.occurredAt).getTime() < new Date(selectedRecord.occurredAt).getTime()
      ? record
      : selectedRecord
  }, null)
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
  const monthEnd = new Date(result.getFullYear(), result.getMonth() + 1, 0).getDate()
  result.setDate(Math.min(repaymentDay, monthEnd))
  return result
}
</script>

<template>
  <section class="debt-account-page" aria-label="负债账户">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="debt-account-header">
      <PageHeader title="负债账户" back-to="/finance/accounts" back-label="返回账户管理" />
      <button
        type="button"
        :class="['debt-manage-button', { active: isManageMode }]"
        :aria-label="isManageMode ? '退出管理模式' : '进入管理模式'"
        @click="toggleManageMode"
      >
        {{ isManageMode ? '完成' : '管理' }}
      </button>
    </header>

    <p v-if="pageError" class="debt-page-message debt-page-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="debt-summary-card" aria-label="负债汇总">
        <div class="debt-summary-top">
          <div class="debt-summary-title">
            <span>贷款待还总额</span>
            <p>主要记录房贷、车贷等分期还款项目</p>
          </div>
          <span class="debt-summary-badge">{{ summary.accountCount }} 个负债账户</span>
        </div>

        <AmountText tag="strong" class="debt-summary-amount" :value="summaryAmountText" tone="inherit" />

        <div class="debt-summary-metrics">
          <div class="debt-summary-metric">
            <span>本月待还</span>
            <strong>{{ formatCurrency(currentMonthLiabilityTotal) }}</strong>
          </div>
          <div class="debt-summary-metric">
            <span>待还</span>
            <strong>{{ formatCurrency(summary.totalAmount) }}</strong>
          </div>
          <div class="debt-summary-metric">
            <span>记录数</span>
            <strong>{{ summary.recordCount }} 条</strong>
          </div>
        </div>
      </section>

      <section class="debt-account-list" aria-label="负债账户列表">
        <p v-if="liabilityCards.length === 0" class="debt-empty">
          请先新增房贷、车贷等负债账户
        </p>

        <article
          v-for="card in liabilityCards"
          v-else
          :key="card.account.id"
          :class="['debt-account-card', { 'is-manage-mode': isManageMode }]"
          :role="isManageMode ? undefined : 'button'"
          :tabindex="isManageMode ? -1 : 0"
          @click="openLiabilityDetail(card.account.id)"
        >
          <div class="debt-account-card-top">
            <div class="debt-account-card-person">
              <span class="debt-account-avatar" :class="card.avatarClass">
                {{ card.avatarText }}
              </span>
              <div class="debt-account-card-text">
                <div class="debt-account-card-name-row">
                  <strong>{{ card.account.name }}</strong>
                  <span class="debt-account-card-phone">{{ card.secondaryText }}</span>
                </div>
                <p>{{ card.noteText }}</p>
              </div>
            </div>

            <div class="debt-account-card-side">
              <AmountText tag="strong" class="debt-account-card-amount" :value="card.amountText" tone="inherit" />
              <span class="debt-account-card-tag is-negative">待还</span>
              <span class="debt-account-card-link">{{ card.amountHint }}</span>
            </div>
          </div>

          <div class="debt-account-card-bottom">
            <span>{{ card.noteText }}</span>
            <div v-if="isManageMode" class="debt-account-card-actions">
              <button type="button" class="debt-card-action" @click.stop="openEditAccountModal(card.account)">
                编辑
              </button>
              <button type="button" class="debt-card-action is-danger" @click.stop="openDeleteModal(card.account)">
                删除
              </button>
            </div>
            <span v-else class="debt-account-card-link">查看还款账单</span>
          </div>
        </article>
      </section>
    </template>

    <FloatingAddButton aria-label="新增负债账户" storage-key="liability-account-page" @click="openCreateAccountModal" />

    <CommonModal
      v-model="showAccountModal"
      :title="accountModalTitle"
    >
      <div class="debt-form">
        <CommonInput v-model="accountName" label="账户名称" placeholder="例如：招行房贷、比亚迪车贷、公积金贷" />
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
        <CommonInput v-model="accountRemark" label="备注" placeholder="输入贷款机构、还款日等说明" />
        <CommonSwitch v-model="includeInNetWorth" label="是否计入总资产" />
        <p v-if="accountFormError" class="debt-form-error">
          {{ accountFormError }}
        </p>
      </div>

      <template #footer>
        <div class="debt-modal-actions">
          <CommonButton variant="secondary" :disabled="isSavingAccount" @click="closeAccountModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSavingAccount" @click="saveAccount">
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
      <p v-if="deleteError" class="debt-delete-error">
        {{ deleteError }}
      </p>

      <template #footer>
        <div class="debt-modal-actions">
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
