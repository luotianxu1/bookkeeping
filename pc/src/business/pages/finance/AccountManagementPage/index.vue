<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AmountText from '@/business/components/common/AmountText/index.vue'
import MetricStrip from '@/business/components/MetricStrip.vue'
import AccountIcon from '@/business/components/common/AccountIcon/index.vue'
import { ArrowRight, ChevronDown, ChevronRight, Search } from '@lucide/vue'

import { formatAmount } from '@/business/utils/format'
// 账户管理页：还原 Pencil「账户管理」画板中的总览、账户分组和新增按钮。
import { computed, onMounted, ref, watch } from 'vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'

import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'
import { createAccount, getAccountTypes } from '@/business/api/modules/finance'
import { getContacts, type Contact } from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import {
  allAccountIconOptions,
  DEFAULT_CASH_ACCOUNT_ICON,
  DEFAULT_INVESTMENT_ACCOUNT_ICON,
} from '@/business/data/account-icons'
import type { AccountGroup, AccountOverview } from '@/business/types/account'

import { getErrorMessage } from '@/business/api/request'
import { useFinanceFamilyView } from '@/business/composables/useFinanceFamilyView'
import { buildAccountGroups } from './accountGroup'
import { useCollapsedGroupState } from './useCollapsedGroupState'
import { useAccountSummary } from './useAccountSummary'

const LIABILITY_ACCOUNT_CODES = new Set(['liability'])
const CONTACT_LINKED_ACCOUNT_CODES = new Set(['debt', 'human_relation'])

const showCreateAccountModal = ref(false)
const accountName = ref('')
const accountRemark = ref('')
const accountType = ref('')
const accountIcon = ref(DEFAULT_CASH_ACCOUNT_ICON)
const accountContactId = ref('')
const liabilityLoanTotalAmount = ref('')
const liabilityLoanInterestRate = ref('')
const liabilityLoanTotalPeriods = ref('')
const liabilityLoanRepaymentDay = ref('')
const liabilityLoanStartDate = ref('')
const includeInNetWorth = ref(true)
const contacts = ref<Contact[]>([])
const isLoadingAccountTypes = ref(false)
const isLoadingContacts = ref(false)
const isSavingAccount = ref(false)
const accountTypeError = ref('')
const accountFormError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const {
  familyView,
  familyViewOptions,
  selectedFamilyView,
  canSwitchFamilyView,
  isSelfView,
  isReadOnlyFamilyView,
  selectedViewerUserIds,
  viewerNameByUserId,
  loadFamilyMembers,
} = useFinanceFamilyView()

const {
  accounts,
  accountTypes,
  overviewTotalAssets,
  loadAccounts,
  isLoadingAccounts,
  accountListError,
} = useAccountSummary(selectedViewerUserIds)

const { collapsedGroupState, restoreCollapsedGroupState, toggleAccountGroup } =
  useCollapsedGroupState()

