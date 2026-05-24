<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  createDebtRecord,
  deleteAccount,
  deleteDebtRecord,
  getAccount,
  getAccounts,
  getDebtAccountSummary,
  getDebtRecords,
  updateDebtRecord,
  type Account,
  type DebtAccountSummary,
  type DebtDirection,
  type DebtRecord,
} from '@/api/modules/finance'
import { getContacts, type Contact } from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

const DIRECTION_OPTIONS = [
  { label: '借入', value: 'payable' },
  { label: '借出', value: 'receivable' },
]

const route = useRoute()
const router = useRouter()

const account = ref<Account | null>(null)
const contacts = ref<Contact[]>([])
const cashAccounts = ref<Account[]>([])
const summary = ref<DebtAccountSummary>({
  netAmount: 0,
  payableTotal: 0,
  receivableTotal: 0,
  accountCount: 0,
  recordCount: 0,
})
const records = ref<DebtRecord[]>([])
const isLoading = ref(false)
const pageError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showRecordModal = ref(false)
const showDeleteModal = ref(false)
const showAccountDeleteModal = ref(false)
const isSavingRecord = ref(false)
const isDeleting = ref(false)
const isDeletingAccount = ref(false)
const recordFormError = ref('')
const deleteError = ref('')
const accountDeleteError = ref('')
const editingRecord = ref<DebtRecord | null>(null)
const deletingRecord = ref<DebtRecord | null>(null)
const recordFundingAccountId = ref('')
const recordDirection = ref<DebtDirection>('payable')
const recordAmount = ref('')
const recordOccurredAt = ref('')
const recordRemark = ref('')
let requestVersion = 0

const accountId = computed(() => Number(route.params.accountId))
const contactMap = computed(() => new Map(contacts.value.map((contact) => [contact.id, contact])))
const relatedContact = computed(() => {
  const contactId = account.value?.contactId
  return contactId ? contactMap.value.get(contactId) ?? null : null
})
const detailName = computed(() => relatedContact.value?.name?.trim() || account.value?.name || '债务账户')
const detailSubtitle = computed(() => {
  if (relatedContact.value?.phone?.trim()) {
    return `手机号 ${relatedContact.value.phone.trim()}`
  }
  return '未填写联系人手机号'
})
const detailNote = computed(() => account.value?.remark?.trim() || relatedContact.value?.remark?.trim() || '')
const summaryAmountText = computed(() => formatSignedCurrency(Number(account.value?.currentBalance ?? 0)))
const summarySubText = computed(() => `待还 ${formatCurrency(summary.value.payableTotal)} · 待收 ${formatCurrency(summary.value.receivableTotal)}`)
const payableCount = computed(() => records.value.filter((record) => record.direction === 'payable').length)
const receivableCount = computed(() => records.value.filter((record) => record.direction === 'receivable').length)
const latestRecordText = computed(() => records.value[0] ? formatDate(records.value[0].occurredAt) : '暂无更新')
const recordModalTitle = computed(() => editingRecord.value ? '修改债务记录' : '新增债务记录')
const fundingAccountOptions = computed(() => [
  {
    label: '不关联现金账户',
    value: '',
  },
  ...cashAccounts.value.map((cashAccount) => ({
    label: `${cashAccount.name}（余额 ${formatCurrency(Number(cashAccount.currentBalance ?? 0))}）`,
    value: String(cashAccount.id),
  })),
])

watch(accountId, () => {
  void loadDetail()
}, { immediate: true })

