<script setup lang="ts">
// 账户管理页：还原 Pencil「账户管理」画板中的总览、账户分组和新增按钮。
import { computed, onMounted, ref, watch } from 'vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import {
  createAccount,
  getAccounts,
  getAccountTypes,
  getFinanceOverview,
  type Account,
  type AccountType,
} from '@/api/modules/finance'
import { getFamilyOverview, type FamilyMember } from '@/api/modules/auth'
import { getContacts, type Contact } from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'
import type { AccountGroup, AccountOverview } from '@/types/account'
import AccountGroupCard from '../components/AccountGroupCard/index.vue'
import AccountOverviewCard from '../components/AccountOverviewCard/index.vue'

const DEBT_ACCOUNT_CODES = new Set(['debt'])
const LIABILITY_ACCOUNT_CODES = new Set(['liability'])
const CONTACT_LINKED_ACCOUNT_CODES = new Set(['debt', 'human_relation'])
const ACCOUNT_GROUP_COLLAPSE_STORAGE_KEY = 'finance-account-management-group-collapse'

type FamilyViewOption = {
  value: string
  label: string
  userId?: number
  kind: 'self' | 'member' | 'total'
}

const showCreateAccountModal = ref(false)
const accountName = ref('')
const accountRemark = ref('')
const accountType = ref('')
const accountContactId = ref('')
const liabilityLoanTotalAmount = ref('')
const liabilityLoanInterestRate = ref('')
const liabilityLoanTotalPeriods = ref('')
const liabilityLoanRepaymentDay = ref('')
const liabilityLoanStartDate = ref('')
const includeInNetWorth = ref(true)
const accountTypes = ref<AccountType[]>([])
const accounts = ref<Account[]>([])
const overviewTotalAssets = ref<number | null>(null)
const contacts = ref<Contact[]>([])
const isLoadingAccounts = ref(false)
const isLoadingAccountTypes = ref(false)
const isLoadingContacts = ref(false)
const isSavingAccount = ref(false)
const accountListError = ref('')
const accountTypeError = ref('')
const accountFormError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const collapsedGroupState = ref<Record<string, boolean>>({})
const familyMembers = ref<FamilyMember[]>([])
const familyView = ref('self')
const isDebtAccountTypeSelected = computed(() => CONTACT_LINKED_ACCOUNT_CODES.has(accountType.value))
const isLiabilityAccountTypeSelected = computed(() => LIABILITY_ACCOUNT_CODES.has(accountType.value))
const contactMap = computed(() => new Map(contacts.value.map((contact) => [contact.id, contact])))
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
const currentUser = computed(() => getStoredCurrentUser())
const familyRoster = computed(() => {
  const roster = new Map<number, { userId: number; displayName: string }>()
  const me = currentUser.value

  if (me) {
    roster.set(me.id, {
      userId: me.id,
      displayName: me.displayName?.trim() || me.username || '我',
    })
  }

  familyMembers.value.forEach((member) => {
    roster.set(member.userId, {
      userId: member.userId,
      displayName: member.displayName?.trim() || `成员${member.userId}`,
    })
  })

  return Array.from(roster.values())
})
const familyViewOptions = computed<FamilyViewOption[]>(() => {
  const me = currentUser.value
  if (!me) {
    return []
  }

  const options: FamilyViewOption[] = [
    { value: 'self', label: '我的数据', userId: me.id, kind: 'self' },
  ]

  if (familyRoster.value.length > 1) {
    options.unshift({ value: 'total', label: '家庭总计', kind: 'total' })
  }

  familyRoster.value
    .filter((member) => member.userId !== me.id)
    .forEach((member) => {
      options.push({
        value: `member-${member.userId}`,
        label: member.displayName,
        userId: member.userId,
        kind: 'member',
      })
    })

  return options
})
const selectedFamilyView = computed<FamilyViewOption>(() =>
  familyViewOptions.value.find((option) => option.value === familyView.value)
  ?? familyViewOptions.value[0]
  ?? { value: 'self', label: '我的数据', kind: 'self' },
)
const canSwitchFamilyView = computed(() => familyViewOptions.value.length > 1)
const isSelfView = computed(() => selectedFamilyView.value.kind === 'self')
const isReadOnlyFamilyView = computed(() => selectedFamilyView.value.kind !== 'self')
const selectedViewerUserIds = computed(() => {
  if (selectedFamilyView.value.kind === 'total') {
    return familyRoster.value.map((member) => member.userId)
  }

  if (selectedFamilyView.value.userId) {
    return [selectedFamilyView.value.userId]
  }

  return currentUser.value ? [currentUser.value.id] : []
})
const viewerNameByUserId = computed(() => new Map(
  familyRoster.value.map((member) => [member.userId, member.displayName]),
))
const familyViewHint = computed(() => {
  if (!isReadOnlyFamilyView.value) {
    return ''
  }

  return selectedFamilyView.value.kind === 'total'
    ? '当前为家庭总计视角，可查看全家账户汇总。'
    : `当前查看 ${selectedFamilyView.value.label} 的账户数据。`
})

