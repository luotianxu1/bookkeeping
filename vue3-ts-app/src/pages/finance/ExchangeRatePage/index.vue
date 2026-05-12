<script setup lang="ts">
// 汇率换算页：输入支付金额并进行币种换算展示。
import { computed, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'

const fromCurrency = ref('USD 美元')
const toCurrency = ref('CNY 人民币')
const fromAmount = ref('100.00')
const rate = ref(7.21)

const fromOptions = ['USD 美元', 'CNY 人民币', 'EUR 欧元', 'JPY 日元', 'HKD 港币']
const toOptions = ['CNY 人民币', 'USD 美元', 'EUR 欧元', 'JPY 日元', 'HKD 港币']

const fromCode = computed(() => fromCurrency.value.split(' ')[0] ?? 'USD')
const toCode = computed(() => toCurrency.value.split(' ')[0] ?? 'CNY')

const parsedAmount = computed(() => {
  const numeric = Number(fromAmount.value)
  return Number.isFinite(numeric) ? numeric : 0
})

const convertedAmount = computed(() => {
  const value = parsedAmount.value * rate.value
  return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
})

const rateText = computed(() => `1 ${fromCode.value} = ${rate.value.toFixed(4)} ${toCode.value}`)

function swapCurrencies() {
  const previousFrom = fromCurrency.value
  fromCurrency.value = toCurrency.value
  toCurrency.value = previousFrom
  rate.value = rate.value === 7.21 ? 0.1387 : 7.21
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
            <option v-for="option in fromOptions" :key="option" :value="option">
              {{ option }}
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
            <option v-for="option in toOptions" :key="option" :value="option">
              {{ option }}
            </option>
          </select>
          <strong>{{ convertedAmount }}</strong>
        </div>
      </section>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
