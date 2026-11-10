<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

// 现金账户页：支持管理模式切换与新增账户弹窗。
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
  updateAccount,
  type Account,
  type AccountType,
} from '@/business/api/modules/finance'
import {
  allAccountIconOptions,
  DEFAULT_CASH_ACCOUNT_ICON,
  resolveCashAccountIcon,
} from '@/business/data/account-icons'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

const showCreateAccountModal = ref(false)
const showDeleteConfirmModal = ref(false)
const editingAccountId = ref<number | null>(null)
const deletingAccount = ref<Account | null>(null)
const accountItems = ref<Account[]>([])
const cashAccountType = ref<AccountType | null>(null)
const isLoadingAccounts = ref(true)
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

const accountModalTitle = computed(() =>
  editingAccountId.value ? '修改现金账户' : '新增现金账户',
)
const accountAmountLabel = computed(() =>
  editingAccountId.value ? '当前余额' : '初始金额',
)

onMounted(() => {
  loadCashAccounts()
})

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
  const normalizedAmount =
    editingAccountId.value && Number.isFinite(numericAmount) ? numericAmount : 0
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
    const message = getErrorMessage(error, '账户保存失败')
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
    const message = getErrorMessage(error, '账户删除失败')
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

async function loadCashAccounts() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    accountListError.value = '请先登录后查看账户'
    isLoadingAccounts.value = false
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
    accountListError.value = getErrorMessage(error, '现金账户加载失败')
  } finally {
    isLoadingAccounts.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

type DesktopRow = (typeof accountItems.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace">
    <PageHeader title="现金账户">
      <template #right>
        <el-button type="primary" @click="openCreateModal">
          新增现金账户
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="accountListError"
      :title="accountListError"
      type="error"
      :closable="false"
      show-icon
    />
    <DesktopTable
      title="现金账户列表"
      searchable
      search-placeholder="账户名称 / 备注"
      :rows="accountItems"
      :loading="isLoadingAccounts"
    >
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="账户"
        prop="name"
        sortable="custom"
        min-width="220"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="币种"
        prop="currencyCode"
        sortable="custom"
        min-width="100"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="余额" min-width="180">
        <template #default="{ row }: { row: DesktopRow }">
          {{
            Number(row.currentBalance).toLocaleString('zh-CN', {
              minimumFractionDigits: 2,
            })
          }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="计入净资产" min-width="120">
        <template #default="{ row }: { row: DesktopRow }">
          <el-tag :type="row.includeInNetWorth ? 'success' : 'info'">
            {{ row.includeInNetWorth ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="备注"
        prop="remark"
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
              @click="router.push('/finance/accounts/cash/' + row.id)"
            >
              明细
            </el-button>
            <el-button link type="primary" @click="openEditModal(row)">
              编辑
            </el-button>
            <el-button link type="danger" @click="openDeleteConfirmModal(row)">
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
    <CommonModal v-model="showCreateAccountModal" :title="accountModalTitle">
      <el-form
        class="cash-create-form"
        @submit.prevent="saveCashAccount"
        label-position="top"
      >
        <CommonInput
          v-model="formName"
          label="账户名称"
          placeholder="例如：日常钱包"
        />
        <CommonSelect
          v-model="formIcon"
          label="账户图标"
          :options="allAccountIconOptions"
        />
        <CommonInput
          v-if="editingAccountId"
          v-model="formAmount"
          :label="accountAmountLabel"
          placeholder="0.00"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput
          v-model="formRemark"
          label="备注"
          placeholder="例如：日常零用"
        />
        <CommonSwitch v-model="setAsCommon" label="是否计入总资产" />
        <p v-if="accountFormError" class="cash-form-error">
          {{ accountFormError }}
        </p>
      </el-form>
      <template #footer>
        <div class="pc-toolbar cash-create-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSavingAccount"
            @click="closeCreateModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSavingAccount"
            @click="saveCashAccount"
          >
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
        <div class="pc-toolbar cash-create-actions">
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
