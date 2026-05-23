<script setup lang="ts">
// 投资账户详情页：展示单个投资账户的汇总、持仓列表和新增持仓。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import {
  createInvestmentPosition,
  getAccounts,
  getInvestmentPositions,
  getInvestmentProducts,
  getInvestmentSummary,
  type Account,
  type InvestmentPosition,
  type InvestmentProduct,
  type InvestmentProductType,
  type InvestmentSummary,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const router = useRouter()
const route = useRoute()

const investmentTabs = ['A股', '基金']
const activeTab = ref('A股')
const showAddModal = ref(false)
const isLoading = ref(false)
const isSaving = ref(false)
const pageError = ref('')
const formError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
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

const addAssetKeyword = ref('')
const addAssetName = ref('')
const addAssetSymbol = ref('')
const addAssetMarket = ref('')
const addAssetCategory = ref<InvestmentProductType>('fund')
const addAssetFundingAccount = ref('')
const addAssetAmount = ref('')
const addAssetCurrentPrice = ref('')
const isLookingUpProduct = ref(false)
const productLookupMessage = ref('')
let productLookupTimer: number | undefined
let isFillingProduct = false

const fundingAccountOptions = computed(() =>
  fundingAccounts.value.map((account) => ({
    label: `${account.name}（余额 ${formatAmount(account.currentBalance)}）`,
    value: String(account.id),
  })),
)

const holdings = computed(() => {
  if (activeTab.value === 'A股') {
    return positions.value.filter((item) => item.productType === 'stock')
  }

  return positions.value.filter((item) => item.productType === 'fund')
})

const summaryMetrics = computed(() => [
  { label: '持仓盈亏', value: summary.value.holdingProfit },
  { label: '持仓盈亏率', value: summary.value.holdingProfitRate, isRate: true },
  { label: '累计盈亏', value: summary.value.cumulativeProfit },
  { label: '累计盈亏率', value: summary.value.cumulativeProfitRate, isRate: true },
])

onMounted(() => {
  void loadInvestmentData()
})

watch(() => route.params.accountId, () => {
  void loadInvestmentData()
})

watch(addAssetKeyword, (nextKeyword) => {
  if (isFillingProduct) {
    return
  }

  window.clearTimeout(productLookupTimer)
  const keyword = nextKeyword.trim()
  productLookupMessage.value = ''

  if (keyword === `${addAssetSymbol.value} ${addAssetName.value}`.trim()) {
    return
  }

  if (keyword.length < 2) {
    clearProductFields()
    return
  }

  productLookupTimer = window.setTimeout(() => {
    lookupProductByKeyword(keyword)
  }, 400)
})

onBeforeUnmount(() => {
  window.clearTimeout(productLookupTimer)
})

function parseAccountId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

const selectedAccountId = computed(() => parseAccountId(route.params.accountId))
const selectedAccount = computed(() =>
  investmentAccounts.value.find((account) => account.id === selectedAccountId.value) ?? null,
)

async function loadInvestmentData() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看投资详情'
    return
  }

  if (!selectedAccountId.value) {
    pageError.value = '投资账户不存在'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }),
    ])
    investmentAccounts.value = accountList.filter((account) => account.accountTypeCode === 'investment')
    fundingAccounts.value = accountList.filter((account) => account.accountTypeCode === 'cash')

    const targetAccount = investmentAccounts.value.find((account) => account.id === selectedAccountId.value)
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
      pageError.value = '投资账户不存在'
      return
    }

    const [summaryData, positionList] = await Promise.all([
      getInvestmentSummary({ userId: currentUser.id, accountId: targetAccount.id }),
      getInvestmentPositions({ userId: currentUser.id, accountId: targetAccount.id }),
    ])

    summary.value = summaryData
    positions.value = positionList
    syncActiveInvestmentTab()
    if (!addAssetFundingAccount.value || !fundingAccounts.value.some((account) => String(account.id) === addAssetFundingAccount.value)) {
      addAssetFundingAccount.value = fundingAccounts.value[0] ? String(fundingAccounts.value[0].id) : ''
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '投资账户加载失败'
  } finally {
    isLoading.value = false
  }
}

