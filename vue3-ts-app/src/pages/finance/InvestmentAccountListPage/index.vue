<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  createAccount,
  deleteAccount,
  getAccounts,
  getAccountTypes,
  getInvestmentPositions,
  getInvestmentSummary,
  type Account,
  type AccountType,
  type InvestmentPosition,
  type InvestmentSummary,
  updateAccount,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

type InvestmentAccountCard = {
  account: Account
  accountId: number
  name: string
  marketValue: number
  stockCount: number
  fundCount: number
  dayProfit: number | null
  totalProfit: number
}

type DeleteTarget = {
  id: number
  name: string
}

const router = useRouter()

const accounts = ref<Account[]>([])
const accountTypes = ref<AccountType[]>([])
const positions = ref<InvestmentPosition[]>([])
const summary = ref<InvestmentSummary>({
  userId: 0,
  totalMarketValue: 0,
  dayProfit: 0,
  dayProfitRate: 0,
  holdingProfit: 0,
  holdingProfitRate: 0,
  cumulativeProfit: 0,
  cumulativeProfitRate: 0,
  lastSyncedAt: null,
})
const isLoading = ref(false)
const isManageMode = ref(false)
const pageError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showAccountModal = ref(false)
const showDeleteModal = ref(false)
const isSavingAccount = ref(false)
const isDeleting = ref(false)
const accountFormError = ref('')
const deleteError = ref('')
const editingAccount = ref<Account | null>(null)
const deletingTarget = ref<DeleteTarget | null>(null)
const accountName = ref('')
const accountRemark = ref('')
const includeInNetWorth = ref(true)

const INVESTMENT_ACCOUNT_CODE = 'investment'

const investmentAccountType = computed(() =>
  accountTypes.value.find((type) => type.code === INVESTMENT_ACCOUNT_CODE) ?? null,
)
const accountModalTitle = computed(() => (editingAccount.value ? '修改投资账户' : '新增投资账户'))
const accountSubmitLabel = computed(() => (editingAccount.value ? '保存账户' : '新增账户'))

const positionGroups = computed(() => {
  const grouped = new Map<number, InvestmentPosition[]>()
  for (const position of positions.value) {
    const list = grouped.get(position.accountId) ?? []
    list.push(position)
    grouped.set(position.accountId, list)
  }
  return grouped
})

const investmentAccountCards = computed<InvestmentAccountCard[]>(() =>
  accounts.value.map((account) => {
    const accountPositions = positionGroups.value.get(account.id) ?? []
    const stockCount = accountPositions.filter((position) => position.productType === 'stock').length
    const fundCount = accountPositions.filter((position) => position.productType === 'fund').length

    return {
      account,
      accountId: account.id,
      name: account.name,
      marketValue: Number(account.currentBalance ?? 0),
      stockCount,
      fundCount,
      dayProfit: accountPositions.length > 0 && accountPositions.every((position) => position.dayProfit !== null && position.dayProfit !== undefined)
        ? accountPositions.reduce((total, position) => total + Number(position.dayProfit), 0)
        : null,
      totalProfit: accountPositions.reduce(
        (total, position) => total + Number(position.cumulativeProfit ?? 0) + Number(position.holdingProfit ?? 0),
        0,
      ),
    }
  }),
)

const totalSummaryProfit = computed(() => Number(summary.value.cumulativeProfit ?? 0) + Number(summary.value.holdingProfit ?? 0))

const summaryMetrics = computed(() => [
  { label: '今日盈亏', value: summary.value.dayProfit, isRate: false },
  { label: '持仓盈亏', value: summary.value.holdingProfit, isRate: false },
  { label: '累计总收益', value: totalSummaryProfit.value, isRate: false },
])

onMounted(() => {
  void loadInvestmentAccounts()
})

async function loadInvestmentAccounts() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看投资账户'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList, typeList, summaryData, positionList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }),
      getAccountTypes({ status: 'active' }),
      getInvestmentSummary({ userId: currentUser.id }),
      getInvestmentPositions({ userId: currentUser.id }),
    ])
    accounts.value = accountList.filter((account) => account.accountTypeCode === 'investment')
    accountTypes.value = typeList
    positions.value = positionList
    summary.value = summaryData
  } catch (error) {
    const message = error instanceof Error ? error.message : '投资账户加载失败'
    pageError.value = message
    showFeedback(message, 'error')
  } finally {
    isLoading.value = false
  }
}

