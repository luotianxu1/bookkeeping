<script setup lang="ts">
// 板块高亮卡片：领涨/领跌 TOP6 正方形方块阵（行业/概念/美股行业通用）。
import { computed } from 'vue'
import type { SectorKind, SectorRow } from '@/api/modules/market'
import { amt, dirClass, pct } from '@/utils/marketFormat'

const props = defineProps<{
  sectors: SectorRow[]
  loading: boolean
  error: string
  /** 板块分类：industry=行业板块 / concept=概念板块（默认） */
  kind?: SectorKind
  /** 覆盖标题（如美股行业板块） */
  heading?: string
  /** 是否显示「更多」入口（需有对应板块列表页） */
  showMore?: boolean
  /** 成交额币种符号（美股 $、港股 HK$；A 股缺省无符号） */
  currency?: string
}>()
const emit = defineEmits<{ retry: []; more: [] }>()

const isConcept = computed(() => (props.kind ?? 'concept') === 'concept')
const title = computed(() => props.heading ?? (isConcept.value ? '概念板块' : '行业板块'))
const unitNote = computed(() =>
  props.heading ? '个行业' : isConcept.value ? '个概念' : '个行业',
)

/* 领涨 / 领跌 TOP6（接口已按涨跌幅降序） */
const topGainers = computed(() => props.sectors.slice(0, 6))
const topLosers = computed(() => props.sectors.slice(-6).reverse())
</script>

<template>
  <section aria-labelledby="sector-title" class="sector-section">
    <div class="spot-head">
      <h2 id="sector-title">{{ title }}</h2>
      <span class="spot-note">领涨 / 领跌 TOP6 · 共 {{ sectors.length }} {{ unitNote }}</span>
      <button v-if="showMore !== false" class="more-btn" type="button" @click="emit('more')">
        更多 <span class="more-arrow" aria-hidden="true">→</span>
      </button>
    </div>

    <p v-if="error" class="error-box" role="alert">板块加载失败：{{ error }}</p>

    <div v-else-if="loading" class="table-wrap" aria-busy="true">
      <div v-for="n in 4" :key="n" class="table-line"></div>
    </div>

    <div v-else-if="sectors.length" class="sector-grid">
      <div class="sector-group">
        <h3 class="sector-sub up">领涨板块</h3>
        <div class="sec-tiles">
          <article v-for="sec in topGainers" :key="sec['代码']" class="sector-card">
            <span class="sec-name">{{ sec['名称'] }}</span>
            <span class="num sec-pct" :class="dirClass(sec['涨跌幅'])">{{ pct(sec['涨跌幅']) }}</span>
            <span class="num sec-vol">{{ amt(sec['总成交额'], currency ?? '') }}</span>
          </article>
        </div>
      </div>
      <div class="sector-group">
        <h3 class="sector-sub down">领跌板块</h3>
        <div class="sec-tiles">
          <article v-for="sec in topLosers" :key="sec['代码']" class="sector-card">
            <span class="sec-name">{{ sec['名称'] }}</span>
            <span class="num sec-pct" :class="dirClass(sec['涨跌幅'])">{{ pct(sec['涨跌幅']) }}</span>
            <span class="num sec-vol">{{ amt(sec['总成交额'], currency ?? '') }}</span>
          </article>
        </div>
      </div>
    </div>

    <p v-else class="empty">暂无数据</p>
  </section>
</template>

<style scoped lang="scss">
.sector-section {
  padding-top: var(--space-4);
}

.num {
  font-variant-numeric: tabular-nums;
}

/* ---------- 头部 ---------- */
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

/* 「更多」入口：板块区右侧 */
.more-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-4);
  margin-left: auto;
  min-height: 34px;
  padding: 0 var(--space-14);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  color: var(--color-text-muted);
  font: inherit;
  font-size: var(--font-size-11);
  cursor: pointer;
  transition:
    color 0.15s ease-out,
    border-color 0.15s ease-out;

  &:hover {
    color: var(--color-brand);
    border-color: var(--color-brand);
  }

  &:focus-visible {
    outline: 3px solid var(--color-ring-focus);
    outline-offset: 1px;
  }
}

.more-arrow {
  display: inline-block;
  transition: transform 0.15s ease-out;
}

.more-btn:hover .more-arrow {
  transform: translateX(2px);
}

.sector-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-12) var(--space-16);
}

.sector-sub {
  margin: 0 0 var(--space-8);
  font-size: var(--font-size-12);
  font-weight: 600;

  &.up {
    color: var(--color-danger);
  }

  &.down {
    color: var(--color-success);
  }
}

/* 正方形卡片：3 列方块阵 */
.sec-tiles {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-8);
}

.sector-card {
  display: flex;
  aspect-ratio: 1; /* 正方形 */
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-8);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-10);
  text-align: center;
  transition:
    border-color 0.15s ease-out,
    transform 0.15s ease-out;

  &:hover {
    border-color: var(--color-border-soft-2);
    transform: translateY(-2px);
  }
}

.sec-name {
  max-width: 100%;
  overflow: hidden;
  font-size: var(--font-size-12);
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sec-pct {
  font-size: var(--font-size-16);
  font-weight: 800;
  line-height: 1.1;

  &.up {
    color: var(--color-danger);
  }

  &.down {
    color: var(--color-success);
  }
}

.sec-vol {
  color: var(--color-text-muted);
  font-size: var(--font-size-11);
}

/* 骨架 */
.table-wrap {
  display: grid;
  gap: var(--space-8);
}

.table-line {
  height: 52px;
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

.empty {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-12);
}

@media (max-width: 760px) {
  .sector-grid {
    grid-template-columns: 1fr;
  }

  /* 移动端取消正方形约束：卡片高度随内容收紧，不再占半屏 */
  .sector-card {
    aspect-ratio: auto;
    padding: var(--space-10) var(--space-8);
  }
}

@media (prefers-reduced-motion: reduce) {
  .table-line {
    animation: none;
  }
}
</style>