const accountOverview = computed<AccountOverview>(() => ({
  label: selectedFamilyView.value.kind === 'total'
    ? '家庭总资产'
    : selectedFamilyView.value.kind === 'member'
      ? `${selectedFamilyView.value.label}资产`
      : '总资产',
  amount: overviewTotalAssets.value === null ? '--' : formatAmount(overviewTotalAssets.value),
}))

const accountGroups = computed<AccountGroup[]>(() => {
  const groupedAccounts = new Map<number, Account[]>()

  accounts.value.forEach((account) => {
    if (account.status !== 'active') {
      return
    }

    const groupAccounts = groupedAccounts.get(account.accountTypeId) ?? []
    groupAccounts.push(account)
    groupedAccounts.set(account.accountTypeId, groupAccounts)
  })

  const typeById = new Map(accountTypes.value.map((type) => [type.id, type]))
  const groupTypeIds = Array.from(groupedAccounts.keys()).sort((leftId, rightId) => {
    const leftType = typeById.get(leftId)
    const rightType = typeById.get(rightId)
    const leftSort = leftType?.sortOrder ?? 0
    const rightSort = rightType?.sortOrder ?? 0
    return (leftSort - rightSort) || (leftId - rightId)
  })

  return groupTypeIds.map((accountTypeId) => {
    const groupAccounts = groupedAccounts.get(accountTypeId) ?? []
    const firstAccount = groupAccounts[0]
    const accountType = typeById.get(accountTypeId)
    const groupCode = accountType?.code ?? firstAccount?.accountTypeCode
    const groupStorageKey = buildGroupStorageKey(accountTypeId, groupCode, accountType?.name ?? firstAccount?.accountTypeName)
    const title = DEBT_ACCOUNT_CODES.has(groupCode ?? '')
      ? '债务账户'
      : LIABILITY_ACCOUNT_CODES.has(groupCode ?? '')
        ? '负债账户'
      : groupCode === 'human_relation'
        ? '人情账户'
      : accountType?.name
        ? `${accountType.name}账户`
        : firstAccount?.accountTypeName
          ? `${firstAccount.accountTypeName}账户`
          : '其他账户'

    return {
      accountTypeId,
      storageKey: groupStorageKey,
      title,
      amount: formatAmount(groupAccounts.reduce((total, account) => total + getSignedBalance(account), 0)),
      path: isSelfView.value && groupCode === 'cash'
        ? '/finance/accounts/cash'
        : isSelfView.value && DEBT_ACCOUNT_CODES.has(groupCode ?? '')
          ? '/finance/accounts/debt'
        : isSelfView.value && LIABILITY_ACCOUNT_CODES.has(groupCode ?? '')
          ? '/finance/accounts/liability'
        : isSelfView.value && groupCode === 'human_relation'
          ? '/finance/accounts/human-relation'
        : isSelfView.value && groupCode === 'gold'
          ? '/finance/accounts/gold'
        : isSelfView.value && groupCode === 'investment'
          ? '/finance/accounts/investment'
        : undefined,
      collapsed: collapsedGroupState.value[groupStorageKey] ?? false,
      items: groupAccounts.map((account) => ({
          id: account.id,
          icon: getAccountIcon(account.icon, account.accountTypeCode),
          name: isReadOnlyFamilyView.value
            ? `${account.name} · ${viewerNameByUserId.value.get(account.userId) ?? `成员${account.userId}`}`
            : account.name,
          subtitle: account.remark ?? account.accountTypeName ?? '',
          amount: formatAmount(getSignedBalance(account)),
          path: isSelfView.value && account.accountTypeCode === 'cash'
            ? `/finance/accounts/cash/${account.id}`
            : isSelfView.value && DEBT_ACCOUNT_CODES.has(account.accountTypeCode ?? '')
              ? `/finance/accounts/debt/${account.id}`
            : isSelfView.value && LIABILITY_ACCOUNT_CODES.has(account.accountTypeCode ?? '')
              ? `/finance/accounts/liability/${account.id}`
              : isSelfView.value && account.accountTypeCode === 'human_relation'
                ? `/finance/accounts/human-relation/${account.id}`
              : isSelfView.value && account.accountTypeCode === 'gold'
                ? `/finance/accounts/gold/position?accountId=${account.id}`
            : isSelfView.value && account.accountTypeCode === 'investment'
              ? `/finance/accounts/investment/${account.id}`
              : undefined,
      })),
    }
  })
})

