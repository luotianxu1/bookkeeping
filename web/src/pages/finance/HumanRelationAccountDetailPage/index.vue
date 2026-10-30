<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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
  createHumanRelationRecord,
  deleteHumanRelationRecord,
  getAccount,
  getAccounts,
  getHumanRelationAccountSummary,
  getHumanRelationRecords,
  updateHumanRelationRecord,
  type Account,
  type HumanRelationAccountSummary,
  type HumanRelationDirection,
  type HumanRelationRecord,
} from '@/api/modules/finance'
import { getContacts, type Contact } from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

const DIRECTION_OPTIONS = [
  { label: '送出', value: 'outgoing' },
  { label: '收到', value: 'incoming' },
]

const route = useRoute()

const account = ref<Account | null>(null)
const contacts = ref<Contact[]>([])
const cashAccounts = ref<Account[]>([])
const summary = ref<HumanRelationAccountSummary>({
  netAmount: 0,
  outgoingTotal: 0,
  incomingTotal: 0,
  accountCount: 0,
  recordCount: 0,
})
const records = ref<HumanRelationRecord[]>([])
const isLoading = ref(false)
const pageError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showRecordModal = ref(false)
const showDeleteModal = ref(false)
const isSavingRecord = ref(false)
const isDeleting = ref(false)
const recordFormError = ref('')
const deleteError = ref('')
const editingRecord = ref<HumanRelationRecord | null>(null)
const deletingRecord = ref<HumanRelationRecord | null>(null)
const recordFundingAccountId = ref('')
const recordDirection = ref<HumanRelationDirection>('outgoing')
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
const detailName = computed(() => relatedContact.value?.name?.trim() || account.value?.name || '人情账户')
const detailSubtitle = computed(() => {
  if (relatedContact.value?.phone?.trim()) {
    return `手机号 ${relatedContact.value.phone.trim()}`
  }
  return '未填写联系人手机号'
})
const detailNote = computed(() => account.value?.remark?.trim() || relatedContact.value?.remark?.trim() || '')
const summaryAmountText = computed(() => formatSignedCurrency(Number(account.value?.currentBalance ?? 0)))
const summarySubText = computed(() => `累计送出 ${formatCurrency(summary.value.outgoingTotal)} · 累计收到 ${formatCurrency(summary.value.incomingTotal)}`)
const outgoingCount = computed(() => records.value.filter((record) => record.direction === 'outgoing').length)
const incomingCount = computed(() => records.value.filter((record) => record.direction === 'incoming').length)
const latestRecordText = computed(() => records.value[0] ? formatDate(records.value[0].occurredAt) : '暂无更新')
const recordModalTitle = computed(() => editingRecord.value ? '修改人情记录' : '新增人情记录')
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
    pageError.value = '请先登录后查看人情详情'
    return
  }

  if (!Number.isFinite(accountId.value) || accountId.value <= 0) {
    pageError.value = '人情账户不存在'
    return
  }

  isLoading.value = true

  try {
    const [accountDetail, summaryData, recordList, contactList, accountList] = await Promise.all([
      getAccount(accountId.value),
      getHumanRelationAccountSummary(currentUser.id, accountId.value),
      getHumanRelationRecords({ userId: currentUser.id, accountId: accountId.value }),
      getContacts({ userId: currentUser.id, status: 'active' }),
      getAccounts({ userId: currentUser.id, status: 'active' }),
    ])

    if (currentRequestVersion !== requestVersion) {
      return
    }

    if (accountDetail.accountTypeCode !== 'human_relation') {
      pageError.value = '当前账户不是人情账户'
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
    pageError.value = error instanceof Error ? error.message : '人情详情加载失败'
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

function openAddRecordModal() {
  if (!account.value) {
    showFeedback('当前人情账户不存在', 'error')
    return
  }
  editingRecord.value = null
  resetRecordForm()
  showRecordModal.value = true
}

function openEditRecordModal(record: HumanRelationRecord) {
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
  recordDirection.value = 'outgoing'
  recordAmount.value = ''
  recordOccurredAt.value = toDateTimeLocalValue(new Date().toISOString())
  recordRemark.value = ''
  recordFormError.value = ''
}

function openDeleteModal(record: HumanRelationRecord) {
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

async function saveRecord() {
  const currentUser = getStoredCurrentUser()
  const fundingAccountId = Number(recordFundingAccountId.value)
  const normalizedFundingAccountId = Number.isFinite(fundingAccountId) && fundingAccountId > 0
    ? fundingAccountId
    : null
  const normalizedAmount = Number(recordAmount.value || '0')
  const isEditing = Boolean(editingRecord.value)

  if (!currentUser || !account.value) {
    recordFormError.value = '人情账户信息不完整'
    return
  }

  if (!recordDirection.value) {
    recordFormError.value = '请选择送出或收到'
    return
  }

  if (!Number.isFinite(normalizedAmount) || normalizedAmount <= 0) {
    recordFormError.value = '请输入有效的人情金额'
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
      await updateHumanRelationRecord(editingRecord.value.id, payload)
    } else {
      await createHumanRelationRecord(payload)
    }

    closeRecordModal(true)
    showFeedback(isEditing ? '人情记录已更新' : '人情记录已新增', 'success')
    await loadDetail()
  } catch (error) {
    const message = error instanceof Error ? error.message : '人情记录保存失败'
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
    await deleteHumanRelationRecord(deletingRecord.value.id, currentUser.id)
    closeDeleteModal(true)
    showFeedback('人情记录已删除', 'success')
    await loadDetail()
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

function formatRecordDirectionLabel(direction: HumanRelationDirection) {
  return direction === 'incoming' ? '收到' : '送出'
}

function formatRecordAmount(record: HumanRelationRecord) {
  const amountText = formatCurrency(Number(record.amount ?? 0))
  return `${record.direction === 'outgoing' ? '+' : '-'}${amountText}`
}

function formatFundingAccountName(record: HumanRelationRecord) {
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
  <section class="human-relation-account-detail-page" aria-label="人情详情">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="human-relation-detail-header">
      <PageHeader title="人情详情" back-to="/finance/accounts/human-relation" back-label="返回人情账户" />
    </header>

    <p v-if="pageError" class="human-relation-detail-message human-relation-detail-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="human-relation-detail-summary-card" aria-label="人情详情总览">
        <div class="human-relation-detail-summary-top">
          <div class="human-relation-detail-summary-title">
            <strong>{{ detailName }}</strong>
            <span>{{ detailSubtitle }}</span>
          </div>
          <span class="human-relation-detail-summary-badge">{{ summary.recordCount }} 条记录</span>
        </div>

        <AmountText tag="p" class="human-relation-detail-summary-amount" :value="summaryAmountText" tone="inherit" />
        <p class="human-relation-detail-summary-sub">{{ summarySubText }}</p>
        <p v-if="detailNote" class="human-relation-detail-summary-note">{{ detailNote }}</p>

        <div class="human-relation-detail-metrics">
          <div class="human-relation-detail-metric">
            <span>送出笔数</span>
            <strong>{{ outgoingCount }} 笔</strong>
          </div>
          <div class="human-relation-detail-metric">
            <span>收到笔数</span>
            <strong>{{ incomingCount }} 笔</strong>
          </div>
          <div class="human-relation-detail-metric">
            <span>最近更新</span>
            <strong>{{ latestRecordText }}</strong>
          </div>
        </div>
      </section>

      <section class="human-relation-record-history" aria-label="人情记录">
        <div class="human-relation-record-history-list">
          <p v-if="records.length === 0" class="human-relation-record-empty">
            暂无人情记录
          </p>

          <article
            v-for="record in records"
            v-else
            :key="record.id"
            class="human-relation-record-card"
          >
            <div class="human-relation-record-card-main">
              <div class="human-relation-record-card-top">
                <span :class="['human-relation-record-chip', record.direction === 'incoming' ? 'is-incoming' : 'is-outgoing']">
                  {{ formatRecordDirectionLabel(record.direction) }}
                </span>
                <span class="human-relation-record-date">{{ formatDate(record.occurredAt) }}</span>
              </div>
              <p class="human-relation-record-funding-account">现金账户：{{ formatFundingAccountName(record) }}</p>
              <p class="human-relation-record-remark">{{ record.remark || '未填写备注' }}</p>
            </div>

            <div class="human-relation-record-card-side">
              <strong :class="['human-relation-record-amount', record.direction === 'outgoing' ? 'is-positive' : 'is-negative']">
                {{ formatRecordAmount(record) }}
              </strong>
              <div class="human-relation-record-actions">
                <button type="button" class="human-relation-record-action" @click="openEditRecordModal(record)">
                  修改
                </button>
                <button type="button" class="human-relation-record-action is-danger" @click="openDeleteModal(record)">
                  删除
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <FloatingAddButton
        aria-label="新增人情记录"
        storage-key="human-relation-record-create"
        @click="openAddRecordModal"
      />
    </template>

    <CommonModal
      v-model="showRecordModal"
      :title="recordModalTitle"
    >
      <div class="human-relation-record-form">
        <CommonSelect v-model="recordFundingAccountId" label="现金账户" :options="fundingAccountOptions" />
        <CommonSelect v-model="recordDirection" label="人情方向" :options="DIRECTION_OPTIONS" />
        <CommonInput
          v-model="recordAmount"
          label="人情金额"
          placeholder="输入人情金额"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="recordOccurredAt"
          label="发生时间"
          input-type="datetime-local"
        />
        <CommonInput v-model="recordRemark" label="备注" placeholder="输入场景、节日、事项等" />
        <p v-if="recordFormError" class="human-relation-record-form-error">
          {{ recordFormError }}
        </p>
      </div>

      <template #footer>
        <div class="human-relation-record-modal-actions">
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
      v-model="showDeleteModal"
      title="删除人情记录"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="human-relation-record-delete-message">
        删除后无法恢复，确认删除这条人情记录吗？
      </p>
      <p v-if="deleteError" class="human-relation-record-delete-error">
        {{ deleteError }}
      </p>

      <template #footer>
        <div class="human-relation-record-modal-actions">
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