const isDebtAccountTypeSelected = computed(() =>
  CONTACT_LINKED_ACCOUNT_CODES.has(accountType.value),
)
const isCashAccountTypeSelected = computed(() => accountType.value === 'cash')
const isInvestmentAccountTypeSelected = computed(
  () => accountType.value === 'investment',
)
const isLiabilityAccountTypeSelected = computed(() =>
  LIABILITY_ACCOUNT_CODES.has(accountType.value),
)
const contactMap = computed(
  () => new Map(contacts.value.map((contact) => [contact.id, contact])),
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
const familyViewSelectOptions = computed<CommonSelectOption[]>(() =>
  familyViewOptions.value.map((option) => ({
    label: option.label,
    value: option.value,
  })),
)

const accountOverview = computed<AccountOverview>(() => ({
  label:
    selectedFamilyView.value.kind === 'total'
      ? '家庭总资产'
      : selectedFamilyView.value.kind === 'member'
        ? `${selectedFamilyView.value.label}资产`
        : '总资产',
  amount:
    overviewTotalAssets.value === null
      ? '--'
      : formatAmount(overviewTotalAssets.value),
}))

const accountGroups = computed<AccountGroup[]>(() =>
  buildAccountGroups(
    accounts.value,
    accountTypes.value,
    collapsedGroupState.value,
    {
      isSelfView: isSelfView.value,
      isReadOnly: isReadOnlyFamilyView.value,
      viewerNameByUserId: viewerNameByUserId.value,
    },
  ),
)

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
  accountIcon.value =
    nextType === 'cash'
      ? DEFAULT_CASH_ACCOUNT_ICON
      : nextType === 'investment'
        ? DEFAULT_INVESTMENT_ACCOUNT_ICON
        : nextType
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
  const selectedAccountType = accountTypes.value.find(
    (type) => type.code === accountType.value,
  )
  const trimmedName = accountName.value.trim()
  const trimmedRemark = accountRemark.value.trim()
  const normalizedContactId = accountContactId.value
    ? Number(accountContactId.value)
    : null
  const debtContactName = normalizedContactId
    ? (contactMap.value.get(normalizedContactId)?.name?.trim() ?? '')
    : ''
  const normalizedLoanTotalAmount = normalizePositiveAmount(
    liabilityLoanTotalAmount.value,
  )
  const normalizedLoanInterestRate = normalizeNullableAmount(
    liabilityLoanInterestRate.value,
  )
  const normalizedLoanTotalPeriods = normalizePositiveInteger(
    liabilityLoanTotalPeriods.value,
  )
  const normalizedLoanRepaymentDay = normalizePositiveInteger(
    liabilityLoanRepaymentDay.value,
  )
  const normalizedLoanStartDate = liabilityLoanStartDate.value.trim()
  const resolvedName = CONTACT_LINKED_ACCOUNT_CODES.has(
    selectedAccountType?.code ?? '',
  )
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

  if (
    CONTACT_LINKED_ACCOUNT_CODES.has(selectedAccountType.code) &&
    normalizedContactId === null
  ) {
    accountFormError.value = '请选择联系人'
    return
  }

  if (normalizedContactId !== null && !Number.isInteger(normalizedContactId)) {
    accountFormError.value = '请选择有效的联系人'
    return
  }

  if (selectedAccountType.code === 'liability') {
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
  }

  isSavingAccount.value = true
  accountFormError.value = ''

  try {
    await createAccount({
      userId: currentUser.id,
      accountTypeId: selectedAccountType.id,
      contactId: CONTACT_LINKED_ACCOUNT_CODES.has(selectedAccountType.code)
        ? normalizedContactId
        : null,
      name: resolvedName,
      icon:
        selectedAccountType.code === 'cash' ||
        selectedAccountType.code === 'investment'
          ? accountIcon.value
          : selectedAccountType.code,
      currencyCode: 'CNY',
      currentBalance: 0,
      loanTotalAmount:
        selectedAccountType.code === 'liability'
          ? normalizedLoanTotalAmount
          : null,
      loanInterestRate:
        selectedAccountType.code === 'liability'
          ? normalizedLoanInterestRate
          : null,
      loanTotalPeriods:
        selectedAccountType.code === 'liability'
          ? normalizedLoanTotalPeriods
          : null,
      loanRepaymentDay:
        selectedAccountType.code === 'liability'
          ? normalizedLoanRepaymentDay
          : null,
      loanStartDate:
        selectedAccountType.code === 'liability'
          ? normalizedLoanStartDate
          : null,
      includeInNetWorth: includeInNetWorth.value,
      status: 'active',
      remark: trimmedRemark || null,
    })
    resetCreateAccountForm()
    showCreateAccountModal.value = false
    showFeedback('新增成功', 'success')
    await loadAccounts()
  } catch (error) {
    const message = getErrorMessage(error, '账户保存失败')
    accountFormError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingAccount.value = false
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

    const hasSelectedType = types.some(
      (type) => type.code === accountType.value,
    )
    if (!hasSelectedType) {
      accountType.value = types[0]?.code ?? ''
    }
  } catch (error) {
    accountTypeError.value = getErrorMessage(error, '账户类型加载失败')
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
    accountFormError.value = getErrorMessage(error, '联系人加载失败')
  } finally {
    isLoadingContacts.value = false
  }
}

