<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import { formatAmount } from '@/business/utils/format'
// 投资账户详情页：展示单个投资账户的汇总、持仓列表和新增持仓。
// 持仓展示的纯函数见 ./holdingDisplay，新增资产弹窗逻辑见 ./useAddAsset。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CommonButton from '@/business/components/common/CommonButton/index.vue'

import CommonInput from '@/business/components/common/CommonInput/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import {
  getAccounts,
  getInvestmentAutoInvestPlans,
  getInvestmentPositions,
  getInvestmentSummary,
  getInvestmentTransactions,
  runInvestmentQuoteSyncTask,
  updateAccount,
  type Account,
  type InvestmentAutoInvestPlan,
  type InvestmentPosition,
  type InvestmentSummary,
  type InvestmentTransaction,
} from '@/business/api/modules/finance'
import {
  DEFAULT_INVESTMENT_ACCOUNT_ICON,
  allAccountIconOptions,
  resolveInvestmentAccountIcon,
} from '@/business/data/account-icons'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage, isCancelRequest } from '@/business/api/request'
import { useRequestAbort } from '@/business/composables/useRequestAbort'
import { useVisibilityPolling } from '@/business/composables/useVisibilityPolling'
import {
  compareHoldingsByProfitRate,
  getHoldingDayProfitText,
  getHoldingDayProfitTone,
  getHoldingMarketValue,
  getHoldingProfitValue,
  getHoldingQuantityTag,
  isAfterQuoteRefreshTime,
  isInitialFundSubscriptionTransaction,
  shouldDisplayHolding,
} from './holdingDisplay'
import { useAddAsset } from './useAddAsset'

const router = useRouter()
const route = useRoute()
// 数据加载防竞态：新加载先 abort 旧请求，旧响应按代际号静默丢弃
const { signalFor } = useRequestAbort()
let loadSeq = 0

const investmentTabs = ['A股', '基金']
const activeTab = ref('A股')
const showAccountSettingsModal = ref(false)
const isLoading = ref(true)
const pageError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const currentTime = ref(new Date())
const investmentAccounts = ref<Account[]>([])
const fundingAccounts = ref<Account[]>([])
const summary = ref<InvestmentSummary>({
  userId: 0,
  totalMarketValue: 0,
  dayProfit: 0,
  dayProfitRate: 0,
  holdingProfit: 0,
  holdingProfitRate: 0,
  cumulativeProfit: 0,
  cumulativeProfitRate: 0,
  lastSyncedAt: null as string | null,
})
const positions = ref<InvestmentPosition[]>([])
const transactions = ref<InvestmentTransaction[]>([])
const autoInvestPlans = ref<InvestmentAutoInvestPlan[]>([])
const isRefreshingQuotes = ref(false)
const isSavingAccountSettings = ref(false)
const accountSettingsName = ref('')
const accountSettingsIcon = ref(DEFAULT_INVESTMENT_ACCOUNT_ICON)
const accountSettingsRemark = ref('')
const accountSettingsIncludeInNetWorth = ref(true)
const accountSettingsError = ref('')

const {
  showAddModal,
  isSaving,
  formError,
  addAssetKeyword,
  addAssetFundingAccount,
  addAssetDividendType,
  addAssetQuantity,
  addAssetAmount,
  addAssetSubscriptionTimeSlot,
  isLookingUpProduct,
  addAssetSearchResults,
  fundingAccountOptions,
  subscriptionTimeSlotOptions,
  dividendTypeOptions,
  isFundSubscriptionDraft,
  isQuantityBasedDraft,
  saveAsset,
  lookupProductByKeyword,
  getAddAssetResultKey,
  selectAddAssetProduct,
  getAddAssetResultLabel,
  getAddAssetResultMeta,
  isAddAssetProductSelected,
  openAddModal,
  closeAddModal,
} = useAddAsset({
  fundingAccounts,
  getDefaultCategory: () => (activeTab.value === 'A股' ? 'stock' : 'fund'),
  getAccountId: () => selectedAccountId.value,
  onSaved: () => loadInvestmentData(),
  showFeedback,
})

const visiblePositions = computed(() =>
  positions.value.filter((item) => shouldDisplayHolding(item)),
)