const accountTypeOptions = computed(() => {
  if (isLoadingAccountTypes.value) {
    return [{ label: '加载中...', value: '', disabled: true }]
  }

  if (accountTypes.value.length === 0) {
    return [{ label: '暂无可用账户类型', value: '', disabled: true }]
  }

  return accountTypes.value.map((type) => ({
    label: type.name,
    value: type.code,
  }))
})

watch(showCreateAccountModal, (visible) => {
  if (visible) {
    loadAccountTypes()
    loadContactsForForm()
  }
})

watch(familyView, () => {
  if (showCreateAccountModal.value) {
    closeCreateAccountModal()
  }
  void loadAccounts()
})

watch(accountType, (nextType) => {
  const selectedType = accountTypes.value.find((type) => type.code === nextType)
  if (selectedType) {
    includeInNetWorth.value = selectedType.includeInNetWorthDefault
  }
  if (CONTACT_LINKED_ACCOUNT_CODES.has(nextType)) {
    return
  }
  if (!LIABILITY_ACCOUNT_CODES.has(nextType)) {
    liabilityLoanTotalAmount.value = ''
    liabilityLoanInterestRate.value = ''
    liabilityLoanTotalPeriods.value = ''
    liabilityLoanRepaymentDay.value = ''
    liabilityLoanStartDate.value = ''
  }
  accountContactId.value = ''
  accountName.value = ''
})

onMounted(() => {
  restoreCollapsedGroupState()
  void initializePage()
})

async function initializePage() {
  await loadFamilyMembers()
  await loadAccounts()
}

async function loadFamilyMembers() {
  const me = currentUser.value
  if (!me) {
    familyMembers.value = []
    familyView.value = 'self'
    return
  }

  try {
    const familyOverview = await getFamilyOverview()
    familyMembers.value = familyOverview.hasFamily ? familyOverview.members : []
  } catch {
    familyMembers.value = []
  }

  const validValues = new Set(familyViewOptions.value.map((option) => option.value))
  if (!validValues.has(familyView.value)) {
    familyView.value = 'self'
  }
}

function openCreateAccountModal() {
  resetCreateAccountForm()
  showCreateAccountModal.value = true
}

function closeCreateAccountModal() {
  resetCreateAccountForm()
  showCreateAccountModal.value = false
}

