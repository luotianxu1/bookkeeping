<script setup lang="ts">
// 账户管理页：还原 Pencil「账户管理」画板中的总览、账户分组和新增按钮。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import { createAccount, getAccounts, getAccountTypes, type Account, type AccountType } from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import type { AccountGroup, AccountOverview } from '@/types/account'
import AccountGroupCard from '../components/AccountGroupCard/index.vue'
import AccountOverviewCard from '../components/AccountOverviewCard/index.vue'

const showCreateAccountModal = ref(false)
const accountName = ref('')
const accountRemark = ref('')
const accountType = ref('')
const includeInNetWorth = ref(true)
const accountTypes = ref<AccountType[]>([])
const accounts = ref<Account[]>([])
const isLoadingAccounts = ref(false)
const isLoadingAccountTypes = ref(false)
const isSavingAccount = ref(false)
const accountListError = ref('')
const accountTypeError = ref('')
const accountFormError = ref('')
const successMessage = ref('')
let successMessageTimer: number | undefined

const accountOverview = computed<AccountOverview>(() => ({
  label: '总资产',
  amount: formatAmount(
    accounts.value
      .filter((account) => account.includeInNetWorth && account.status === 'active')
      .reduce((total, account) => total + Number(account.currentBalance), 0),
  ),
}))

const accountGroups = computed<AccountGroup[]>(() => {
  const groupedAccounts = new Map<string, Account[]>()

  accounts.value.forEach((account) => {
    if (account.status !== 'active') {
      return
    }

    const groupTitle = account.accountTypeName ? `${account.accountTypeName}账户` : '其他账户'
    const groupAccounts = groupedAccounts.get(groupTitle) ?? []
    groupAccounts.push(account)
    groupedAccounts.set(groupTitle, groupAccounts)
  })

  return Array.from(groupedAccounts.entries()).map(([title, groupAccounts]) => {
    const firstAccount = groupAccounts[0]

    return {
      title,
      path: firstAccount?.accountTypeCode === 'cash' ? '/finance/accounts/cash' : undefined,
      items: groupAccounts.map((account) => ({
      icon: getAccountIcon(account.icon, account.accountTypeCode),
      name: account.name,
      subtitle: account.remark ?? account.accountTypeName ?? '',
      amount: formatAmount(Number(account.currentBalance)),
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
  }
})

watch(accountType, (nextType) => {
  const selectedType = accountTypes.value.find((type) => type.code === nextType)
  if (selectedType) {
    includeInNetWorth.value = selectedType.includeInNetWorthDefault
  }
})

onMounted(() => {
  loadAccounts()
})

onBeforeUnmount(() => {
  window.clearTimeout(successMessageTimer)
})

function closeCreateAccountModal() {
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

  if (!currentUser) {
    accountFormError.value = '请先登录后再新增账户'
    return
  }

  if (!trimmedName) {
    accountFormError.value = '请输入账户名称'
    return
  }

  if (!selectedAccountType) {
    accountFormError.value = '请选择账户类型'
    return
  }

  isSavingAccount.value = true
  accountFormError.value = ''

  try {
    await createAccount({
      userId: currentUser.id,
      accountTypeId: selectedAccountType.id,
      name: trimmedName,
      icon: selectedAccountType.code,
      currencyCode: 'CNY',
      currentBalance: 0,
      includeInNetWorth: includeInNetWorth.value,
      status: 'active',
      remark: trimmedRemark || null,
    })
    resetCreateAccountForm()
    showCreateAccountModal.value = false
    showSuccessMessage()
    await loadAccounts()
  } catch (error) {
    accountFormError.value = error instanceof Error ? error.message : '账户保存失败'
  } finally {
    isSavingAccount.value = false
  }
}

async function loadAccounts() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    accountListError.value = '请先登录后查看账户'
    return
  }

  isLoadingAccounts.value = true
  accountListError.value = ''

  try {
    accounts.value = await getAccounts({ userId: currentUser.id, status: 'active' })
  } catch (error) {
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

function resetCreateAccountForm() {
  accountName.value = ''
  accountRemark.value = ''
  accountFormError.value = ''
}

function showSuccessMessage() {
  successMessage.value = '新增成功'
  window.clearTimeout(successMessageTimer)
  successMessageTimer = window.setTimeout(() => {
    successMessage.value = ''
  }, 1800)
}

function formatAmount(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
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
    loan_receivable: '↙',
    loan_payable: '↗',
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
    <Transition name="account-toast">
      <div v-if="successMessage" class="account-toast" role="status">
        {{ successMessage }}
      </div>
    </Transition>

    <PageHeader title="账户管理" back-to="/finance" back-label="返回财务首页" />

    <AccountOverviewCard :overview="accountOverview" />

    <p v-if="accountListError" class="account-list-message account-list-message-error">
      {{ accountListError }}
    </p>
    <p v-else-if="isLoadingAccounts" class="account-list-message">
      加载中...
    </p>
    <p v-else-if="accountGroups.length === 0" class="account-list-message">
      暂无账户
    </p>

    <div v-else class="account-groups">
      <AccountGroupCard v-for="group in accountGroups" :key="group.title" :group="group" />
    </div>

    <button
      class="account-fab"
      type="button"
      aria-label="新增账户"
      @click="showCreateAccountModal = true"
    >
      +
    </button>

    <CommonModal
      v-model="showCreateAccountModal"
      title="新增账户"
      :show-close="false"
    >
      <form class="create-account-form" @submit.prevent="saveAccount">
        <CommonInput v-model="accountName" label="账户名称" placeholder="例如：建设银行卡" />
        <CommonSelect
          v-model="accountType"
          label="账户类型"
          :options="accountTypeOptions"
          :disabled="isLoadingAccountTypes || accountTypes.length === 0"
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
