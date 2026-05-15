<script setup lang="ts">
// 投资账户页：通过后端接口展示投资账户汇总、持仓列表和新增持仓。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import {
  createInvestmentPosition,
  getAccounts,
  getAccountTypes,
  getInvestmentPositions,
  getInvestmentProducts,
  getInvestmentSummary,
  type Account,
  type AccountType,
  type InvestmentPosition,
  type InvestmentProductType,
  type InvestmentSummary,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

const router = useRouter()
const route = useRoute()

const investmentTabs = ['全部', 'A股', '基金', '其他']
const activeTab = ref(investmentTabs[0])
const showAddModal = ref(false)
const isLoading = ref(false)
const isSaving = ref(false)
const pageError = ref('')
const formError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const investmentAccounts = ref<Account[]>([])
const accountTypes = ref<AccountType[]>([])
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
const selectedAccountId = ref<number | null>(null)

const addAssetName = ref('')
const addAssetSymbol = ref('')
const addAssetMarket = ref('')
const addAssetCategoryOptions: CommonSelectOption[] = [
  { label: 'A股', value: 'stock' },
  { label: '基金', value: 'fund' },
  { label: '债券', value: 'bond' },
  { label: '黄金', value: 'gold' },
  { label: '其他', value: 'other' },
]
const addAssetCategory = ref<InvestmentProductType>('fund')
const addAssetAccount = ref('')
const addAssetCost = ref('')
const addAssetQuantity = ref('')
const addAssetCurrentPrice = ref('')
const addAssetIncludeInNetWorth = ref(true)
const addAssetRemark = ref('')
const isLookingUpProduct = ref(false)
const productLookupMessage = ref('')
let productLookupTimer: number | undefined

const accountOptions = computed<CommonSelectOption[]>(() =>
  investmentAccounts.value.map((account) => ({
    label: account.name,
    value: String(account.id),
  })),
)

const holdings = computed(() => {
  if (activeTab.value === '全部') {
    return positions.value
  }

  if (activeTab.value === 'A股') {
    return positions.value.filter((item) => item.productType === 'stock')
  }

  if (activeTab.value === '基金') {
    return positions.value.filter((item) => item.productType === 'fund')
  }

  return positions.value.filter((item) => !item.productType || item.productType === 'other' || item.productType === 'gold' || item.productType === 'bond')
})

const summaryMetrics = computed(() => [
  { label: '持仓盈亏', value: summary.value.holdingProfit },
  { label: '持仓盈亏率', value: summary.value.holdingProfitRate, isRate: true },
  { label: '累计盈亏', value: summary.value.cumulativeProfit },
  { label: '累计盈亏率', value: summary.value.cumulativeProfitRate, isRate: true },
])

onMounted(() => {
  selectedAccountId.value = parseAccountId(route.query.accountId)
  loadInvestmentData()
})

