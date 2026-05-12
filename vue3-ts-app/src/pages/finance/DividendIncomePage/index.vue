<script setup lang="ts">
// 攒股收息页：与 Pencil 设计稿一致展示预估分红与累计分红。
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'

interface DividendHolding {
  name: string
  shares: string
  estimateIncome: string
  estimateRate: string
  totalIncome: string
  totalRate: string
}

const summary = {
  estimateIncome: '+ 126.80',
  estimateRate: '+0.32%',
  totalIncome: '+ 8,640',
  totalRate: '+12.36%',
}

const holdings: DividendHolding[] = [
  {
    name: '中国移动',
    shares: '持仓 600 股',
    estimateIncome: '+46.20',
    estimateRate: '+0.92%',
    totalIncome: '+3,420',
    totalRate: '+8.74%',
  },
  {
    name: '中国神华',
    shares: '持仓 500 股',
    estimateIncome: '+12.60',
    estimateRate: '+0.31%',
    totalIncome: '+640',
    totalRate: '+3.13%',
  },
  {
    name: '工商银行',
    shares: '持仓 3000 股',
    estimateIncome: '+58.90',
    estimateRate: '+1.14%',
    totalIncome: '+5,260',
    totalRate: '+14.22%',
  },
  {
    name: '长江电力',
    shares: '持仓 800 股',
    estimateIncome: '+34.30',
    estimateRate: '+0.67%',
    totalIncome: '+1,980',
    totalRate: '+7.21%',
  },
]
</script>

<template>
  <section class="dividend-income-page" aria-label="攒股收息">
    <PageHeader title="攒股收息" back-label="返回更多功能" />

    <section class="summary-card" aria-label="收息总览">
      <p class="summary-title">持仓分红收益预测</p>
      <div class="summary-row">
        <AmountText tag="strong" :value="summary.estimateIncome" />
        <AmountText tag="span" :value="`预计 ${summary.estimateRate}`" />
      </div>
      <div class="summary-total-row">
        <AmountText tag="strong" :value="`累计收益 ${summary.totalIncome}`" />
        <AmountText tag="span" :value="`累计收益率 ${summary.totalRate}`" />
      </div>
    </section>

    <section class="holding-card" aria-label="持仓分红计划">
      <header class="holding-header">
        <span class="holding-spacer" />
        <div class="holding-header-right">
          <span>预估分红</span>
          <span>累计分红</span>
        </div>
      </header>

      <article v-for="item in holdings" :key="item.name" class="holding-row">
        <div class="holding-left">
          <p>{{ item.name }}</p>
          <small>{{ item.shares }}</small>
        </div>

        <div class="holding-right">
          <div class="value-column">
            <AmountText tag="strong" :value="item.estimateIncome" />
            <AmountText tag="small" :value="item.estimateRate" />
          </div>
          <div class="value-column">
            <AmountText tag="strong" :value="item.totalIncome" />
            <AmountText tag="small" :value="item.totalRate" />
          </div>
        </div>
      </article>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
