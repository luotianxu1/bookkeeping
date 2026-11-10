<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'
// 三大财务报表（A股）：利润表/资产负债表/现金流量表，自 DetailPage 抽出。
// 报表 tab 状态与「无数据自动切换」逻辑内聚在本组件。
import { computed, ref, watch } from 'vue'
import type { StatementsData } from '@/business/api/modules/market'

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
  return (
    /营业总收入|净利润|资产总计|负债合计|所有者权益|经营活动产生的现金流量净额/.test(
      label,
    ) && !/增长率|比重|综合收益/.test(label)
  )
}
</script>

<template>
  <section class="kline-section">
    <div class="spot-head">
      <h2>财务报表</h2>
      <span class="spot-note num">单位：万元 · 最近 5 个报告期 · 新浪</span>
    </div>
    <div class="pc-toolbar stmt-tabs" role="group" aria-label="报表选择">
      <el-button
        v-for="t in STMT_TABS"
        :key="t.key"
        class="sub-tab"
        :class="{ active: stmtTab === t.key }"
        native-type="button"
        @click="stmtTab = t.key"
      >
        {{ t.label }}
      </el-button>
    </div>
    <div v-if="curStatements" class="table-wrap">
      <el-table :data="curStatements.rows" stripe empty-text="暂无数据">
        <el-table-column label="科目" min-width="140">
          <template #default="{ row: r }">{{ r.label }}</template>
        </el-table-column>
        <el-table-column
          v-for="(p, i) in curStatements.periods"
          :key="p"
          :label="p"
          min-width="140"
        >
          <template #default="{ row: r }">
            <span v-for="v in [r.vals[i]]" :key="i">
              {{
                v === null
                  ? '—'
                  : /[%％]|增长率|利润率|比重/.test(r.label)
                    ? formatPercentage(v)
                    : v.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
              }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>
