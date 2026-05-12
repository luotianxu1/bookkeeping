<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  goldLiquidationRecords,
  goldLiquidationSummary,
} from '@/data/account'
</script>

<template>
  <section class="gold-liquidation-page" aria-label="清仓记录">
    <PageHeader title="清仓记录" back-label="返回黄金账户持仓" />

    <section class="liquidation-summary">
      <div class="summary-row">
        <span>累计克重</span>
        <strong class="weight">{{ goldLiquidationSummary.cumulativeWeight }}</strong>
      </div>
      <div class="summary-row">
        <span>累计收益</span>
        <strong class="profit">{{ goldLiquidationSummary.cumulativeProfit }}</strong>
      </div>
    </section>

    <section class="liquidation-list">
      <article v-for="record in goldLiquidationRecords" :key="record.id" class="liquidation-card">
        <div class="card-top">
          <strong>{{ record.time }}</strong>
          <em>收益 {{ record.profit }}</em>
        </div>

        <div class="detail-grid">
          <div>
            <span>克重</span>
            <strong>{{ record.weight }}</strong>
          </div>
          <div>
            <span>买入价</span>
            <strong>{{ record.buyPrice }}</strong>
          </div>
          <div>
            <span>卖出价</span>
            <strong>{{ record.sellPrice }}</strong>
          </div>
          <div>
            <span>手续费</span>
            <strong>{{ record.fee }}</strong>
          </div>
        </div>
      </article>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
