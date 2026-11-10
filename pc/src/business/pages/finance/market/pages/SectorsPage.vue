<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'

// 行业和概念板块列表，支持涨跌幅、成交额排序。
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
  <section class="desktop-workspace">
    <PageHeader :title="KIND_META[kind].title">
      <template #right></template>
    </PageHeader>
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
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <DesktopTable
      :rows="sorted"
      :loading="loading"
      title="板块排名"
      search-placeholder="搜索板块"
      searchable
    >
      <el-table-column
        prop="代码"
        label="代码"
        min-width="140"
        sortable="custom"
        show-overflow-tooltip
      />
      <el-table-column
        prop="名称"
        label="板块"
        min-width="220"
        sortable="custom"
        show-overflow-tooltip
      />
      <!-- @vue-generic {SectorRow} -->
      <el-table-column label="涨跌幅" min-width="150">
        <template #default="{ row }: { row: SectorRow }">
          <span :class="dirClass(row['涨跌幅'])">{{ pct(row['涨跌幅']) }}</span>
        </template>
      </el-table-column>
      <!-- @vue-generic {SectorRow} -->
      <el-table-column label="成交额" min-width="150">
        <template #default="{ row }: { row: SectorRow }">
          {{ amt(row['总成交额']) }}
        </template>
      </el-table-column>
    </DesktopTable>
  </section>
</template>
