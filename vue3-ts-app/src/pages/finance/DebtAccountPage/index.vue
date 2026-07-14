<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
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
} from '@/api/modules/finance'
import { getContacts, type Contact } from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

type DebtCardView = {
  account: Account
  displayName: string
  avatarText: string
  avatarClass: string
  secondaryText: string
  noteText: string
  netAmount: number
  netAmountText: string
  netTagText: string
  netTagClass: 'is-positive' | 'is-negative' | 'is-settled'
  latestRecordText: string
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
const debtRecords = ref<Array<{
  id: number
  accountId: number
  direction: 'payable' | 'receivable'
  recordType?: 'borrow' | 'repayment'
  amount: number
  occurredAt: string
}>>([])
const summary = ref<DebtAccountSummary>({
  netAmount: 0,
  payableTotal: 0,
  receivableTotal: 0,
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
const accountContactId = ref('')
const accountRemark = ref('')
const includeInNetWorth = ref(true)

const contactMap = computed(() => new Map(contacts.value.map((contact) => [contact.id, contact])))
const debtAccountType = computed(() => accountTypes.value.find((type) => type.code === DEFAULT_DEBT_ACCOUNT_CODE) ?? null)
const accountModalTitle = computed(() => (editingAccount.value ? '修改债务账户' : '新增债务账户'))
const accountSubmitLabel = computed(() => (editingAccount.value ? '保存账户' : '新增账户'))

const contactOptions = computed(() => [
  {
    label: contacts.value.length > 0 ? '请选择联系人' : '暂无可选联系人，请先新增联系人',
    value: '',
    disabled: true,
  },
  ...contacts.value.map((contact) => ({
    label: contact.phone?.trim() ? `${contact.name} · ${contact.phone}` : contact.name,
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
  accounts.value.map((account, index) => {
    const contact = account.contactId ? contactMap.value.get(account.contactId) ?? null : null
    const records = recordsByAccountId.value.get(account.id) ?? []
    const netAmount = sumDebtBalance(records)
    const latestRecord = records[0]

    return {
      account,
      displayName: contact?.name?.trim() || account.name,
      avatarText: (contact?.name?.trim() || account.name || '债').slice(0, 1),
      avatarClass: `debt-avatar-${index % 4}`,
      secondaryText: contact?.phone?.trim() ? `手机号 ${contact.phone.trim()}` : '未填写联系人手机号',
      noteText: contact?.remark?.trim() || account.remark?.trim() || '',
      netAmount,
      netAmountText: formatSignedCurrency(netAmount),
      netTagText: formatDebtStatusText(netAmount),
      netTagClass: formatDebtStatusClass(netAmount),
      latestRecordText: latestRecord ? `最近一笔 ${formatDate(latestRecord.occurredAt)}` : '暂无债务记录',
    }
  }),
)

const summaryAmountText = computed(() => formatSignedCurrency(summary.value.netAmount))

onMounted(() => {
  void loadDebtPage()
})

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

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
  includeInNetWorth.value = debtAccountType.value?.includeInNetWorthDefault ?? true
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
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList, typeList, contactList, summaryData, recordList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }),
      getAccountTypes({ status: 'active' }),
      getContacts({ userId: currentUser.id, status: 'active' }),
      getDebtAccountSummary(currentUser.id),
      getDebtRecords({ userId: currentUser.id }),
    ])

    accounts.value = accountList.filter((account) => DEBT_ACCOUNT_CODES.has(account.accountTypeCode ?? ''))
    accountTypes.value = typeList
    contacts.value = contactList
    summary.value = summaryData
    debtRecords.value = recordList
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '债务账户加载失败'
  } finally {
    isLoading.value = false
  }
}

async function saveAccount() {
  const currentUser = getStoredCurrentUser()
  const account = editingAccount.value
  const targetAccountType = account ? accountTypes.value.find((type) => type.id === account.accountTypeId) ?? null : debtAccountType.value
  const normalizedContactId = accountContactId.value ? Number(accountContactId.value) : null
  const contactName = normalizedContactId ? contactMap.value.get(normalizedContactId)?.name?.trim() ?? '' : ''

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
    const message = error instanceof Error ? error.message : '债务账户保存失败'
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
    const message = error instanceof Error ? error.message : '删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function sumDebtBalance(records: Array<{ direction: 'payable' | 'receivable'; recordType?: 'borrow' | 'repayment'; amount: number }>) {
  return records.reduce((total, record) => total + getDebtRecordBalanceDelta(record), 0)
}

function getDebtRecordBalanceDelta(record: { direction: 'payable' | 'receivable'; recordType?: 'borrow' | 'repayment'; amount: number }) {
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
</script>

<template>
  <section class="debt-account-page" aria-label="债务账户">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="debt-account-header">
      <PageHeader title="债务账户" back-to="/finance/accounts" back-label="返回账户管理">
        <template #right>
          <CommonHeaderActionButton
            :label="isManageMode ? '完成管理' : '管理债务账户'"
            @click="toggleManageMode"
          >
            <svg v-if="isManageMode" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M20 6L9 17L4 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 15.2A3.2 3.2 0 1 0 12 8.8A3.2 3.2 0 0 0 12 15.2Z" stroke="currentColor" stroke-width="1.8" />
              <path d="M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 1 1 16.96 19.71L16.9 19.65A1.65 1.65 0 0 0 15.08 19.32A1.65 1.65 0 0 0 14.08 20.83V21A2 2 0 1 1 10.08 21V20.91A1.65 1.65 0 0 0 9 19.4A1.65 1.65 0 0 0 7.18 19.73L7.12 19.79A2 2 0 1 1 4.29 16.96L4.35 16.9A1.65 1.65 0 0 0 4.68 15.08A1.65 1.65 0 0 0 3.17 14.08H3A2 2 0 1 1 3 10.08H3.09A1.65 1.65 0 0 0 4.6 9A1.65 1.65 0 0 0 4.27 7.18L4.21 7.12A2 2 0 1 1 7.04 4.29L7.1 4.35A1.65 1.65 0 0 0 8.92 4.68H9A1.65 1.65 0 0 0 10 3.17V3A2 2 0 1 1 14 3V3.09A1.65 1.65 0 0 0 15 4.6A1.65 1.65 0 0 0 16.82 4.27L16.88 4.21A2 2 0 1 1 19.71 7.04L19.65 7.1A1.65 1.65 0 0 0 19.32 8.92V9A1.65 1.65 0 0 0 20.83 10H21A2 2 0 1 1 21 14H20.91A1.65 1.65 0 0 0 19.4 15Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </CommonHeaderActionButton>
        </template>
      </PageHeader>
    </header>

    <p v-if="pageError" class="debt-page-message debt-page-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="debt-summary-card" aria-label="债务汇总">
        <div class="debt-summary-top">
          <div class="debt-summary-title">
            <span>债务往来净额</span>
          </div>
          <span class="debt-summary-badge">{{ summary.accountCount }} 个账户</span>
        </div>

        <AmountText tag="strong" class="debt-summary-amount" :value="summaryAmountText" tone="inherit" />

        <div class="debt-summary-metrics">
          <div class="debt-summary-metric">
            <span>待收</span>
            <strong>{{ formatCurrency(summary.receivableTotal) }}</strong>
          </div>
          <div class="debt-summary-metric">
            <span>待还</span>
            <strong>{{ formatCurrency(summary.payableTotal) }}</strong>
          </div>
          <div class="debt-summary-metric">
            <span>记录数</span>
            <strong>{{ summary.recordCount }} 条</strong>
          </div>
        </div>
      </section>

      <section class="debt-account-list" aria-label="债务账户列表">
        <p v-if="debtCards.length === 0" class="debt-empty">
          请先在账户管理中新增债务账户
        </p>

        <article
          v-for="card in debtCards"
          v-else
          :key="card.account.id"
          :class="['debt-account-card', { 'is-manage-mode': isManageMode }]"
          :role="isManageMode ? undefined : 'button'"
          :tabindex="isManageMode ? -1 : 0"
          @click="openDebtDetail(card.account.id)"
        >
          <div class="debt-account-card-top">
            <div class="debt-account-card-person">
              <span class="debt-account-avatar" :class="card.avatarClass">
                {{ card.avatarText }}
              </span>
              <div class="debt-account-card-text">
                <div class="debt-account-card-name-row">
                  <strong>{{ card.displayName }}</strong>
                  <span class="debt-account-card-phone">{{ card.secondaryText }}</span>
                </div>
                <p v-if="card.noteText">{{ card.noteText }}</p>
              </div>
            </div>

            <div class="debt-account-card-side">
              <AmountText tag="strong" class="debt-account-card-amount" :value="card.netAmountText" tone="inherit" />
              <span class="debt-account-card-tag" :class="card.netTagClass">{{ card.netTagText }}</span>
            </div>
          </div>

          <div class="debt-account-card-bottom">
            <span>{{ card.latestRecordText }}</span>
            <div v-if="isManageMode" class="debt-account-card-actions">
              <button type="button" class="debt-card-action" @click.stop="openEditAccountModal(card.account)">
                编辑
              </button>
              <button type="button" class="debt-card-action is-danger" @click.stop="openDeleteModal(card.account)">
                删除
              </button>
            </div>
            <span v-else class="debt-account-card-link">查看明细</span>
          </div>
        </article>
      </section>
    </template>

    <FloatingAddButton aria-label="新增债务账户" storage-key="debt-account-page" @click="openCreateAccountModal" />

    <CommonModal
      v-model="showAccountModal"
      :title="accountModalTitle"
    >
      <div class="debt-form">
        <CommonSelect
          :model-value="accountContactId"
          label="关联联系人"
          :options="contactOptions"
          @update:model-value="accountContactId = $event"
        />
        <p v-if="accountContactId" class="debt-form-tip">
          账户名称将使用联系人姓名：{{ contactMap.get(Number(accountContactId))?.name ?? '' }}
        </p>
        <CommonInput v-model="accountRemark" label="备注" placeholder="输入账户说明" />
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
      title="删除债务账户"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="debt-delete-message">
        删除后该联系人的债务记录会一并移除，确认继续吗？
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
