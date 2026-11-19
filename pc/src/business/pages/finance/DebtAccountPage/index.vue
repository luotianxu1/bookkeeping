<script setup lang="ts">
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'

import {
  createAccount,
  deleteAccount,
  getAccounts,
  getAccountTypes,
  getDebtAccountSummary,
  getDebtRecords,
  updateAccount,
  type Account,
  type AccountType,
  type DebtAccountSummary,
} from '@/business/api/modules/finance'
import { getContacts, type Contact } from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

type DebtCardView = {
  account: Account
  displayName: string
  noteText: string
  netAmount: number
  netAmountText: string
  netTagText: string
  netTagClass: 'is-positive' | 'is-negative' | 'is-settled'
  lentTotalText: string
  returnedTotalText: string
  progressPercent: number
  progressLabel: string
}

type DeleteTarget = {
  id: number
  name: string
}

const DEBT_ACCOUNT_CODES = new Set(['debt'])
const DEFAULT_DEBT_ACCOUNT_CODE = 'debt'

const router = useRouter()
const accounts = ref<Account[]>([])
const contacts = ref<Contact[]>([])
const accountTypes = ref<AccountType[]>([])
const debtRecords = ref<
  Array<{
    id: number
    accountId: number
    direction: 'payable' | 'receivable'
    recordType?: 'borrow' | 'repayment'
    amount: number
    occurredAt: string
  }>
