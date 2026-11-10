<script setup lang="ts">
// A 股快照表：成交额前 20，行点击进个股详情。
import { useRouter } from 'vue-router'
import type { SpotRow } from '@/business/api/modules/market'
import { dirClass, fmt, f2, toYi } from '@/business/utils/marketFormat'
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
      <el-button class="retry" native-type="button" @click="emit('retry')">
        重试
      </el-button>
    </p>
    <div v-else-if="loading" class="table-wrap" aria-busy="true">
      <div v-for="n in 6" :key="n" class="table-line"></div>
    </div>
    <div v-else-if="stocks.length" class="table-wrap data-wrap">
      <el-table :data="stocks" stripe empty-text="暂无数据">
        <el-table-column label="代码" min-width="140">
          <template #default="{ row: s }">{{ fmt(s['代码']) }}</template>
        </el-table-column>
        <el-table-column label="名称" min-width="120">
          <template #default="{ row: s }">
            <el-button link type="primary" @click="open(s['代码'])">
              {{ fmt(s['名称']) }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="最新价" min-width="120">
          <template #default="{ row: s }">{{ f2(s['最新价']) }}</template>
        </el-table-column>
        <el-table-column label="涨跌幅" min-width="120">
          <template #default="{ row: s }">
            <span :class="dirClass(s['涨跌幅'])">
              {{ s['涨跌幅'] !== null ? fmt(s['涨跌幅']) + '%' : '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="成交额(亿)" min-width="120">
          <template #default="{ row: s }">{{ toYi(s['成交额']) }}</template>
        </el-table-column>
      </el-table>
    </div>
    <p v-else class="empty">暂无数据</p>
  </section>
</template>
