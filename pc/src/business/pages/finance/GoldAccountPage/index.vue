<script setup lang="ts">
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'

import { formatAmount } from '@/business/utils/format'
// 黄金账户页：展示黄金账户汇总，并支持黄金账户列表增删改查。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonInput from '@/business/components/common/CommonInput/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'

import {
  createAccount,
  deleteAccount,
  getAccounts,
  getAccountTypes,
  getGoldAccountHoldings,
  getGoldLiquidations,
  getGoldAccountSummary,
  updateAccount,
  type Account,
  type AccountType,
  type GoldAccountHolding,
  type GoldLiquidation,
  type GoldAccountSummary,
} from '@/business/api/modules/finance'
import {
  refreshGoldPriceCache,
  useGoldPriceCache,
} from '@/business/utils/gold-price-cache'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

const router = useRouter()

const isManageMode = ref(false)
const showCreateAccountModal = ref(false)
const showDeleteConfirmModal = ref(false)
const editingAccountId = ref<number | null>(null)
const deletingAccount = ref<Account | null>(null)
const isLoading = ref(true)
const isSavingAccount = ref(false)
const isDeletingAccount = ref(false)
const pageError = ref('')
const accountFormError = ref('')
const deleteError = ref('')
const isRefreshingGold = ref(false)
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
const liquidation = ref<GoldLiquidation>({
  cumulativeWeight: 0,
  cumulativeProfit: 0,
  records: [],
})
const goldAccountType = ref<AccountType | null>(null)
const goldAccounts = ref<Account[]>([])
const holdings = ref<GoldAccountHolding[]>([])
const goldPrice = useGoldPriceCache()
const formName = ref('')
const formRemark = ref('')
const includeInNetWorth = ref(true)
let requestVersion = 0

const accountModalTitle = computed(() =>
  editingAccountId.value ? '修改黄金账户' : '新增黄金账户',
)
const realtimeGoldPrice = computed(() => Number(goldPrice.value?.price ?? 0))

const holdingsByAccountId = computed(() =>
  displayHoldings.value.reduce<Record<number, GoldAccountHolding>>(
    (result, item) => {
      result[item.accountId] = mergeAccountHolding(result[item.accountId], item)
      return result
    },
    {},
  ),
)
const displayHoldings = computed(() =>
  holdings.value.map((item) => decorateHolding(item, realtimeGoldPrice.value)),
)
const displaySummary = computed(() =>
  realtimeGoldPrice.value > 0
    ? buildSummary(
        displayHoldings.value,
        liquidation.value.cumulativeProfit || summary.value.cumulativeProfit,
      )
    : {
        ...summary.value,
        cumulativeProfit:
          liquidation.value.cumulativeProfit || summary.value.cumulativeProfit,
      },
)
const accountRows = computed(() =>
  goldAccounts.value
    .map((account, index) => {
      const accountHolding = holdingsByAccountId.value[account.id]

      return {
        id: account.id,
        account,
        orderIndex: index,
        name: account.name,
        avgCostPrice: Number(accountHolding?.avgCostPrice ?? 0),
        purchaseAmount: Number(accountHolding?.purchaseAmount ?? 0),
        weight: Number(accountHolding?.weight ?? 0),
        holdingProfit: Number(accountHolding?.holdingProfit ?? 0),
        createdAt: accountHolding?.createdAt ?? null,
        remark: account.remark ?? '',
      }
    })
    .sort((left, right) => {
      const profitDiff = right.holdingProfit - left.holdingProfit
      if (profitDiff !== 0) {
        return profitDiff
      }

      return left.orderIndex - right.orderIndex
    }),
)

onMounted(() => {
  void loadGoldAccount()
})

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

function closeDeleteConfirmModal(force = false) {
  if (isDeletingAccount.value && !force) {
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
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [typeList, summaryData, holdingList, liquidationData] =
      await Promise.all([
        getAccountTypes({ status: 'active' }),
        getGoldAccountSummary(currentUser.id),
        getGoldAccountHoldings(currentUser.id),
        getGoldLiquidations(currentUser.id),
      ])

    if (currentRequestVersion !== requestVersion) {
      return
    }

    goldAccountType.value =
      typeList.find((item) => item.code === 'gold') ?? null
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
    liquidation.value = liquidationData
  } catch (error) {
    if (currentRequestVersion !== requestVersion) {
      return
    }
    pageError.value = getErrorMessage(error, '黄金账户加载失败')
  } finally {
    if (currentRequestVersion === requestVersion) {
      isLoading.value = false
    }
  }
}

