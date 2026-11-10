<script setup lang="ts">
import TransactionTable from '@/business/components/TransactionTable.vue'
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'

import { formatAmount } from '@/business/utils/format'
// 现金资产详情页：展示单个现金账户的余额与收支记录。
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonInput from '@/business/components/common/CommonInput/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'

import {
  deleteTransaction,
  getAccount,
  getTransactionPage,
  updateAccount,
  type Account,
  type Transaction as ApiTransaction,
  type TransactionPage,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import {
  allAccountIconOptions,
  DEFAULT_CASH_ACCOUNT_ICON,
  resolveCashAccountIcon,
} from '@/business/data/account-icons'
import { buildTransactionDayGroups } from '@/business/utils/transaction-day-groups'
import type { DayGroup, Transaction } from '@/business/types/finance'

import { getErrorMessage } from '@/business/api/request'

const CASH_TRANSACTION_PAGE_SIZE = 50

const route = useRoute()
const router = useRouter()

const account = ref<Account | null>(null)
const transactions = ref<ApiTransaction[]>([])
const currentPage = ref(1)
const totalPages = ref(1)
const totalItems = ref(0)
const isLoading = ref(true)
const isLoadingTransactions = ref(false)
const pageError = ref('')
const historyError = ref('')
const deletingId = ref<number | null>(null)
const pendingDeleteTransaction = ref<Transaction | null>(null)
const showDeleteConfirmModal = ref(false)
const deleteError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showEditAccountModal = ref(false)
const isSavingAccount = ref(false)
const accountFormError = ref('')
const formName = ref('')
const formIcon = ref(DEFAULT_CASH_ACCOUNT_ICON)
const formAmount = ref('')
const formRemark = ref('')
const setAsCommon = ref(true)
let requestVersion = 0

const accountId = computed(() => Number(route.params.accountId))

const accountName = computed(() => account.value?.name ?? '现金账户')
const accountTypeName = computed(() =>
  account.value?.accountTypeName
    ? `${account.value.accountTypeName}账户`
    : '现金账户',
)
const accountAmount = computed(() =>
  formatAmount(Number(account.value?.currentBalance ?? 0)),
)
const dayGroups = computed<DayGroup[]>(() =>
  buildTransactionDayGroups(transactions.value),
)
const hasPagination = computed(() => totalPages.value > 1)
const paginationSummary = computed(
  () =>
    `第 ${currentPage.value} / ${totalPages.value} 页，共 ${totalItems.value} 条`,
)

watch(
  accountId,
  () => {
    currentPage.value = 1
    void loadDetail()
  },
  { immediate: true },
)

function openEditTransaction(transaction: Transaction) {
  if (
    !transaction.id ||
    transaction.sourceType !== 'transaction' ||
    !transaction.accountId ||
    !transaction.categoryId ||
    !transaction.occurredAt
  ) {
    showFeedback('当前记录暂不支持修改', 'error')
    return
  }

  router.push({
    path: '/finance/entry/expense',
    query: {
      transactionId: String(transaction.id),
      type: transaction.type,
      amount: String(transaction.rawAmount ?? 0),
      accountId: String(transaction.accountId),
      categoryId: String(transaction.categoryId),
      occurredAt: transaction.occurredAt,
      remark: transaction.remark ?? '',
      redirect: `/finance/accounts/cash/${accountId.value}`,
    },
  })
}

function openEditAccountModal() {
  if (!account.value) {
    return
  }

  formName.value = account.value.name
  formIcon.value = resolveCashAccountIcon(account.value.icon)
  formAmount.value = String(account.value.currentBalance ?? 0)
  formRemark.value = account.value.remark ?? ''
  setAsCommon.value = account.value.includeInNetWorth
  accountFormError.value = ''
  showEditAccountModal.value = true
}

function closeEditAccountModal() {
  showEditAccountModal.value = false
  resetAccountForm()
}

function resetAccountForm() {
  formName.value = ''
  formIcon.value = DEFAULT_CASH_ACCOUNT_ICON
  formAmount.value = ''
  formRemark.value = ''
  setAsCommon.value = true
  accountFormError.value = ''
}

async function saveCashAccount() {
  if (isSavingAccount.value || !account.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const trimmedName = formName.value.trim()
  const trimmedRemark = formRemark.value.trim()

  if (!currentUser) {
    accountFormError.value = '请先登录后再修改账户'
    return
  }

  if (!trimmedName) {
    accountFormError.value = '请输入账户名称'
    return
  }

  const numericAmount = Number(formAmount.value || '0')
  const normalizedAmount = Number.isFinite(numericAmount) ? numericAmount : 0
  isSavingAccount.value = true
  accountFormError.value = ''

  try {
    await updateAccount(account.value.id, {
      userId: currentUser.id,
      accountTypeId: account.value.accountTypeId,
      contactId: account.value.contactId ?? null,
      name: trimmedName,
      icon: formIcon.value,
      color: account.value.color ?? null,
      currencyCode: account.value.currencyCode || 'CNY',
      currentBalance: normalizedAmount,
      includeInNetWorth: setAsCommon.value,
      sortOrder: account.value.sortOrder,
      status: account.value.status || 'active',
      remark: trimmedRemark || null,
    })

    closeEditAccountModal()
    showFeedback('修改成功', 'success')
    await loadDetail()
  } catch (error) {
    const message = getErrorMessage(error, '账户保存失败')
    accountFormError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingAccount.value = false
  }
}

async function loadDetail() {
  const currentRequestVersion = ++requestVersion
  account.value = null
  transactions.value = []
  resetPagination()

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看资产详情'
    isLoading.value = false
    return
  }
  if (!Number.isFinite(accountId.value) || accountId.value <= 0) {
    pageError.value = '账户不存在'
    isLoading.value = false
    return
  }

  isLoading.value = true
  isLoadingTransactions.value = false
  pageError.value = ''
  historyError.value = ''

  try {
    const [accountDetail, transactionPage] = await Promise.all([
      getAccount(accountId.value),
      getTransactionPage({
        userId: currentUser.id,
        accountId: accountId.value,
        cashOnly: true,
        sortOrder: 'desc',
        page: currentPage.value,
        pageSize: CASH_TRANSACTION_PAGE_SIZE,
      }),
    ])
    if (currentRequestVersion !== requestVersion) {
      return
    }
    account.value = accountDetail
    applyTransactionPage(transactionPage)
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = getErrorMessage(error, '资产详情加载失败')
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

async function loadTransactionPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    historyError.value = '请先登录后查看收支记录'
    return
  }
  if (!Number.isFinite(accountId.value) || accountId.value <= 0) {
    historyError.value = '账户不存在'
    return
  }

  const currentRequestVersion = ++requestVersion
  isLoadingTransactions.value = true
  historyError.value = ''

  try {
    const transactionPage = await getTransactionPage({
      userId: currentUser.id,
      accountId: accountId.value,
      cashOnly: true,
      sortOrder: 'desc',
      page: currentPage.value,
      pageSize: CASH_TRANSACTION_PAGE_SIZE,
    })
    if (currentRequestVersion !== requestVersion) {
      return
    }
    applyTransactionPage(transactionPage)
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    transactions.value = []
    historyError.value = getErrorMessage(error, '收支记录加载失败')
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoadingTransactions.value = false
    }
  }
}