async function saveAccount() {
  if (isSavingAccount.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const selectedAccountType = accountTypes.value.find((type) => type.code === accountType.value)
  const trimmedName = accountName.value.trim()
  const trimmedRemark = accountRemark.value.trim()
  const normalizedContactId = accountContactId.value ? Number(accountContactId.value) : null
  const debtContactName = normalizedContactId ? contactMap.value.get(normalizedContactId)?.name?.trim() ?? '' : ''
  const normalizedLoanTotalAmount = normalizePositiveAmount(liabilityLoanTotalAmount.value)
  const normalizedLoanInterestRate = normalizeNullableAmount(liabilityLoanInterestRate.value)
  const normalizedLoanTotalPeriods = normalizePositiveInteger(liabilityLoanTotalPeriods.value)
  const normalizedLoanRepaymentDay = normalizePositiveInteger(liabilityLoanRepaymentDay.value)
  const normalizedLoanStartDate = liabilityLoanStartDate.value.trim()
  const resolvedName = CONTACT_LINKED_ACCOUNT_CODES.has(selectedAccountType?.code ?? '')
    ? debtContactName
    : trimmedName

  if (!currentUser) {
    accountFormError.value = '请先登录后再新增账户'
    return
  }

  if (!resolvedName) {
    accountFormError.value = '请输入账户名称'
    return
  }

  if (!selectedAccountType) {
    accountFormError.value = '请选择账户类型'
    return
  }

  if (CONTACT_LINKED_ACCOUNT_CODES.has(selectedAccountType.code) && normalizedContactId === null) {
    accountFormError.value = '请选择联系人'
    return
  }

  if (normalizedContactId !== null && !Number.isInteger(normalizedContactId)) {
    accountFormError.value = '请选择有效的联系人'
    return
  }

  if (selectedAccountType.code === 'liability') {
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
  }

  isSavingAccount.value = true
  accountFormError.value = ''

  try {
    await createAccount({
      userId: currentUser.id,
      accountTypeId: selectedAccountType.id,
      contactId: CONTACT_LINKED_ACCOUNT_CODES.has(selectedAccountType.code) ? normalizedContactId : null,
      name: resolvedName,
      icon: selectedAccountType.code,
      currencyCode: 'CNY',
      currentBalance: 0,
      loanTotalAmount: selectedAccountType.code === 'liability' ? normalizedLoanTotalAmount : null,
      loanInterestRate: selectedAccountType.code === 'liability' ? normalizedLoanInterestRate : null,
      loanTotalPeriods: selectedAccountType.code === 'liability' ? normalizedLoanTotalPeriods : null,
      loanRepaymentDay: selectedAccountType.code === 'liability' ? normalizedLoanRepaymentDay : null,
      loanStartDate: selectedAccountType.code === 'liability' ? normalizedLoanStartDate : null,
      includeInNetWorth: includeInNetWorth.value,
      status: 'active',
      remark: trimmedRemark || null,
    })
    resetCreateAccountForm()
    showCreateAccountModal.value = false
    showFeedback('新增成功', 'success')
    await loadAccounts()
  } catch (error) {
    const message = error instanceof Error ? error.message : '账户保存失败'
    accountFormError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingAccount.value = false
  }
}

async function loadAccounts() {
  const me = currentUser.value
  if (!me) {
    accountListError.value = '请先登录后查看账户'
    accounts.value = []
    overviewTotalAssets.value = null
    return
  }

  isLoadingAccounts.value = true
  accountListError.value = ''

  try {
    const targetUserIds = selectedViewerUserIds.value
    const [accountList, typeList, overviewList] = await Promise.all([
      Promise.all(targetUserIds.map((userId) => getAccounts({ userId, status: 'active' }))),
      getAccountTypes({ status: 'active' }),
      Promise.all(targetUserIds.map((userId) => getFinanceOverview(userId))),
    ])
    accounts.value = accountList.flat()
    accountTypes.value = typeList
    overviewTotalAssets.value = overviewList.reduce((total, item) => total + toNumber(item.totalAssets), 0)
  } catch (error) {
    accounts.value = []
    overviewTotalAssets.value = null
    accountListError.value = error instanceof Error ? error.message : '账户列表加载失败'
  } finally {
    isLoadingAccounts.value = false
  }
}

async function loadAccountTypes() {
  if (isLoadingAccountTypes.value) {
    return
  }

  isLoadingAccountTypes.value = true
  accountTypeError.value = ''

  try {
    const types = await getAccountTypes({ status: 'active' })
    accountTypes.value = types

    const hasSelectedType = types.some((type) => type.code === accountType.value)
    if (!hasSelectedType) {
      accountType.value = types[0]?.code ?? ''
    }
  } catch (error) {
    accountTypeError.value = error instanceof Error ? error.message : '账户类型加载失败'
  } finally {
    isLoadingAccountTypes.value = false
  }
}

async function loadContactsForForm() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || isLoadingContacts.value) {
    return
  }

  isLoadingContacts.value = true

  try {
    contacts.value = await getContacts({
      userId: currentUser.id,
      status: 'active',
    })
  } catch (error) {
    accountFormError.value = error instanceof Error ? error.message : '联系人加载失败'
  } finally {
    isLoadingContacts.value = false
  }
}