const holdings = computed(() => {
  const targetType = activeTab.value === 'A股' ? 'stock' : 'fund'

  return visiblePositions.value
    .filter((item) => item.productType === targetType)
    .slice()
    .sort(compareHoldingsByProfitRate)
})
const showInvestmentTabSwitch = computed(() => {
  const hasStocks = visiblePositions.value.some(
    (item) => item.productType === 'stock',
  )
  const hasFunds = visiblePositions.value.some(
    (item) => item.productType === 'fund',
  )
  return hasStocks && hasFunds
})

const pendingAdditionalAmountsByPositionId = computed(() => {
  const pendingMap = new Map<number, number>()
  for (const entry of transactions.value) {
    if (
      entry.settlementStatus !== 'pending' ||
      entry.tradeType !== 'buy' ||
      !entry.positionId ||
      isInitialFundSubscriptionTransaction(entry)
    ) {
      continue
    }
    const nextAmount =
      (pendingMap.get(entry.positionId) ?? 0) + Number(entry.amount || 0)
    pendingMap.set(entry.positionId, nextAmount)
  }
  return pendingMap
})

const hasRefreshableHoldings = computed(() =>
  visiblePositions.value.some(
    (item) => item.productType === 'fund' || item.productType === 'stock',
  ),
)
const shouldShowQuoteRefreshButton = computed(
  () =>
    hasRefreshableHoldings.value && isAfterQuoteRefreshTime(currentTime.value),
)

const totalSummaryProfit = computed(
  () =>
    Number(summary.value.cumulativeProfit ?? 0) +
    Number(summary.value.holdingProfit ?? 0),
)
const totalSummaryProfitRate = computed(() => {
  const currentCostAmount =
    Number(summary.value.totalMarketValue ?? 0) -
    Number(summary.value.holdingProfit ?? 0)
  if (!Number.isFinite(currentCostAmount) || currentCostAmount <= 0) {
    return 0
  }
  return (totalSummaryProfit.value / currentCostAmount) * 100
})
const summaryDayProfitText = computed(() =>
  summary.value.dayProfit === null || summary.value.dayProfit === undefined
    ? '--'
    : formatAmount(summary.value.dayProfit),
)
const summaryDayProfitRateText = computed(() =>
  summary.value.dayProfitRate === null ||
  summary.value.dayProfitRate === undefined
    ? '--'
    : formatPercentage(summary.value.dayProfitRate),
)

const summaryMetrics = computed(() => [
  { label: '持仓盈亏', value: summary.value.holdingProfit },
  { label: '持仓盈亏率', value: summary.value.holdingProfitRate, isRate: true },
  { label: '累计总收益', value: totalSummaryProfit.value },
  { label: '累计总收益率', value: totalSummaryProfitRate.value, isRate: true },
])

onMounted(() => {
  void loadInvestmentData()
  currentTime.value = new Date()
})

// 时钟轮询感知页面可见性：后台标签页跳过，恢复可见立即校准
useVisibilityPolling(() => {
  currentTime.value = new Date()
}, 30 * 1000)

watch(
  () => route.params.accountId,
  () => {
    void loadInvestmentData()
  },
)

function openAccountSettingsModal() {
  const account = selectedAccount.value
  if (!account) {
    return
  }

  accountSettingsName.value = account.name
  accountSettingsIcon.value = resolveInvestmentAccountIcon(account.icon)
  accountSettingsRemark.value = account.remark ?? ''
  accountSettingsIncludeInNetWorth.value = account.includeInNetWorth
  accountSettingsError.value = ''
  showAccountSettingsModal.value = true
}

function closeAccountSettingsModal(force = false) {
  if (isSavingAccountSettings.value && !force) {
    return
  }

  showAccountSettingsModal.value = false
  accountSettingsName.value = ''
  accountSettingsIcon.value = DEFAULT_INVESTMENT_ACCOUNT_ICON
  accountSettingsRemark.value = ''
  accountSettingsIncludeInNetWorth.value = true
  accountSettingsError.value = ''
}