function resetCreateAccountForm() {
  accountName.value = ''
  accountIcon.value = DEFAULT_CASH_ACCOUNT_ICON
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
  const contact = value ? (contactMap.value.get(Number(value)) ?? null) : null
  accountName.value = contact?.name?.trim() ?? ''
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
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

const desktopAccountGroup = ref('')
const desktopAccountKeyword = ref('')

// 账户总览：按类型分区块展示，支持类型筛选与名称搜索。
const visibleAccountGroups = computed(() => {
  const query = desktopAccountKeyword.value.trim().toLocaleLowerCase()
  return accountGroups.value
    .filter(
      (group) =>
        !desktopAccountGroup.value || group.title === desktopAccountGroup.value,
    )
    .map((group) => ({
      ...group,
      items: query
        ? group.items.filter((item) =>
            `${item.name} ${item.subtitle ?? ''}`
              .toLocaleLowerCase()
              .includes(query),
          )
        : group.items,
    }))
    .filter((group) => group.items.length > 0)
})

// 同一分组共享账户类型，取首个账户的类型编码驱动分组图标与主题色。
function accountGroupTypeCode(group: AccountGroup) {
  return group.items[0]?.accountTypeCode ?? ''
}
</script>
<template>
  <section class="desktop-workspace stagger-in">
    <PageHeader title="账户管理">
      <template #right>
        <CommonSelect
          v-if="canSwitchFamilyView"
          v-model="familyView"
          label="家庭视角"
          :options="familyViewSelectOptions"
          aria-label="家庭视角"
        />
        <el-button
          v-if="isSelfView"
          type="primary"
          @click="openCreateAccountModal"
        >
          新增账户
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="accountListError"
      :title="accountListError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[
        { label: '资产总额', value: accountOverview.amount },
        { label: '账户数量', value: accounts.length },
        { label: '资产类型', value: accountGroups.length },
      ]"
    />
    <section
      class="desktop-data-panel account-overview-panel"
      :aria-busy="isLoadingAccounts"
    >
      <header class="desktop-data-toolbar account-overview-toolbar">
        <div class="desktop-data-heading">
          <h2>账户总览</h2>
          <nav class="account-type-filter" aria-label="账户类型筛选">
            <button
              type="button"
              :class="{ 'is-active': !desktopAccountGroup }"
              @click="desktopAccountGroup = ''"
            >
              全部
            </button>
            <button
              v-for="group in accountGroups"
              :key="group.storageKey ?? group.title"
              type="button"
              :class="{ 'is-active': desktopAccountGroup === group.title }"
              @click="desktopAccountGroup = group.title"
            >
              {{ group.title }}
            </button>
          </nav>
        </div>
        <el-input
          v-model="desktopAccountKeyword"
          placeholder="搜索账户"
          aria-label="搜索账户"
          clearable
          class="desktop-table-search"
        >
          <template #prefix><Search :size="15" /></template>
        </el-input>
      </header>
      <el-skeleton v-if="isLoadingAccounts" :rows="5" animated />
      <template v-else>
        <p
          v-if="!visibleAccountGroups.length"
          class="record-list-empty account-overview-empty"
        >
          <strong>{{
            accountGroups.length ? '没有匹配的账户' : '暂无账户'
          }}</strong>
          <span>{{
            accountGroups.length
              ? '换个关键词或类型试试'
              : '点击右上角新增第一个账户'
          }}</span>
        </p>
        <div v-else class="account-type-sections">
          <section
            v-for="group in visibleAccountGroups"
            :key="group.storageKey ?? group.title"
            class="account-type-section"
            :data-code="accountGroupTypeCode(group)"
          >
            <header
              class="account-type-head"
              role="button"
              tabindex="0"
              :aria-expanded="!group.collapsed"
              @click="toggleAccountGroup(group)"
              @keydown.enter.prevent="toggleAccountGroup(group)"
            >
              <div class="account-type-meta">
                <h3>{{ group.title }}</h3>
                <span class="account-type-count">
                  {{ group.items.length }} 个账户
                </span>
              </div>
              <span class="account-type-total">
                <AmountText :value="group.amount || '—'" />
              </span>
              <RouterLink
                v-if="group.path"
                class="account-type-link"
                :to="group.path"
                @click.stop
              >
                <el-button link type="primary">查看账户</el-button>
              </RouterLink>
              <component
                :is="group.collapsed ? ChevronRight : ChevronDown"
                :size="16"
                class="account-type-chevron"
                aria-hidden="true"
              />
            </header>
            <div
              v-show="!group.collapsed"
              class="record-list-grid account-type-grid"
            >
              <article
                v-for="item in group.items"
                :key="item.id ?? item.name"
                class="record-card account-record-card"
              >
                <div class="record-card-head account-record-head">
                  <AccountIcon
                    :icon="item.icon"
                    :account-type-code="accountGroupTypeCode(group)"
                    :color="item.color"
                    :name="item.name"
                    :size="24"
                  />
                  <span class="account-record-name">{{ item.name }}</span>
                  <RouterLink
                    v-if="item.path"
                    class="account-record-jump"
                    :to="item.path"
                    :aria-label="`查看${item.name}`"
                  >
                    <ArrowRight :size="14" />
                  </RouterLink>
                </div>
                <div class="account-record-amount">
                  <AmountText :value="item.amount || '—'" />
                </div>
              </article>
            </div>
          </section>
        </div>
      </template>
    </section>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal
      v-model="showCreateAccountModal"
      title="新增账户"
      :show-close="false"
    >
      <el-form
        class="create-account-form"
        @submit.prevent="saveAccount"
        label-position="top"
      >
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
          v-if="isCashAccountTypeSelected"
          v-model="accountIcon"
          label="账户图标"
          :options="allAccountIconOptions"
        />
        <CommonSelect
          v-if="isInvestmentAccountTypeSelected"
          v-model="accountIcon"
          label="账户图标"
          :options="allAccountIconOptions"
        />
        <CommonSelect
          v-if="isDebtAccountTypeSelected"
          :model-value="accountContactId"
          label="关联联系人"
          :options="contactOptions"
          :disabled="isLoadingContacts"
          @update:model-value="handleDebtContactChange"
        />
        <p
          v-if="isDebtAccountTypeSelected && accountName"
          class="create-account-name-hint"
        >
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
          percentage
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
        <p v-if="accountTypeError" class="create-account-error">
          {{ accountTypeError }}
        </p>
        <CommonInput
          v-model="accountRemark"
          label="备注"
          placeholder="可选，添加账户说明"
        />
        <CommonSwitch v-model="includeInNetWorth" label="是否计入总资产" />
        <p v-if="accountFormError" class="create-account-error">
          {{ accountFormError }}
        </p>
      </el-form>
      <template #footer>
        <div class="pc-toolbar create-account-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSavingAccount"
            @click="closeCreateAccountModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSavingAccount"
            @click="saveAccount"
          >
            {{ isSavingAccount ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped>
.account-overview-panel {
  padding: 16px 18px;
}

/* 类型筛选 chips */
.account-type-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.account-type-filter button {
  padding: 4px 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  transition:
    color 160ms ease-out,
    background-color 160ms ease-out,
    border-color 160ms ease-out;
}
.account-type-filter button:hover {
  color: var(--el-color-primary);
}
.account-type-filter button.is-active {
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

/* 类型分区 */
.account-type-sections {
  display: grid;
  gap: 18px;
  margin-top: 14px;
}
.account-type-section {
  --type-accent: var(--el-color-primary);
  padding: 14px;
  background: var(--el-fill-color-lighter);
  border-radius: 12px;
}
.account-type-section[data-code='cash'] {
  --type-accent: #1d4ed8;
}
.account-type-section[data-code='investment'] {
  --type-accent: #be123c;
}
.account-type-section[data-code='gold'] {
  --type-accent: #b45309;
}
.account-type-section[data-code='term_deposit'] {
  --type-accent: #0f766e;
}
.account-type-section[data-code='liability'] {
  --type-accent: #15803d;
}
.account-type-section[data-code='debt'] {
  --type-accent: #c2410c;
}
.account-type-section[data-code='human_relation'] {
  --type-accent: #7c3aed;
}

.account-type-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 6px;
  cursor: pointer;
  border-radius: 10px;
  transition: background-color 160ms ease-out;
}
.account-type-head:hover {
  background: var(--el-fill-color);
}
.account-type-head:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 2px;
}
.account-type-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.account-type-meta h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
}
.account-type-meta h3::before {
  content: '';
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 7px;
  border-radius: 999px;
  background: var(--type-accent);
  vertical-align: 2px;
}
.account-type-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.account-type-total {
  margin-left: auto;
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.account-type-link {
  flex-shrink: 0;
}
.account-type-chevron {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}
.account-type-grid {
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

/* 紧凑账户卡：第一行 = 图标 + 名称 + 跳转图标，金额独占一行 */
.account-type-grid .record-card {
  gap: 5px;
  padding: 10px 12px;
}
.account-record-head {
  align-items: center;
  gap: 8px;
}
.account-record-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
}
.account-record-amount {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.account-record-jump {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  border-radius: 6px;
  transition:
    color 160ms ease-out,
    background-color 160ms ease-out;
}
.account-record-jump:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.account-overview-empty {
  margin: 0;
}
</style>
