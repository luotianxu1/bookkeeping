<script setup lang="ts">
// 投资详情页：整合基金详情与投资项详情，展示总览、分析与交易记录。
import { computed } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import {
  investmentDetailAnalysisStats,
  investmentDetailBaseStats,
  investmentDetailDescription,
  investmentDetailOverview,
  investmentDetailTransactions,
} from '@/data/account'

const transactionCountText = computed(() => `共 ${investmentDetailTransactions.length} 条`)
</script>

<template>
  <section class="investment-detail-page" aria-label="投资详情">
    <PageHeader title="投资详情" back-to="/finance/accounts/investment" back-label="返回投资账户" />

    <section class="investment-detail-summary-card" aria-label="投资详情总览">
      <div class="investment-detail-summary-head">
        <div class="investment-detail-summary-title">
          <strong>{{ investmentDetailOverview.name }}</strong>
          <span>{{ investmentDetailOverview.subtitle }}</span>
        </div>
        <div class="investment-detail-summary-side">
          <span>{{ investmentDetailOverview.todayLabel }}</span>
          <AmountText tag="strong" :value="investmentDetailOverview.todayValue" />
        </div>
      </div>
      <AmountText tag="p" class="investment-detail-summary-amount" :value="investmentDetailOverview.amount" />
      <p class="investment-detail-summary-updated">{{ investmentDetailOverview.updatedAt }}</p>
    </section>

    <section class="investment-detail-card" aria-label="基金详细数据">
      <h2>基金详细数据</h2>
      <div class="investment-detail-grid">
        <div v-for="entry in investmentDetailBaseStats" :key="entry.label" class="investment-detail-grid-item">
          <span>{{ entry.label }}</span>
          <AmountText tag="strong" :class="entry.tone ? `tone-${entry.tone}` : ''" :value="entry.value" />
        </div>
      </div>
    </section>

    <section class="investment-detail-card" aria-label="持仓分析">
      <h2>持仓分析</h2>
      <div class="investment-detail-grid">
        <div
          v-for="entry in investmentDetailAnalysisStats"
          :key="entry.label"
          class="investment-detail-grid-item"
        >
          <span>{{ entry.label }}</span>
          <AmountText tag="strong" :class="entry.tone ? `tone-${entry.tone}` : ''" :value="entry.value" />
        </div>
      </div>
    </section>

    <section class="investment-detail-card" aria-label="基金说明">
      <h2>基金说明</h2>
      <p class="investment-detail-description">{{ investmentDetailDescription }}</p>
    </section>

    <section class="investment-detail-transactions-wrap" aria-label="交易记录">
      <header class="investment-detail-transactions-head">
        <strong>交易记录</strong>
        <span>{{ transactionCountText }}</span>
      </header>

      <section class="investment-detail-transactions-card">
        <article
          v-for="entry in investmentDetailTransactions"
          :key="`${entry.title}-${entry.time}`"
          class="investment-detail-transaction-item"
        >
          <div class="investment-detail-transaction-left">
            <strong>{{ entry.title }}</strong>
            <span>{{ entry.time }}</span>
          </div>
          <div class="investment-detail-transaction-right">
            <AmountText tag="span" :value="entry.amount" />
            <AmountText
              tag="strong"
              :class="entry.trend === 'up' ? 'trend-up' : 'trend-down'"
              :value="entry.units"
            />
            <span>{{ entry.note }}</span>
          </div>
        </article>
      </section>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
