<script setup lang="ts">
// 板块高亮卡片：领涨/领跌 TOP6 正方形方块阵（行业/概念/美股行业通用）。
import { computed } from 'vue'
import type { SectorKind, SectorRow } from '@/business/api/modules/market'
import { amt, dirClass, pct } from '@/business/utils/marketFormat'

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
const title = computed(
  () => props.heading ?? (isConcept.value ? '概念板块' : '行业板块'),
)
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
      <span class="spot-note">
        领涨 / 领跌 TOP6 · 共 {{ sectors.length }} {{ unitNote }}
      </span>
      <el-button
        v-if="showMore !== false"
        class="more-btn"
        native-type="button"
        @click="emit('more')"
      >
        更多
        <span class="more-arrow" aria-hidden="true">→</span>
      </el-button>
    </div>
    <p v-if="error" class="error-box" role="alert">板块加载失败：{{ error }}</p>
    <div v-else-if="loading" class="table-wrap" aria-busy="true">
      <div v-for="n in 4" :key="n" class="table-line"></div>
    </div>
    <div v-else-if="sectors.length" class="pc-grid sector-grid">
      <div class="sector-group">
        <h3 class="sector-sub up">领涨板块</h3>
        <div class="sec-tiles">
          <el-card
            v-for="sec in topGainers"
            :key="sec['代码']"
            class="pc-record-card sector-card"
            shadow="never"
          >
            <span class="sec-name">{{ sec['名称'] }}</span>
            <span class="num sec-pct" :class="dirClass(sec['涨跌幅'])">
              {{ pct(sec['涨跌幅']) }}
            </span>
            <span class="num sec-vol">
              {{ amt(sec['总成交额'], currency ?? '') }}
            </span>
          </el-card>
        </div>
      </div>
      <div class="sector-group">
        <h3 class="sector-sub down">领跌板块</h3>
        <div class="sec-tiles">
          <el-card
            v-for="sec in topLosers"
            :key="sec['代码']"
            class="pc-record-card sector-card"
            shadow="never"
          >
            <span class="sec-name">{{ sec['名称'] }}</span>
            <span class="num sec-pct" :class="dirClass(sec['涨跌幅'])">
              {{ pct(sec['涨跌幅']) }}
            </span>
            <span class="num sec-vol">
              {{ amt(sec['总成交额'], currency ?? '') }}
            </span>
          </el-card>
        </div>
      </div>
    </div>
    <p v-else class="empty">暂无数据</p>
  </section>
</template>
