<script setup lang="ts">
// F10：融资融券 + 财务指标（A股），自 DetailPage 抽出。
import type { F10Data } from '@/api/modules/market'
import { dirClass, pct, signedYi } from '@/utils/marketFormat'
import { finVal, wan, yi } from './format'

defineProps<{
  f10: F10Data
}>()
</script>

<template>
  <section v-if="f10.margin || f10.financials" class="extra-row">
    <div v-if="f10.margin" class="panel">
      <div class="spot-head">
        <h2>融资融券</h2>
        <span class="spot-note">{{ f10.margin.date }} · 交易所官方</span>
      </div>
      <div class="margin-hero">
        <div class="margin-hero-main">
          <span class="margin-hero-label">融资余额</span>
          <span class="num flow-hero-value">{{ yi(f10.margin.balance) }}</span>
        </div>
        <div v-if="f10.margin.delta !== null" class="margin-hero-sub num">
          较上日 <b :class="dirClass(f10.margin.delta)">{{ signedYi(f10.margin.delta) }}</b>
        </div>
      </div>
      <dl class="val-grid">
        <div class="val-item">
          <dt>融资买入额</dt>
          <dd class="num">{{ yi(f10.margin.buy) }}</dd>
        </div>
        <div class="val-item">
          <dt>融资偿还额</dt>
          <dd class="num">{{ f10.margin.repay === null ? '—' : yi(f10.margin.repay) }}</dd>
        </div>
        <div class="val-item">
          <dt>融券余量</dt>
          <dd class="num">{{ wan(f10.margin.short_vol) }}</dd>
        </div>
        <div class="val-item">
          <dt>融券卖出量</dt>
          <dd class="num">{{ f10.margin.short_sell === null ? '—' : wan(f10.margin.short_sell) }}</dd>
        </div>
      </dl>
    </div>

    <div v-if="f10.financials" class="panel">
      <div class="spot-head">
        <h2>财务指标</h2>
        <span class="spot-note num">{{ f10.financials.latest_date }} vs {{ f10.financials.prev_date }}</span>
      </div>
      <div class="table-wrap">
        <table class="fin-table">
          <thead>
            <tr>
              <th>指标</th>
              <th class="num">最新报告期</th>
              <th class="num">上年同期</th>
              <th class="num">同比</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in f10.financials.rows" :key="r.name">
              <td>{{ r.name }}</td>
              <td class="num">{{ finVal(r.latest) }}</td>
              <td class="num muted">{{ finVal(r.prev) }}</td>
              <td class="num" :class="dirClass(r.yoy ?? 0)">{{ r.yoy === null ? '—' : pct(r.yoy) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use './panelShared';

.margin-hero {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--space-8) var(--space-16);
  padding: var(--space-2) 0 var(--space-16);
}

.margin-hero-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-10);
}

.margin-hero-label {
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

.margin-hero-sub {
  color: var(--color-text-muted);
  font-size: var(--font-size-12);

  b {
    font-weight: 600;
  }
}

.fin-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-12);

  th,
  td {
    padding: var(--space-8) var(--space-10);
    border-bottom: 1px solid var(--color-border);
    text-align: left;
    white-space: nowrap;
  }

  th:first-child,
  td:first-child {
    padding-left: 0;
  }

  tr:last-child td {
    border-bottom: none;
  }
}
</style>
