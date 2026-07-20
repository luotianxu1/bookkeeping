<script setup lang="ts">
// 黄金账户页：展示黄金账户汇总，并支持黄金账户列表增删改查。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
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
  getGoldLiquidations,
  getGoldAccountSummary,
  updateAccount,
  type Account,
  type AccountType,
  type GoldAccountHolding,
  type GoldLiquidation,
  type GoldAccountSummary,
} from '@/api/modules/finance'
import { refreshGoldPriceCache, useGoldPriceCache } from '@/utils/gold-price-cache'
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

const accountModalTitle = computed(() => (editingAccountId.value ? '修改黄金账户' : '新增黄金账户'))
const realtimeGoldPrice = computed(() => Number(goldPrice.value?.price ?? 0))
const realtimeGoldUpdatedAt = computed(() => formatGoldUpdatedAt(goldPrice.value?.updatedAt))
const holdingsByAccountId = computed(() =>
  displayHoldings.value.reduce<Record<number, GoldAccountHolding>>((result, item) => {
    result[item.accountId] = mergeAccountHolding(result[item.accountId], item)
    return result
  }, {}),
)
const displayHoldings = computed(() => holdings.value.map((item) => decorateHolding(item, realtimeGoldPrice.value)))
const displaySummary = computed(() => (
  realtimeGoldPrice.value > 0
    ? buildSummary(displayHoldings.value, liquidation.value.cumulativeProfit || summary.value.cumulativeProfit)
    : {
      ...summary.value,
      cumulativeProfit: liquidation.value.cumulativeProfit || summary.value.cumulativeProfit,
    }
))
const accountRows = computed(() =>
  goldAccounts.value.map((account) => {
    const accountHolding = holdingsByAccountId.value[account.id]

    return {
      id: account.id,
      account,
      name: account.name,
      avgCostPrice: Number(accountHolding?.avgCostPrice ?? 0),
      purchaseAmount: Number(accountHolding?.purchaseAmount ?? 0),
      weight: Number(accountHolding?.weight ?? 0),
      holdingProfit: Number(accountHolding?.holdingProfit ?? 0),
      createdAt: accountHolding?.createdAt ?? null,
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
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [typeList, summaryData, holdingList, liquidationData] = await Promise.all([
      getAccountTypes({ status: 'active' }),
      getGoldAccountSummary(currentUser.id),
      getGoldAccountHoldings(currentUser.id),
      getGoldLiquidations(currentUser.id),
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
    liquidation.value = liquidationData
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

async function refreshGoldData() {
  if (isRefreshingGold.value) {
    return
  }

  isRefreshingGold.value = true

  try {
    await refreshGoldPriceCache()
    showFeedback('黄金信息已刷新', 'success')
  } catch (error) {
    const message = error instanceof Error ? error.message : '黄金信息刷新失败'
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
    closeDeleteConfirmModal(true)
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
  const purchaseAmount = Number(((previous.purchaseAmount ?? 0) + (current.purchaseAmount ?? 0)).toFixed(2))
  const marketValue = Number(((previous.marketValue ?? 0) + (current.marketValue ?? 0)).toFixed(2))
  const holdingProfit = Number(((previous.holdingProfit ?? 0) + (current.holdingProfit ?? 0)).toFixed(2))
  const avgCostPrice = totalWeight > 0 ? Number((purchaseAmount / totalWeight).toFixed(2)) : 0

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

function buildSummary(items: GoldAccountHolding[], cumulativeProfit: number): GoldAccountSummary {
  const totalWeight = items.reduce((total, item) => total + Number(item.weight ?? 0), 0)
  const purchaseTotal = items.reduce((total, item) => total + Number(item.purchaseAmount ?? 0), 0)
  const estimatedValue = items.reduce((total, item) => total + Number(item.marketValue ?? 0), 0)
  const estimatedProfit = items.reduce((total, item) => total + Number(item.holdingProfit ?? 0), 0)

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

function formatAmount(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatCompactWeight(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })
}

function formatHoldingMeta(weight: number | null | undefined, amount: number | null | undefined) {
  return `${formatCompactWeight(weight)}克 ${formatAmount(amount)}元`
}

function formatGoldUpdatedAt(value: string | null | undefined) {
  if (!value) {
    return '等待刷新'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '刚刚更新'
  }

  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

function formatCreatedDate(value: string | null | undefined) {
  if (!value) {
    return '--'
  }

  const matched = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (matched) {
    return `${matched[1]}.${matched[2]}.${matched[3]}`
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}.${month}.${day}`
}

function formatSummaryWeight(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
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
      <PageHeader title="黄金账户" back-label="返回账户管理">
        <template #right>
          <div class="gold-account-header-actions">
            <CommonHeaderActionButton
              :label="isManageMode ? '完成管理' : '管理黄金账户'"
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
            <CommonHeaderRefreshButton
              label="刷新黄金信息"
              :loading="isRefreshingGold"
              @click="refreshGoldData"
            />
          </div>
        </template>
      </PageHeader>
    </header>

    <p v-if="pageError" class="gold-account-message gold-account-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="gold-account-summary">
        <div class="summary-head">
          <div class="summary-head-main">
            <span class="summary-weight-label">总重量(克)</span>
            <strong class="summary-weight-value">{{ formatSummaryWeight(displaySummary.totalWeight) }}</strong>
          </div>
          <div class="gold-price-chip" aria-label="当前实时金价">
            <span class="gold-price-chip-label">实时金价</span>
            <strong class="gold-price-chip-value">
              {{ realtimeGoldPrice > 0 ? `${formatAmount(realtimeGoldPrice)} 元/克` : '--' }}
            </strong>
            <span class="gold-price-chip-time">{{ realtimeGoldUpdatedAt }}</span>
          </div>
        </div>

        <div class="summary-grid">
          <article>
            <span>平均克价(元)</span>
            <strong>{{ formatAmount(displaySummary.averagePrice) }}</strong>
          </article>
          <article>
            <span>购入总价(元)</span>
            <strong>{{ formatAmount(displaySummary.purchaseTotal) }}</strong>
          </article>
          <article>
            <span>预估价值(元)</span>
            <strong>{{ formatAmount(displaySummary.estimatedValue) }}</strong>
          </article>
          <article>
            <span>预估收益(元)</span>
            <strong :class="{ up: displaySummary.estimatedProfit >= 0, negative: displaySummary.estimatedProfit < 0 }">
              {{ formatSignedAmount(displaySummary.estimatedProfit) }}
            </strong>
          </article>
          <article>
            <span>收益率(%)</span>
            <strong :class="{ up: displaySummary.profitRate >= 0, negative: displaySummary.profitRate < 0 }">
              {{ formatRate(displaySummary.profitRate) }}
            </strong>
          </article>
          <article>
            <span>累计收益(元)</span>
            <strong :class="{ up: displaySummary.cumulativeProfit >= 0, negative: displaySummary.cumulativeProfit < 0 }">
              {{ formatSignedAmount(displaySummary.cumulativeProfit) }}
            </strong>
          </article>
        </div>
      </section>

      <section class="gold-holding-list">
        <template v-if="hasAccounts">
          <article
            v-for="row in accountRows"
            :key="row.id"
            class="gold-account-row"
          >
            <button
              v-if="isManageMode"
              type="button"
              class="gold-remove-trigger"
              :aria-label="`删除${row.name}`"
              @click="openDeleteConfirmModal(row.account)"
            >
              <span class="gold-remove-dash"></span>
            </button>

            <button
              v-if="isManageMode"
              type="button"
              class="gold-edit-trigger"
              :aria-label="`修改${row.name}`"
              @click="openEditModal(row.account)"
            >
              ✎
            </button>

            <button
              type="button"
              :class="['gold-holding-card', { 'manage-shifted': isManageMode }]"
              @click="handleAccountClick(row.id)"
            >
              <span class="price-tag">
                {{
                  row.avgCostPrice > 0
                    ? `${formatAmount(row.avgCostPrice)}元/克`
                    : '暂无持仓'
                }}
              </span>
              <div class="holding-top">
                <strong>{{ row.name }}</strong>
                <span class="holding-profit-inline" :class="{ negative: row.holdingProfit < 0 }">
                  <span>收益:</span>
                  <AmountText tag="strong" tone="inherit" show-sign :value="formatAmount(row.holdingProfit)" />
                </span>
              </div>
              <div class="holding-bottom">
                <span class="holding-meta">{{ formatHoldingMeta(row.weight, row.purchaseAmount) }}</span>
                <span>{{ formatCreatedDate(row.createdAt) }}</span>
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
