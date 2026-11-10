<script setup lang="ts">
import { formatAmount } from '@/business/utils/format'
// 账户管理页：还原 Pencil「账户管理」画板中的总览、账户分组和新增按钮。
import { computed, onMounted, ref, watch } from 'vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonEmpty from '@/business/components/common/CommonEmpty/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import FloatingAddButton from '@/business/components/common/FloatingAddButton/index.vue'
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
import AccountGroupCard from '../components/AccountGroupCard/index.vue'
import AccountOverviewCard from '../components/AccountOverviewCard/index.vue'
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
const familyViewHint = computed(() => {
  if (!isReadOnlyFamilyView.value) {
    return ''
  }

  return selectedFamilyView.value.kind === 'total'
    ? '当前为家庭总计视角，可查看全家账户汇总。'
    : `当前查看 ${selectedFamilyView.value.label} 的账户数据。`
})

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
</script>

<template>
  <section class="pc-page account-management-page" aria-label="账户管理">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <PageHeader title="账户管理" back-to="/finance" back-label="返回财务首页">
      <template #right>
        <div v-if="canSwitchFamilyView" class="account-family-switch">
          <CommonSelect
            v-model="familyView"
            label="切换家庭成员账户视角"
            :options="familyViewSelectOptions"
          />
        </div>
      </template>
    </PageHeader>
    <AccountOverviewCard :overview="accountOverview" />
    <p v-if="familyViewHint" class="account-view-hint">{{ familyViewHint }}</p>
    <p
      v-if="accountListError"
      class="account-list-message account-list-message-error"
    >
      {{ accountListError }}
    </p>
    <CommonLoading v-else-if="isLoadingAccounts" />
    <CommonEmpty
      v-else-if="accountGroups.length === 0"
      :title="
        selectedFamilyView.kind === 'total' ? '当前家庭暂无账户' : '暂无账户'
      "
      description="添加一个现金或投资账户，开始记录每笔收支。"
      action-text="新增账户"
      @action="openCreateAccountModal()"
    />
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