watch(addAssetSymbol, (nextSymbol) => {
  window.clearTimeout(productLookupTimer)
  const keyword = nextSymbol.trim()
  productLookupMessage.value = ''

  if (keyword.length < 2) {
    return
  }

  productLookupTimer = window.setTimeout(() => {
    lookupProductByCode(keyword)
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

async function loadInvestmentData() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看投资账户'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [typeList, accountList] = await Promise.all([
      getAccountTypes({ status: 'active' }),
      getAccounts({ userId: currentUser.id, status: 'active' }),
    ])
    accountTypes.value = typeList
    investmentAccounts.value = accountList.filter((account) => account.accountTypeCode === 'investment')

    const selectedExists = investmentAccounts.value.some((account) => account.id === selectedAccountId.value)
    if (!selectedAccountId.value || !selectedExists) {
      selectedAccountId.value = investmentAccounts.value[0]?.id ?? null
    }

    const accountId = selectedAccountId.value ?? undefined
    const [summaryData, positionList] = await Promise.all([
      getInvestmentSummary({ userId: currentUser.id, accountId }),
      getInvestmentPositions({ userId: currentUser.id, accountId }),
    ])

    summary.value = summaryData
    positions.value = positionList

    if (investmentAccounts.value.length > 0) {
      addAssetAccount.value = selectedAccountId.value
        ? String(selectedAccountId.value)
        : String(investmentAccounts.value[0].id)
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '投资账户加载失败'
  } finally {
    isLoading.value = false
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

  const accountId = Number(addAssetAccount.value)
  const currentPrice = Number(addAssetCurrentPrice.value)
  const quantity = Number(addAssetQuantity.value)
  const costAmount = Number(addAssetCost.value)

  if (!addAssetName.value.trim() || !addAssetSymbol.value.trim()) {
    formError.value = '请输入资产名称和代码'
    return
  }
  if (!Number.isFinite(accountId)) {
    formError.value = '请选择投资账户'
    return
  }
  if (!Number.isFinite(quantity) || quantity <= 0) {
    formError.value = '请输入持仓数量'
    return
  }
  if (!Number.isFinite(costAmount) || costAmount <= 0) {
    formError.value = '请输入买入金额'
    return
  }
  if (!Number.isFinite(currentPrice) || currentPrice <= 0) {
    formError.value = '请输入当前价格'
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    await createInvestmentPosition({
      userId: currentUser.id,
      accountId,
      product: {
        productType: addAssetCategory.value as InvestmentProductType,
        market: addAssetMarket.value.trim() || null,
        symbol: addAssetSymbol.value.trim(),
        name: addAssetName.value.trim(),
        currencyCode: 'CNY',
        unitName: addAssetCategory.value === 'gold' ? '克' : '份',
        status: 'active',
        remark: addAssetRemark.value.trim() || null,
      },
      holdingQuantity: quantity,
      availableQuantity: quantity,
      frozenQuantity: 0,
      costAmount,
      currentPrice,
      includeInNetWorth: addAssetIncludeInNetWorth.value,
      status: 'active',
      remark: addAssetRemark.value.trim() || null,
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

async function lookupProductByCode(keyword: string) {
  if (isLookingUpProduct.value) {
    return
  }

  isLookingUpProduct.value = true
  productLookupMessage.value = '正在识别代码...'

  try {
    const products = await getInvestmentProducts({ keyword })
    const normalizedKeyword = keyword.toUpperCase()
    const matchedProduct = products.find((product) => product.symbol.toUpperCase() === normalizedKeyword)
      ?? products[0]

    if (!matchedProduct) {
      productLookupMessage.value = '未找到该代码，可手动填写资产信息'
      return
    }

    addAssetName.value = matchedProduct.name
    addAssetSymbol.value = matchedProduct.symbol
    addAssetMarket.value = matchedProduct.market || ''
    addAssetCategory.value = matchedProduct.productType
    if (matchedProduct.latestPrice && matchedProduct.latestPrice > 0) {
      addAssetCurrentPrice.value = String(matchedProduct.latestPrice)
    }
    productLookupMessage.value = `已识别：${matchedProduct.name}`
  } catch (error) {
    productLookupMessage.value = error instanceof Error ? error.message : '代码识别失败'
  } finally {
    isLookingUpProduct.value = false
  }
}

function openAddModal() {
  showAddModal.value = true
}

function closeAddModal() {
  showAddModal.value = false
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

    <PageHeader title="投资账户" back-to="/finance/accounts" back-label="返回账户管理" />

    <p v-if="pageError" class="investment-message investment-message-error">{{ pageError }}</p>
    <p v-else-if="isLoading" class="investment-message">加载中...</p>

    <template v-else>
      <section class="investment-summary-card" aria-label="投资总览">
        <div class="investment-summary-top">
          <div class="investment-summary-main">
            <p>投资总市值</p>
            <AmountText tag="strong" :value="formatAmount(summary.totalMarketValue)" />
            <span>同步于 {{ summary.lastSyncedAt ? new Date(summary.lastSyncedAt).toLocaleString('zh-CN') : '暂无' }}</span>
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
                <span>{{ holding.productSymbol }}</span>
              </div>
              <div class="holding-tags">
                <span class="holding-tag">{{ getProductTag(holding) }}</span>
                <span class="holding-market-value">{{ holding.accountName }}</span>
              </div>
            </div>
            <div class="holding-right">
              <span>今日盈亏</span>
              <AmountText tag="strong" :value="formatAmount(holding.dayProfit)" />
            </div>
          </div>

          <div class="holding-row middle">
            <div class="holding-left compact">
              <AmountText tag="strong" :value="formatAmount(holding.marketValue)" />
              <span>市值</span>
            </div>
            <div class="holding-right compact">
              <span>成本</span>
              <AmountText tag="strong" :value="formatAmount(holding.costAmount)" />
            </div>
          </div>

          <div class="holding-divider"></div>

          <div class="holding-row bottom">
            <div class="holding-left compact">
              <span>持仓盈亏</span>
              <div class="holding-pnl-line">
                <AmountText tag="strong" :value="formatAmount(holding.holdingProfit)" />
                <AmountText tag="span" class="holding-pnl-rate" :value="`${formatAmount(holding.holdingProfitRate)}%`" />
              </div>
            </div>
            <div class="holding-right compact">
              <span>持仓数量</span>
              <div class="holding-allocation">
                <div class="holding-allocation-track">
                  <span :style="{ width: `${Math.min(Math.max(Number(holding.cumulativeProfitRate) || 0, 0), 100)}%` }"></span>
                </div>
                <AmountText tag="strong" :value="`${formatAmount(holding.holdingQuantity)} ${holding.unitName || '份'}`" />
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

    <CommonModal v-model="showAddModal" title="添加资产">
      <div class="investment-add-modal-form">
        <CommonInput v-model="addAssetSymbol" label="基金/股票代码" placeholder="输入代码后自动识别" />
        <p v-if="productLookupMessage" class="investment-lookup-message">
          {{ productLookupMessage }}
        </p>
        <CommonInput v-model="addAssetName" label="资产名称" placeholder="自动填入或手动输入" />
        <CommonInput v-model="addAssetMarket" label="市场" placeholder="例如：CN / HK / US" />
        <CommonSelect v-model="addAssetCategory" label="资产分类" :options="addAssetCategoryOptions" />
        <CommonSelect v-model="addAssetAccount" label="所属账户" :options="accountOptions" />
        <CommonInput v-model="addAssetQuantity" label="持仓数量" input-mode="decimal" placeholder="请输入数量" />
        <CommonInput v-model="addAssetCost" label="买入金额" input-mode="decimal" placeholder="请输入金额" />
        <CommonInput v-model="addAssetCurrentPrice" label="当前价格" input-mode="decimal" placeholder="请输入价格" />
        <CommonSwitch v-model="addAssetIncludeInNetWorth" label="是否计入总资产" />
        <CommonInput v-model="addAssetRemark" label="备注" placeholder="可选，添加说明" />
        <p v-if="formError" class="investment-add-error">{{ formError }}</p>
      </div>

      <template #footer>
        <div class="investment-add-modal-actions">
          <CommonButton variant="secondary" :disabled="isSaving" @click="closeAddModal">取消</CommonButton>
          <CommonButton variant="primary" :disabled="isSaving" @click="saveAsset">
            {{ isSaving ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
