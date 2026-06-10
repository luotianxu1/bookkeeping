<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import {
  getInvestmentDividendForecast,
  getInvestmentProducts,
  type InvestmentDividendForecast,
  type InvestmentProduct,
} from '@/api/modules/finance'

const inputModeOptions = [
  { label: '持仓数量', value: 'quantity' },
  { label: '持仓金额', value: 'amount' },
]

const searchKeyword = ref('')
const searchResults = ref<InvestmentProduct[]>([])
const selectedProductKey = ref('')
const selectedProduct = ref<InvestmentProduct | null>(null)
const holdingInputMode = ref<'quantity' | 'amount'>('quantity')
const holdingInputValue = ref('')
const isSearching = ref(false)
const isCalculating = ref(false)
const searchMessage = ref('')
const formError = ref('')
const pageError = ref('')
const forecastResult = ref<InvestmentDividendForecast | null>(null)
let isFillingKeyword = false

const selectedProductSummary = computed(() => {
  if (!selectedProduct.value) {
    return ''
  }
  const typeLabel = selectedProduct.value.productType === 'fund' ? '基金' : '股票'
  const marketLabel = selectedProduct.value.market?.trim() ? ` · ${selectedProduct.value.market}` : ''
  return `${typeLabel}${marketLabel}`
})
const holdingInputLabel = computed(() => {
  if (holdingInputMode.value === 'amount') {
    return '持仓金额'
  }
  const unitName = selectedProduct.value?.unitName || (selectedProduct.value?.productType === 'stock' ? '股' : '份')
  return `持仓数量(${unitName})`
})
const currentPriceLabel = computed(() => selectedProduct.value?.productType === 'fund' ? '当前净值' : '当前股价')

function getProductMatchScore(product: InvestmentProduct, keyword: string, normalizedKeyword: string) {
  const symbol = product.symbol.toUpperCase()
  const name = product.name
  if (symbol === normalizedKeyword) return 400
  if (symbol.startsWith(normalizedKeyword)) return 300
  if (name === keyword) return 200
  if (name.includes(keyword)) return 100
  return 0
}

function getSearchResultKey(product: InvestmentProduct) {
  const fallbackId = Number(product.id)
  if (Number.isFinite(fallbackId) && fallbackId > 0) {
    return `id:${fallbackId}`
  }
  return [
    product.productType || 'unknown',
    product.market || '',
    product.exchangeCode || '',
    product.symbol || '',
    product.name || '',
  ].join('|')
}

function getSearchResultMeta(product: InvestmentProduct) {
  const typeLabel = product.productType === 'fund' ? '基金' : product.productType === 'stock' ? '股票' : '投资资产'
  const marketLabel = product.market?.trim() ? ` · ${product.market}` : ''
  return `${typeLabel}${marketLabel}`
}

function normalizeSearchProductName(product: InvestmentProduct) {
  return (product.name || '')
    .trim()
    .replace(/[\s()（）\-_.]+/g, '')
    .toUpperCase()
}

function getSearchResultDedupKey(product: InvestmentProduct) {
  return [
    product.productType || 'unknown',
    product.symbol?.trim().toUpperCase() || '',
    normalizeSearchProductName(product),
  ].join('|')
}

function getProductCompletenessScore(product: InvestmentProduct) {
  let score = 0
  if ((product.latestPrice ?? 0) > 0) {
    score += 16
  }
  if (product.exchangeCode?.trim()) {
    score += 8
  }
  if (product.market?.trim()) {
    score += 4
  }
  if (product.currencyCode?.trim()) {
    score += 2
  }
  if (product.unitName?.trim()) {
    score += 1
  }
  if (product.name?.trim()) {
    score += Math.min(product.name.trim().length, 20)
  }
  return score
}

