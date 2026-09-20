<script setup lang="ts">
// 现金账户页：支持管理模式切换与新增账户弹窗。
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
import AccountIcon from '@/components/common/AccountIcon/index.vue'
import { createAccount, deleteAccount, getAccounts, getAccountTypes, updateAccount, type Account, type AccountType } from '@/api/modules/finance'
import { cashAccountIconOptions, DEFAULT_CASH_ACCOUNT_ICON, resolveCashAccountIcon } from '@/data/account-icons'
import { getStoredCurrentUser } from '@/utils/current-user'

const isManageMode = ref(false)
const showCreateAccountModal = ref(false)
const showDeleteConfirmModal = ref(false)
const editingAccountId = ref<number | null>(null)
const deletingAccount = ref<Account | null>(null)
const accountItems = ref<Account[]>([])
const cashAccountType = ref<AccountType | null>(null)
const isLoadingAccounts = ref(false)
const isSavingAccount = ref(false)
const isDeletingAccount = ref(false)
const accountListError = ref('')
const accountFormError = ref('')
const deleteError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const router = useRouter()

const formName = ref('')
const formIcon = ref(DEFAULT_CASH_ACCOUNT_ICON)
const formAmount = ref('')
const formRemark = ref('')
const setAsCommon = ref(true)

const accountModalTitle = computed(() => (editingAccountId.value ? '修改现金账户' : '新增现金账户'))
const accountAmountLabel = computed(() => (editingAccountId.value ? '当前余额' : '初始金额'))

const computedOverviewAmount = computed(() => {
  const total = accountItems.value
    .filter((account) => account.includeInNetWorth)
    .reduce((sum, account) => sum + Number(account.currentBalance), 0)

  return formatAmount(total)
})

onMounted(() => {
  loadCashAccounts()
})

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function openCreateModal() {
  editingAccountId.value = null
  resetForm()
  showCreateAccountModal.value = true
}

function closeCreateModal() {
  showCreateAccountModal.value = false
  editingAccountId.value = null
  resetForm()
}

function resetForm() {
  formName.value = ''
  formIcon.value = DEFAULT_CASH_ACCOUNT_ICON
  formAmount.value = ''
  formRemark.value = ''
  setAsCommon.value = true
  accountFormError.value = ''
}

function openEditModal(account: Account) {
  editingAccountId.value = account.id
  formName.value = account.name
  formIcon.value = resolveCashAccountIcon(account.icon)
  formAmount.value = String(account.currentBalance ?? 0)
  formRemark.value = account.remark ?? ''
  setAsCommon.value = account.includeInNetWorth
  accountFormError.value = ''
  showCreateAccountModal.value = true
}

async function saveCashAccount() {
  if (isSavingAccount.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const trimmedName = formName.value.trim()
  const trimmedRemark = formRemark.value.trim()

  if (!currentUser) {
    accountFormError.value = '请先登录后再新增账户'
    return
  }

  if (!cashAccountType.value) {
    accountFormError.value = '现金账户类型加载失败'
    return
  }

  if (!trimmedName) {
    accountFormError.value = '请输入账户名称'
    return
  }

  const numericAmount = Number(formAmount.value || '0')
  const normalizedAmount = editingAccountId.value && Number.isFinite(numericAmount) ? numericAmount : 0
  isSavingAccount.value = true
  accountFormError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      accountTypeId: cashAccountType.value.id,
      name: trimmedName,
      icon: formIcon.value,
      currencyCode: 'CNY',
      currentBalance: normalizedAmount,
      includeInNetWorth: setAsCommon.value,
      status: 'active',
      remark: trimmedRemark || null,
    }

    if (editingAccountId.value) {
      await updateAccount(editingAccountId.value, payload)
      showFeedback('修改成功', 'success')
    } else {
      await createAccount(payload)
      showFeedback('新增成功', 'success')
    }

    closeCreateModal()
    await loadCashAccounts()
  } catch (error) {
    const message = error instanceof Error ? error.message : '账户保存失败'
    accountFormError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingAccount.value = false
  }
}

async function removeAccount(id: number) {
  if (isDeletingAccount.value) {
    return
  }

  try {
    isDeletingAccount.value = true
    deleteError.value = ''
    await deleteAccount(id)
    closeDeleteConfirmModal()
    showFeedback('删除成功', 'success')
    await loadCashAccounts()
  } catch (error) {
    const message = error instanceof Error ? error.message : '账户删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeletingAccount.value = false
  }
}

