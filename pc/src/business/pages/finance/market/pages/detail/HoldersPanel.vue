<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

// 十大流通股东 + 股东人数变化（A股），自 DetailPage 抽出。
import { computed } from 'vue'
import type { F10Data } from '@/business/api/modules/market'
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
        {{ holders.date }} 报告期
        <template v-if="holders.announce">
          · 公告 {{ holders.announce }}
        </template>
        <template v-if="holders.total_holders">
          · 股东总数 {{ holders.total_holders.toLocaleString() }} 户
        </template>
      </span>
    </div>
    <div class="table-wrap">
      <el-table :data="holders.rows" stripe empty-text="暂无数据">
        <el-table-column label="#" min-width="140">
          <template #default="{ row: h }">{{ h.rank }}</template>
        </el-table-column>
        <el-table-column label="股东名称" min-width="120">
          <template #default="{ row: h }">{{ h.name }}</template>
        </el-table-column>
        <el-table-column label="持股数量" min-width="120">
          <template #default="{ row: h }">{{ wan(h.shares) }}</template>
        </el-table-column>
        <el-table-column label="占总股本" min-width="120">
          <template #default="{ row: h }">
            {{ h.ratio === null ? '—' : formatPercentage(h.ratio) }}
          </template>
        </el-table-column>
        <el-table-column label="较上期" min-width="120">
          <template #default="{ row: h }">
            <span v-if="h.note === '增持'" class="holder-up">↑ 增持</span>
            <span v-else-if="h.note === '减持'" class="holder-down">
              ↓ 减持
            </span>
            <span v-else-if="h.note === '新进'" class="holder-new">新进</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <!-- 股东人数变化：户数减少 = 筹码集中 -->
    <div v-if="holderTrendRows.length" class="holder-trend">
      <h3>股东人数变化</h3>
      <div class="table-wrap">
        <el-table :data="holderTrendRows" stripe empty-text="暂无数据">
          <el-table-column label="报告期" min-width="140">
            <template #default="{ row: t }">{{ t.date }}</template>
          </el-table-column>
          <el-table-column label="股东户数" min-width="120">
            <template #default="{ row: t }">
              {{ t.total.toLocaleString() }}
            </template>
          </el-table-column>
          <el-table-column label="较上期" min-width="120">
            <template #default="{ row: t }">
              <span
                :class="
                  t.delta === null
                    ? 'muted'
                    : t.delta <= 0
                      ? 'holder-up'
                      : 'holder-down'
                "
              >
                <template v-if="t.delta === null">—</template>
                <template v-else>
                  {{ t.delta > 0 ? '+' : '' }}{{ t.delta.toFixed(1) }}%
                </template>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="筹码" min-width="120">
            <template #default="{ row: t }">
              <span v-if="t.delta === null" class="muted">—</span>
              <span v-else-if="t.delta <= -3" class="holder-up">筹码集中</span>
              <span v-else-if="t.delta >= 3" class="holder-down">筹码分散</span>
              <span v-else class="muted">基本持平</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </section>
</template>