function openInvestmentAccountDetail(accountId: number) {
  if (isManageMode.value) {
    return
  }
  router.push(`/finance/accounts/investment/${accountId}`)
}

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function openCreateAccountModal() {
  editingAccount.value = null
  accountName.value = ''
  accountRemark.value = ''
  includeInNetWorth.value = investmentAccountType.value?.includeInNetWorthDefault ?? true
  accountFormError.value = ''
  showAccountModal.value = true
}

function openEditAccountModal(account: Account) {
  editingAccount.value = account
  accountName.value = account.name
  accountRemark.value = account.remark ?? ''
  includeInNetWorth.value = account.includeInNetWorth
  accountFormError.value = ''
  showAccountModal.value = true
}

function closeAccountModal(force = false) {
  if (isSavingAccount.value && !force) {
    return
  }
  showAccountModal.value = false
  editingAccount.value = null
  accountName.value = ''
  accountRemark.value = ''
  includeInNetWorth.value = true
  accountFormError.value = ''
}

function openDeleteModal(account: Account) {
  deletingTarget.value = {
    id: account.id,
    name: account.name,
  }
  deleteError.value = ''
  showDeleteModal.value = true
}

function closeDeleteModal(force = false) {
  if (isDeleting.value && !force) {
    return
  }
  showDeleteModal.value = false
  deletingTarget.value = null
  deleteError.value = ''
}

async function saveAccount() {
  if (isSavingAccount.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const targetAccountType = investmentAccountType.value
  const trimmedName = accountName.value.trim()
  const trimmedRemark = accountRemark.value.trim()

  if (!currentUser) {
    accountFormError.value = '请先登录后再操作'
    return
  }
  if (!trimmedName) {
    accountFormError.value = '请输入账户名称'
    return
  }
  if (!targetAccountType) {
    accountFormError.value = '投资账户类型不存在'
    return
  }

  isSavingAccount.value = true
  accountFormError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      accountTypeId: targetAccountType.id,
      contactId: null,
      name: trimmedName,
      icon: targetAccountType.code,
      currencyCode: editingAccount.value?.currencyCode || 'CNY',
      currentBalance: 0,
      includeInNetWorth: includeInNetWorth.value,
      status: 'active',
      remark: trimmedRemark || null,
    }

    if (editingAccount.value) {
      await updateAccount(editingAccount.value.id, payload)
      showFeedback('投资账户已更新', 'success')
    } else {
      await createAccount(payload)
      showFeedback('投资账户已新增', 'success')
    }
    closeAccountModal(true)
    await loadInvestmentAccounts()
  } catch (error) {
    const message = error instanceof Error ? error.message : '投资账户保存失败'
    accountFormError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingAccount.value = false
  }
}

async function confirmDelete() {
  const target = deletingTarget.value
  if (!target) {
    return
  }

  isDeleting.value = true
  deleteError.value = ''

  try {
    await deleteAccount(target.id)
    closeDeleteModal(true)
    showFeedback('投资账户已删除', 'success')
    await loadInvestmentAccounts()
  } catch (error) {
    const message = error instanceof Error ? error.message : '删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function formatMetricValue(value: number | null | undefined, isRate: boolean, isCount = false) {
  if (value === null || value === undefined) {
    return '--'
  }
  if (isCount) {
    return String(value)
  }
  if (isRate) {
    return formatSignedRate(value)
  }
  return formatSignedCurrency(value)
}

function formatAmount(value: number, digits = 2) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(value))
}

function formatCurrency(value: number) {
  return `¥${formatAmount(value)}`
}

function formatSignedRate(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}${formatAmount(value)}%`
}

function formatSignedCurrency(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}¥${formatAmount(value)}`
}

function formatNullableSignedCurrency(value?: number | null) {
  return value === null || value === undefined ? '--' : formatSignedCurrency(value)
}

function formatNullableSignedRate(value?: number | null) {
  return value === null || value === undefined ? '--' : formatSignedRate(value)
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}
</script>

