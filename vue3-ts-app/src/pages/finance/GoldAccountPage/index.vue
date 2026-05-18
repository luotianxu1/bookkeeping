<script setup lang="ts">
// 黄金账户页：展示黄金账户汇总，并支持黄金账户列表增删改查。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import {
  createAccount,
  deleteAccount,
  getAccounts,
  getAccountTypes,
  getGoldAccountHoldings,
  getGoldAccountSummary,
  updateAccount,
  type Account,
  type AccountType,
  type GoldAccountHolding,
  type GoldAccountSummary,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const router = useRouter()

const isManageMode = ref(false)
const showCreateAccountModal = ref(false)
const showDeleteConfirmModal = ref(false)
const editingAccountId = ref<number | null>(null)
const deletingAccount = ref<Account | null>(null)
const isLoading = ref(false)
const isSavingAccount = ref(false)
const isDeletingAccount = ref(false)
const pageError = ref('')
const accountFormError = ref('')
const deleteError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const summary = ref<GoldAccountSummary>({
  totalWeight: 0,
  averagePrice: 0,
  purchaseTotal: 0,
  estimatedValue: 0,
  estimatedProfit: 0,
  profitRate: 0,
  cumulativeProfit: 0,
})
const goldAccountType = ref<AccountType | null>(null)
const goldAccounts = ref<Account[]>([])
const holdings = ref<GoldAccountHolding[]>([])
const formName = ref('')
const formRemark = ref('')
const includeInNetWorth = ref(true)
let requestVersion = 0

const accountModalTitle = computed(() => (editingAccountId.value ? '修改黄金账户' : '新增黄金账户'))
const holdingsByAccountId = computed(() =>
  holdings.value.reduce<Record<number, GoldAccountHolding>>((result, item) => {
    result[item.accountId] = item
    return result
  }, {}),
)
const accountRows = computed(() =>
  goldAccounts.value.map((account) => {
    const accountHolding = holdingsByAccountId.value[account.id]

    return {
      id: account.id,
      name: account.name,
      amount: Number(account.currentBalance ?? 0),
      currentPrice: Number(accountHolding?.currentPrice ?? 0),
      weight: Number(accountHolding?.weight ?? 0),
      holdingProfit: Number(accountHolding?.holdingProfit ?? 0),
      remark: account.remark ?? '',
    }
  }),
)
const hasAccounts = computed(() => accountRows.value.length > 0)

onMounted(() => {
  void loadGoldAccount()
})

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function openCreateModal() {
  editingAccountId.value = null
  resetForm()
  showCreateAccountModal.value = true
}

function openEditModal(account: Account) {
  editingAccountId.value = account.id
  formName.value = account.name
  formRemark.value = account.remark ?? ''
  includeInNetWorth.value = account.includeInNetWorth
  accountFormError.value = ''
  showCreateAccountModal.value = true
}

function closeCreateModal(force = false) {
  if (isSavingAccount.value && !force) {
    return
  }

  showCreateAccountModal.value = false
  editingAccountId.value = null
  resetForm()
}

function resetForm() {
  formName.value = ''
  formRemark.value = ''
  includeInNetWorth.value = true
  accountFormError.value = ''
}

