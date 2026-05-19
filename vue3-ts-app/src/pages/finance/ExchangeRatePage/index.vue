<script setup lang="ts">
// 汇率换算页：输入支付金额并进行币种换算展示。
import { computed, onMounted, ref, watch } from 'vue'
import { getExchangeRate } from '@/api/modules/finance'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'

const currencyOptions = [
  { code: 'USD', label: 'USD 美元' },
  { code: 'CNY', label: 'CNY 人民币' },
  { code: 'EUR', label: 'EUR 欧元' },
  { code: 'JPY', label: 'JPY 日元' },
  { code: 'HKD', label: 'HKD 港币' },
  { code: 'GBP', label: 'GBP 英镑' },
  { code: 'AUD', label: 'AUD 澳元' },
  { code: 'CAD', label: 'CAD 加元' },
  { code: 'SGD', label: 'SGD 新加坡元' },
]

const fromCurrency = ref('USD')
const toCurrency = ref('CNY')
const fromAmount = ref('100.00')
const rate = ref(0)
const exchangeRateError = ref('')
const isLoadingRate = ref(false)
const updatedAt = ref('')
let requestId = 0

const fromCode = computed(() => fromCurrency.value)
const toCode = computed(() => toCurrency.value)

const parsedAmount = computed(() => {
  const numeric = Number(fromAmount.value)
  return Number.isFinite(numeric) ? numeric : 0
})

const convertedAmount = computed(() => {
  const value = parsedAmount.value * rate.value
  return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
})

const rateText = computed(() => {
  if (isLoadingRate.value && !rate.value) {
    return '汇率加载中...'
  }
  return `1 ${fromCode.value} = ${rate.value.toFixed(4)} ${toCode.value}`
})

const updatedAtText = computed(() => {
  if (!updatedAt.value) return ''
  const date = new Date(updatedAt.value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (part: number) => String(part).padStart(2, '0')
  return `更新时间 ${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
})

onMounted(() => {
  loadExchangeRate()
})

watch([fromCurrency, toCurrency], () => {
  loadExchangeRate()
})

function swapCurrencies() {
  const previousFrom = fromCurrency.value
  fromCurrency.value = toCurrency.value
  toCurrency.value = previousFrom
}

async function loadExchangeRate() {
  const currentRequestId = ++requestId
  isLoadingRate.value = true
  exchangeRateError.value = ''

  try {
    const result = await getExchangeRate(fromCode.value, toCode.value)
    if (currentRequestId !== requestId) {
      return
    }
    rate.value = Number(result.rate)
    updatedAt.value = result.updatedAt
  } catch (error) {
    if (currentRequestId !== requestId) {
      return
    }
    exchangeRateError.value = error instanceof Error ? error.message : '汇率加载失败'
    rate.value = 0
    updatedAt.value = ''
  } finally {
    if (currentRequestId === requestId) {
      isLoadingRate.value = false
    }
  }
}
</script>

<template>
  <section class="exchange-rate-page" aria-label="汇率换算">
    <PageHeader title="汇率换算" back-label="返回更多功能" />

    <section class="converter-card" aria-label="汇率转换器">
      <section class="currency-card">
        <p>支付币种</p>
        <div class="currency-row">
          <select v-model="fromCurrency" aria-label="支付币种">
            <option v-for="option in currencyOptions" :key="option.code" :value="option.code">
              {{ option.label }}
            </option>
          </select>
          <input v-model="fromAmount" type="text" inputmode="decimal" aria-label="支付金额" />
        </div>
      </section>

      <div class="mid-row">
        <span>{{ rateText }}</span>
        <button type="button" @click="swapCurrencies">切换</button>
      </div>

      <section class="currency-card">
        <p>到账币种</p>
        <div class="currency-row">
          <select v-model="toCurrency" aria-label="到账币种">
            <option v-for="option in currencyOptions" :key="option.code" :value="option.code">
              {{ option.label }}
            </option>
          </select>
          <strong>{{ convertedAmount }}</strong>
        </div>
      </section>

      <p v-if="exchangeRateError" class="exchange-message exchange-message-error">
        {{ exchangeRateError }}
      </p>
      <CommonLoading v-else-if="isLoadingRate" text="汇率加载中..." />
      <p v-else-if="updatedAtText" class="exchange-message">
        {{ updatedAtText }}
      </p>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
