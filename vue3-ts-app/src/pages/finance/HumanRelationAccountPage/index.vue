<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
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
  getHumanRelationAccountSummary,
  getHumanRelationRecords,
  updateAccount,
  type Account,
  type AccountType,
  type HumanRelationAccountSummary,
} from '@/api/modules/finance'
import { getContacts, type Contact } from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

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
const records = ref<Array<{
  id: number
  accountId: number
  direction: 'outgoing' | 'incoming'
  amount: number
  occurredAt: string
}>>([])
const summary = ref<HumanRelationAccountSummary>({
  netAmount: 0,
  outgoingTotal: 0,
  incomingTotal: 0,
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
const includeInNetWorth = ref(false)

const contactMap = computed(() => new Map(contacts.value.map((contact) => [contact.id, contact])))
const humanRelationAccountType = computed(() => accountTypes.value.find((type) => type.code === HUMAN_RELATION_ACCOUNT_CODE) ?? null)
const accountModalTitle = computed(() => (editingAccount.value ? '修改人情账户' : '新增人情账户'))
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
    const contact = account.contactId ? contactMap.value.get(account.contactId) ?? null : null
    const accountRecords = recordsByAccountId.value.get(account.id) ?? []
    const outgoingTotal = sumByDirection(accountRecords, 'outgoing')
    const incomingTotal = sumByDirection(accountRecords, 'incoming')
    const netAmount = outgoingTotal - incomingTotal

    return {
      account,
      displayName: contact?.name?.trim() || account.name,
      avatarText: (contact?.name?.trim() || account.name || '礼').slice(0, 1),
      avatarClass: `human-relation-avatar-${index % 4}`,
      secondaryText: contact?.phone?.trim() ? `手机号 ${contact.phone.trim()}` : '未填写联系人手机号',
      noteText: contact?.remark?.trim() || account.remark?.trim() || '',
      outgoingTotal,
      incomingTotal,
      netAmount,
      netAmountText: formatSignedCurrency(netAmount),
      recordCount: accountRecords.length,
    }
  }),
)

const summaryAmountText = computed(() => formatSignedCurrency(summary.value.netAmount))

onMounted(() => {
  void loadHumanRelationPage()
})

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

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
  includeInNetWorth.value = humanRelationAccountType.value?.includeInNetWorthDefault ?? false
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
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList, typeList, contactList, summaryData, recordList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }),
      getAccountTypes({ status: 'active' }),
      getContacts({ userId: currentUser.id, status: 'active' }),
      getHumanRelationAccountSummary(currentUser.id),
      getHumanRelationRecords({ userId: currentUser.id }),
    ])

    accounts.value = accountList.filter((account) => account.accountTypeCode === HUMAN_RELATION_ACCOUNT_CODE)
    accountTypes.value = typeList
    contacts.value = contactList
    summary.value = summaryData
    records.value = recordList
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '人情账户加载失败'
  } finally {
    isLoading.value = false
  }
}

