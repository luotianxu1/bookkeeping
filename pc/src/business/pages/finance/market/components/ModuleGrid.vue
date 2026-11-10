<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import DesktopTable from '@/business/components/DesktopTable.vue'

// 全球页六大模块行情网格（A股指数/全球指数/股指期货/汇率指数/债券期货/全球商品）。
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  getGlobalModules,
  type GlobalModule,
} from '@/business/api/modules/market'
import { dirClass, f2 } from '@/business/utils/marketFormat'
import { detailRoute } from '../routes'
import { getErrorMessage } from '@/business/api/request'

const router = useRouter()

/* ---------- 状态 ---------- */
const modules = ref<GlobalModule[]>([])
const loading = ref(true)
const error = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const data = await getGlobalModules()
    modules.value = data.modules
  } catch (e) {
    error.value = getErrorMessage(e, '行情加载失败')
  } finally {
    loading.value = false
  }
}

void load()

/** 商品模块品种多，用 4 列，其余 3 列 */

/* ---------- 展示辅助 ---------- */
/** 智能价格：汇率等小数品种保留 4 位，指数/期货 2 位 */
function price(v: number | null): string {
  if (v == null) return '—'
  return Math.abs(v) < 10 ? v.toFixed(4) : f2(v)
}

/** 涨跌额带符号 */
function chg(row: GlobalModule['rows'][number]): string {
  const v = row['涨跌额'] as number | null
  if (v == null) return ''
  const s = Math.abs(v) < 10 ? v.toFixed(4) : f2(v)
  return (v > 0 ? '+' : '') + s
}

/** 涨跌幅带符号（后端已给 % 值） */
function pctS(v: number | null): string {
  return formatPercentage(v, true)
}

/** 模块涨跌家数统计 */

/** 模块条目详情路由：A股指数走 index，其余走 module（完整新浪代码） */
function openModule(m: GlobalModule, row: GlobalModule['rows'][number]): void {
  if (m.key === 'cn_index') {
    void router.push(detailRoute('index', row['代码']))
    return
  }
  void router.push(detailRoute('module', row['源代码'] ?? row['代码']))
}

type QuoteRow = GlobalModule['rows'][number]
</script>
<template>
  <section class="desktop-workspace">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <el-skeleton v-if="loading" :rows="8" animated />
    <el-tabs v-else class="desktop-tabs">
      <el-tab-pane v-for="m in modules" :key="m.key" :label="m.title">
        <DesktopTable
          :rows="m.rows"
          :loading="false"
          title="全球行情"
          search-placeholder="搜索代码或名称"
          searchable
        >
          <el-table-column
            prop="名称"
            label="名称"
            min-width="220"
            sortable="custom"
            show-overflow-tooltip
          />
          <el-table-column
            prop="代码"
            label="代码"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {QuoteRow} -->
          <el-table-column label="最新价" min-width="150">
            <template #default="{ row }: { row: QuoteRow }">
              <span :class="dirClass(row['涨跌幅'])">
                {{ price(row['最新价']) }}
              </span>
            </template>
          </el-table-column>
          <!-- @vue-generic {QuoteRow} -->
          <el-table-column label="涨跌额" min-width="150">
            <template #default="{ row }: { row: QuoteRow }">
              {{ chg(row) }}
            </template>
          </el-table-column>
          <!-- @vue-generic {QuoteRow} -->
          <el-table-column label="涨跌幅" min-width="150">
            <template #default="{ row }: { row: QuoteRow }">
              <span :class="dirClass(row['涨跌幅'])">
                {{ pctS(row['涨跌幅']) }}
              </span>
            </template>
          </el-table-column>
          <!-- @vue-generic {QuoteRow} -->
          <el-table-column fixed="right" label="操作" width="150">
            <template #default="{ row }: { row: QuoteRow }">
              <el-button
                link
                type="primary"
                v-if="row.linkable !== false"
                @click="openModule(m, row)"
              >
                查看行情
              </el-button>
            </template>
          </el-table-column>
        </DesktopTable>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
