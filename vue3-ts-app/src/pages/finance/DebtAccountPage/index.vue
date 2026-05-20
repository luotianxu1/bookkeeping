<script setup lang="ts">
// 债务账户页：统一管理债务账户，并展示汇总信息。
import { computed, onMounted, ref } from 'vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  createAccount,
  deleteAccount,
  getAccounts,
  getAccountTypes,
  getDebtAccountSummary,
  updateAccount,
  type Account,
  type AccountType,
  type DebtAccountSummary,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const DEBT_ACCOUNT_CODE = 'debt'
const DEBT_ACCOUNT_CODES = new Set(['debt', 'loan_receivable', 'loan_payable'])

const accounts = ref<Account[]>([])
const accountTypes = ref<AccountType[]>([])
const summary = ref<DebtAccountSummary>({
  totalAmount: 0,
  accountCount: 0,
})
const isLoading = ref(false)
const isSaving = ref(false)
const isDeleting = ref(false)
const pageError = ref('')
const formError = ref('')
const deleteError = ref('')
const showCreateModal = ref(false)
const showDeleteModal = ref(false)
const editingAccount = ref<Account | null>(null)
const deletingAccount = ref<Account | null>(null)
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const formName = ref('')
const formAmount = ref('')
const formRemark = ref('')
const includeInNetWorth = ref(true)

const debtAccountType = computed(() => accountTypes.value.find((type) => type.code === DEBT_ACCOUNT_CODE) ?? null)
const createModalTitle = computed(() => (editingAccount.value ? '修改债务账户' : '新增债务账户'))

onMounted(() => {
  void loadDebtAccounts()
})

function openCreateModal() {
  editingAccount.value = null
  resetForm()
  syncIncludeInNetWorthDefault()
  showCreateModal.value = true
}

function openEditModal(account: Account) {
  editingAccount.value = account
  formName.value = account.name
  formAmount.value = String(Number(account.currentBalance ?? 0))
  formRemark.value = account.remark ?? ''
  includeInNetWorth.value = account.includeInNetWorth
  formError.value = ''
  showCreateModal.value = true
}

function closeCreateModal(force = false) {
  if (isSaving.value && !force) {
    return
  }

  showCreateModal.value = false
  editingAccount.value = null
  resetForm()
}

function resetForm() {
  formName.value = ''
  formAmount.value = ''
  formRemark.value = ''
  includeInNetWorth.value = true
  formError.value = ''
}

function syncIncludeInNetWorthDefault() {
  const selectedType = debtAccountType.value
  if (selectedType) {
    includeInNetWorth.value = selectedType.includeInNetWorthDefault
  }
}

function openDeleteModal(account: Account) {
  deletingAccount.value = account
  deleteError.value = ''
  showDeleteModal.value = true
}

function closeDeleteModal(force = false) {
  if (isDeleting.value && !force) {
    return
  }

  showDeleteModal.value = false
  deletingAccount.value = null
  deleteError.value = ''
}

async function loadDebtAccounts() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看债务账户'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [typeList, accountList, summaryData] = await Promise.all([
      getAccountTypes({ status: 'active' }),
      getAccounts({ userId: currentUser.id, status: 'active' }),
      getDebtAccountSummary(currentUser.id),
    ])

    accountTypes.value = typeList
    accounts.value = accountList.filter((account) => DEBT_ACCOUNT_CODES.has(account.accountTypeCode ?? ''))
    summary.value = summaryData

    if (!debtAccountType.value) {
      pageError.value = '未找到债务账户类型，请先同步账户类型数据'
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '债务账户加载失败'
  } finally {
    isLoading.value = false
  }
}

async function saveAccount() {
  if (isSaving.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const selectedType = debtAccountType.value
  const trimmedName = formName.value.trim()
  const trimmedRemark = formRemark.value.trim()
  const numericAmount = Number(formAmount.value || '0')
  const normalizedAmount = Number.isFinite(numericAmount) && numericAmount >= 0 ? numericAmount : 0
  const isEditing = Boolean(editingAccount.value)

  if (!currentUser) {
    formError.value = '请先登录后再保存账户'
    return
  }

  if (!selectedType) {
    formError.value = '未找到债务账户类型'
    return
  }

  if (!trimmedName) {
    formError.value = '请输入账户名称'
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      accountTypeId: selectedType.id,
      name: trimmedName,
      icon: selectedType.code,
      currencyCode: 'CNY',
      currentBalance: normalizedAmount,
      includeInNetWorth: includeInNetWorth.value,
      status: 'active',
      remark: trimmedRemark || null,
    }

    if (editingAccount.value) {
      await updateAccount(editingAccount.value.id, payload)
    } else {
      await createAccount(payload)
    }

    closeCreateModal(true)
    showFeedback(isEditing ? '修改成功' : '新增成功', 'success')
    await loadDebtAccounts()
  } catch (error) {
    const message = error instanceof Error ? error.message : '账户保存失败'
    formError.value = message
    showFeedback(message, 'error')
  } finally {
    isSaving.value = false
  }
}

