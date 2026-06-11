<script setup lang="ts">
import { computed, ref } from 'vue'
import AmountText from '@/components/common/AmountText/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  getInvestmentProducts,
  type InvestmentProduct,
} from '@/api/modules/finance'

const searchKeyword = ref('')
const searchResults = ref<InvestmentProduct[]>([])
const selectedProductKey = ref('')
const selectedProduct = ref<InvestmentProduct | null>(null)
const holdingQuantityInput = ref('')
const holdingCostInput = ref('')
const targetQuantityInput = ref('')
const isSearching = ref(false)
const searchMessage = ref('')
const pageError = ref('')
const hasCalculated = ref(false)

let isFillingKeyword = false

const quantityLabel = computed(() => selectedProduct.value?.productType === 'stock' ? '股数' : '份额')
const currentPrice = computed(() => normalizePositiveNumber(selectedProduct.value?.latestPrice))
const holdingQuantity = computed(() => normalizePositiveNumber(holdingQuantityInput.value))
const holdingCostPrice = computed(() => normalizePositiveNumber(holdingCostInput.value))
const targetQuantity = computed(() => normalizePositiveNumber(targetQuantityInput.value))

const validationMessage = computed(() => {
  if (!selectedProduct.value) {
    return '请先搜索并选择股票或基金'
  }
  if (!currentPrice.value) {
    return '该资产暂无可用的最新价格，暂时无法预测'
  }
  if (!holdingQuantityInput.value.trim()) {
    return `请输入当前持有${quantityLabel.value}`
  }
  if (!holdingQuantity.value) {
    return `请输入有效的当前持有${quantityLabel.value}`
  }
  if (!holdingCostInput.value.trim()) {
    return '请输入当前持仓成本价'
  }
  if (!holdingCostPrice.value) {
    return '请输入有效的当前持仓成本价'
  }
  if (!targetQuantityInput.value.trim()) {
    return `请输入预计持有${quantityLabel.value}`
  }
  if (!targetQuantity.value) {
    return `请输入有效的预计持有${quantityLabel.value}`
  }
  if (targetQuantity.value < holdingQuantity.value) {
    return `预计持有${quantityLabel.value}需大于等于当前持有${quantityLabel.value}`
  }
  return ''
})

const forecastResult = computed(() => {
  if (validationMessage.value || !currentPrice.value || !holdingQuantity.value || !holdingCostPrice.value || !targetQuantity.value) {
    return null
  }

  const holdingCostAmount = holdingQuantity.value * holdingCostPrice.value
  const marketValue = holdingQuantity.value * currentPrice.value
  const profitAmount = marketValue - holdingCostAmount
  const lossAmount = Math.max(holdingCostAmount - marketValue, 0)
  const avgCostPrice = holdingCostPrice.value
  const additionalQuantity = Math.max(targetQuantity.value - holdingQuantity.value, 0)
  const additionalCost = additionalQuantity * currentPrice.value
  const projectedCostAmount = holdingCostAmount + additionalCost
  const projectedBreakEvenPrice = projectedCostAmount / targetQuantity.value
  const requiredIncreaseRate = Math.max(((projectedBreakEvenPrice - currentPrice.value) / currentPrice.value) * 100, 0)

  return {
    holdingCostAmount,
    marketValue,
    profitAmount,
    lossAmount,
    avgCostPrice,
    additionalQuantity,
    additionalCost,
    projectedCostAmount,
    projectedBreakEvenPrice,
    requiredIncreaseRate,
    isAlreadyRecovered: profitAmount >= 0,
  }
})

const visibleForecastResult = computed(() => (hasCalculated.value ? forecastResult.value : null))