>([])
const summary = ref<DebtAccountSummary>({
  netAmount: 0,
  payableTotal: 0,
  receivableTotal: 0,
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
const accountContactId = ref('')
const accountRemark = ref('')
const includeInNetWorth = ref(true)

const contactMap = computed(
  () => new Map(contacts.value.map((contact) => [contact.id, contact])),
)
const debtAccountType = computed(
  () =>
    accountTypes.value.find(
      (type) => type.code === DEFAULT_DEBT_ACCOUNT_CODE,
    ) ?? null,
)
const accountModalTitle = computed(() =>
  editingAccount.value ? '修改债务账户' : '新增债务账户',
)
const accountSubmitLabel = computed(() =>
  editingAccount.value ? '保存账户' : '新增账户',
)

const contactOptions = computed(() => [
  {
    label:
      contacts.value.length > 0
        ? '请选择联系人'
        : '暂无可选联系人，请先新增联系人',
    value: '',
    disabled: true,
  },
  ...contacts.value.map((contact) => ({
    label: contact.phone?.trim()
      ? `${contact.name} · ${contact.phone}`
      : contact.name,
    value: String(contact.id),
  })),
])

const recordsByAccountId = computed(() => {
  const grouped = new Map<number, typeof debtRecords.value>()
  for (const record of debtRecords.value) {
    const list = grouped.get(record.accountId) ?? []
    list.push(record)
    grouped.set(record.accountId, list)
  }
  return grouped
})

const debtCards = computed<DebtCardView[]>(() =>
  accounts.value
    .map((account) => {
      const contact = account.contactId
        ? (contactMap.value.get(account.contactId) ?? null)
        : null
      const records = recordsByAccountId.value.get(account.id) ?? []
      const netAmount = sumDebtBalance(records)
      const lentTotal = records
        .filter((record) => record.recordType !== 'repayment')
        .reduce((total, record) => total + Number(record.amount ?? 0), 0)
      const returnedTotal = records
        .filter((record) => record.recordType === 'repayment')
        .reduce((total, record) => total + Number(record.amount ?? 0), 0)

      return {
        account,
        displayName: contact?.name?.trim() || account.name,
        noteText: contact?.remark?.trim() || account.remark?.trim() || '',
        netAmount,
        netAmountText: formatSignedCurrency(netAmount),
        netTagText: formatDebtStatusText(netAmount),
        netTagClass: formatDebtStatusClass(netAmount),
        lentTotalText: formatCurrency(lentTotal),
        returnedTotalText: formatCurrency(returnedTotal),
        progressPercent:
          lentTotal > 0
            ? Math.min(100, Math.round((returnedTotal / lentTotal) * 100))
            : 0,
        progressLabel:
          netAmount > 0 ? '回款进度' : netAmount < 0 ? '还款进度' : '结清进度',
      }
    })
    .filter((card) => {
      if (Math.round(card.netAmount * 100) !== 0) {
        return true
      }
      return (recordsByAccountId.value.get(card.account.id)?.length ?? 0) === 0
    }),
)

onMounted(() => {
  void loadDebtPage()
})

function openDebtDetail(accountId: number) {
  if (isManageMode.value) {
    return
  }
  router.push(`/finance/accounts/debt/${accountId}`)
}

function openEditAccountModal(account: Account) {
  editingAccount.value = account
  accountContactId.value = account.contactId ? String(account.contactId) : ''
  accountRemark.value = account.remark ?? ''
  includeInNetWorth.value = account.includeInNetWorth
  accountFormError.value = ''
  showAccountModal.value = true
}

function openCreateAccountModal() {
  editingAccount.value = null
  accountContactId.value = ''
  accountRemark.value = ''
  includeInNetWorth.value =
    debtAccountType.value?.includeInNetWorthDefault ?? true
  accountFormError.value = ''
  showAccountModal.value = true
}

function closeAccountModal(force = false) {
  if (isSavingAccount.value && !force) {
    return
  }
  showAccountModal.value = false
  editingAccount.value = null
  accountContactId.value = ''
  accountRemark.value = ''
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

async function loadDebtPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看债务账户'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList, typeList, contactList, summaryData, recordList] =
      await Promise.all([
        getAccounts({ userId: currentUser.id, status: 'active' }),
        getAccountTypes({ status: 'active' }),
        getContacts({ userId: currentUser.id, status: 'active' }),
        getDebtAccountSummary(currentUser.id),
        getDebtRecords({ userId: currentUser.id }),
      ])

    accounts.value = accountList.filter((account) =>
      DEBT_ACCOUNT_CODES.has(account.accountTypeCode ?? ''),
    )
    accountTypes.value = typeList
    contacts.value = contactList
    summary.value = summaryData
    debtRecords.value = recordList
  } catch (error) {
    pageError.value = getErrorMessage(error, '债务账户加载失败')
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
    : debtAccountType.value
  const normalizedContactId = accountContactId.value
    ? Number(accountContactId.value)
    : null
  const contactName = normalizedContactId
    ? (contactMap.value.get(normalizedContactId)?.name?.trim() ?? '')
    : ''

  if (!currentUser) {
    accountFormError.value = '债务账户信息不完整'
    return
  }

  if (normalizedContactId === null || !contactName) {
    accountFormError.value = '请选择有效的联系人'
    return
  }

  if (!targetAccountType) {
    accountFormError.value = '债务账户类型不存在'
    return
  }

  isSavingAccount.value = true
  accountFormError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      accountTypeId: targetAccountType.id,
      contactId: normalizedContactId,
      name: contactName,
      icon: targetAccountType.code ?? DEFAULT_DEBT_ACCOUNT_CODE,
      currencyCode: account?.currencyCode || 'CNY',
      currentBalance: 0,
      includeInNetWorth: includeInNetWorth.value,
      status: 'active',
      remark: accountRemark.value.trim() || null,
    }

    if (account) {
      await updateAccount(account.id, payload)
      showFeedback('债务账户已更新', 'success')
    } else {
      await createAccount(payload)
      showFeedback('债务账户已新增', 'success')
    }
    closeAccountModal(true)
    await loadDebtPage()
  } catch (error) {
    const message = getErrorMessage(error, '债务账户保存失败')
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
    showFeedback('债务账户已删除', 'success')
    await loadDebtPage()
  } catch (error) {
    const message = getErrorMessage(error, '删除失败')
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function sumDebtBalance(
  records: Array<{
    direction: 'payable' | 'receivable'
    recordType?: 'borrow' | 'repayment'
    amount: number
  }>,
) {
  return records.reduce(
    (total, record) => total + getDebtRecordBalanceDelta(record),
    0,
  )
}

function getDebtRecordBalanceDelta(record: {
  direction: 'payable' | 'receivable'
  recordType?: 'borrow' | 'repayment'
  amount: number
}) {
  const amount = Number(record.amount ?? 0)
  if (!Number.isFinite(amount) || amount <= 0) {
    return 0
  }
  if (record.direction === 'receivable') {
    return record.recordType === 'repayment' ? -amount : amount
  }
  return record.recordType === 'repayment' ? amount : -amount
}

function formatDebtStatusText(amount: number) {
  if (amount > 0) {
    return '待收'
  }
  if (amount < 0) {
    return '待还'
  }
  return '已结清'
}

function formatDebtStatusClass(amount: number): DebtCardView['netTagClass'] {
  if (amount > 0) {
    return 'is-positive'
  }
  if (amount < 0) {
    return 'is-negative'
  }
  return 'is-settled'
}

// 收=红、支=绿：待收（对方欠我）用 danger，待还用 success。
function statusTagType(amount: number): 'danger' | 'success' | 'info' {
  if (amount > 0) {
    return 'danger'
  }
  if (amount < 0) {
    return 'success'
  }
  return 'info'
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

function formatSignedCurrency(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}${formatCurrency(value)}`
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

type DesktopRow = (typeof debtCards.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace stagger-in">
    <PageHeader title="债权账户">
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
    <DesktopRecordList
      title="债权账户列表"
      searchable
      search-placeholder="联系人 / 备注"
      :search-keys="['displayName', 'noteText']"
      :rows="debtCards"
      :loading="isLoading"
      row-key="account.id"
      empty-title="没有债权账户"
      empty-hint="点击右上角新增第一个债权账户"
    >
      <template #default="{ row }: { row: DesktopRow }">
        <article class="record-card debt-account-card" :class="row.netTagClass">
          <div class="record-card-head">
            <div class="record-card-title">
              <span class="debt-account-avatar" aria-hidden="true">{{
                row.displayName.slice(0, 1)
              }}</span>
              <span>{{ row.displayName }}</span>
            </div>
            <div class="record-card-aside">
              <el-tag size="small" effect="light" :type="statusTagType(row.netAmount)">
                {{ row.netTagText }}
              </el-tag>
            </div>
          </div>
          <div class="debt-account-net">
            <span class="debt-account-net-label">往来净额</span>
            <strong class="debt-account-net-value">{{ row.netAmountText }}</strong>
          </div>
          <div class="debt-account-progress">
            <div class="debt-account-progress-meta">
              <span>{{ row.progressLabel }}</span>
              <span>{{ row.returnedTotalText }} / {{ row.lentTotalText }}</span>
            </div>
            <el-progress
              :percentage="row.progressPercent"
              :show-text="false"
              :stroke-width="8"
              :status="row.netAmount === 0 ? 'success' : undefined"
            />
          </div>
          <p v-if="row.noteText" class="record-card-note">{{ row.noteText }}</p>
          <div class="record-card-foot">
            <el-button
              link
              type="primary"
              @click="openDebtDetail(row.account.id)"
            >
              明细
            </el-button>
            <div class="pc-row-actions">
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
          </div>
        </article>
      </template>
    </DesktopRecordList>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal v-model="showAccountModal" :title="accountModalTitle">
      <div class="pc-form-grid debt-form">
        <CommonSelect
          :model-value="accountContactId"
          label="关联联系人"
          :options="contactOptions"
          @update:model-value="accountContactId = $event"
        />
        <p v-if="accountContactId" class="debt-form-tip">
          账户名称将使用联系人姓名：{{
            contactMap.get(Number(accountContactId))?.name ?? ''
          }}
        </p>
        <CommonInput
          v-model="accountRemark"
          label="备注"
          placeholder="输入账户说明"
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
      title="删除债务账户"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="debt-delete-message">
        删除后该联系人的债务记录会一并移除，确认继续吗？
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
<style scoped>
/* 债权账户卡：方向色 + 大数字净额 + 回款/还款进度条。收=红、支=绿。 */
.debt-account-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 50%;
  font-size: 14px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.debt-account-net {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.debt-account-net-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.debt-account-net-value {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.debt-account-card.is-positive .debt-account-net-value {
  color: var(--el-color-danger);
}
.debt-account-card.is-negative .debt-account-net-value {
  color: var(--el-color-success);
}
.debt-account-card.is-settled .debt-account-net-value {
  color: var(--el-text-color-secondary);
}
.debt-account-progress {
  display: grid;
  gap: 6px;
}
.debt-account-progress-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
</style>