async function confirmDeleteAccount() {
  const account = deletingAccount.value
  if (!account) {
    return
  }

  isDeleting.value = true

  try {
    await deleteAccount(account.id)
    closeDeleteModal(true)
    showFeedback('删除成功', 'success')
    await loadDebtAccounts()
  } catch (error) {
    const message = error instanceof Error ? error.message : '删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function formatAmount(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatSignedAmount(value: number, negative = false) {
  const amount = formatAmount(Math.abs(value))
  return negative ? `-${amount}` : amount
}

function getAccountIcon(code?: string | null) {
  if (code === DEBT_ACCOUNT_CODE) {
    return '债'
  }
  return '债'
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}
</script>

<template>
  <CommonFeedback
    v-model="showFeedbackModal"
    :message="feedbackMessage"
    :type="feedbackType"
  />

  <section class="debt-account-page" aria-label="债务账户">
    <PageHeader title="债务账户" back-to="/finance/accounts" back-label="返回账户管理" />

    <section class="debt-summary" aria-label="债务汇总">
      <article class="debt-summary-card debt-summary-card-total">
        <span>债务总额</span>
        <AmountText tag="strong" :value="formatSignedAmount(summary.totalAmount, true)" show-unit show-sign />
        <small>当前全部债务余额</small>
      </article>
      <article class="debt-summary-card debt-summary-card-net">
        <span>账户数量</span>
        <strong>{{ summary.accountCount }}</strong>
        <small>个债务账户</small>
      </article>
    </section>

    <p v-if="pageError" class="debt-message debt-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <section v-else class="debt-groups" aria-label="债务账户列表">
      <article class="debt-group-card">
        <header class="debt-group-header">
          <div>
            <strong>债务账户列表</strong>
            <p>统一管理全部债务账户</p>
          </div>
          <AmountText tag="strong" :value="formatSignedAmount(summary.totalAmount, true)" show-unit show-sign />
        </header>

        <p v-if="accounts.length === 0" class="debt-empty">
          暂无债务账户
        </p>

        <div v-else class="debt-list">
          <article
            v-for="account in accounts"
            :key="account.id"
            class="debt-item"
          >
            <div class="debt-item-main">
              <span class="debt-item-icon">
                {{ getAccountIcon(account.accountTypeCode) }}
              </span>
              <div class="debt-item-text">
                <strong>{{ account.name }}</strong>
                <p>{{ account.remark || account.accountTypeName || '' }}</p>
              </div>
            </div>
            <div class="debt-item-side">
              <AmountText
                tag="strong"
                :value="formatSignedAmount(Number(account.currentBalance), true)"
                show-unit
                show-sign
              />
              <div class="debt-item-actions">
                <button type="button" class="debt-action debt-action-edit" @click="openEditModal(account)">
                  编辑
                </button>
                <button type="button" class="debt-action debt-action-delete" @click="openDeleteModal(account)">
                  删除
                </button>
              </div>
            </div>
          </article>
        </div>
      </article>
    </section>

    <FloatingAddButton aria-label="新增债务账户" @click="openCreateModal" />
  </section>

  <CommonModal
    v-model="showCreateModal"
    :title="createModalTitle"
  >
    <div class="debt-form">
      <CommonInput v-model="formName" label="账户名称" placeholder="输入账户名称" />
      <CommonInput
        v-model="formAmount"
        label="债务金额"
        placeholder="输入债务金额"
        input-type="number"
        input-mode="decimal"
      />
      <CommonSwitch v-model="includeInNetWorth" label="是否计入总资产" />
      <CommonInput v-model="formRemark" label="备注" placeholder="输入备注" />
      <p v-if="formError" class="debt-form-error">
        {{ formError }}
      </p>
    </div>

    <template #footer>
      <div class="debt-actions">
        <button type="button" class="debt-modal-button debt-modal-button-secondary" @click="closeCreateModal()">
          取消
        </button>
        <button type="button" class="debt-modal-button debt-modal-button-primary" :disabled="isSaving" @click="saveAccount">
          {{ editingAccount ? '保存修改' : '保存账户' }}
        </button>
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
      确认删除“{{ deletingAccount?.name }}”吗？
    </p>
    <p v-if="deleteError" class="debt-delete-error">
      {{ deleteError }}
    </p>

    <template #footer>
      <div class="debt-actions">
        <button type="button" class="debt-modal-button debt-modal-button-secondary" @click="closeDeleteModal()">
          取消
        </button>
        <button type="button" class="debt-modal-button debt-modal-button-danger" :disabled="isDeleting" @click="confirmDeleteAccount">
          确认删除
        </button>
      </div>
    </template>
  </CommonModal>
</template>

<style scoped lang="scss" src="./style.scss"></style>
