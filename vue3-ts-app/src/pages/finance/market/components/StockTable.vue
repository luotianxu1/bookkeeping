<script setup lang="ts">
// A 股快照表：成交额前 20，行点击进个股详情。
import { useRouter } from 'vue-router'
import type { SpotRow } from '@/api/modules/market'
import { dirClass, fmt, f2, toYi } from '@/utils/marketFormat'
import { detailRoute } from '../routes'

defineProps<{
  stocks: SpotRow[]
  loading: boolean
  error: string
}>()
const emit = defineEmits<{ retry: [] }>()
const router = useRouter()

/** 行点击 / 键盘回车进入个股详情 */
function open(code: number | string): void {
  void router.push(detailRoute('stock', String(code)))
}
</script>

<template>
  <section aria-labelledby="spot-title" class="spot-section">
    <div class="spot-head">
      <h2 id="spot-title">A 股快照</h2>
      <span class="spot-note">成交额前 20（实时）</span>
    </div>

    <p v-if="error" class="error-box" role="alert">
      快照加载失败：{{ error }}
      <button class="retry" type="button" @click="emit('retry')">重试</button>
    </p>

    <div v-else-if="loading" class="table-wrap" aria-busy="true">
      <div v-for="n in 6" :key="n" class="table-line"></div>
    </div>

    <div v-else-if="stocks.length" class="table-wrap data-wrap">
      <table>
        <thead>
          <tr>
            <th>代码</th>
            <th>名称</th>
            <th class="num">最新价</th>
            <th class="num">涨跌幅</th>
            <th class="num">成交额(亿)</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="s in stocks"
            :key="s['代码']"
            class="stock-row"
            tabindex="0"
            :aria-label="`${s['名称']} 详情`"
            @click="open(s['代码'])"
            @keydown.enter="open(s['代码'])"
          >
            <td class="num muted">{{ fmt(s['代码']) }}</td>
            <td>{{ fmt(s['名称']) }}</td>
            <td class="num">{{ f2(s['最新价']) }}</td>
            <td class="num" :class="dirClass(s['涨跌幅'])">
              {{ s['涨跌幅'] !== null ? fmt(s['涨跌幅']) + '%' : '—' }}
            </td>
            <td class="num">{{ toYi(s['成交额']) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-else class="empty">暂无数据</p>
  </section>
</template>

<style scoped lang="scss">
.spot-section {
  padding-top: var(--space-4);
}

.num {
  font-variant-numeric: tabular-nums;
}

.spot-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-10);
  margin-bottom: var(--space-10);

  h2 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: var(--font-size-14);
    font-weight: 700;
  }
}

.spot-note {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

.table-wrap {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  background: var(--color-bg-card);
  overflow-x: auto;
}

.data-wrap {
  padding: 0 var(--space-8);
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-12);
}

th,
td {
  padding: var(--space-10) var(--space-12);
  border-bottom: 1px solid var(--color-border);
  text-align: left;
  white-space: nowrap;
}

th {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
  font-weight: 500;
  letter-spacing: 0.08em;
  border-bottom-color: var(--color-border-soft-2);
}

tbody tr:last-child td {
  border-bottom: none;
}

tbody tr {
  transition: background 0.15s ease-out;
}

tbody tr:hover {
  background: var(--color-bg-soft);
}

.muted {
  color: var(--color-text-muted);
}

td.up {
  color: var(--color-danger);
}

td.down {
  color: var(--color-success);
}

/* 可点击行：指针 + 键盘焦点态 */
.stock-row {
  cursor: pointer;
}

.stock-row:focus-visible {
  outline: 3px solid var(--color-ring-focus);
  outline-offset: -2px;
}

/* 骨架 */
.table-line {
  height: 40px;
  border-radius: var(--radius-8);
  background: linear-gradient(90deg, var(--color-bg-soft) 25%, var(--color-bg-card) 50%, var(--color-bg-soft) 75%);
  background-size: 200% 100%;
  animation: skeleton-flow 1.4s ease infinite;
}

@keyframes skeleton-flow {
  from {
    background-position: 200% 0;
  }

  to {
    background-position: -200% 0;
  }
}

.error-box {
  margin: 0;
  padding: var(--space-12) var(--space-16);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  background: var(--color-bg-card);
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

.retry {
  min-height: 36px;
  margin-left: var(--space-10);
  padding: 0 var(--space-16);
  border: 1px solid var(--color-brand);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-brand);
  font: inherit;
  font-size: var(--font-size-12);
  font-weight: 700;
  cursor: pointer;
}

.empty {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

@media (prefers-reduced-motion: reduce) {
  .table-line {
    animation: none;
  }
}
</style>
