<script setup lang="ts">
// 现金资产详情页：展示单个现金账户的余额与收支记录。
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  deleteTransaction,
  getAccount,
  getTransactionPage,
  updateAccount,
  type Account,
  type Transaction as ApiTransaction,
  type TransactionPage,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { buildTransactionDayGroups } from '@/utils/transaction-day-groups'
import type { DayGroup, Transaction } from '@/types/finance'
import TransactionDayCard from '../components/TransactionDayCard/index.vue'

const CASH_TRANSACTION_PAGE_SIZE = 50

const route = useRoute()
const router = useRouter()

const account = ref<Account | null>(null)
const transactions = ref<ApiTransaction[]>([])
const currentPage = ref(1)
const totalPages = ref(1)
const totalItems = ref(0)
const isLoading = ref(false)
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
const formAmount = ref('')
const formRemark = ref('')
const setAsCommon = ref(true)
let requestVersion = 0

const accountId = computed(() => Number(route.params.accountId))
const historyCountText = computed(() => `共 ${totalItems.value} 条`)
const accountName = computed(() => account.value?.name ?? '现金账户')
const accountTypeName = computed(() => account.value?.accountTypeName ? `${account.value.accountTypeName}账户` : '现金账户')
const accountAmount = computed(() => formatAmount(Number(account.value?.currentBalance ?? 0)))
const dayGroups = computed<DayGroup[]>(() => buildTransactionDayGroups(transactions.value))
const hasPagination = computed(() => totalPages.value > 1)
const paginationSummary = computed(() => `第 ${currentPage.value} / ${totalPages.value} 页，共 ${totalItems.value} 条`)
const canGoPrevPage = computed(() => currentPage.value > 1)
const canGoNextPage = computed(() => currentPage.value < totalPages.value)

watch(accountId, () => {
  currentPage.value = 1
  void loadDetail()
}, { immediate: true })

function openEditTransaction(transaction: Transaction) {
  if (!transaction.id || transaction.sourceType !== 'transaction' || !transaction.accountId || !transaction.categoryId || !transaction.occurredAt) {
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
      icon: getCashIconCode(trimmedName),
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
    const message = error instanceof Error ? error.message : '账户保存失败'
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
    return
  }
  if (!Number.isFinite(accountId.value) || accountId.value <= 0) {
    pageError.value = '账户不存在'
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
    pageError.value = error instanceof Error ? error.message : '资产详情加载失败'
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
    historyError.value = error instanceof Error ? error.message : '收支记录加载失败'
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoadingTransactions.value = false
    }
  }
}

function applyTransactionPage(transactionPage: TransactionPage) {
  transactions.value = transactionPage.items.filter((transaction) => transaction.accountId === accountId.value)
  currentPage.value = Math.max(1, transactionPage.page || 1)
  totalPages.value = Math.max(1, transactionPage.totalPages || 1)
  totalItems.value = Math.max(0, transactionPage.total || 0)
}

function resetPagination() {
  totalPages.value = 1
  totalItems.value = 0
}

function goToPrevPage() {
  if (!canGoPrevPage.value || isLoadingTransactions.value || isLoading.value) {
    return
  }
  currentPage.value -= 1
  void loadTransactionPage()
}

