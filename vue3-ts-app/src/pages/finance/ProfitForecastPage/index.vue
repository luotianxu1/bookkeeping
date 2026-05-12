<script setup lang="ts">
// 收益预测页：展示当日预估收益、累计收益和累计收益率。
import { computed } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'

interface HoldingRow {
  name: string
  holdingAmount: string
  estimateProfit: string
  estimateRate: string
  totalProfit: string
  totalRate: string
}

const summary = {
  estimateProfit: '+ 126.80',
  estimateRate: '+0.84%',
  totalProfit: '+ 8,640',
  totalRate: '+12.36%',
}

const holdingRows: HoldingRow[] = [
  {
    name: '易方达蓝筹精选',
    holdingAmount: '52,300',
    estimateProfit: '+46.20',
    estimateRate: '+0.92%',
    totalProfit: '+3,420',
    totalRate: '+8.74%',
  },
  {
    name: '招商中证白酒',
    holdingAmount: '19,800',
    estimateProfit: '-12.60',
    estimateRate: '-0.31%',
    totalProfit: '-640',
    totalRate: '-3.13%',
  },
  {
    name: '广发纳斯达克100',
    holdingAmount: '41,600',
    estimateProfit: '+58.90',
    estimateRate: '+1.14%',
    totalProfit: '+5,260',
    totalRate: '+14.22%',
  },
  {
    name: '华夏上证50ETF联接',
    holdingAmount: '28,900',
    estimateProfit: '+34.30',
    estimateRate: '+0.67%',
    totalProfit: '+1,980',
    totalRate: '+7.21%',
  },
]

const currentTimeText = computed(() => {
  const now = new Date()
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
})
</script>

<template>
  <section class="profit-forecast-page" aria-label="收益预测">
    <PageHeader title="收益预测" back-label="返回更多功能" />

    <section class="summary-card" aria-label="收益预测汇总">
      <p class="summary-title">持仓基金当日收益预测</p>
      <div class="summary-row">
        <AmountText tag="strong" :value="summary.estimateProfit" />
        <AmountText tag="span" :value="`预计 ${summary.estimateRate}`" />
      </div>
      <div class="summary-row summary-row--total">
        <AmountText tag="strong" :value="`累计 ${summary.totalProfit}`" />
        <AmountText tag="span" :value="`累计收益率 ${summary.totalRate}`" />
      </div>
      <p class="summary-hint">按当前时间估算净值实时预测（{{ currentTimeText }}）</p>
    </section>

    <section class="holding-card" aria-label="持仓收益预测">
      <header class="holding-header">
        <span class="holding-spacer" />
        <div class="holding-header-right">
          <span>预估收益</span>
          <span>累计收益</span>
        </div>
      </header>

      <article v-for="item in holdingRows" :key="item.name" class="holding-row">
        <div class="holding-left">
          <p class="holding-name">{{ item.name }}</p>
          <AmountText tag="p" class="holding-amount" :value="item.holdingAmount" />
        </div>

        <div class="holding-right">
          <div class="value-column">
            <AmountText tag="strong" :value="item.estimateProfit" />
            <AmountText tag="span" :value="item.estimateRate" />
          </div>
          <div class="value-column">
            <AmountText tag="strong" :value="item.totalProfit" />
            <AmountText tag="span" :value="item.totalRate" />
          </div>
        </div>
      </article>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