function applyTransactionPage(transactionPage: TransactionPage) {
  transactions.value = transactionPage.items.filter(
    (transaction) => transaction.accountId === accountId.value,
  )
  currentPage.value = Math.max(1, transactionPage.page || 1)
  totalPages.value = Math.max(1, transactionPage.totalPages || 1)
  totalItems.value = Math.max(0, transactionPage.total || 0)
}

function resetPagination() {
  totalPages.value = 1
  totalItems.value = 0
}

function openDeleteConfirm(transaction: Transaction) {
  pendingDeleteTransaction.value = transaction
  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function closeDeleteConfirm() {
  if (deletingId.value) {
    return
  }

  showDeleteConfirmModal.value = false
  pendingDeleteTransaction.value = null
  deleteError.value = ''
}

async function confirmDeleteTransaction() {
  const transactionId = pendingDeleteTransaction.value?.id
  const currentUser = getStoredCurrentUser()
  if (!transactionId || !currentUser) {
    deleteError.value = '请选择要删除的收支记录'
    return
  }

  deletingId.value = transactionId
  deleteError.value = ''

  try {
    await deleteTransaction(transactionId, currentUser.id)
    showDeleteConfirmModal.value = false
    pendingDeleteTransaction.value = null
    showFeedback('删除成功', 'success')
    if (transactions.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    await loadDetail()
  } catch (error) {
    const message = getErrorMessage(error, '删除失败')
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    deletingId.value = null
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function changeDesktopPage(page: number) {
  currentPage.value = page
  void loadTransactionPage()
}
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader :title="accountName">
      <template #right>
        <el-button v-if="account" @click="openEditAccountModal">
          修改账户
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <el-skeleton v-if="isLoading" :rows="6" animated />
    <template v-else>
      <MetricStrip
        :items="[
          { label: '账户余额', value: accountAmount },
          { label: '账户类型', value: accountTypeName },
          { label: '流水笔数', value: totalItems },
        ]"
      />
      <div class="desktop-main-aside">
        <div>
          <el-alert
            v-if="historyError"
            :title="historyError"
            type="error"
            :closable="false"
            show-icon
          ></el-alert>
          <TransactionTable
            :rows="dayGroups.flatMap((group) => group.transactions)"
            :loading="isLoadingTransactions"
            :paginate="false"
            :show-delete="account?.userId === getStoredCurrentUser()?.id"
            :deleting-id="deletingId"
            @edit="openEditTransaction"
            @delete="openDeleteConfirm"
          />
          <div v-if="hasPagination" class="desktop-pagination">
            <span>{{ paginationSummary }}</span>
            <el-pagination
              :current-page="currentPage"
              :page-size="CASH_TRANSACTION_PAGE_SIZE"
              :total="totalItems"
              layout="prev, pager, next"
              :disabled="isLoadingTransactions"
              @current-change="changeDesktopPage"
            />
          </div>
        </div>
        <PagePanel title="账户资料">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="名称">
              {{ accountName }}
            </el-descriptions-item>
            <el-descriptions-item label="类型">
              {{ accountTypeName }}
            </el-descriptions-item>
            <el-descriptions-item label="币种">
              {{ account?.currencyCode || 'CNY' }}
            </el-descriptions-item>
            <el-descriptions-item label="备注">
              {{ account?.remark || '—' }}
            </el-descriptions-item>
          </el-descriptions>
        </PagePanel>
      </div>
    </template>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :close-on-overlay="!deletingId"
      @close="closeDeleteConfirm"
    >
      <p class="cash-delete-confirm-text">
        删除后会同步恢复账户余额，确认删除这条收支记录吗？
      </p>
      <p v-if="deleteError" class="cash-delete-error">{{ deleteError }}</p>
      <template #footer>
        <div class="pc-toolbar cash-delete-actions">
          <CommonButton
            variant="secondary"
            :disabled="Boolean(deletingId)"
            @click="closeDeleteConfirm"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="Boolean(deletingId)"
            @click="confirmDeleteTransaction"
          >
            {{ deletingId ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal v-model="showEditAccountModal" title="修改现金账户">
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
          v-model="formAmount"
          label="当前余额"
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
            @click="closeEditAccountModal"
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
  </section>
</template>
