<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

// 五档盘口 + 估值资料（A股盘口 / A股港股估值），自 DetailPage 抽出。
import { computed } from 'vue'
import type { DetailData } from '@/business/api/modules/market'
import { dirClass, f2, pct } from '@/business/utils/marketFormat'
import { vol } from './format'

const props = defineProps<{
  depth: DetailData['depth']
  valuation: DetailData['valuation']
  price: number | null
  changePct: number | null
}>()

/** 卖档自上而下：卖5 → 卖1 */
const asksDesc = computed(() =>
  props.depth ? [...props.depth.asks].reverse() : [],
)

function formatVal(k: string, v: number): string {
  if (k === '总市值' || k === '流通市值') return `${f2(v)}亿`
  if (k === '换手率' || k === '振幅') return formatPercentage(v)
  return f2(v)
}
</script>

<template>
  <section v-if="depth || valuation" class="extra-row">
    <el-card v-if="depth" class="pc-record-card panel" shadow="never">
      <div class="spot-head">
        <h2>五档盘口</h2>
        <span class="spot-note num">{{ depth.date }} {{ depth.time }}</span>
      </div>
      <el-table
        :data="[
          ...asksDesc.map((v, i) => ({
            side: '卖' + (asksDesc.length - i),
            price: f2(v[0]),
            volume: vol(v[1]),
          })),
          { side: '最新', price: f2(price), volume: pct(changePct) },
          ...(depth?.bids ?? []).map((v, i) => ({
            side: '买' + (i + 1),
            price: f2(v[0]),
            volume: vol(v[1]),
          })),
        ]"
        stripe
      >
        <el-table-column label="档位" prop="side" />
        <el-table-column label="价格" prop="price" />
        <el-table-column label="数量" prop="volume" />
      </el-table>
    </el-card>
    <el-card v-if="valuation" class="pc-record-card panel" shadow="never">
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
    </el-card>
  </section>
</template>