function resetCreateAccountForm() {
  accountName.value = ''
  accountRemark.value = ''
  accountContactId.value = ''
  liabilityLoanTotalAmount.value = ''
  liabilityLoanInterestRate.value = ''
  liabilityLoanTotalPeriods.value = ''
  liabilityLoanRepaymentDay.value = ''
  liabilityLoanStartDate.value = ''
  accountFormError.value = ''
}

function handleDebtContactChange(value: string) {
  accountContactId.value = value
  const contact = value ? contactMap.value.get(Number(value)) ?? null : null
  accountName.value = contact?.name?.trim() ?? ''
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function buildGroupStorageKey(accountTypeId?: number, groupCode?: string | null, fallbackName?: string | null) {
  if (typeof accountTypeId === 'number') {
    return `type-${accountTypeId}`
  }
  if (groupCode?.trim()) {
    return `code-${groupCode.trim()}`
  }
  if (fallbackName?.trim()) {
    return `name-${fallbackName.trim()}`
  }
  return 'group-unknown'
}

function getCollapsedGroupStorageId() {
  const currentUser = getStoredCurrentUser()
  return `${ACCOUNT_GROUP_COLLAPSE_STORAGE_KEY}-${currentUser?.id ?? 'guest'}`
}

function restoreCollapsedGroupState() {
  if (typeof window === 'undefined') {
    return
  }
  const raw = window.localStorage.getItem(getCollapsedGroupStorageId())
  if (!raw) {
    collapsedGroupState.value = {}
    return
  }
  try {
    const parsed = JSON.parse(raw) as Record<string, boolean>
    collapsedGroupState.value = Object.entries(parsed).reduce<Record<string, boolean>>((result, [key, value]) => {
      if (typeof value === 'boolean') {
        result[key] = value
      }
      return result
    }, {})
  } catch {
    window.localStorage.removeItem(getCollapsedGroupStorageId())
    collapsedGroupState.value = {}
  }
}

function persistCollapsedGroupState() {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(getCollapsedGroupStorageId(), JSON.stringify(collapsedGroupState.value))
}

function toggleAccountGroup(group: AccountGroup) {
  const groupStorageKey = group.storageKey
  if (!groupStorageKey) {
    return
  }
  collapsedGroupState.value = {
    ...collapsedGroupState.value,
    [groupStorageKey]: !(collapsedGroupState.value[groupStorageKey] ?? false),
  }
  persistCollapsedGroupState()
}

function formatAmount(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function toNumber(value: unknown) {
  const parsed = Number(value ?? 0)
  return Number.isFinite(parsed) ? parsed : 0
}

function normalizePositiveAmount(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return Number.NaN
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

function normalizeNullableAmount(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return 0
  }
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? parsed : Number.NaN
}

function getSignedBalance(account: Account) {
  const rawBalance = Number(account.currentBalance ?? 0)
  if (CONTACT_LINKED_ACCOUNT_CODES.has(account.accountTypeCode ?? '')) {
    return rawBalance
  }
  const accountType = accountTypes.value.find((type) => type.id === account.accountTypeId)
  return accountType?.balanceDirection === 'credit' ? rawBalance * -1 : rawBalance
}

function getAccountIcon(icon?: string | null, accountTypeCode?: string | null) {
  const iconMap: Record<string, string> = {
    wallet: '💵',
    cash: '💵',
    'bank-card': '🏦',
    alipay: '💳',
    'reserve-fund': '🧧',
    investment: '📈',
    fund: '📈',
    gold: '🥇',
    stock: '◉',
    credit_card: '💳',
    debt: '债',
    liability: '负',
    human_relation: '礼',
    other_asset: '资',
    other_liability: '债',
  }

  if (icon && iconMap[icon]) {
    return iconMap[icon]
  }

  if (accountTypeCode && iconMap[accountTypeCode]) {
    return iconMap[accountTypeCode]
  }

  return icon || '账'
}
</script>

<template>
  <section class="account-management-page" aria-label="账户管理">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="账户管理" back-to="/finance" back-label="返回财务首页">
      <template #right>
        <label v-if="canSwitchFamilyView" class="account-family-switch">
          <select v-model="familyView" class="account-family-switch-select" aria-label="切换家庭成员账户视角">
            <option
              v-for="option in familyViewOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </label>
      </template>
    </PageHeader>

    <AccountOverviewCard :overview="accountOverview" />

    <p v-if="familyViewHint" class="account-view-hint">
      {{ familyViewHint }}
    </p>

    <p v-if="accountListError" class="account-list-message account-list-message-error">
      {{ accountListError }}
    </p>
    <CommonLoading v-else-if="isLoadingAccounts" />
    <p v-else-if="accountGroups.length === 0" class="account-list-message">
      {{ selectedFamilyView.kind === 'total' ? '当前家庭暂无账户' : '暂无账户' }}
    </p>

    <div v-else class="account-groups">
      <AccountGroupCard
        v-for="group in accountGroups"
        :key="group.accountTypeId ?? group.title"
        :group="group"
        @toggle="toggleAccountGroup(group)"
      />
    </div>

    <FloatingAddButton
      v-if="isSelfView"
      aria-label="新增账户"
      storage-key="account-management"
      @click="openCreateAccountModal"
    />

    <CommonModal
      v-model="showCreateAccountModal"
      title="新增账户"
      :show-close="false"
    >
      <form class="create-account-form" @submit.prevent="saveAccount">
        <CommonInput
          v-if="!isDebtAccountTypeSelected"
          v-model="accountName"
          label="账户名称"
          placeholder="例如：建设银行卡"
        />
        <CommonSelect
          v-model="accountType"
          label="账户类型"
          :options="accountTypeOptions"
          :disabled="isLoadingAccountTypes || accountTypes.length === 0"
        />
        <CommonSelect
          v-if="isDebtAccountTypeSelected"
          :model-value="accountContactId"
          label="关联联系人"
          :options="contactOptions"
          :disabled="isLoadingContacts"
          @update:model-value="handleDebtContactChange"
        />
        <p v-if="isDebtAccountTypeSelected && accountName" class="create-account-name-hint">
          账户名称将使用联系人姓名：{{ accountName }}
        </p>
        <CommonInput
          v-if="isLiabilityAccountTypeSelected"
          v-model="liabilityLoanTotalAmount"
          label="贷款总额"
          placeholder="例如：680000"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-if="isLiabilityAccountTypeSelected"
          v-model="liabilityLoanInterestRate"
          label="贷款利率（%）"
          placeholder="例如：3.25"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-if="isLiabilityAccountTypeSelected"
          v-model="liabilityLoanTotalPeriods"
          label="贷款总期数"
          placeholder="例如：360"
          input-type="number"
          input-mode="numeric"
        />
        <CommonInput
          v-if="isLiabilityAccountTypeSelected"
          v-model="liabilityLoanRepaymentDay"
          label="每月还款日"
          placeholder="例如：20"
          input-type="number"
          input-mode="numeric"
        />
        <CommonInput
          v-if="isLiabilityAccountTypeSelected"
          v-model="liabilityLoanStartDate"
          label="首期账单日期"
          input-type="date"
        />
        <p v-if="accountTypeError" class="create-account-error">{{ accountTypeError }}</p>
        <CommonInput v-model="accountRemark" label="备注" placeholder="可选，添加账户说明" />
        <CommonSwitch v-model="includeInNetWorth" label="是否计入总资产" />
        <p v-if="accountFormError" class="create-account-error">{{ accountFormError }}</p>
      </form>

      <template #footer>
        <div class="create-account-actions">
          <CommonButton variant="secondary" :disabled="isSavingAccount" @click="closeCreateAccountModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSavingAccount" @click="saveAccount">
            {{ isSavingAccount ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