function filterForecastProducts(products: InvestmentProduct[]) {
  const dedupedProducts = new Map<string, InvestmentProduct>()
  products
    .filter((product) => product.productType === 'fund' || product.productType === 'stock')
    .forEach((product) => {
      const dedupKey = getSearchResultDedupKey(product)
      const currentProduct = dedupedProducts.get(dedupKey)
      if (!currentProduct || getProductCompletenessScore(product) > getProductCompletenessScore(currentProduct)) {
        dedupedProducts.set(dedupKey, product)
      }
    })
  return Array.from(dedupedProducts.values())
}

function isResultSelected(product: InvestmentProduct) {
  return selectedProductKey.value === getSearchResultKey(product)
}

async function searchProducts() {
  if (isSearching.value) {
    return
  }
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    formError.value = '请输入基金或股票代码'
    return
  }
  isSearching.value = true
  formError.value = ''
  pageError.value = ''
  searchMessage.value = '正在搜索资产...'
  try {
    const products = await getInvestmentProducts({ keyword })
    const supportedProducts = filterForecastProducts(products)
    const normalizedKeyword = keyword.toUpperCase()
    const matchedProducts = supportedProducts.slice().sort((left, right) =>
      getProductMatchScore(right, keyword, normalizedKeyword) - getProductMatchScore(left, keyword, normalizedKeyword),
    )
    searchResults.value = matchedProducts
    selectedProduct.value = null
    selectedProductKey.value = ''
    forecastResult.value = null
    searchMessage.value = matchedProducts.length > 0 ? `找到 ${matchedProducts.length} 个资产，请选择` : '未找到相关资产'
  } catch (error) {
    searchResults.value = []
    searchMessage.value = ''
    pageError.value = error instanceof Error ? error.message : '资产搜索失败'
  } finally {
    isSearching.value = false
  }
}

function selectProduct(product: InvestmentProduct) {
  selectedProduct.value = product
  selectedProductKey.value = getSearchResultKey(product)
  isFillingKeyword = true
  searchKeyword.value = `${product.symbol} ${product.name}`
  isFillingKeyword = false
  searchResults.value = []
  searchMessage.value = ''
  forecastResult.value = null
  formError.value = ''
}

function handleKeywordInput(value: string) {
  searchKeyword.value = value
  if (isFillingKeyword) {
    return
  }
  selectedProduct.value = null
  selectedProductKey.value = ''
  searchResults.value = []
  forecastResult.value = null
  searchMessage.value = ''
}

async function calculateForecast() {
  if (isCalculating.value) {
    return
  }
  if (!selectedProduct.value) {
    formError.value = '请先搜索并选择资产'
    return
  }
  const inputValue = Number(holdingInputValue.value)
  if (!Number.isFinite(inputValue) || inputValue <= 0) {
    formError.value = holdingInputMode.value === 'amount' ? '请输入有效的持仓金额' : '请输入有效的持仓数量'
    return
  }
  isCalculating.value = true
  formError.value = ''
  pageError.value = ''
  try {
    forecastResult.value = await getInvestmentDividendForecast({
      productType: selectedProduct.value.productType,
      symbol: selectedProduct.value.symbol,
      name: selectedProduct.value.name,
      market: selectedProduct.value.market,
      exchangeCode: selectedProduct.value.exchangeCode,
      currencyCode: selectedProduct.value.currencyCode,
      unitName: selectedProduct.value.unitName,
      latestPrice: selectedProduct.value.latestPrice ?? undefined,
      holdingQuantity: holdingInputMode.value === 'quantity' ? inputValue : undefined,
      holdingAmount: holdingInputMode.value === 'amount' ? inputValue : undefined,
    })
  } catch (error) {
    forecastResult.value = null
    pageError.value = error instanceof Error ? error.message : '收息预测失败'
  } finally {
    isCalculating.value = false
  }
}

function formatCurrency(value: number, digits = 2) {
  return `¥${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value)}`
}

function formatNumber(value: number, digits = 2) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value)
}

function formatPercent(value: number) {
  return `${formatNumber(value, 2)}%`
}
</script>