async function saveAccountSettings() {
  if (isSavingAccountSettings.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const account = selectedAccount.value
  const trimmedName = accountSettingsName.value.trim()
  const trimmedRemark = accountSettingsRemark.value.trim()

  if (!currentUser || !account) {
    accountSettingsError.value = '当前投资账户不存在'
    return
  }
  if (!trimmedName) {
    accountSettingsError.value = '请输入账户名称'
    return
  }

  isSavingAccountSettings.value = true
  accountSettingsError.value = ''

  try {
    await updateAccount(account.id, {
      userId: currentUser.id,
      accountTypeId: account.accountTypeId,
      contactId: account.contactId ?? null,
      name: trimmedName,
      icon: accountSettingsIcon.value,
      color: account.color ?? null,
      currencyCode: account.currencyCode || 'CNY',
      currentBalance: account.currentBalance,
      includeInNetWorth: accountSettingsIncludeInNetWorth.value,
      sortOrder: account.sortOrder,
      status: account.status || 'active',
      remark: trimmedRemark || null,
    })

    closeAccountSettingsModal(true)
    showFeedback('投资账户已更新', 'success')
    await loadInvestmentData()
  } catch (error) {
    const message = getErrorMessage(error, '投资账户保存失败')
    accountSettingsError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingAccountSettings.value = false
  }
}

function parseAccountId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

const selectedAccountId = computed(() => parseAccountId(route.params.accountId))
const selectedAccount = computed(
  () =>
    investmentAccounts.value.find(
      (account) => account.id === selectedAccountId.value,
    ) ?? null,
)

async function loadInvestmentData() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看投资详情'
    isLoading.value = false
    return
  }

  if (!selectedAccountId.value) {
    pageError.value = '投资账户不存在'
    isLoading.value = false
    return
  }

  const seq = ++loadSeq
  const signal = signalFor()

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }, { signal }),
    ])
    investmentAccounts.value = accountList.filter(
      (account) => account.accountTypeCode === 'investment',
    )
    fundingAccounts.value = accountList.filter(
      (account) => account.accountTypeCode === 'cash',
    )

    const targetAccount = investmentAccounts.value.find(
      (account) => account.id === selectedAccountId.value,
    )
    if (!targetAccount) {
      positions.value = []
      summary.value = {
        userId: currentUser.id,
        totalMarketValue: 0,
        dayProfit: 0,
        dayProfitRate: 0,
        holdingProfit: 0,
        holdingProfitRate: 0,
        cumulativeProfit: 0,
        cumulativeProfitRate: 0,
        lastSyncedAt: null,
      }
      transactions.value = []
      autoInvestPlans.value = []
      pageError.value = '投资账户不存在'
      return
    }

    const [summaryData, positionList, transactionList, autoInvestPlanList] =
      await Promise.all([
        getInvestmentSummary(
          { userId: currentUser.id, accountId: targetAccount.id },
          { signal },
        ),
        getInvestmentPositions(
          { userId: currentUser.id, accountId: targetAccount.id },
          { signal },
        ),
        // 该列表仅用于汇总各持仓的待确认金额，服务端过滤 pending 避免全量拉取交易流水
        getInvestmentTransactions(
          {
            userId: currentUser.id,
            accountId: targetAccount.id,
            settlementStatus: 'pending',
          },
          { signal },
        ),
        getInvestmentAutoInvestPlans(
          { userId: currentUser.id, accountId: targetAccount.id },
          { signal },
        ),
      ])

    if (seq !== loadSeq) {
      return
    }

    summary.value = summaryData
    positions.value = positionList
    transactions.value = transactionList
    autoInvestPlans.value = autoInvestPlanList
    syncActiveInvestmentTab()
  } catch (error) {
    if (isCancelRequest(error) || seq !== loadSeq) {
      return
    }
    pageError.value = getErrorMessage(error, '投资账户加载失败')
  } finally {
    if (seq === loadSeq) {
      isLoading.value = false
    }
  }
}

async function refreshQuoteData() {
  if (!shouldShowQuoteRefreshButton.value || isRefreshingQuotes.value) {
    return
  }
  const currentUser = getStoredCurrentUser()
  if (!currentUser || !selectedAccountId.value) {
    showFeedback('当前投资账户不存在', 'error')
    return
  }

  isRefreshingQuotes.value = true
  try {
    await runInvestmentQuoteSyncTask({
      userId: currentUser.id,
      accountId: selectedAccountId.value,
    })
    showFeedback('已提交刷新任务，请稍后刷新页面', 'success')
  } catch (error) {
    const message = getErrorMessage(error, '投资持仓刷新失败')
    showFeedback(message, 'error')
  } finally {
    isRefreshingQuotes.value = false
  }
}

