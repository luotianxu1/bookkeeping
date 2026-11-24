<script setup lang="ts">
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'

import { formatAmount as formatNumberAmount } from '@/business/utils/format'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'

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
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import {
  DEFAULT_INVESTMENT_ACCOUNT_ICON,
  allAccountIconOptions,
  resolveInvestmentAccountIcon,
} from '@/business/data/account-icons'
import { getErrorMessage } from '@/business/api/request'

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

function formatAmount(value: number, digits = 2) {
  return formatNumberAmount(Math.abs(value), digits)
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
const isLoading = ref(true)
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
const accountIcon = ref(DEFAULT_INVESTMENT_ACCOUNT_ICON)
const accountRemark = ref('')
const includeInNetWorth = ref(true)

const INVESTMENT_ACCOUNT_CODE = 'investment'

const investmentAccountType = computed(
  () =>
    accountTypes.value.find((type) => type.code === INVESTMENT_ACCOUNT_CODE) ??
    null,
)
const accountModalTitle = computed(() =>
  editingAccount.value ? '修改投资账户' : '新增投资账户',
)
const accountSubmitLabel = computed(() =>
  editingAccount.value ? '保存账户' : '新增账户',
)

const visiblePositions = computed(() =>
  positions.value.filter((position) => shouldDisplayHolding(position)),
)

const positionGroups = computed(() => {
  const grouped = new Map<number, InvestmentPosition[]>()
  for (const position of visiblePositions.value) {
    const list = grouped.get(position.accountId) ?? []
    list.push(position)
    grouped.set(position.accountId, list)
  }
  return grouped
})

const investmentAccountCards = computed<InvestmentAccountCard[]>(() =>
  accounts.value.map((account) => {
    const accountPositions = positionGroups.value.get(account.id) ?? []
    const stockCount = accountPositions.filter(
      (position) => position.productType === 'stock',
    ).length
    const fundCount = accountPositions.filter(
      (position) => position.productType === 'fund',
    ).length
    const dayProfitPositions = accountPositions.filter(
      (position) =>
        position.dayProfit !== null && position.dayProfit !== undefined,
    )

    return {
      account,
      accountId: account.id,
      name: account.name,
      marketValue: Number(account.currentBalance ?? 0),
      stockCount,
      fundCount,
      dayProfit:
        dayProfitPositions.length > 0
          ? dayProfitPositions.reduce(
              (total, position) => total + Number(position.dayProfit),
              0,
            )
          : null,
      // 后端累计收益已含浮盈（已实现+分红+持仓盈亏），直接累加
      totalProfit: accountPositions.reduce(
        (total, position) =>
          total +
          Number(position.cumulativeProfit ?? 0),
        0,
      ),
    }
  }),
)

onMounted(() => {
  void loadInvestmentAccounts()
})

async function loadInvestmentAccounts() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看投资账户'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList, typeList, summaryData, positionList] =
      await Promise.all([
        getAccounts({ userId: currentUser.id, status: 'active' }),
        getAccountTypes({ status: 'active' }),
        getInvestmentSummary({ userId: currentUser.id }),
        getInvestmentPositions({ userId: currentUser.id }),
      ])
    accounts.value = accountList.filter(
      (account) => account.accountTypeCode === 'investment',
    )
    accountTypes.value = typeList
    positions.value = positionList
    summary.value = summaryData
  } catch (error) {
    const message = getErrorMessage(error, '投资账户加载失败')
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

function isPendingSubscription(position: InvestmentPosition) {
  return position.subscriptionStatus === 'pending'
}

function hasConfirmedHoldingQuantity(position: InvestmentPosition) {
  const quantity = Number(position.holdingQuantity ?? 0)
  return Number.isFinite(quantity) && quantity > 0
}

function shouldDisplayHolding(position: InvestmentPosition) {
  if (position.productType !== 'fund') {
    return true
  }
  return (
    hasConfirmedHoldingQuantity(position) || isPendingSubscription(position)
  )
}

function openCreateAccountModal() {
  editingAccount.value = null
  accountName.value = ''
  accountIcon.value = DEFAULT_INVESTMENT_ACCOUNT_ICON
  accountRemark.value = ''
  includeInNetWorth.value =
    investmentAccountType.value?.includeInNetWorthDefault ?? true
  accountFormError.value = ''
  showAccountModal.value = true
}

function openEditAccountModal(account: Account) {
  editingAccount.value = account
  accountName.value = account.name
  accountIcon.value = resolveInvestmentAccountIcon(account.icon)
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
  accountIcon.value = DEFAULT_INVESTMENT_ACCOUNT_ICON
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
      icon: accountIcon.value,
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
    const message = getErrorMessage(error, '投资账户保存失败')
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
    const message = getErrorMessage(error, '删除失败')
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function formatCurrency(value: number) {
  return `¥${formatAmount(value)}`
}

function formatSignedCurrency(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}¥${formatAmount(value)}`
}

function formatNullableSignedCurrency(value?: number | null) {
  return value === null || value === undefined
    ? '--'
    : formatSignedCurrency(value)
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function profitToneClass(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  if (!Number.isFinite(amount) || amount === 0) {
    return ''
  }
  return amount > 0 ? 'tone-up' : 'tone-down'
}

type DesktopRow = (typeof investmentAccountCards.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace stagger-in">
    <PageHeader title="投资账户">
      <template #right>
        <el-button type="primary" @click="openCreateAccountModal">
          新增账户
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    />
    <DesktopRecordList
      title="投资账户列表"
      searchable
      search-placeholder="账户名称"
      :search-keys="['name']"
      :rows="investmentAccountCards"
      :loading="isLoading"
      row-key="accountId"
    >
      <template #default="{ row }: { row: DesktopRow }">
        <article class="record-card invest-account-card">
          <div class="record-card-head">
            <div class="record-card-title">
              <span>{{ row.name }}</span>
            </div>
            <div class="record-card-aside invest-account-counts">
              <el-tag size="small" effect="plain">A股 {{ row.stockCount }}</el-tag>
              <el-tag size="small" effect="plain">基金 {{ row.fundCount }}</el-tag>
            </div>
          </div>
          <div class="invest-account-market">
            <span class="invest-account-market-label">账户市值</span>
            <strong class="invest-account-market-value">
              {{ formatCurrency(row.marketValue) }}
            </strong>
          </div>
          <div class="invest-account-profits">
            <div
              class="invest-account-profit"
              :class="profitToneClass(row.dayProfit)"
            >
              <span>当日收益</span>
              <strong>{{ formatNullableSignedCurrency(row.dayProfit) }}</strong>
            </div>
            <div
              class="invest-account-profit"
              :class="profitToneClass(row.totalProfit)"
            >
              <span>累计收益</span>
              <strong>{{ formatSignedCurrency(row.totalProfit) }}</strong>
            </div>
          </div>
          <div class="record-card-foot">
            <el-button
              link
              type="primary"
              @click="openInvestmentAccountDetail(row.account.id)"
            >
              明细
            </el-button>
            <el-button
              link
              type="primary"
              @click="openEditAccountModal(row.account)"
            >
              编辑
            </el-button>
            <el-button
              link
              type="danger"
              @click="openDeleteModal(row.account)"
            >
              删除
            </el-button>
          </div>
        </article>
      </template>
    </DesktopRecordList>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal
      v-model="showAccountModal"
      :title="accountModalTitle"
      :close-on-overlay="!isSavingAccount"
      @close="closeAccountModal()"
    >
      <div class="pc-form-grid investment-account-form">
        <CommonInput
          v-model="accountName"
          label="账户名称"
          placeholder="输入投资账户名称"
        />
        <CommonSelect
          v-model="accountIcon"
          label="账户图标"
          :options="allAccountIconOptions"
        />
        <CommonInput
          v-model="accountRemark"
          label="备注"
          placeholder="输入账户说明"
        />
        <CommonSwitch v-model="includeInNetWorth" label="是否计入总资产" />
        <p v-if="accountFormError" class="investment-account-form-error">
          {{ accountFormError }}
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar investment-account-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSavingAccount"
            @click="closeAccountModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSavingAccount"
            @click="saveAccount"
          >
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
        <div class="pc-toolbar investment-account-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isDeleting"
            @click="confirmDelete"
          >
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped>
/* 投资账户卡：市值大数字 + 双列盈亏块（红涨绿跌） */
.invest-account-counts {
  display: flex;
  gap: 6px;
}

.invest-account-market {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.invest-account-market-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.invest-account-market-value {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.invest-account-profits {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.invest-account-profit {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.invest-account-profit strong {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

/* 中国市场惯例：盈利红、亏损绿 */
.invest-account-profit.tone-up strong {
  color: var(--color-danger);
}

.invest-account-profit.tone-down strong {
  color: var(--color-success);
}
</style>
