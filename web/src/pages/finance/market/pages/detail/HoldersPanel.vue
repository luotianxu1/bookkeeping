<script setup lang="ts">
// 十大流通股东 + 股东人数变化（A股），自 DetailPage 抽出。
import { computed } from 'vue'
import type { F10Data } from '@/api/modules/market'
import { wan } from './format'

const props = defineProps<{
  holders: NonNullable<F10Data['holders']>
}>()

/** 股东人数趋势（附较上期变化%，从新到旧） */
const holderTrendRows = computed(() => {
  const trend = props.holders.trend ?? []
  return trend.slice(0, 8).map((t, i) => {
    const prev = trend[i + 1]?.total
    const delta = prev && prev > 0 ? ((t.total - prev) / prev) * 100 : null
    return { date: t.date, total: t.total, delta }
  })
})
</script>

<template>
  <section v-if="holders.rows.length" class="kline-section">
    <div class="spot-head">
      <h2>十大流通股东</h2>
      <span class="spot-note num">
        {{ holders.date }} 报告期<template v-if="holders.announce"> · 公告 {{ holders.announce }}</template>
        <template v-if="holders.total_holders"> · 股东总数 {{ holders.total_holders.toLocaleString() }} 户</template>
      </span>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>股东名称</th>
            <th class="num">持股数量</th>
            <th class="num">占总股本</th>
            <th class="num">较上期</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="h in holders.rows" :key="h.rank">
            <td class="num muted">{{ h.rank }}</td>
            <td>{{ h.name }}</td>
            <td class="num">{{ wan(h.shares) }}</td>
            <td class="num muted">{{ h.ratio === null ? '—' : `${h.ratio}%` }}</td>
            <td class="num">
              <span v-if="h.note === '增持'" class="holder-up">↑ 增持</span>
              <span v-else-if="h.note === '减持'" class="holder-down">↓ 减持</span>
              <span v-else-if="h.note === '新进'" class="holder-new">新进</span>
              <span v-else class="muted">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <!-- 股东人数变化：户数减少 = 筹码集中 -->
    <div v-if="holderTrendRows.length" class="holder-trend">
      <h3>股东人数变化</h3>
      <div class="table-wrap">
        <table class="ht-table">
          <thead>
            <tr>
              <th class="num">报告期</th>
              <th class="num">股东户数</th>
              <th class="num">较上期</th>
              <th>筹码</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in holderTrendRows" :key="t.date">
              <td class="num">{{ t.date }}</td>
              <td class="num">{{ t.total.toLocaleString() }}</td>
              <td class="num" :class="t.delta === null ? 'muted' : t.delta <= 0 ? 'holder-up' : 'holder-down'">
                <template v-if="t.delta === null">—</template>
                <template v-else>{{ t.delta > 0 ? '+' : '' }}{{ t.delta.toFixed(1) }}%</template>
              </td>
              <td>
                <span v-if="t.delta === null" class="muted">—</span>
                <span v-else-if="t.delta <= -3" class="holder-up">筹码集中</span>
                <span v-else-if="t.delta >= 3" class="holder-down">筹码分散</span>
                <span v-else class="muted">基本持平</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use './panelShared';

/* 股东人数变化表 */
.holder-trend {
  margin-top: var(--space-14);
  padding-top: var(--space-10);
  border-top: 1px dashed var(--color-border);

  h3 {
    margin: 0 0 var(--space-8);
    color: var(--color-brand);
    font-size: var(--font-size-11);
    letter-spacing: 0.08em;
  }
}

.holder-up {
  color: var(--color-danger);
}

.holder-down {
  color: var(--color-success);
}

.holder-new {
  color: var(--color-chart-gold);
}
</style>
