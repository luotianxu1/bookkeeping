<script setup lang="ts">
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import PagePanel from '@/business/components/PagePanel.vue'

// 汇率换算页：输入支付金额并进行币种换算展示。
import { computed, onMounted, ref, watch } from 'vue'
import { getExchangeRate } from '@/business/api/modules/finance'

import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { getErrorMessage } from '@/business/api/request'

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
const isLoadingRate = ref(true)
const updatedAt = ref('')
let requestId = 0

const fromCode = computed(() => fromCurrency.value)
const toCode = computed(() => toCurrency.value)
const currencySelectOptions = computed<CommonSelectOption[]>(() =>
  currencyOptions.map((option) => ({
    label: option.label,
    value: option.code,
  })),
)

const parsedAmount = computed(() => {
  const numeric = Number(fromAmount.value)
  return Number.isFinite(numeric) ? numeric : 0
})

const convertedAmount = computed(() => {
  if (!Number.isFinite(rate.value) || rate.value <= 0) return '—'
  const value = parsedAmount.value * rate.value
  return value.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
})

const rateText = computed(() => {
  if (isLoadingRate.value && !rate.value) {
    return '汇率加载中...'
  }
  if (!Number.isFinite(rate.value) || rate.value <= 0) {
    return '汇率暂不可用'
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
    if (!Number.isFinite(rate.value)) rate.value = 0
    updatedAt.value = result.updatedAt
  } catch (error) {
    if (currentRequestId !== requestId) {
      return
    }
    exchangeRateError.value = getErrorMessage(error, '汇率加载失败')
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
  <section class="desktop-workspace desktop-workspace--form">
    <PageHeader title="汇率换算">
      <template #right>
        <el-button @click="swapCurrencies">交换币种</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="exchangeRateError"
      :title="exchangeRateError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <div class="desktop-columns">
      <PagePanel title="支付金额">
        <el-form label-position="top">
          <div class="desktop-form-grid">
            <CommonSelect
              v-model="fromCurrency"
              label="支付币种"
              :options="currencySelectOptions"
            />
            <CommonInput
              v-model="fromAmount"
              label="金额"
              input-mode="decimal"
            />
          </div>
        </el-form>
      </PagePanel>
      <PagePanel title="换算结果">
        <CommonSelect
          v-model="toCurrency"
          label="到账币种"
          :options="currencySelectOptions"
        />
        <div class="desktop-converted-amount">{{ convertedAmount }}</div>
        <p class="desktop-note">{{ rateText }} · {{ updatedAtText }}</p>
      </PagePanel>
    </div>
  </section>
</template>
