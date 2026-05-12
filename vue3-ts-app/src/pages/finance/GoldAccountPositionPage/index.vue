<script setup lang="ts">
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import { goldAccountHoldings, goldAccountOverview } from '@/data/account'
</script>

<template>
  <section class="gold-position-page" aria-label="黄金账户持仓">
    <PageHeader title="黄金账户持仓" back-label="返回黄金账户" />

    <section class="gold-position-summary">
      <div class="summary-head">
        <span>总重量(克)</span>
      </div>
      <div class="summary-main">
        <strong>{{ goldAccountOverview.totalWeight }}</strong>
        <RouterLink class="liquidation-link" to="/finance/accounts/gold/liquidation">
          清仓记录
        </RouterLink>
      </div>

      <div class="summary-grid">
        <article>
          <span>平均克价(元)</span>
          <strong>{{ goldAccountOverview.averagePrice }}</strong>
        </article>
        <article>
          <span>购入总价(元)</span>
          <strong>{{ goldAccountOverview.purchaseTotal }}</strong>
        </article>
        <article>
          <span>预估价值(元)</span>
          <strong>{{ goldAccountOverview.estimatedValue }}</strong>
        </article>
        <article>
          <span>预估收益(元)</span>
          <strong class="up">{{ goldAccountOverview.estimatedProfit }}</strong>
        </article>
        <article>
          <span>收益率(%)</span>
          <strong class="up">{{ goldAccountOverview.profitRate }}</strong>
        </article>
        <article>
          <span>累计收益(元)</span>
          <strong class="up">{{ goldAccountOverview.cumulativeProfit }}</strong>
        </article>
      </div>
    </section>

    <section class="position-list">
      <article v-for="item in goldAccountHoldings" :key="item.id" class="position-card">
        <span class="price-tag">{{ item.tagPrice }}</span>
        <div class="position-top">
          <strong>{{ item.accountName }}</strong>
          <div class="profit">{{ item.holdingProfit }}</div>
        </div>
        <div class="position-bottom">
          <span>{{ item.weight }}</span>
          <span>{{ item.date }}</span>
        </div>
      </article>
    </section>

    <FloatingAddButton aria-label="新增黄金" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
