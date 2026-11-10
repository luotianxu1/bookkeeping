<script setup lang="ts">
// 资金流向（A股）：最新日英雄数字 + 双向条形图，自 DetailPage 抽出。
import { computed } from 'vue'
import type { ExtraInfo } from '@/business/api/modules/market'
import { dirClass, f2, pct, signedYi } from '@/business/utils/marketFormat'

const props = defineProps<{
  rows: ExtraInfo['moneyflow']
}>()

const latestFlow = computed(() => props.rows[0] ?? null)
const maxAbsNet = computed(() =>
  Math.max(...props.rows.map((r) => Math.abs(r.net)), 1),
)

/** 双向条形图定位：轨道中线为 0，流入向右（红）、流出向左（绿） */
function netBarStyle(net: number): Record<string, string> {
  const half = (Math.abs(net) / maxAbsNet.value) * 50
  return net >= 0
    ? { left: '50%', width: `${half}%` }
    : { right: '50%', width: `${half}%` }
}

/** 连续同向天数：从最新日起净流入（或流出）连续的天数 */
const flowStreak = computed(() => {
  const rows = props.rows
  if (!rows.length) return 0
  const inflow = rows[0].net >= 0
  let n = 0
  for (const r of rows) {
    if (r.net >= 0 === inflow) n++
    else break
  }
  return n
})

/** 区间累计（返回顺序与表格列一致：主力/超大/大/中/小） */
const flowSum = computed(() => {
  const s = { net: 0, xl: 0, l: 0, m: 0, s: 0 }
  for (const r of props.rows) {
    s.net += r.net
    s.xl += r.xl_net
    s.l += r.l_net
    s.m += r.m_net
    s.s += r.s_net
  }
  return s
})

/** 区间日均净流入率（tfoot 用） */
const flowSumAvg = computed(() => {
  const rows = props.rows
  if (!rows.length) return '—'
  const avg = rows.reduce((acc, r) => acc + r.ratio, 0) / rows.length
  return `日均 ${avg >= 0 ? '+' : ''}${avg.toFixed(1)}%`
})
</script>

<template>
  <section class="kline-section">
    <div class="spot-head">
      <h2>资金流向</h2>
      <span class="spot-note">
        近 {{ rows.length }} 日 · 主力 = 超大单 + 大单 · 新浪
      </span>
    </div>
    <el-card v-if="latestFlow" class="pc-record-card flow-hero" shadow="never">
      <div class="flow-hero-main">
        <span class="flow-hero-label num">
          {{ latestFlow.date }} 主力净流入
        </span>
        <span class="num flow-hero-value" :class="dirClass(latestFlow.net)">
          {{ signedYi(latestFlow.net) }}
        </span>
        <span
          v-if="flowStreak > 1"
          class="flow-streak num"
          :class="dirClass(latestFlow.net)"
        >
          {{ latestFlow.net >= 0 ? '连续流入' : '连续流出' }}
          {{ flowStreak }} 日
        </span>
      </div>
      <div class="flow-hero-sub num">
        <span>
          净流入率
          <b :class="dirClass(latestFlow.net)">
            {{ latestFlow.ratio.toFixed(1) }}%
          </b>
        </span>
        <span>
          超大单
          <b :class="dirClass(latestFlow.xl_net)">
            {{ signedYi(latestFlow.xl_net) }}
          </b>
        </span>
        <span>
          大单
          <b :class="dirClass(latestFlow.l_net)">
            {{ signedYi(latestFlow.l_net) }}
          </b>
        </span>
        <span>
          中单
          <b :class="dirClass(latestFlow.m_net)">
            {{ signedYi(latestFlow.m_net) }}
          </b>
        </span>
        <span>
          小单
          <b :class="dirClass(latestFlow.s_net)">
            {{ signedYi(latestFlow.s_net) }}
          </b>
        </span>
      </div>
    </el-card>
    <div class="table-wrap">
      <el-table :data="rows" stripe empty-text="暂无数据">
        <el-table-column label="日期" min-width="140">
          <template #default="{ row: r }">{{ r.date }}</template>
        </el-table-column>
        <el-table-column label="收盘价" min-width="120">
          <template #default="{ row: r }">{{ f2(r.close) }}</template>
        </el-table-column>
        <el-table-column label="涨跌幅" min-width="120">
          <template #default="{ row: r }">
            <span :class="dirClass(r.pct)">{{ pct(r.pct) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="换手率" min-width="120">
          <template #default="{ row: r }">
            {{ r.turnover.toFixed(1) }}%
          </template>
        </el-table-column>
        <el-table-column label="净流入率" min-width="120">
          <template #default="{ row: r }">{{ r.ratio.toFixed(1) }}%</template>
        </el-table-column>
        <el-table-column label="主力净流入" min-width="120">
          <template #default="{ row: r }">
            <span :class="dirClass(r.net)">{{ signedYi(r.net) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="净流入分布" min-width="120">
          <template #default="{ row: r }">
            <div class="flow-bar-track" aria-hidden="true">
              <div
                class="flow-bar"
                :class="r.net >= 0 ? 'in' : 'out'"
                :style="netBarStyle(r.net)"
              ></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="超大单" min-width="120">
          <template #default="{ row: r }">
            <span :class="dirClass(r.xl_net)">{{ signedYi(r.xl_net) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="大单" min-width="120">
          <template #default="{ row: r }">
            <span :class="dirClass(r.l_net)">{{ signedYi(r.l_net) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="中单" min-width="120">
          <template #default="{ row: r }">
            <span :class="dirClass(r.m_net)">{{ signedYi(r.m_net) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="小单" min-width="120">
          <template #default="{ row: r }">
            <span :class="dirClass(r.s_net)">{{ signedYi(r.s_net) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pc-toolbar table-totals">
        <span>累计</span>
        <span>{{ rows.length }} 个交易日</span>
        <span>{{ flowSumAvg }}</span>
        <span>{{ signedYi(flowSum.net) }}</span>
        <span></span>
        <span>{{ signedYi(flowSum.xl) }}</span>
        <span>{{ signedYi(flowSum.l) }}</span>
        <span>{{ signedYi(flowSum.m) }}</span>
        <span>{{ signedYi(flowSum.s) }}</span>
      </div>
    </div>
  </section>
</template>
