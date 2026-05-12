<script setup lang="ts">
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/common/PageHeader/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import {
  goldAccountHoldings,
  goldAccountOverview,
} from '@/data/account'
</script>

<template>
  <section class="gold-account-page" aria-label="黄金账户">
    <PageHeader title="黄金账户" back-label="返回账户管理" />

    <section class="gold-account-summary">
      <div class="summary-head">
        <span>总重量(克)</span>
        <strong>{{ goldAccountOverview.totalWeight }}</strong>
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

    <section class="gold-holding-list">
      <RouterLink
        v-for="item in goldAccountHoldings"
        :key="item.id"
        class="gold-holding-card"
        to="/finance/accounts/gold/position"
      >
        <span class="price-tag">{{ item.tagPrice }}</span>
        <div class="holding-top">
          <strong>{{ item.accountName }}</strong>
          <p>{{ item.amount }}</p>
        </div>
        <div class="holding-bottom">
          <span>{{ item.weight }}</span>
          <em>{{ item.holdingProfit }}</em>
        </div>
      </RouterLink>
    </section>

    <FloatingAddButton aria-label="新增黄金持仓" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