async function loadDetail() {
  const currentRequestVersion = ++requestVersion
  const currentUser = getStoredCurrentUser()

  account.value = null
  records.value = []
  contacts.value = []
  cashAccounts.value = []
  pageError.value = ''

  if (!currentUser) {
    pageError.value = '请先登录后查看债务详情'
    return
  }

  if (!Number.isFinite(accountId.value) || accountId.value <= 0) {
    pageError.value = '债务账户不存在'
    return
  }

  isLoading.value = true

  try {
    const [accountDetail, summaryData, recordList, contactList, accountList] = await Promise.all([
      getAccount(accountId.value),
      getDebtAccountSummary(currentUser.id, accountId.value),
      getDebtRecords({ userId: currentUser.id, accountId: accountId.value }),
      getContacts({ userId: currentUser.id, status: 'active' }),
      getAccounts({ userId: currentUser.id, status: 'active' }),
    ])

    if (currentRequestVersion !== requestVersion) {
      return
    }

    if (!['debt', 'loan_receivable', 'loan_payable'].includes(accountDetail.accountTypeCode ?? '')) {
      pageError.value = '当前账户不是债务账户'
      return
    }

    account.value = accountDetail
    summary.value = summaryData
    records.value = recordList
    contacts.value = contactList
    cashAccounts.value = accountList.filter((item) => item.accountTypeCode === 'cash')
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = error instanceof Error ? error.message : '债务详情加载失败'
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

function openAddRecordModal() {
  if (!account.value) {
    showFeedback('当前债务账户不存在', 'error')
    return
  }
  editingRecord.value = null
  resetRecordForm()
  showRecordModal.value = true
}

function openEditRecordModal(record: DebtRecord) {
  editingRecord.value = record
  recordFundingAccountId.value = record.fundingAccountId ? String(record.fundingAccountId) : ''
  recordDirection.value = record.direction
  recordAmount.value = String(Number(record.amount ?? 0))
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
  recordFundingAccountId.value = ''
  recordDirection.value = 'payable'
  recordAmount.value = ''
  recordOccurredAt.value = toDateTimeLocalValue(new Date().toISOString())
  recordRemark.value = ''
  recordFormError.value = ''
}

function openDeleteModal(record: DebtRecord) {
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
  const fundingAccountId = Number(recordFundingAccountId.value)
  const normalizedFundingAccountId = Number.isFinite(fundingAccountId) && fundingAccountId > 0
    ? fundingAccountId
    : null
  const normalizedAmount = Number(recordAmount.value || '0')
  const isEditing = Boolean(editingRecord.value)

  if (!currentUser || !account.value) {
    recordFormError.value = '债务账户信息不完整'
    return
  }

  if (!recordDirection.value) {
    recordFormError.value = '请选择借入或借出'
    return
  }

  if (!Number.isFinite(normalizedAmount) || normalizedAmount <= 0) {
    recordFormError.value = '请输入有效的债务金额'
    return
  }

  isSavingRecord.value = true
  recordFormError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      accountId: account.value.id,
      fundingAccountId: normalizedFundingAccountId,
      direction: recordDirection.value,
      amount: normalizedAmount,
      currencyCode: account.value.currencyCode || 'CNY',
      remark: recordRemark.value.trim() || null,
      occurredAt: recordOccurredAt.value ? toIsoLocalString(recordOccurredAt.value) : undefined,
    }

    if (editingRecord.value) {
      await updateDebtRecord(editingRecord.value.id, payload)
    } else {
      await createDebtRecord(payload)
    }

    closeRecordModal(true)
    showFeedback(isEditing ? '债务记录已更新' : '债务记录已新增', 'success')
    await loadDetail()
  } catch (error) {
    const message = error instanceof Error ? error.message : '债务记录保存失败'
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
    await deleteDebtRecord(deletingRecord.value.id, currentUser.id)
    closeDeleteModal(true)
    showFeedback('债务记录已删除', 'success')
    await loadDetail()
  } catch (error) {
    const message = error instanceof Error ? error.message : '删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

async function confirmDeleteAccount() {
  const currentUser = getStoredCurrentUser()
  const currentAccount = account.value
  if (!currentUser || !currentAccount) {
    accountDeleteError.value = '债务账户不存在'
    return
  }

  isDeletingAccount.value = true
  accountDeleteError.value = ''

  try {
    await deleteAccount(currentAccount.id)
    closeAccountDeleteModal(true)
    showFeedback('债务账户已删除', 'success')
    await router.push('/finance/accounts/debt')
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

function formatSignedCurrency(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}¥${formatNumber(value)}`
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

function formatRecordDirectionLabel(direction: DebtDirection) {
  return direction === 'receivable' ? '借出' : '借入'
}

function formatRecordAmount(record: DebtRecord) {
  const amountText = formatCurrency(Number(record.amount ?? 0))
  return `${record.direction === 'receivable' ? '+' : '-'}${amountText}`
}

function formatFundingAccountName(record: DebtRecord) {
  return record.fundingAccountName?.trim() || '未关联现金账户'
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
  <section class="debt-account-detail-page" aria-label="债务详情">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="debt-detail-header">
      <PageHeader title="债务详情" back-to="/finance/accounts/debt" back-label="返回债务账户" />
      <button
        type="button"
        class="debt-detail-delete-button"
        :disabled="isDeletingAccount || !account"
        @click="openAccountDeleteModal"
      >
        删除账户
      </button>
    </header>

    <p v-if="pageError" class="debt-detail-message debt-detail-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="debt-detail-summary-card" aria-label="债务详情总览">
        <div class="debt-detail-summary-top">
          <div class="debt-detail-summary-title">
            <strong>{{ detailName }}</strong>
            <span>{{ detailSubtitle }}</span>
          </div>
          <span class="debt-detail-summary-badge">{{ summary.recordCount }} 条记录</span>
        </div>

        <AmountText tag="p" class="debt-detail-summary-amount" :value="summaryAmountText" tone="inherit" />
        <p class="debt-detail-summary-sub">{{ summarySubText }}</p>
        <p v-if="detailNote" class="debt-detail-summary-note">{{ detailNote }}</p>

        <div class="debt-detail-metrics">
          <div class="debt-detail-metric">
            <span>借入笔数</span>
            <strong>{{ payableCount }} 笔</strong>
          </div>
          <div class="debt-detail-metric">
            <span>借出笔数</span>
            <strong>{{ receivableCount }} 笔</strong>
          </div>
          <div class="debt-detail-metric">
            <span>最近更新</span>
            <strong>{{ latestRecordText }}</strong>
          </div>
        </div>
      </section>

      <section class="debt-record-history" aria-label="债务记录">
        <div class="debt-record-history-list">
          <p v-if="records.length === 0" class="debt-record-empty">
            暂无债务记录
          </p>

          <article
            v-for="record in records"
            v-else
            :key="record.id"
            class="debt-record-card"
          >
            <div class="debt-record-card-main">
              <div class="debt-record-card-top">
                <span :class="['debt-record-chip', record.direction === 'receivable' ? 'is-receivable' : 'is-payable']">
                  {{ formatRecordDirectionLabel(record.direction) }}
                </span>
                <span class="debt-record-date">{{ formatDate(record.occurredAt) }}</span>
              </div>
              <p class="debt-record-funding-account">现金账户：{{ formatFundingAccountName(record) }}</p>
              <p class="debt-record-remark">{{ record.remark || '未填写备注' }}</p>
            </div>

            <div class="debt-record-card-side">
              <strong :class="['debt-record-amount', record.direction === 'receivable' ? 'is-positive' : 'is-negative']">
                {{ formatRecordAmount(record) }}
              </strong>
              <div class="debt-record-actions">
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
        aria-label="新增债务记录"
        storage-key="debt-record-create"
        @click="openAddRecordModal"
      />
    </template>

    <CommonModal
      v-model="showRecordModal"
      :title="recordModalTitle"
    >
      <div class="debt-record-form">
        <CommonSelect v-model="recordFundingAccountId" label="现金账户" :options="fundingAccountOptions" />
        <CommonSelect v-model="recordDirection" label="债务方向" :options="DIRECTION_OPTIONS" />
        <CommonInput
          v-model="recordAmount"
          label="债务金额"
          placeholder="输入债务金额"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="recordOccurredAt"
          label="发生时间"
          input-type="datetime-local"
        />
        <CommonInput v-model="recordRemark" label="备注" placeholder="输入借款原因、约定时间等" />
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
            {{ editingRecord ? '保存修改' : '保存记录' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showAccountDeleteModal"
      title="删除债务账户"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="debt-record-delete-message">
        删除后该联系人的债务记录会一并移除，确认继续吗？
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
      title="删除债务记录"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="debt-record-delete-message">
        删除后无法恢复，确认删除这条债务记录吗？
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
