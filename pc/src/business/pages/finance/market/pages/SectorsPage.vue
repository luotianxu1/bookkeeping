<script setup lang="ts">
// 板块列表页：全部行业/概念板块，涨跌幅或成交额排序（移植自 investment Sectors 页）。
import { computed, onMounted, ref } from 'vue'
import {
  getSectorSpot,
  type SectorKind,
  type SectorRow,
} from '@/business/api/modules/market'
import { amt, dirClass, pct } from '@/business/utils/marketFormat'
import { getErrorMessage } from '@/business/api/request'

/* ---------- 状态 ---------- */
const kind = ref<SectorKind>('industry')
const sectors = ref<SectorRow[]>([])
const loading = ref(true)
const error = ref('')
const sortBy = ref<'pct' | 'amount'>('pct')

const KIND_META: Record<SectorKind, { title: string; unit: string }> = {
  industry: { title: '行业板块', unit: '个行业' },
  concept: { title: '概念板块', unit: '个概念' },
}

/* 排序：涨跌幅降序（默认）/ 成交额降序 */
const sorted = computed<SectorRow[]>(() => {
  const list = [...sectors.value]
  if (sortBy.value === 'pct') {
    list.sort((a, b) => (b['涨跌幅'] ?? -9999) - (a['涨跌幅'] ?? -9999))
  } else {
    list.sort((a, b) => (b['总成交额'] ?? 0) - (a['总成交额'] ?? 0))
  }
  return list
})

/* 入场错峰：前 12 个递增延迟，之后统一 */
function delay(idx: number): string {
  return `${Math.min(idx, 12) * 40}ms`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const data = await getSectorSpot(kind.value)
    sectors.value = data.data
  } catch (e) {
    error.value = getErrorMessage(e, '板块数据加载失败')
  } finally {
    loading.value = false
  }
}

/** 切换行业/概念 */
function switchKind(k: SectorKind): void {
  if (k === kind.value) return
  kind.value = k
  void load()
}

onMounted(load)
</script>

<template>
  <div class="pc-page sectors-page">
    <!-- 页头 -->
    <header class="page-head">
      <h1>{{ KIND_META[kind].title }}</h1>
      <span class="page-note num">
        共 {{ sectors.length }} {{ KIND_META[kind].unit }}
      </span>
    </header>
    <!-- 分类 + 排序工具条 -->
    <div class="pc-toolbar toolbar">
      <div class="pc-toolbar toolbar-group" role="group" aria-label="板块分类">
        <el-button
          v-for="k in ['industry', 'concept'] as SectorKind[]"
          :key="k"
          class="tab-btn"
          :class="{ active: kind === k }"
          :aria-pressed="kind === k"
          native-type="button"
          @click="switchKind(k)"
        >
          {{ KIND_META[k].title }}
        </el-button>
      </div>
      <div class="pc-toolbar toolbar-group" role="group" aria-label="排序方式">
        <el-button
          class="sort-btn"
          :class="{ active: sortBy === 'pct' }"
          :aria-pressed="sortBy === 'pct'"
          native-type="button"
          @click="sortBy = 'pct'"
        >
          按涨跌幅
        </el-button>
        <el-button
          class="sort-btn"
          :class="{ active: sortBy === 'amount' }"
          :aria-pressed="sortBy === 'amount'"
          native-type="button"
          @click="sortBy = 'amount'"
        >
          按成交额
        </el-button>
      </div>
    </div>
    <p v-if="error" class="error-box" role="alert">
      板块加载失败：{{ error }}
      <el-button class="retry" native-type="button" @click="load">
        重试
      </el-button>
    </p>
    <div v-else-if="loading" class="tiles" aria-busy="true">
      <div v-for="n in 18" :key="n" class="tile skeleton-tile"></div>
    </div>
    <!-- 全部板块：正方形方块阵 -->
    <div v-else-if="sorted.length" class="tiles">
      <el-card
        v-for="(sec, idx) in sorted"
        :key="sec['代码']"
        class="pc-record-card tile"
        :style="{ '--rise-delay': delay(idx) }"
        shadow="never"
      >
        <span class="rank num">{{ idx + 1 }}</span>
        <span class="tile-name">{{ sec['名称'] }}</span>
        <span class="num tile-pct" :class="dirClass(sec['涨跌幅'])">
          {{ pct(sec['涨跌幅']) }}
        </span>
        <span class="num tile-vol">{{ amt(sec['总成交额']) }}</span>
      </el-card>
    </div>
    <p v-else class="empty">暂无数据</p>
  </div>
</template>
