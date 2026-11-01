<script setup lang="ts">
// 五档盘口 + 估值资料（A股盘口 / A股港股估值），自 DetailPage 抽出。
import { computed } from 'vue'
import type { DetailData } from '@/api/modules/market'
import { dirClass, f2, pct } from '@/utils/marketFormat'
import { vol } from './format'

const props = defineProps<{
  depth: DetailData['depth']
  valuation: DetailData['valuation']
  price: number | null
  changePct: number | null
}>()

/** 卖档自上而下：卖5 → 卖1 */
const asksDesc = computed(() => (props.depth ? [...props.depth.asks].reverse() : []))

function formatVal(k: string, v: number): string {
  if (k === '总市值' || k === '流通市值') return `${f2(v)}亿`
  if (k === '换手率' || k === '振幅') return `${f2(v)}%`
  return f2(v)
}
</script>

<template>
  <section v-if="depth || valuation" class="extra-row">
    <div v-if="depth" class="panel">
      <div class="spot-head">
        <h2>五档盘口</h2>
        <span class="spot-note num">{{ depth.date }} {{ depth.time }}</span>
      </div>
      <table class="depth-table">
        <tbody>
          <tr v-for="(a, i) in asksDesc" :key="'a' + i">
            <td class="side muted">卖{{ asksDesc.length - i }}</td>
            <td class="num ask-price">{{ f2(a[0]) }}</td>
            <td class="num muted">{{ vol(a[1]) }}</td>
          </tr>
          <tr class="now-row">
            <td class="side muted">最新</td>
            <td class="num" :class="dirClass(changePct)">{{ f2(price) }}</td>
            <td class="num muted" :class="dirClass(changePct)">{{ pct(changePct) }}</td>
          </tr>
          <tr v-for="(b, i) in depth.bids" :key="'b' + i">
            <td class="side muted">买{{ i + 1 }}</td>
            <td class="num bid-price">{{ f2(b[0]) }}</td>
            <td class="num muted">{{ vol(b[1]) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="valuation" class="panel">
      <div class="spot-head">
        <h2>估值资料</h2>
        <span class="spot-note">腾讯行情</span>
      </div>
      <dl class="val-grid">
        <div v-for="(v, k) in valuation" :key="k" class="val-item">
          <dt>{{ k }}</dt>
          <dd class="num">{{ formatVal(k, v) }}</dd>
        </div>
      </dl>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use './panelShared';

.depth-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-12);

  td {
    padding: var(--space-4) var(--space-10);
    border-bottom: 1px solid var(--color-border);
    white-space: nowrap;
  }

  tr:last-child td {
    border-bottom: none;
  }

  .side {
    width: 3em;
  }

  .now-row td {
    border-top: 1px solid var(--color-border-soft-2);
    border-bottom: 1px solid var(--color-border-soft-2);
    font-weight: 700;
  }
}

/* A股盘口惯例：卖档绿、买档红 */
.ask-price {
  color: var(--color-success);
}

.bid-price {
  color: var(--color-danger);
}
</style>
