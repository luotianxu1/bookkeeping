<script setup lang="ts">
// 资金流向（A股）：最新日英雄数字 + 双向条形图，自 DetailPage 抽出。
import { computed } from 'vue'
import type { ExtraInfo } from '@/api/modules/market'
import { dirClass, f2, pct, signedYi } from '@/utils/marketFormat'

const props = defineProps<{
  rows: ExtraInfo['moneyflow']
}>()

const latestFlow = computed(() => props.rows[0] ?? null)
const maxAbsNet = computed(() => Math.max(...props.rows.map((r) => Math.abs(r.net)), 1))

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
    if ((r.net >= 0) === inflow) n++
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
  return `日均 ${avg >= 0 ? '+' : ''}${avg.toFixed(2)}%`
})
</script>

<template>
  <section class="kline-section">
    <div class="spot-head">
      <h2>资金流向</h2>
      <span class="spot-note">近 {{ rows.length }} 日 · 主力 = 超大单 + 大单 · 新浪</span>
    </div>
    <div v-if="latestFlow" class="flow-hero">
      <div class="flow-hero-main">
        <span class="flow-hero-label num">{{ latestFlow.date }} 主力净流入</span>
        <span class="num flow-hero-value" :class="dirClass(latestFlow.net)">{{ signedYi(latestFlow.net) }}</span>
        <span v-if="flowStreak > 1" class="flow-streak num" :class="dirClass(latestFlow.net)">
          {{ latestFlow.net >= 0 ? '连续流入' : '连续流出' }} {{ flowStreak }} 日
        </span>
      </div>
      <div class="flow-hero-sub num">
        <span>净流入率 <b :class="dirClass(latestFlow.net)">{{ latestFlow.ratio.toFixed(2) }}%</b></span>
        <span>超大单 <b :class="dirClass(latestFlow.xl_net)">{{ signedYi(latestFlow.xl_net) }}</b></span>
        <span>大单 <b :class="dirClass(latestFlow.l_net)">{{ signedYi(latestFlow.l_net) }}</b></span>
        <span>中单 <b :class="dirClass(latestFlow.m_net)">{{ signedYi(latestFlow.m_net) }}</b></span>
        <span>小单 <b :class="dirClass(latestFlow.s_net)">{{ signedYi(latestFlow.s_net) }}</b></span>
      </div>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>日期</th>
            <th class="num">收盘价</th>
            <th class="num">涨跌幅</th>
            <th class="num">换手率</th>
            <th class="num">净流入率</th>
            <th class="num">主力净流入</th>
            <th class="flow-bar-head">净流入分布</th>
            <th class="num">超大单</th>
            <th class="num">大单</th>
            <th class="num">中单</th>
            <th class="num">小单</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.date">
            <td class="num">{{ r.date }}</td>
            <td class="num">{{ f2(r.close) }}</td>
            <td class="num" :class="dirClass(r.pct)">{{ pct(r.pct) }}</td>
            <td class="num muted">{{ r.turnover.toFixed(2) }}%</td>
            <td class="num muted">{{ r.ratio.toFixed(2) }}%</td>
            <td class="num flow-strong" :class="dirClass(r.net)">{{ signedYi(r.net) }}</td>
            <td class="flow-bar-cell">
              <div class="flow-bar-track" aria-hidden="true">
                <div class="flow-bar" :class="r.net >= 0 ? 'in' : 'out'" :style="netBarStyle(r.net)"></div>
              </div>
            </td>
            <td class="num" :class="dirClass(r.xl_net)">{{ signedYi(r.xl_net) }}</td>
            <td class="num" :class="dirClass(r.l_net)">{{ signedYi(r.l_net) }}</td>
            <td class="num" :class="dirClass(r.m_net)">{{ signedYi(r.m_net) }}</td>
            <td class="num" :class="dirClass(r.s_net)">{{ signedYi(r.s_net) }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td class="flow-foot-label">累计</td>
            <td colspan="3" class="muted">{{ rows.length }} 个交易日</td>
            <td class="num muted">{{ flowSumAvg }}</td>
            <td class="num flow-strong" :class="dirClass(flowSum.net)">{{ signedYi(flowSum.net) }}</td>
            <td></td>
            <td class="num" :class="dirClass(flowSum.xl)">{{ signedYi(flowSum.xl) }}</td>
            <td class="num" :class="dirClass(flowSum.l)">{{ signedYi(flowSum.l) }}</td>
            <td class="num" :class="dirClass(flowSum.m)">{{ signedYi(flowSum.m) }}</td>
            <td class="num" :class="dirClass(flowSum.s)">{{ signedYi(flowSum.s) }}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use './panelShared';

/* 英雄数字 + 双向条形图 */
.flow-hero {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--space-8) var(--space-20);
  margin-bottom: var(--space-12);
  padding: var(--space-12) var(--space-16);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  /* 琥珀金自上而下渗光，区分于普通表格面板 */
  background:
    linear-gradient(180deg, rgba(234, 179, 8, 0.08), transparent 72%),
    var(--color-bg-card);
}

.flow-hero-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-12);
}

.flow-hero-label {
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
  letter-spacing: 0.06em;
}

.flow-hero-value {
  font-size: var(--font-size-26);
  font-weight: 700;
  white-space: nowrap;
}

.flow-hero-sub {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-6) var(--space-14);
  color: var(--color-text-muted);
  font-size: var(--font-size-12);

  b {
    font-weight: 600;
    white-space: nowrap;
  }
}

/* 连续同向天数徽标 */
.flow-streak {
  padding: var(--space-2) var(--space-8);
  border: 1px solid currentColor;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-11);
  font-weight: 600;
  opacity: 0.9;
}

.flow-strong {
  font-weight: 700;
}

.flow-bar-head {
  min-width: 150px;
}

/* 累计行：双线压顶 + 淡琥珀底，与数据行区分 */
.flow-foot-label {
  font-weight: 700;
  letter-spacing: 0.1em;
}

tfoot td {
  border-top: 2px solid var(--color-border);
  border-bottom: none;
  background: rgba(234, 179, 8, 0.05);
  font-weight: 600;
}

.flow-bar-cell {
  min-width: 150px;
  padding-top: var(--space-12);
  padding-bottom: var(--space-12);
}

/* 发丝中线为 0 轴，条形自中线向两侧生长 */
.flow-bar-track {
  position: relative;
  height: 12px;

  &::before {
    content: '';
    position: absolute;
    left: 50%;
    top: -5px;
    bottom: -5px;
    width: 1px;
    background: var(--color-border);
  }
}

.flow-bar {
  position: absolute;
  top: 50%;
  height: 8px;
  border-radius: 2px;
  transform: translateY(-50%);

  &.in {
    background: linear-gradient(90deg, rgba(220, 38, 38, 0.2), var(--color-danger));
  }

  &.out {
    background: linear-gradient(270deg, rgba(22, 163, 74, 0.2), var(--color-success));
  }
}
</style>
