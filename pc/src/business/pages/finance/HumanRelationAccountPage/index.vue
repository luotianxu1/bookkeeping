<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonInput from '@/business/components/common/CommonInput/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'

import {
  createAccount,
  deleteAccount,
  getAccounts,
  getAccountTypes,
  getHumanRelationAccountSummary,
  getHumanRelationRecords,
  updateAccount,
  type Account,
  type AccountType,
  type HumanRelationAccountSummary,
} from '@/business/api/modules/finance'
import { getContacts, type Contact } from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

type HumanRelationCardView = {
  account: Account
  displayName: string
  avatarText: string
  avatarClass: string
  secondaryText: string
  noteText: string
  outgoingTotal: number
  incomingTotal: number
  netAmount: number
  netAmountText: string
  recordCount: number
}

type DeleteTarget = {
  id: number
  name: string
}

const HUMAN_RELATION_ACCOUNT_CODE = 'human_relation'

const router = useRouter()
const accounts = ref<Account[]>([])
const contacts = ref<Contact[]>([])
const accountTypes = ref<AccountType[]>([])
const records = ref<
  Array<{
    id: number
    accountId: number
    direction: 'outgoing' | 'incoming'
    amount: number
    occurredAt: string
  }>
>([])
const summary = ref<HumanRelationAccountSummary>({
  netAmount: 0,
  outgoingTotal: 0,
  incomingTotal: 0,
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
const includeInNetWorth = ref(false)

const contactMap = computed(
  () => new Map(contacts.value.map((contact) => [contact.id, contact])),
)
const humanRelationAccountType = computed(
  () =>
    accountTypes.value.find(
      (type) => type.code === HUMAN_RELATION_ACCOUNT_CODE,
    ) ?? null,
)
const accountModalTitle = computed(() =>
  editingAccount.value ? '修改人情账户' : '新增人情账户',
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
  const grouped = new Map<number, typeof records.value>()
  for (const record of records.value) {
    const list = grouped.get(record.accountId) ?? []
    list.push(record)
    grouped.set(record.accountId, list)
  }
  return grouped
})

const relationCards = computed<HumanRelationCardView[]>(() =>
  accounts.value.map((account, index) => {
    const contact = account.contactId
      ? (contactMap.value.get(account.contactId) ?? null)
      : null
    const accountRecords = recordsByAccountId.value.get(account.id) ?? []
    const outgoingTotal = sumByDirection(accountRecords, 'outgoing')
    const incomingTotal = sumByDirection(accountRecords, 'incoming')
    const netAmount = outgoingTotal - incomingTotal

    return {
      account,
      displayName: contact?.name?.trim() || account.name,
      avatarText: (contact?.name?.trim() || account.name || '礼').slice(0, 1),
      avatarClass: `human-relation-avatar-${index % 4}`,
      secondaryText: contact?.phone?.trim()
        ? `手机号 ${contact.phone.trim()}`
        : '未填写联系人手机号',
      noteText: contact?.remark?.trim() || account.remark?.trim() || '',
      outgoingTotal,
      incomingTotal,
      netAmount,
      netAmountText: formatSignedCurrency(netAmount),
      recordCount: accountRecords.length,
    }
  }),
)

onMounted(() => {
  void loadHumanRelationPage()
})

function openHumanRelationDetail(accountId: number) {
  if (isManageMode.value) {
    return
  }
  router.push(`/finance/accounts/human-relation/${accountId}`)
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
    humanRelationAccountType.value?.includeInNetWorthDefault ?? false
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
  includeInNetWorth.value = false
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

async function loadHumanRelationPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看人情账户'
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
        getHumanRelationAccountSummary(currentUser.id),
        getHumanRelationRecords({ userId: currentUser.id }),
      ])

    accounts.value = accountList.filter(
      (account) => account.accountTypeCode === HUMAN_RELATION_ACCOUNT_CODE,
    )
    accountTypes.value = typeList
    contacts.value = contactList
    summary.value = summaryData
    records.value = recordList
  } catch (error) {
    pageError.value = getErrorMessage(error, '人情账户加载失败')
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
    : humanRelationAccountType.value
  const normalizedContactId = accountContactId.value
    ? Number(accountContactId.value)
    : null
  const contactName = normalizedContactId
    ? (contactMap.value.get(normalizedContactId)?.name?.trim() ?? '')
    : ''

  if (!currentUser) {
    accountFormError.value = '人情账户信息不完整'
    return
  }

  if (normalizedContactId === null || !contactName) {
    accountFormError.value = '请选择有效的联系人'
    return
  }

  if (!targetAccountType) {
    accountFormError.value = '人情账户类型不存在'
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
      icon: targetAccountType.code ?? HUMAN_RELATION_ACCOUNT_CODE,
      currencyCode: account?.currencyCode || 'CNY',
      currentBalance: 0,
      includeInNetWorth: includeInNetWorth.value,
      status: 'active',
      remark: accountRemark.value.trim() || null,
    }

    if (account) {
      await updateAccount(account.id, payload)
      showFeedback('人情账户已更新', 'success')
    } else {
      await createAccount(payload)
      showFeedback('人情账户已新增', 'success')
    }
    closeAccountModal(true)
    await loadHumanRelationPage()
  } catch (error) {
    const message = getErrorMessage(error, '人情账户保存失败')
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
    showFeedback('人情账户已删除', 'success')
    await loadHumanRelationPage()
  } catch (error) {
    const message = getErrorMessage(error, '删除失败')
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function sumByDirection(
  accountRecords: Array<{ direction: 'outgoing' | 'incoming'; amount: number }>,
  direction: 'outgoing' | 'incoming',
) {
  return accountRecords
    .filter((record) => record.direction === direction)
    .reduce((total, record) => total + Number(record.amount ?? 0), 0)
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

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

type DesktopRow = (typeof relationCards.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace">
    <PageHeader title="人情往来">
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
    <DesktopTable
      title="往来账户列表"
      searchable
      search-placeholder="联系人 / 备注"
      :rows="relationCards"
      :loading="isLoading"
    >
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="联系人"
        prop="displayName"
        sortable="custom"
        min-width="180"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="累计送出" min-width="140">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatCurrency(row.outgoingTotal) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="累计收到" min-width="140">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatCurrency(row.incomingTotal) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="结余"
        prop="netAmountText"
        sortable="custom"
        min-width="160"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="备注"
        prop="noteText"
        sortable="custom"
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
              @click="openHumanRelationDetail(row.account.id)"
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
      <div class="pc-form-grid human-relation-form">
        <CommonSelect
          :model-value="accountContactId"
          label="关联联系人"
          :options="contactOptions"
          @update:model-value="accountContactId = $event"
        />
        <p v-if="accountContactId" class="human-relation-form-tip">
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
        <p v-if="accountFormError" class="human-relation-form-error">
          {{ accountFormError }}
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar human-relation-modal-actions">
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
      title="删除人情账户"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="human-relation-delete-message">
        删除后该联系人的人情记录会一并移除，确认继续吗？
      </p>
      <p v-if="deleteError" class="human-relation-delete-error">
        {{ deleteError }}
      </p>
      <template #footer>
        <div class="pc-toolbar human-relation-modal-actions">
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