function normalizePositiveNumber(value: string | number | null | undefined) {
  const normalized = String(value ?? '')
    .trim()
    .replace(/[,\s，]/g, '')
  const parsed = Number(normalized)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function getProductMatchScore(product: InvestmentProduct, keyword: string, normalizedKeyword: string) {
  const symbol = product.symbol.toUpperCase()
  const name = product.name
  if (symbol === normalizedKeyword) return 400
  if (symbol.startsWith(normalizedKeyword)) return 300
  if (name === keyword) return 200
  if (name.includes(keyword)) return 100
  return 0
}

function normalizeSearchProductName(product: InvestmentProduct) {
  return (product.name || '')
    .trim()
    .replace(/[\s()（）\-_.]+/g, '')
    .toUpperCase()
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
  const typeLabel = product.productType === 'fund'
    ? '基金'
    : product.productType === 'stock'
      ? '股票'
      : '投资资产'
  const marketLabel = product.market?.trim() ? ` · ${product.market}` : ''
  return `${typeLabel}${marketLabel}`
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
  if ((product.latestPrice ?? 0) > 0) score += 16
  if (product.exchangeCode?.trim()) score += 8
  if (product.market?.trim()) score += 4
  if (product.currencyCode?.trim()) score += 2
  if (product.unitName?.trim()) score += 1
  if (product.name?.trim()) score += Math.min(product.name.trim().length, 20)
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

async function searchProducts() {
  if (isSearching.value) {
    return
  }

  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    searchMessage.value = '请输入股票或基金名称/代码'
    return
  }

  isSearching.value = true
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
  pageError.value = ''
  hasCalculated.value = false
}

function handleKeywordInput(value: string) {
  searchKeyword.value = value
  if (isFillingKeyword) {
    return
  }

  selectedProduct.value = null
  selectedProductKey.value = ''
  searchResults.value = []
  searchMessage.value = ''
  hasCalculated.value = false
}

function isResultSelected(product: InvestmentProduct) {
  return selectedProductKey.value === getSearchResultKey(product)
}

function handleCalculate() {
  hasCalculated.value = true
}

function getProfitTone(value: number) {
  if (value > 0) return 'positive'
  if (value < 0) return 'negative'
  return 'neutral'
}

function formatCurrency(value: number, digits = 2) {
  return `¥${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value)}`
}

function formatSignedCurrency(value: number, digits = 2) {
  const sign = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${sign}${formatCurrency(Math.abs(value), digits)}`
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
  <section class="break-even-forecast-page" aria-label="回本预测">
    <PageHeader title="回本预测" back-to="/finance/more-features" back-label="返回更多功能" />

    <p v-if="pageError" class="forecast-message forecast-message-error">
      {{ pageError }}
    </p>

    <section class="forecast-card" aria-label="预测条件">
      <div class="forecast-search-box">
        <label class="forecast-search-field" aria-label="输入基金或股票名称">
          <input
            :value="searchKeyword"
            type="text"
            inputmode="search"
            placeholder="输入股票/基金名称或代码"
            @input="handleKeywordInput(($event.target as HTMLInputElement).value)"
            @keydown.enter.prevent="searchProducts"
          >
          <button type="button" :disabled="isSearching" @click="searchProducts">
            {{ isSearching ? '搜索中' : '搜索' }}
          </button>
        </label>

        <div v-if="searchResults.length" class="forecast-search-results">
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

      <div class="forecast-grid">
        <label class="forecast-field">
          <span>当前持有{{ quantityLabel }}</span>
          <input
            v-model="holdingQuantityInput"
            class="forecast-field-control"
            type="text"
            inputmode="decimal"
            placeholder="请输入当前持有数量"
            @input="hasCalculated = false"
          >
        </label>

        <label class="forecast-field">
          <span>当前持仓成本价</span>
          <input
            v-model="holdingCostInput"
            class="forecast-field-control"
            type="text"
            inputmode="decimal"
            :placeholder="selectedProduct?.productType === 'stock' ? '例如 11.70 元/股' : '例如 1.2350 元/份'"
            @input="hasCalculated = false"
          >
        </label>

        <label class="forecast-field forecast-field-full">
          <span>预计持有{{ quantityLabel }}</span>
          <input
            v-model="targetQuantityInput"
            class="forecast-field-control"
            type="text"
            inputmode="decimal"
            :placeholder="`请输入计划补仓后的总${quantityLabel}`"
            @input="hasCalculated = false"
          >
        </label>
      </div>

      <p v-if="validationMessage" class="forecast-message">{{ validationMessage }}</p>

      <CommonButton variant="primary" :disabled="Boolean(validationMessage)" @click="handleCalculate">
        开始测算
      </CommonButton>
    </section>

    <section v-if="visibleForecastResult" class="result-card" aria-label="回本预测结果">
      <div class="result-hero">
        <div class="result-hero-block">
          <span>{{ visibleForecastResult.profitAmount >= 0 ? '当前浮盈' : '当前浮亏' }}</span>
          <AmountText
            tag="strong"
            class="result-amount"
            :value="formatSignedCurrency(visibleForecastResult.profitAmount)"
            :tone="getProfitTone(visibleForecastResult.profitAmount)"
          />
        </div>
        <div class="result-hero-block">
          <span>回本所需涨幅</span>
          <strong class="result-rate">
            {{ visibleForecastResult.isAlreadyRecovered ? '0.00%' : formatPercent(visibleForecastResult.requiredIncreaseRate) }}
          </strong>
        </div>
      </div>

      <dl class="result-list">
        <div class="result-row">
          <dt>当前市值</dt>
          <dd>{{ formatCurrency(visibleForecastResult.marketValue) }}</dd>
        </div>
        <div class="result-row">
          <dt>当前成本价</dt>
          <dd>{{ formatCurrency(visibleForecastResult.avgCostPrice, 2) }}</dd>
        </div>
        <div class="result-row">
          <dt>当前总成本</dt>
          <dd>{{ formatCurrency(visibleForecastResult.holdingCostAmount) }}</dd>
        </div>
        <div class="result-row">
          <dt>预计补仓金额</dt>
          <dd>{{ formatCurrency(visibleForecastResult.additionalCost) }}</dd>
        </div>
        <div class="result-row">
          <dt>预计总成本</dt>
          <dd>{{ formatCurrency(visibleForecastResult.projectedCostAmount) }}</dd>
        </div>
      </dl>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