function goToNextPage() {
  if (!canGoNextPage.value || isLoadingTransactions.value || isLoading.value) {
    return
  }
  currentPage.value += 1
  void loadTransactionPage()
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
    const message = error instanceof Error ? error.message : '删除失败'
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

function formatAmount(value: number) {
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function getCashIconCode(name: string) {
  if (name.includes('银行') || name.includes('卡')) {
    return 'bank-card'
  }
  if (name.includes('支付宝') || name.includes('微信')) {
    return 'alipay'
  }
  if (name.includes('备用')) {
    return 'reserve-fund'
  }
  return 'wallet'
}

</script>

<template>
  <section class="cash-asset-detail-page" aria-label="现金资产详情">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="cash-asset-detail-header">
      <PageHeader title="资产详情" back-to="/finance/accounts/cash" back-label="返回现金账户">
        <template #right>
          <CommonHeaderActionButton
            v-if="account"
            label="修改现金账户"
            @click="openEditAccountModal"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 15.2A3.2 3.2 0 1 0 12 8.8A3.2 3.2 0 0 0 12 15.2Z" stroke="currentColor" stroke-width="1.8" />
              <path d="M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 1 1 16.96 19.71L16.9 19.65A1.65 1.65 0 0 0 15.08 19.32A1.65 1.65 0 0 0 14.08 20.83V21A2 2 0 1 1 10.08 21V20.91A1.65 1.65 0 0 0 9 19.4A1.65 1.65 0 0 0 7.18 19.73L7.12 19.79A2 2 0 1 1 4.29 16.96L4.35 16.9A1.65 1.65 0 0 0 4.68 15.08A1.65 1.65 0 0 0 3.17 14.08H3A2 2 0 1 1 3 10.08H3.09A1.65 1.65 0 0 0 4.6 9A1.65 1.65 0 0 0 4.27 7.18L4.21 7.12A2 2 0 1 1 7.04 4.29L7.1 4.35A1.65 1.65 0 0 0 8.92 4.68H9A1.65 1.65 0 0 0 10 3.17V3A2 2 0 1 1 14 3V3.09A1.65 1.65 0 0 0 15 4.6A1.65 1.65 0 0 0 16.82 4.27L16.88 4.21A2 2 0 1 1 19.71 7.04L19.65 7.1A1.65 1.65 0 0 0 19.32 8.92V9A1.65 1.65 0 0 0 20.83 10H21A2 2 0 1 1 21 14H20.91A1.65 1.65 0 0 0 19.4 15Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </CommonHeaderActionButton>
        </template>
      </PageHeader>
    </header>

    <p v-if="pageError" class="cash-asset-message cash-asset-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="cash-asset-overview-card" aria-label="现金资产总览">
        <div class="cash-asset-overview-top">
          <div class="cash-asset-overview-title">
            <strong>{{ accountName }}</strong>
            <span>{{ accountTypeName }}</span>
          </div>
        </div>

        <AmountText tag="p" class="cash-asset-overview-amount" :value="accountAmount" />
      </section>

      <section class="cash-asset-history-wrap" aria-label="收支记录">
        <header class="cash-asset-history-head">
          <strong>收支记录</strong>
          <span>{{ historyCountText }}</span>
        </header>

        <CommonLoading v-if="isLoadingTransactions" />
        <section v-else class="cash-asset-history-groups">
          <p v-if="historyError" class="cash-asset-empty cash-asset-empty-error">{{ historyError }}</p>
          <p v-else-if="transactions.length === 0" class="cash-asset-empty">暂无收支记录</p>

          <template v-else>
            <TransactionDayCard
              v-for="group in dayGroups"
              :key="group.date"
              :group="group"
              summary-mode="stacked"
              show-delete
              :deleting-id="deletingId"
              @edit="openEditTransaction"
              @delete="openDeleteConfirm"
            />

            <div v-if="hasPagination" class="cash-asset-pagination" aria-label="收支记录分页">
              <button
                class="cash-asset-pagination-button"
                type="button"
                :disabled="!canGoPrevPage || isLoadingTransactions"
                @click="goToPrevPage"
              >
                上一页
              </button>
              <span class="cash-asset-pagination-summary">{{ paginationSummary }}</span>
              <button
                class="cash-asset-pagination-button"
                type="button"
                :disabled="!canGoNextPage || isLoadingTransactions"
                @click="goToNextPage"
              >
                下一页
              </button>
            </div>
          </template>
        </section>
      </section>
    </template>

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
        <div class="cash-delete-actions">
          <CommonButton variant="secondary" :disabled="Boolean(deletingId)" @click="closeDeleteConfirm">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="Boolean(deletingId)" @click="confirmDeleteTransaction">
            {{ deletingId ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal v-model="showEditAccountModal" title="修改现金账户">
      <form class="cash-create-form" @submit.prevent="saveCashAccount">
        <CommonInput v-model="formName" label="账户名称" placeholder="例如：日常钱包" />
        <CommonInput
          v-model="formAmount"
          label="当前余额"
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
          <CommonButton variant="secondary" :disabled="isSavingAccount" @click="closeEditAccountModal">取消</CommonButton>
          <CommonButton variant="primary" :disabled="isSavingAccount" @click="saveCashAccount">
            {{ isSavingAccount ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