function openDeleteConfirmModal(account: Account) {
  deletingAccount.value = account
  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function closeDeleteConfirmModal() {
  showDeleteConfirmModal.value = false
  deletingAccount.value = null
  deleteError.value = ''
}

function confirmDeleteAccount() {
  if (deletingAccount.value) {
    removeAccount(deletingAccount.value.id)
  }
}

function handleAccountClick(account: Account) {
  if (isManageMode.value) {
    return
  }

  openDetail(toAccountItem(account).path)
}

async function loadCashAccounts() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    accountListError.value = '请先登录后查看账户'
    return
  }

  isLoadingAccounts.value = true
  accountListError.value = ''

  try {
    const types = await getAccountTypes({ status: 'active' })
    cashAccountType.value = types.find((type) => type.code === 'cash') ?? null

    if (!cashAccountType.value) {
      accountItems.value = []
      accountListError.value = '现金账户类型不存在'
      return
    }

    accountItems.value = await getAccounts({
      userId: currentUser.id,
      accountTypeId: cashAccountType.value.id,
      status: 'active',
    })
  } catch (error) {
    accountListError.value = error instanceof Error ? error.message : '现金账户加载失败'
  } finally {
    isLoadingAccounts.value = false
  }
}

function toAccountItem(account: Account) {
  return {
    id: account.id,
    name: account.name,
    subtitle: account.remark || account.accountTypeName || '现金账户',
    amount: formatAmount(Number(account.currentBalance)),
    path: `/finance/accounts/cash/${account.id}`,
  }
}

function openDetail(path?: string) {
  if (!path || isManageMode.value) return
  router.push(path)
}

function formatAmount(value: number) {
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}
</script>

<template>
  <section class="cash-account-page" aria-label="现金账户">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="cash-account-header">
      <PageHeader title="现金账户" back-to="/finance/accounts" back-label="返回账户管理">
        <template #right>
          <CommonHeaderActionButton
            :label="isManageMode ? '完成管理' : '管理现金账户'"
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

    <section class="cash-overview-card" aria-label="现金账户总额">
      <p>现金账户总额</p>
      <AmountText tag="strong" :value="computedOverviewAmount" />
    </section>

    <section class="cash-list" aria-label="现金账户列表">
      <p v-if="accountListError" class="cash-list-message cash-list-message-error">
        {{ accountListError }}
      </p>
      <CommonLoading v-else-if="isLoadingAccounts" />
      <p v-else-if="accountItems.length === 0" class="cash-list-message">暂无现金账户</p>

      <template v-else>
      <article v-for="account in accountItems" :key="account.id" class="cash-list-row">
        <button
          :class="['cash-list-item', { 'is-expanded': isManageMode }]"
          type="button"
          @click="handleAccountClick(account)"
        >
        <span class="cash-item-left">
          <AccountIcon
            :icon="account.icon"
            account-type-code="cash"
            :color="account.color"
            :name="account.name"
          />
          <span class="cash-item-text">
            <span class="cash-item-name">{{ account.name }}</span>
            <span class="cash-item-subtitle">{{ toAccountItem(account).subtitle }}</span>
          </span>
        </span>

        <span class="cash-item-right">
          <AmountText tag="strong" class="cash-item-amount" :value="toAccountItem(account).amount" />
          <svg v-if="!isManageMode" class="cash-item-chevron" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </span>
        </button>

        <div v-if="isManageMode" class="cash-list-actions">
          <button
            type="button"
            class="cash-list-action"
            :aria-label="`修改${account.name}`"
            @click="openEditModal(account)"
          >
            修改
          </button>
          <button
            type="button"
            class="cash-list-action is-danger"
            :disabled="isDeletingAccount && deletingAccount?.id === account.id"
            :aria-label="`删除${account.name}`"
            @click="openDeleteConfirmModal(account)"
          >
            {{ isDeletingAccount && deletingAccount?.id === account.id ? '删除中' : '删除' }}
          </button>
        </div>
      </article>
      </template>
    </section>

    <FloatingAddButton aria-label="新增现金账户" storage-key="cash-account" @click="openCreateModal" />

    <CommonModal v-model="showCreateAccountModal" :title="accountModalTitle">
      <form class="cash-create-form" @submit.prevent="saveCashAccount">
        <CommonInput v-model="formName" label="账户名称" placeholder="例如：日常钱包" />
        <CommonSelect v-model="formIcon" label="账户图标" :options="cashAccountIconOptions" />
        <CommonInput
          v-if="editingAccountId"
          v-model="formAmount"
          :label="accountAmountLabel"
          placeholder="0.00"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput v-model="formRemark" label="备注" placeholder="例如：日常零用" />
        <CommonSwitch v-model="setAsCommon" label="是否计入总资产" />
        <p v-if="accountFormError" class="cash-form-error">{{ accountFormError }}</p>
      </form>

      <template #footer>
        <div class="cash-create-actions">
          <CommonButton variant="secondary" :disabled="isSavingAccount" @click="closeCreateModal">取消</CommonButton>
          <CommonButton variant="primary" :disabled="isSavingAccount" @click="saveCashAccount">
            {{ isSavingAccount ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :show-close="false"
    >
      <p class="cash-delete-message">
        确认删除“{{ deletingAccount?.name }}”吗？
      </p>
      <p v-if="deleteError" class="cash-form-error">{{ deleteError }}</p>

      <template #footer>
        <div class="cash-create-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeletingAccount"
            @click="closeDeleteConfirmModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isDeletingAccount"
            @click="confirmDeleteAccount"
          >
            {{ isDeletingAccount ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
