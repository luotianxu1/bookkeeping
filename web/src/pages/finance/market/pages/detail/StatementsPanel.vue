<script setup lang="ts">
// 三大财务报表（A股）：利润表/资产负债表/现金流量表，自 DetailPage 抽出。
// 报表 tab 状态与「无数据自动切换」逻辑内聚在本组件。
import { computed, ref, watch } from 'vue'
import type { StatementsData } from '@/api/modules/market'

const props = defineProps<{
  statements: StatementsData | null
}>()

type StmtKey = 'income' | 'balance' | 'cashflow'
const STMT_TABS: { key: StmtKey; label: string }[] = [
  { key: 'income', label: '利润表' },
  { key: 'balance', label: '资产负债表' },
  { key: 'cashflow', label: '现金流量表' },
]
const stmtTab = ref<StmtKey>('income')

/** 当前选中报表的非空视图，模板里 v-if 收窄用 */
const curStatements = computed(() => props.statements?.[stmtTab.value] ?? null)

// 当前选中的报表若没有数据，自动切到第一张有数据的报表
watch(
  () => props.statements,
  (data) => {
    if (data && !data[stmtTab.value]) {
      const first = STMT_TABS.find((t) => !!data[t.key])
      if (first) stmtTab.value = first.key
    }
  },
)

/** 重点科目行强调（利润/资产/负债/权益/经营现金流合计行） */
function isKeyRow(label: string): boolean {
  return /营业总收入|净利润|资产总计|负债合计|所有者权益|经营活动产生的现金流量净额/.test(label) && !/增长率|比重|综合收益/.test(label)
}
</script>

<template>
  <section class="kline-section">
    <div class="spot-head">
      <h2>财务报表</h2>
      <span class="spot-note num">单位：万元 · 最近 5 个报告期 · 新浪</span>
    </div>
    <div class="stmt-tabs" role="group" aria-label="报表选择">
      <button
        v-for="t in STMT_TABS"
        :key="t.key"
        class="sub-tab"
        :class="{ active: stmtTab === t.key }"
        type="button"
        @click="stmtTab = t.key"
      >
        {{ t.label }}
      </button>
    </div>
    <div v-if="curStatements" class="table-wrap">
      <table class="stmt-table">
        <thead>
          <tr>
            <th class="stmt-label-col">科目</th>
            <th v-for="p in curStatements.periods" :key="p" class="num">{{ p }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in curStatements.rows" :key="r.label" :class="{ 'stmt-key': isKeyRow(r.label) }">
            <td>{{ r.label }}</td>
            <td v-for="(v, i) in r.vals" :key="i" class="num">
              {{ v === null ? '—' : v.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use './panelShared';

.stmt-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding: var(--space-2) 0 var(--space-8);
}

.sub-tab {
  min-height: 24px;
  padding: var(--space-2) var(--space-10);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-8);
  color: var(--color-text-muted);
  font: inherit;
  font-size: var(--font-size-11);
  letter-spacing: 0.04em;
  cursor: pointer;
  transition:
    color 0.15s ease-out,
    border-color 0.15s ease-out;

  &:hover {
    color: var(--color-text-primary);
  }

  &.active {
    border-color: var(--color-border);
    background: var(--color-bg-brand-soft);
    color: var(--color-brand);
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }
}

.stmt-table th,
.stmt-table td {
  white-space: nowrap;
}

.stmt-table .stmt-label-col {
  min-width: 180px;
  text-align: left;
}

.stmt-table tr.stmt-key td {
  color: var(--color-brand);
  font-weight: 600;
}

.stmt-table tr.stmt-key td:first-child {
  border-left: 2px solid rgba(29, 78, 216, 0.35);
}
</style>