<template>
  <section class="dividend-forecast-page" aria-label="收息预测">
    <PageHeader title="收息预测" back-to="/finance/more-features" back-label="返回更多功能" />

    <p v-if="pageError" class="forecast-message forecast-message-error">{{ pageError }}</p>

    <section class="forecast-card" aria-label="预测条件">
      <div class="forecast-search-box">
        <label class="forecast-search-field" aria-label="输入基金或股票代码">
          <input
            :value="searchKeyword"
            type="search"
            inputmode="search"
            placeholder="输入基金或股票代码"
            @input="handleKeywordInput(($event.target as HTMLInputElement).value)"
            @keydown.enter.prevent="searchProducts"
          />
          <button type="button" :disabled="isSearching" @click="searchProducts">
            {{ isSearching ? '搜索中' : '搜索' }}
          </button>
        </label>

        <div v-if="searchResults.length > 0" class="forecast-search-results" aria-label="搜索结果">
          <button
            v-for="product in searchResults"
            :key="getSearchResultKey(product)"
            type="button"
            :class="['forecast-search-item', { active: isResultSelected(product) }]"
            @click="selectProduct(product)"
          >
            <strong>{{ product.symbol }} {{ product.name }}</strong>
            <span>{{ getSearchResultMeta(product) }}</span>
          </button>
        </div>
      </div>

      <p v-if="searchMessage" class="forecast-search-message">{{ searchMessage }}</p>

      <div v-if="selectedProduct" class="forecast-selected-product">
        <strong>{{ selectedProduct.symbol }} {{ selectedProduct.name }}</strong>
        <span>{{ selectedProductSummary }}</span>
      </div>

      <label class="forecast-field">
        <span>输入方式</span>
        <SegmentedControl
          v-model="holdingInputMode"
          :options="inputModeOptions"
          label="持仓输入方式"
        />
      </label>

      <label class="forecast-field">
        <span>{{ holdingInputLabel }}</span>
        <input
          v-model="holdingInputValue"
          class="forecast-field-control"
          type="number"
          inputmode="decimal"
          min="0"
          step="0.01"
          placeholder="请输入数值"
        />
      </label>

      <p v-if="formError" class="forecast-message forecast-message-error">{{ formError }}</p>

      <CommonButton variant="primary" :disabled="isCalculating" @click="calculateForecast">
        {{ isCalculating ? '测算中...' : '开始测算' }}
      </CommonButton>
    </section>

    <CommonLoading v-if="isCalculating" text="正在测算收息..." />

    <section v-else-if="forecastResult" class="result-card" aria-label="收息预测结果">
      <div class="result-card-head">
        <div>
          <p>{{ forecastResult.productTypeLabel }}</p>
          <strong>{{ forecastResult.symbol }} {{ forecastResult.name }}</strong>
        </div>
        <AmountText
          tag="strong"
          class="result-amount"
          :value="formatCurrency(forecastResult.estimatedDividendAmount)"
        />
      </div>

      <p class="result-note">{{ forecastResult.calculationNote }}</p>

      <div class="result-grid">
        <div class="result-stat">
          <span>{{ currentPriceLabel }}</span>
          <strong>{{ formatCurrency(forecastResult.currentPrice, 4) }}</strong>
        </div>
        <div class="result-stat">
          <span>去年分红次数</span>
          <strong>{{ forecastResult.lastYearDividendCount }} 次</strong>
        </div>
        <div class="result-stat">
          <span>去年每单位分红</span>
          <strong>{{ formatNumber(forecastResult.lastYearDividendPerUnit, 4) }} / {{ forecastResult.unitName || '份' }}</strong>
        </div>
        <div class="result-stat">
          <span>折算持仓数量</span>
          <strong>{{ formatNumber(forecastResult.estimatedHoldingQuantity) }} {{ forecastResult.unitName || '份' }}</strong>
        </div>
        <div class="result-stat">
          <span>对应持仓金额</span>
          <strong>{{ formatCurrency(forecastResult.estimatedHoldingAmount) }}</strong>
        </div>
        <div class="result-stat">
          <span>预计收息率</span>
          <strong>{{ formatPercent(forecastResult.estimatedDividendRate) }}</strong>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