function syncActiveInvestmentTab() {
  const hasStocks = positions.value.some((item) => item.productType === 'stock')
  const hasFunds = positions.value.some((item) => item.productType === 'fund')

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

async function saveAsset() {
  if (isSaving.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    formError.value = '请先登录后再新增投资'
    return
  }

  const accountId = selectedAccountId.value
  const fundingAccountId = Number(addAssetFundingAccount.value)
  const currentPrice = Number(addAssetCurrentPrice.value)
  const costAmount = Number(addAssetAmount.value)
  const quantity = currentPrice > 0 ? costAmount / currentPrice : 0

  if (!addAssetName.value.trim() || !addAssetSymbol.value.trim()) {
    formError.value = '请先搜索并选择有效资产'
    return
  }
  if (!accountId) {
    formError.value = '当前投资账户不存在'
    return
  }
  if (!Number.isFinite(fundingAccountId) || fundingAccountId <= 0) {
    formError.value = '请选择资金账户'
    return
  }
  if (!Number.isFinite(costAmount) || costAmount <= 0) {
    formError.value = '请输入买入金额'
    return
  }
  if (!Number.isFinite(currentPrice) || currentPrice <= 0) {
    formError.value = '未获取到当前价格，请重新搜索资产'
    return
  }
  if (!Number.isFinite(quantity) || quantity <= 0) {
    formError.value = '买入金额或当前价格不正确'
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    await createInvestmentPosition({
      userId: currentUser.id,
      accountId,
      fundingAccountId,
      product: {
        productType: addAssetCategory.value as InvestmentProductType,
        market: addAssetMarket.value.trim() || null,
        symbol: addAssetSymbol.value.trim(),
        name: addAssetName.value.trim(),
        currencyCode: 'CNY',
        unitName: addAssetCategory.value === 'stock' ? '股' : addAssetCategory.value === 'gold' ? '克' : '份',
        status: 'active',
        remark: null,
      },
      holdingQuantity: Number(quantity.toFixed(6)),
      availableQuantity: Number(quantity.toFixed(6)),
      frozenQuantity: 0,
      costAmount: Number(costAmount.toFixed(2)),
      currentPrice,
      includeInNetWorth: true,
      status: 'active',
      remark: null,
    })
    closeAddModal()
    showFeedback('新增成功', 'success')
    await loadInvestmentData()
  } catch (error) {
    const message = error instanceof Error ? error.message : '新增投资失败'
    formError.value = message
    showFeedback(message, 'error')
  } finally {
    isSaving.value = false
  }
}

async function lookupProductByKeyword(keyword = addAssetKeyword.value) {
  if (isLookingUpProduct.value) {
    return
  }

  const searchKeyword = keyword.trim()
  if (!searchKeyword) {
    formError.value = '请输入代码或名称'
    return
  }

  isLookingUpProduct.value = true
  productLookupMessage.value = '正在搜索资产...'
  formError.value = ''

  try {
    const products = await getInvestmentProducts({ keyword: searchKeyword })
    const normalizedKeyword = searchKeyword.toUpperCase()
    const matchedProduct = products.find((product) => product.symbol.toUpperCase() === normalizedKeyword)
      ?? products.find((product) => product.name.includes(searchKeyword))
      ?? products[0]

    if (!matchedProduct) {
      clearProductFields()
      productLookupMessage.value = '未找到该资产，请确认代码或名称'
      return
    }

    fillProductFields(matchedProduct)
    productLookupMessage.value = `已识别：${matchedProduct.name}`
  } catch (error) {
    productLookupMessage.value = error instanceof Error ? error.message : '代码识别失败'
  } finally {
    isLookingUpProduct.value = false
  }
}

function fillProductFields(product: InvestmentProduct) {
  isFillingProduct = true
  addAssetName.value = product.name
  addAssetSymbol.value = product.symbol
  addAssetKeyword.value = `${product.symbol} ${product.name}`
  isFillingProduct = false
  addAssetMarket.value = product.market || ''
  addAssetCategory.value = product.productType
  if (product.latestPrice && product.latestPrice > 0) {
    addAssetCurrentPrice.value = String(product.latestPrice)
  }
}

function clearProductFields() {
  addAssetName.value = ''
  addAssetSymbol.value = ''
  addAssetMarket.value = ''
  addAssetCurrentPrice.value = ''
}

function openAddModal() {
  resetAddForm()
  showAddModal.value = true
}

function closeAddModal() {
  showAddModal.value = false
  formError.value = ''
}

function resetAddForm() {
  addAssetKeyword.value = ''
  addAssetName.value = ''
  addAssetSymbol.value = ''
  addAssetMarket.value = ''
  addAssetCategory.value = 'fund'
  addAssetFundingAccount.value = fundingAccounts.value[0] ? String(fundingAccounts.value[0].id) : ''
  addAssetAmount.value = ''
  addAssetCurrentPrice.value = ''
  productLookupMessage.value = ''
  formError.value = ''
}

function openInvestmentDetail(positionId: number) {
  router.push({ path: '/finance/accounts/investment/detail', query: { positionId } })
}

function getProductTag(position: InvestmentPosition) {
  const map: Record<string, string> = {
    stock: 'A股',
    fund: '基金',
    bond: '债券',
    gold: '黄金',
    other: '其他',
  }
  return map[position.productType || 'other'] ?? '其他'
}

function formatCurrency(value: number, digits = 2) {
  const numeric = Number(value)
  const sign = numeric < 0 ? '-' : ''
  return `${sign}¥${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(numeric))}`
}

function formatQuantity(value: number, unitName?: string | null) {
  return `x ${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value)} ${unitName || '份'}`
}

function formatPrice(value: number) {
  return formatCurrency(value, 4)
}

function formatSyncDate(value?: string | null) {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--'
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day}`
}

function getAllocationPercent(position: InvestmentPosition) {
  const total = Number(summary.value.totalMarketValue)
  if (!Number.isFinite(total) || total <= 0) {
    return 0
  }
  const percent = (Number(position.marketValue) / total) * 100
  if (!Number.isFinite(percent)) {
    return 0
  }
  return Math.min(Math.max(percent, 0), 100)
}

function formatAmount(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}
</script>

<template>
  <section class="investment-account-page" aria-label="投资账户">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <PageHeader title="投资详情" back-to="/finance/accounts/investment" back-label="返回投资账户" />

    <p v-if="pageError" class="investment-message investment-message-error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" />

    <template v-else>
      <section class="investment-summary-card" aria-label="投资总览">
        <div class="investment-summary-top">
          <div class="investment-summary-main">
            <p>{{ selectedAccount?.name || '投资账户' }}</p>
            <AmountText tag="strong" :value="formatAmount(summary.totalMarketValue)" />
            <span>{{ selectedAccount?.remark?.trim() || `同步于 ${summary.lastSyncedAt ? new Date(summary.lastSyncedAt).toLocaleString('zh-CN') : '暂无'}` }}</span>
          </div>
          <div class="investment-summary-side">
            <span>今日盈亏</span>
            <AmountText tag="strong" :value="formatAmount(summary.dayProfit)" />
            <AmountText tag="span" :value="`${formatAmount(summary.dayProfitRate)}%`" />
          </div>
        </div>

        <div class="investment-metrics">
          <template v-for="(metric, index) in summaryMetrics" :key="metric.label">
            <div class="investment-metric-item">
              <span>{{ metric.label }}</span>
              <AmountText
                tag="strong"
                :value="metric.isRate ? `${formatAmount(metric.value)}%` : formatAmount(metric.value)"
              />
            </div>
            <div v-if="index !== summaryMetrics.length - 1" class="investment-metric-divider"></div>
          </template>
        </div>
      </section>

      <SegmentedControl v-model="activeTab" :options="investmentTabs" label="投资分类筛选" />

      <section class="investment-holdings" aria-label="持仓列表">
        <article
          v-for="holding in holdings"
          :key="holding.id"
          class="investment-holding-card"
          @click="openInvestmentDetail(holding.id)"
        >
          <div class="holding-row top">
            <div class="holding-left">
              <div class="holding-title">
                <strong>{{ holding.productName }}</strong>
                <span>{{ formatQuantity(holding.holdingQuantity, holding.unitName) }}</span>
              </div>
              <div class="holding-tags">
                <span class="holding-tag">{{ getProductTag(holding) }}</span>
                <span class="holding-market-value">{{ formatCurrency(holding.marketValue, 0) }}</span>
              </div>
            </div>
            <div class="holding-right">
              <span>今日盈亏</span>
              <AmountText tag="strong" :value="holding.dayProfit === 0 ? '--' : formatAmount(holding.dayProfit)" />
            </div>
          </div>

          <div class="holding-row middle">
            <div class="holding-price-line">
              <AmountText tag="strong" tone="inherit" :value="formatPrice(holding.currentPrice)" />
              <span>最新净值 {{ formatSyncDate(holding.lastSyncedAt) }}</span>
            </div>
            <div class="holding-right compact">
              <span>成本价</span>
              <AmountText tag="strong" tone="inherit" :value="formatPrice(holding.avgCostPrice)" />
            </div>
          </div>

          <div class="holding-divider"></div>

          <div class="holding-row bottom">
            <div class="holding-left compact">
              <span>累计盈亏</span>
              <div class="holding-pnl-line">
                <AmountText tag="strong" :value="formatCurrency(holding.cumulativeProfit, 0)" />
                <AmountText tag="span" class="holding-pnl-rate" :value="`${formatAmount(holding.cumulativeProfitRate)}%`" />
              </div>
            </div>
            <div class="holding-right compact">
              <span>仓位占比</span>
              <div class="holding-allocation">
                <div class="holding-allocation-track">
                  <span :style="{ width: `${getAllocationPercent(holding)}%` }"></span>
                </div>
                <AmountText tag="strong" tone="inherit" :value="`${formatAmount(getAllocationPercent(holding))}%`" />
              </div>
            </div>
          </div>
        </article>

        <p v-if="holdings.length === 0" class="investment-message">
          暂无持仓
        </p>
      </section>
    </template>

    <FloatingAddButton aria-label="新增投资资产" storage-key="investment-account" @click="openAddModal" />

    <CommonModal v-model="showAddModal" title="添加资产" size="compact" :show-close="false">
      <div class="investment-add-modal-form">
        <label class="investment-search-field" aria-label="输入代码或名称">
          <input
            v-model="addAssetKeyword"
            type="search"
            inputmode="search"
            placeholder="输入代码或名称"
            @keydown.enter.prevent="lookupProductByKeyword()"
          />
          <button type="button" :disabled="isLookingUpProduct" @click="lookupProductByKeyword()">
            <span aria-hidden="true">⌕</span>
            {{ isLookingUpProduct ? '搜索中' : '搜索' }}
          </button>
        </label>
        <p v-if="productLookupMessage" class="investment-lookup-message">
          {{ productLookupMessage }}
        </p>

        <label class="investment-add-modal-field">
          <span>资金账户</span>
          <select v-model="addAssetFundingAccount" class="investment-field-control">
            <option v-for="option in fundingAccountOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>

        <label class="investment-add-modal-field">
          <span>买入金额</span>
          <input
            v-model="addAssetAmount"
            class="investment-field-control"
            type="number"
            min="0"
            step="0.01"
            inputmode="decimal"
            placeholder="0.00"
            aria-label="买入金额"
          />
        </label>
        <p v-if="formError" class="investment-add-error">{{ formError }}</p>
      </div>

      <template #footer>
        <div class="investment-add-modal-actions">
          <button class="investment-modal-button secondary" type="button" :disabled="isSaving" @click="closeAddModal">取消</button>
          <button class="investment-modal-button primary" type="button" :disabled="isSaving" @click="saveAsset">
            {{ isSaving ? '保存中...' : '确认添加' }}
          </button>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