<template>
  <section class="investment-account-list-page" aria-label="投资账户">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="investment-list-header">
      <PageHeader title="投资账户" back-to="/finance/accounts" back-label="返回账户管理" />
      <button
        type="button"
        :class="['investment-manage-button', { active: isManageMode }]"
        :aria-label="isManageMode ? '退出管理模式' : '进入管理模式'"
        @click="toggleManageMode"
      >
        {{ isManageMode ? '完成' : '管理' }}
      </button>
    </header>

    <p v-if="pageError" class="investment-list-message investment-list-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="investment-list-summary-card" aria-label="投资总览">
        <div class="investment-list-summary-top">
          <div class="investment-list-summary-main">
            <p>投资总市值</p>
            <AmountText tag="strong" :value="formatAmount(summary.totalMarketValue)" />
            <span>同步于 {{ summary.lastSyncedAt ? new Date(summary.lastSyncedAt).toLocaleString('zh-CN') : '暂无' }}</span>
          </div>
          <div class="investment-list-summary-side">
            <span>今日收益率</span>
            <AmountText tag="strong" :value="formatNullableSignedRate(summary.dayProfitRate)" />
          </div>
        </div>

        <div class="investment-list-metrics">
          <template v-for="metric in summaryMetrics" :key="metric.label">
            <div class="investment-list-metric-item">
              <span>{{ metric.label }}</span>
              <AmountText
                tag="strong"
                tone="auto"
                :value="formatMetricValue(metric.value, metric.isRate)"
              />
            </div>
          </template>
        </div>
      </section>

      <section class="investment-account-list" aria-label="投资账户列表">
        <p v-if="investmentAccountCards.length === 0" class="investment-list-empty">
          请先在账户管理中新增投资账户
        </p>

        <article
          v-for="card in investmentAccountCards"
          v-else
          :key="card.accountId"
          :class="['investment-account-list-card', { 'is-manage-mode': isManageMode }]"
          :role="isManageMode ? undefined : 'button'"
          :tabindex="isManageMode ? -1 : 0"
          @click="openInvestmentAccountDetail(card.accountId)"
        >
          <div class="investment-account-list-card-top">
            <div class="investment-account-list-card-title">
              <strong>{{ card.name }}</strong>
            </div>
            <div class="investment-account-list-card-side">
              <AmountText tag="strong" :value="formatCurrency(card.marketValue)" />
            </div>
          </div>

          <div class="investment-account-list-card-metrics">
            <span>股票 {{ card.stockCount }} 项</span>
            <span>基金 {{ card.fundCount }} 项</span>
          </div>

          <div class="investment-account-list-card-bottom">
            <div class="investment-account-list-card-profit">
              <div class="investment-account-list-card-profit-item">
                <span>今日盈亏</span>
                <AmountText
                  tag="strong"
                  class="investment-account-list-card-profit-value"
                  :value="formatNullableSignedCurrency(card.dayProfit)"
                  show-sign
                  show-unit
                />
              </div>
              <div class="investment-account-list-card-profit-item">
                <span>累计总收益</span>
                <AmountText
                  tag="strong"
                  class="investment-account-list-card-profit-value"
                  :value="formatSignedCurrency(card.totalProfit)"
                  show-sign
                  show-unit
                />
              </div>
            </div>
            <div v-if="isManageMode" class="investment-account-list-card-actions">
              <button
                type="button"
                class="investment-card-action"
                @click.stop="openEditAccountModal(card.account)"
              >
                编辑
              </button>
              <button
                type="button"
                class="investment-card-action is-danger"
                @click.stop="openDeleteModal(card.account)"
              >
                删除
              </button>
            </div>
            <span v-else class="investment-account-list-card-arrow">&gt;</span>
          </div>
        </article>
      </section>
    </template>

    <FloatingAddButton aria-label="新增投资账户" storage-key="investment-account-list-page" @click="openCreateAccountModal" />

    <CommonModal
      v-model="showAccountModal"
      :title="accountModalTitle"
      :close-on-overlay="!isSavingAccount"
      @close="closeAccountModal()"
    >
      <div class="investment-account-form">
        <CommonInput v-model="accountName" label="账户名称" placeholder="输入投资账户名称" />
        <CommonInput v-model="accountRemark" label="备注" placeholder="输入账户说明" />
        <CommonSwitch v-model="includeInNetWorth" label="是否计入总资产" />
        <p v-if="accountFormError" class="investment-account-form-error">
          {{ accountFormError }}
        </p>
      </div>

      <template #footer>
        <div class="investment-account-modal-actions">
          <CommonButton variant="secondary" :disabled="isSavingAccount" @click="closeAccountModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSavingAccount" @click="saveAccount">
            {{ accountSubmitLabel }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showDeleteModal"
      title="删除投资账户"
      size="compact"
      :show-close="false"
      :close-on-overlay="false"
    >
      <p class="investment-account-delete-message">
        删除后该投资账户下的持仓与交易记录会一并移除，确认继续吗？
      </p>
      <p v-if="deleteError" class="investment-account-delete-error">
        {{ deleteError }}
      </p>

      <template #footer>
        <div class="investment-account-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeleting" @click="closeDeleteModal()">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isDeleting" @click="confirmDelete">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