function openDeleteConfirmModal(account: Account) {
  deletingAccount.value = account
  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function closeDeleteConfirmModal() {
  if (isDeletingAccount.value) {
    return
  }

  showDeleteConfirmModal.value = false
  deletingAccount.value = null
  deleteError.value = ''
}

function confirmDeleteAccount() {
  if (deletingAccount.value) {
    void removeAccount(deletingAccount.value.id)
  }
}

function handleAccountClick(accountId: number) {
  if (isManageMode.value) {
    return
  }

  router.push({
    path: '/finance/accounts/gold/position',
    query: { accountId: String(accountId) },
  })
}

async function loadGoldAccount() {
  const currentRequestVersion = ++requestVersion
  const currentUser = getStoredCurrentUser()

  if (!currentUser) {
    pageError.value = '请先登录后查看黄金账户'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [typeList, summaryData, holdingList] = await Promise.all([
      getAccountTypes({ status: 'active' }),
      getGoldAccountSummary(currentUser.id),
      getGoldAccountHoldings(currentUser.id),
    ])

    if (currentRequestVersion !== requestVersion) {
      return
    }

    goldAccountType.value = typeList.find((item) => item.code === 'gold') ?? null
    if (!goldAccountType.value) {
      goldAccounts.value = []
      holdings.value = []
      pageError.value = '黄金账户类型不存在'
      return
    }

    const accountList = await getAccounts({
      userId: currentUser.id,
      accountTypeId: goldAccountType.value.id,
      status: 'active',
    })

    if (currentRequestVersion !== requestVersion) {
      return
    }

    summary.value = summaryData
    goldAccounts.value = accountList
    holdings.value = holdingList
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = error instanceof Error ? error.message : '黄金账户加载失败'
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

async function saveGoldAccount() {
  if (isSavingAccount.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const trimmedName = formName.value.trim()
  const trimmedRemark = formRemark.value.trim()

  if (!currentUser) {
    accountFormError.value = '请先登录后再保存账户'
    return
  }

  if (!goldAccountType.value) {
    accountFormError.value = '黄金账户类型加载失败'
    return
  }

  if (!trimmedName) {
    accountFormError.value = '请输入账户名称'
    return
  }

  isSavingAccount.value = true
  accountFormError.value = ''

  try {
    const editingAccount = editingAccountId.value
      ? goldAccounts.value.find((item) => item.id === editingAccountId.value) ?? null
      : null

    const payload = {
      userId: currentUser.id,
      accountTypeId: goldAccountType.value.id,
      name: trimmedName,
      icon: 'gold',
      currencyCode: 'CNY',
      currentBalance: Number(editingAccount?.currentBalance ?? 0),
      includeInNetWorth: includeInNetWorth.value,
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

    closeCreateModal(true)
    await loadGoldAccount()
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

  isDeletingAccount.value = true
  deleteError.value = ''

  try {
    await deleteAccount(id)
    closeDeleteConfirmModal()
    showFeedback('删除成功', 'success')
    await loadGoldAccount()
  } catch (error) {
    const message = error instanceof Error ? error.message : '账户删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeletingAccount.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function formatAmount(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatWeight(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })
}

function formatRate(value: number | null | undefined) {
  return `${Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}%`
}

function formatSignedAmount(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return `${amount >= 0 ? '+' : '-'}${formatAmount(Math.abs(amount))}`
}
</script>

<template>
  <section class="gold-account-page" aria-label="黄金账户">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="gold-account-header">
      <PageHeader title="黄金账户" back-label="返回账户管理" />
      <button class="gold-account-manage" type="button" @click="toggleManageMode">
        管理
      </button>
    </header>

    <p v-if="pageError" class="gold-account-message gold-account-message-error">
      {{ pageError }}
    </p>
    <p v-else-if="isLoading" class="gold-account-message">
      加载中...
    </p>

    <template v-else>
      <section class="gold-account-summary">
        <div class="summary-head">
          <span>总重量(克)</span>
          <strong>{{ formatWeight(summary.totalWeight) }}</strong>
        </div>

        <div class="summary-grid">
          <article>
            <span>平均克价(元)</span>
            <strong>{{ formatAmount(summary.averagePrice) }}</strong>
          </article>
          <article>
            <span>购入总价(元)</span>
            <strong>{{ formatAmount(summary.purchaseTotal) }}</strong>
          </article>
          <article>
            <span>预估价值(元)</span>
            <strong>{{ formatAmount(summary.estimatedValue) }}</strong>
          </article>
          <article>
            <span>预估收益(元)</span>
            <strong :class="{ up: summary.estimatedProfit >= 0, negative: summary.estimatedProfit < 0 }">
              {{ formatSignedAmount(summary.estimatedProfit) }}
            </strong>
          </article>
          <article>
            <span>收益率(%)</span>
            <strong :class="{ up: summary.profitRate >= 0, negative: summary.profitRate < 0 }">
              {{ formatRate(summary.profitRate) }}
            </strong>
          </article>
          <article>
            <span>累计收益(元)</span>
            <strong :class="{ up: summary.cumulativeProfit >= 0, negative: summary.cumulativeProfit < 0 }">
              {{ formatSignedAmount(summary.cumulativeProfit) }}
            </strong>
          </article>
        </div>
      </section>

      <section class="gold-holding-list">
        <template v-if="hasAccounts">
          <article
            v-for="account in goldAccounts"
            :key="account.id"
            class="gold-account-row"
          >
            <button
              v-if="isManageMode"
              type="button"
              class="gold-remove-trigger"
              :aria-label="`删除${account.name}`"
              @click="openDeleteConfirmModal(account)"
            >
              <span class="gold-remove-dash"></span>
            </button>

            <button
              v-if="isManageMode"
              type="button"
              class="gold-edit-trigger"
              :aria-label="`修改${account.name}`"
              @click="openEditModal(account)"
            >
              ✎
            </button>

            <button
              type="button"
              :class="['gold-holding-card', { 'manage-shifted': isManageMode }]"
              @click="handleAccountClick(account.id)"
            >
              <span class="price-tag">
                {{
                  (holdingsByAccountId[account.id]?.currentPrice ?? 0) > 0
                    ? `${formatAmount(holdingsByAccountId[account.id]?.currentPrice)}/克`
                    : '暂无持仓'
                }}
              </span>
              <div class="holding-top">
                <strong>{{ account.name }}</strong>
                <p>{{ formatAmount(account.currentBalance) }}</p>
              </div>
              <div class="holding-bottom">
                <span>
                  {{
                    (holdingsByAccountId[account.id]?.weight ?? 0) > 0
                      ? `${formatWeight(holdingsByAccountId[account.id]?.weight)}g`
                      : '0.000g'
                  }}
                </span>
                <em :class="{ negative: (holdingsByAccountId[account.id]?.holdingProfit ?? 0) < 0 }">
                  {{ formatSignedAmount(holdingsByAccountId[account.id]?.holdingProfit) }}
                </em>
              </div>
            </button>
          </article>
        </template>

        <p v-else class="gold-account-empty">暂无黄金账户</p>
      </section>
    </template>

    <FloatingAddButton aria-label="新增黄金账户" storage-key="gold-account" @click="openCreateModal" />

    <CommonModal v-model="showCreateAccountModal" :title="accountModalTitle">
      <form class="gold-account-form" @submit.prevent="saveGoldAccount">
        <CommonInput v-model="formName" label="账户名称" placeholder="例如：工商银行积存金" />
        <CommonInput v-model="formRemark" label="备注" placeholder="例如：长期配置" />
        <CommonSwitch v-model="includeInNetWorth" label="是否计入总资产" />
        <p v-if="accountFormError" class="gold-account-form-error">{{ accountFormError }}</p>
      </form>

      <template #footer>
        <div class="gold-account-actions">
          <CommonButton variant="secondary" type="button" :disabled="isSavingAccount" @click="closeCreateModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" type="button" :disabled="isSavingAccount" @click="saveGoldAccount">
            {{ isSavingAccount ? '保存中...' : '确认' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :close-on-overlay="!isDeletingAccount"
      @close="closeDeleteConfirmModal"
    >
      <p class="gold-delete-message">
        确认删除“{{ deletingAccount?.name ?? '' }}”吗？
      </p>
      <p v-if="deleteError" class="gold-account-form-error">{{ deleteError }}</p>

      <template #footer>
        <div class="gold-account-actions">
          <CommonButton variant="secondary" type="button" :disabled="isDeletingAccount" @click="closeDeleteConfirmModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" type="button" :disabled="isDeletingAccount" @click="confirmDeleteAccount">
            {{ isDeletingAccount ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