function syncActiveInvestmentTab() {
  const hasStocks = visiblePositions.value.some(
    (item) => item.productType === 'stock',
  )
  const hasFunds = visiblePositions.value.some(
    (item) => item.productType === 'fund',
  )

  if (!investmentTabs.includes(activeTab.value)) {
    activeTab.value = 'A股'
  }

  if (!hasStocks && hasFunds) {
    activeTab.value = '基金'
    return
  }

  if (hasStocks) {
    activeTab.value = 'A股'
    return
  }

  if (hasFunds) {
    activeTab.value = '基金'
  }
}

function openInvestmentDetail(positionId: number) {
  router.push({
    path: '/finance/accounts/investment/detail',
    query: { positionId },
  })
}

function openLiquidationList() {
  if (!selectedAccountId.value) {
    return
  }
  router.push(
    `/finance/accounts/investment/${selectedAccountId.value}/liquidations`,
  )
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

type DetailTableRow = (typeof holdings.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader :title="selectedAccount?.name || '投资持仓'">
      <template #right>
        <el-button @click="openLiquidationList">清仓明细</el-button>
        <el-button
          v-if="shouldShowQuoteRefreshButton"
          :loading="isRefreshingQuotes"
          @click="refreshQuoteData"
        >
          刷新行情
        </el-button>
        <el-button v-if="selectedAccount" @click="openAccountSettingsModal">
          账户设置
        </el-button>
        <el-button type="primary" @click="openAddModal">新增资产</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[
        { label: '总市值', value: formatAmount(summary.totalMarketValue) },
        { label: '当日收益', value: summaryDayProfitText },
        { label: '当日收益率', value: summaryDayProfitRateText },
      ]"
    />
    <MetricStrip
      :items="
        summaryMetrics.map((metric) => ({
          label: metric.label,
          value: metric.isRate
            ? formatPercentage(metric.value)
            : formatAmount(metric.value),
        }))
      "
    />
    <DesktopTable :rows="holdings" title="持仓列表" searchable>
      <template #toolbar>
        <SegmentedControl
          v-if="showInvestmentTabSwitch"
          v-model="activeTab"
          :options="investmentTabs"
          label="投资分类筛选"
        />
      </template>
      <!-- @vue-generic {DetailTableRow} -->
      <el-table-column
        label="产品"
        prop="productName"
        min-width="200"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DetailTableRow} -->
      <el-table-column label="市值 / 待确认" min-width="180">
        <template #default="{ row }: { row: DetailTableRow }">
          {{
            getHoldingMarketValue(
              row,
              pendingAdditionalAmountsByPositionId.get(row.id) ?? 0,
            )
          }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DetailTableRow} -->
      <el-table-column label="份额 / 股数" min-width="140">
        <template #default="{ row }: { row: DetailTableRow }">
          {{ getHoldingQuantityTag(row) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DetailTableRow} -->
      <el-table-column label="当日收益" min-width="140">
        <template #default="{ row }: { row: DetailTableRow }">
          <AmountText
            :value="getHoldingDayProfitText(row)"
            :tone="getHoldingDayProfitTone(row)"
          />
        </template>
      </el-table-column>
      <!-- @vue-generic {DetailTableRow} -->
      <el-table-column label="持仓收益" min-width="150">
        <template #default="{ row }: { row: DetailTableRow }">
          <AmountText :value="getHoldingProfitValue(row)" />
        </template>
      </el-table-column>
      <!-- @vue-generic {DetailTableRow} -->
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }: { row: DetailTableRow }">
          <div class="pc-row-actions">
            <el-button
              link
              type="primary"
              @click="openInvestmentDetail(row.id)"
            >
              详情 / 交易
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
    <CommonModal
      v-model="showAccountSettingsModal"
      title="修改投资账户"
      :close-on-overlay="!isSavingAccountSettings"
      @close="closeAccountSettingsModal()"
    >
      <div
        class="pc-form-grid investment-add-modal-form investment-account-settings-form"
      >
        <CommonInput
          v-model="accountSettingsName"
          label="账户名称"
          placeholder="输入投资账户名称"
        />
        <CommonSelect
          v-model="accountSettingsIcon"
          label="账户图标"
          :options="allAccountIconOptions"
        />
        <CommonInput
          v-model="accountSettingsRemark"
          label="备注"
          placeholder="输入账户说明"
        />
        <CommonSwitch
          v-model="accountSettingsIncludeInNetWorth"
          label="是否计入总资产"
        />
        <p v-if="accountSettingsError" class="investment-add-error">
          {{ accountSettingsError }}
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar investment-add-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSavingAccountSettings"
            @click="closeAccountSettingsModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSavingAccountSettings"
            @click="saveAccountSettings"
          >
            {{ isSavingAccountSettings ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showAddModal"
      title="添加资产"
      size="compact"
      :show-close="false"
    >
      <div class="pc-form-grid investment-add-modal-form">
        <div class="investment-search-dropdown">
          <label
            class="pc-field investment-search-field"
            aria-label="输入代码或名称"
          >
            <DesktopInput
              v-model="addAssetKeyword"
              type="search"
              inputmode="search"
              placeholder="输入代码或名称"
              @keydown.enter.prevent="lookupProductByKeyword()"
            />
            <el-button
              native-type="button"
              :disabled="isLookingUpProduct"
              @click="lookupProductByKeyword()"
            >
              <span aria-hidden="true">⌕</span>
              {{ isLookingUpProduct ? '搜索中' : '搜索' }}
            </el-button>
          </label>
          <div
            v-if="addAssetSearchResults.length > 0"
            class="investment-search-results"
            aria-label="搜索结果"
          >
            <el-button
              v-for="product in addAssetSearchResults"
              :key="getAddAssetResultKey(product)"
              native-type="button"
              :class="[
                'investment-search-result-item',
                { active: isAddAssetProductSelected(product) },
              ]"
              @click="selectAddAssetProduct(getAddAssetResultKey(product))"
              class="pc-content-button"
            >
              <strong>{{ getAddAssetResultLabel(product) }}</strong>
              <span>{{ getAddAssetResultMeta(product) }}</span>
            </el-button>
          </div>
        </div>
        <label
          v-if="isFundSubscriptionDraft"
          class="pc-field investment-add-modal-field"
        >
          <span>申购时点</span>
          <SegmentedControl
            v-model="addAssetSubscriptionTimeSlot"
            :options="subscriptionTimeSlotOptions"
            label="基金申购时点"
          />
        </label>
        <label
          v-if="isFundSubscriptionDraft"
          class="pc-field investment-add-modal-field"
        >
          <span>分红方式</span>
          <SegmentedControl
            v-model="addAssetDividendType"
            :options="dividendTypeOptions"
            label="基金分红方式"
          />
        </label>
        <CommonSelect
          v-model="addAssetFundingAccount"
          :label="
            isFundSubscriptionDraft && addAssetDividendType === 'cash'
              ? '现金分红到账及申购账户'
              : '申购资金账户（选填）'
          "
          :options="fundingAccountOptions"
        />
        <label
          v-if="isQuantityBasedDraft"
          class="pc-field investment-add-modal-field"
        >
          <span>持仓份数</span>
          <DesktopInput
            v-model="addAssetQuantity"
            class="investment-field-control"
            type="number"
            min="0"
            step="0.01"
            inputmode="decimal"
            placeholder="0.00"
            aria-label="持仓份数"
          />
        </label>
        <label class="pc-field investment-add-modal-field">
          <span>{{ isFundSubscriptionDraft ? '买入金额' : '成本价' }}</span>
          <DesktopInput
            v-model="addAssetAmount"
            class="investment-field-control"
            type="number"
            min="0"
            step="0.01"
            inputmode="decimal"
            placeholder="0.00"
            :aria-label="isFundSubscriptionDraft ? '买入金额' : '成本价'"
          />
        </label>
        <p v-if="formError" class="investment-add-error">{{ formError }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar investment-add-modal-actions">
          <el-button
            class="investment-modal-button secondary"
            native-type="button"
            :disabled="isSaving"
            @click="closeAddModal"
          >
            取消
          </el-button>
          <el-button
            class="investment-modal-button primary"
            native-type="button"
            :disabled="isSaving"
            @click="saveAsset"
            type="primary"
          >
            {{ isSaving ? '保存中...' : '确认添加' }}
          </el-button>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