async function saveAccount() {
  const currentUser = getStoredCurrentUser()
  const account = editingAccount.value
  const targetAccountType = account ? accountTypes.value.find((type) => type.id === account.accountTypeId) ?? null : humanRelationAccountType.value
  const normalizedContactId = accountContactId.value ? Number(accountContactId.value) : null
  const contactName = normalizedContactId ? contactMap.value.get(normalizedContactId)?.name?.trim() ?? '' : ''

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
    const message = error instanceof Error ? error.message : '人情账户保存失败'
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
    const message = error instanceof Error ? error.message : '删除失败'
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
</script>

<template>
  <section class="human-relation-account-page" aria-label="人情账户">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="human-relation-account-header">
      <PageHeader title="人情账户" back-to="/finance/accounts" back-label="返回账户管理">
        <template #right>
          <CommonHeaderActionButton
            :label="isManageMode ? '完成管理' : '管理人情账户'"
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

    <p v-if="pageError" class="human-relation-page-message human-relation-page-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="human-relation-summary-card" aria-label="人情汇总">
        <div class="human-relation-summary-top">
          <div class="human-relation-summary-title">
            <span>人情往来结余</span>
          </div>
          <span class="human-relation-summary-badge">{{ summary.accountCount }} 个账户</span>
        </div>

        <AmountText tag="strong" class="human-relation-summary-amount" :value="summaryAmountText" tone="inherit" />

        <div class="human-relation-summary-metrics">
          <div class="human-relation-summary-metric">
            <span>累计送出</span>
            <strong>{{ formatCurrency(summary.outgoingTotal) }}</strong>
          </div>
          <div class="human-relation-summary-metric">
            <span>累计收到</span>
            <strong>{{ formatCurrency(summary.incomingTotal) }}</strong>
          </div>
          <div class="human-relation-summary-metric">
            <span>记录数</span>
            <strong>{{ summary.recordCount }} 条</strong>
          </div>
        </div>
      </section>

      <section class="human-relation-account-list" aria-label="人情账户列表">
        <p v-if="relationCards.length === 0" class="human-relation-empty">
          请先在账户管理中新增人情账户
        </p>

        <article
          v-for="card in relationCards"
          v-else
          :key="card.account.id"
          :class="['human-relation-account-card', { 'is-manage-mode': isManageMode }]"
          :role="isManageMode ? undefined : 'button'"
          :tabindex="isManageMode ? -1 : 0"
          @click="openHumanRelationDetail(card.account.id)"
        >
          <div class="human-relation-account-card-top">
            <div class="human-relation-account-card-person">
              <span class="human-relation-account-avatar" :class="card.avatarClass">
                {{ card.avatarText }}
              </span>
              <div class="human-relation-account-card-text">
                <div class="human-relation-account-card-name-row">
                  <strong>{{ card.displayName }}</strong>
                  <span class="human-relation-account-card-phone">{{ card.secondaryText }}</span>
                </div>
                <p>{{ card.noteText }}</p>
              </div>
            </div>

            <div class="human-relation-account-card-side">
              <AmountText
                tag="strong"
                class="human-relation-account-card-amount"
                :value="card.netAmountText"
                tone="inherit"
              />
              <span :class="['human-relation-account-card-tag', card.netAmount >= 0 ? 'is-positive' : 'is-negative']">
                {{ card.netAmount >= 0 ? '净送出' : '净收到' }}
              </span>
            </div>
          </div>

          <div class="human-relation-account-card-metrics">
            <span>送出 {{ formatCurrency(card.outgoingTotal) }}</span>
            <span>收到 {{ formatCurrency(card.incomingTotal) }}</span>
            <span>{{ card.recordCount }} 条记录</span>
          </div>

          <div v-if="isManageMode" class="human-relation-account-card-actions">
            <button type="button" class="human-relation-card-action" @click.stop="openEditAccountModal(card.account)">
              修改
            </button>
            <button type="button" class="human-relation-card-action is-danger" @click.stop="openDeleteModal(card.account)">
              删除
            </button>
          </div>
        </article>
      </section>
    </template>

    <FloatingAddButton aria-label="新增人情账户" storage-key="human-relation-account-page" @click="openCreateAccountModal" />

    <CommonModal
      v-model="showAccountModal"
      :title="accountModalTitle"
    >
      <div class="human-relation-form">
        <CommonSelect
          :model-value="accountContactId"
          label="关联联系人"
          :options="contactOptions"
          @update:model-value="accountContactId = $event"
        />
        <p v-if="accountContactId" class="human-relation-form-tip">
          账户名称将使用联系人姓名：{{ contactMap.get(Number(accountContactId))?.name ?? '' }}
        </p>
        <CommonInput v-model="accountRemark" label="备注" placeholder="输入账户说明" />
        <CommonSwitch v-model="includeInNetWorth" label="是否计入总资产" />
        <p v-if="accountFormError" class="human-relation-form-error">
          {{ accountFormError }}
        </p>
      </div>

      <template #footer>
        <div class="human-relation-modal-actions">
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
        <div class="human-relation-modal-actions">
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