async function refreshGoldData() {
  if (isRefreshingGold.value) {
    return
  }

  isRefreshingGold.value = true

  try {
    await refreshGoldPriceCache({ forceRemote: true })
    showFeedback('黄金信息已刷新', 'success')
  } catch (error) {
    const message = getErrorMessage(error, '黄金信息刷新失败')
    showFeedback(message, 'error')
  } finally {
    isRefreshingGold.value = false
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
      ? (goldAccounts.value.find(
          (item) => item.id === editingAccountId.value,
        ) ?? null)
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

  isDeletingAccount.value = true
  deleteError.value = ''

  try {
    await deleteAccount(id)
    closeDeleteConfirmModal(true)
    showFeedback('删除成功', 'success')
    await loadGoldAccount()
  } catch (error) {
    const message = getErrorMessage(error, '账户删除失败')
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

function decorateHolding(item: GoldAccountHolding, price: number) {
  if (!Number.isFinite(price) || price <= 0) {
    return item
  }

  const weight = Number(item.weight ?? 0)
  const purchaseAmount = Number(item.purchaseAmount ?? 0)
  const marketValue = Number((weight * price).toFixed(2))
  const holdingProfit = Number((marketValue - purchaseAmount).toFixed(2))

  return {
    ...item,
    currentPrice: price,
    marketValue,
    holdingProfit,
  }
}

function mergeAccountHolding(
  previous: GoldAccountHolding | undefined,
  current: GoldAccountHolding,
): GoldAccountHolding {
  if (!previous) {
    return current
  }

  const previousWeight = Number(previous.weight ?? 0)
  const currentWeight = Number(current.weight ?? 0)
  const totalWeight = Number((previousWeight + currentWeight).toFixed(6))
  const purchaseAmount = Number(
    ((previous.purchaseAmount ?? 0) + (current.purchaseAmount ?? 0)).toFixed(2),
  )
  const marketValue = Number(
    ((previous.marketValue ?? 0) + (current.marketValue ?? 0)).toFixed(2),
  )
  const holdingProfit = Number(
    ((previous.holdingProfit ?? 0) + (current.holdingProfit ?? 0)).toFixed(2),
  )
  const avgCostPrice =
    totalWeight > 0 ? Number((purchaseAmount / totalWeight).toFixed(2)) : 0

  return {
    ...previous,
    currentPrice: Number(current.currentPrice ?? previous.currentPrice ?? 0),
    purchaseAmount,
    weight: totalWeight,
    holdingProfit,
    marketValue,
    avgCostPrice,
    createdAt: pickLatestCreatedAt(previous.createdAt, current.createdAt),
  }
}

function pickLatestCreatedAt(previous: string | undefined, current: string) {
  if (!previous) {
    return current
  }

  const previousTime = new Date(previous).getTime()
  const currentTime = new Date(current).getTime()

  if (Number.isNaN(previousTime)) {
    return current
  }

  return currentTime >= previousTime ? current : previous
}

function buildSummary(
  items: GoldAccountHolding[],
  cumulativeProfit: number,
): GoldAccountSummary {
  const totalWeight = items.reduce(
    (total, item) => total + Number(item.weight ?? 0),
    0,
  )
  const purchaseTotal = items.reduce(
    (total, item) => total + Number(item.purchaseAmount ?? 0),
    0,
  )
  const estimatedValue = items.reduce(
    (total, item) => total + Number(item.marketValue ?? 0),
    0,
  )
  const estimatedProfit = items.reduce(
    (total, item) => total + Number(item.holdingProfit ?? 0),
    0,
  )

  return {
    totalWeight,
    averagePrice: totalWeight > 0 ? purchaseTotal / totalWeight : 0,
    purchaseTotal,
    estimatedValue,
    estimatedProfit,
    profitRate: purchaseTotal > 0 ? (estimatedProfit / purchaseTotal) * 100 : 0,
    cumulativeProfit: Number(cumulativeProfit ?? 0),
  }
}

function formatSummaryWeight(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatSignedAmount(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  return `${amount >= 0 ? '+' : '-'}${formatAmount(Math.abs(amount))}`
}

type DesktopRow = (typeof accountRows.value)[number]

function goldProfitToneClass(value: number | null | undefined) {
  const amount = Number(value ?? 0)
  if (!Number.isFinite(amount) || amount === 0) {
    return ''
  }
  return amount > 0 ? 'tone-up' : 'tone-down'
}
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace stagger-in">
    <PageHeader title="黄金账户">
      <template #right>
        <el-button type="primary" @click="openCreateModal">
          新增黄金账户
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
    <div class="pc-statistics">
      <el-card shadow="never">
        <small>黄金总克重</small>
        <strong>{{ formatSummaryWeight(displaySummary.totalWeight) }}</strong>
      </el-card>
      <el-card shadow="never">
        <small>买入金额</small>
        <strong>{{ formatAmount(displaySummary.purchaseTotal) }}</strong>
      </el-card>
      <el-card shadow="never">
        <small>估值</small>
        <strong>{{ formatAmount(displaySummary.estimatedValue) }}</strong>
      </el-card>
      <el-card shadow="never">
        <small>持有收益</small>
        <strong>
          {{ formatSignedAmount(displaySummary.estimatedProfit) }}
        </strong>
      </el-card>
    </div>
    <DesktopRecordList
      title="黄金账户列表"
      searchable
      search-placeholder="账户名称"
      :search-keys="['name']"
      :rows="accountRows"
      :loading="isLoading"
      row-key="id"
    >
      <template #default="{ row }: { row: DesktopRow }">
        <article class="record-card gold-account-card">
          <div class="record-card-head">
            <div class="record-card-title">
              <span>{{ row.name }}</span>
            </div>
            <div class="record-card-aside">
              <el-tag
                v-if="realtimeGoldPrice > 0"
                size="small"
                effect="plain"
                class="gold-account-price-tag"
              >
                实时金价 {{ formatAmount(realtimeGoldPrice) }}/克
              </el-tag>
            </div>
          </div>
          <div class="gold-account-weight">
            <span class="gold-account-weight-label">持有克重</span>
            <strong class="gold-account-weight-value">
              {{ formatSummaryWeight(row.weight) }}
            </strong>
            <span class="gold-account-weight-unit">克</span>
          </div>
          <dl class="record-card-body">
            <div>
              <dt>成本均价</dt>
              <dd>{{ formatAmount(row.avgCostPrice) }} 元/克</dd>
            </div>
            <div>
              <dt>买入金额</dt>
              <dd>{{ formatAmount(row.purchaseAmount) }}</dd>
            </div>
          </dl>
          <div
            class="gold-account-profit"
            :class="goldProfitToneClass(row.holdingProfit)"
          >
            <span>持有收益</span>
            <strong>{{ formatSignedAmount(row.holdingProfit) }}</strong>
          </div>
          <div class="record-card-foot">
            <el-button link type="primary" @click="handleAccountClick(row.id)">
              持仓
            </el-button>
            <el-button link type="primary" @click="openEditModal(row.account)">
              编辑
            </el-button>
            <el-button
              link
              type="danger"
              @click="openDeleteConfirmModal(row.account)"
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
    <CommonModal v-model="showCreateAccountModal" :title="accountModalTitle">
      <el-form
        class="gold-account-form"
        @submit.prevent="saveGoldAccount"
        label-position="top"
      >
        <CommonInput
          v-model="formName"
          label="账户名称"
          placeholder="例如：工商银行积存金"
        />
        <CommonInput
          v-model="formRemark"
          label="备注"
          placeholder="例如：长期配置"
        />
        <CommonSwitch v-model="includeInNetWorth" label="是否计入总资产" />
        <p v-if="accountFormError" class="gold-account-form-error">
          {{ accountFormError }}
        </p>
      </el-form>
      <template #footer>
        <div class="pc-toolbar gold-account-actions">
          <CommonButton
            variant="secondary"
            type="button"
            :disabled="isSavingAccount"
            @click="closeCreateModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            type="button"
            :disabled="isSavingAccount"
            @click="saveGoldAccount"
          >
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
      <p v-if="deleteError" class="gold-account-form-error">
        {{ deleteError }}
      </p>
      <template #footer>
        <div class="pc-toolbar gold-account-actions">
          <CommonButton
            variant="secondary"
            type="button"
            :disabled="isDeletingAccount"
            @click="closeDeleteConfirmModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            type="button"
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

<style scoped>
/* 黄金账户卡：金色左侧饰条 + 克重大数字金色渐变（红涨绿跌收益） */
.gold-account-card::before {
  content: '';
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: linear-gradient(180deg, #e2b64f, #b8860b);
}

.gold-account-price-tag {
  color: #8a6417;
  border-color: rgba(184, 134, 11, 0.35);
  background: rgba(226, 182, 79, 0.12);
}

.gold-account-weight {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.gold-account-weight-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.gold-account-weight-value {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  background: linear-gradient(120deg, #b8860b, #e2b64f);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.gold-account-weight-unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.gold-account-profit {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.gold-account-profit strong {
  font-size: 15px;
  font-weight: 600;
}

/* 中国市场惯例：盈利红、亏损绿 */
.gold-account-profit.tone-up strong {
  color: var(--color-danger);
}

.gold-account-profit.tone-down strong {
  color: var(--color-success);
}
</style>
