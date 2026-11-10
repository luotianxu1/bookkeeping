<script setup lang="ts">
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
function gridClass(m: GlobalModule): string {
  return m.key === 'commodity' ? 'cols-4' : 'cols-3'
}

/* ---------- 展示辅助 ---------- */
/** 智能价格：汇率等小数品种保留 4 位，指数/期货 2 位 */
function price(v: number | null): string {
  if (v == null) return '—'
  return Math.abs(v) < 10 ? v.toFixed(4) : f2(v)
}

/** 涨跌额带符号 */
function chg(row: Record<string, unknown>): string {
  const v = row['涨跌额'] as number | null
  if (v == null) return ''
  const s = Math.abs(v) < 10 ? v.toFixed(4) : f2(v)
  return (v > 0 ? '+' : '') + s
}

/** 涨跌幅带符号（后端已给 % 值） */
function pctS(v: number | null): string {
  if (v == null) return '—'
  return `${v > 0 ? '+' : ''}${v.toFixed(2)}%`
}

/** 模块涨跌家数统计 */
function stat(m: GlobalModule): { up: number; down: number } {
  let up = 0
  let down = 0
  for (const r of m.rows) {
    const p = r['涨跌幅']
    if (p == null) continue
    if (p > 0) up++
    else if (p < 0) down++
  }
  return { up, down }
}

/** 模块条目详情路由：A股指数走 index，其余走 module（完整新浪代码） */
function openModule(m: GlobalModule, row: GlobalModule['rows'][number]): void {
  if (m.key === 'cn_index') {
    void router.push(detailRoute('index', row['代码']))
    return
  }
  void router.push(detailRoute('module', row['源代码'] ?? row['代码']))
}
</script>

<template>
  <div>
    <p v-if="error" class="error-box" role="alert">
      行情加载失败：{{ error }}
      <el-button class="retry" native-type="button" @click="load">
        重试
      </el-button>
    </p>
    <div v-else-if="loading" class="loading" aria-busy="true">
      <div v-for="n in 3" :key="n" class="skel-group"></div>
    </div>
    <!-- 六模块按 01-06 顺序纵向排列 -->
    <template v-else>
      <section
        v-for="(m, mi) in modules"
        :key="m.key"
        class="module"
        :aria-label="m.title"
      >
        <header class="m-head">
          <span class="num m-idx">{{ String(mi + 1).padStart(2, '0') }}</span>
          <h2 class="m-title">{{ m.title }}</h2>
          <span class="num m-stat">
            <em class="up">{{ stat(m).up }}涨</em>
            <i>/</i>
            <em class="down">{{ stat(m).down }}跌</em>
          </span>
        </header>
        <div class="m-grid" :class="gridClass(m)">
          <template v-for="q in m.rows" :key="m.key + q['代码']">
            <el-button
              v-if="q['linkable'] !== false"
              class="cell link"
              native-type="button"
              :aria-label="`${q['名称']} 详情`"
              @click="openModule(m, q)"
            >
              <span class="cell-top">
                <span class="cell-name">{{ q['名称'] }}</span>
                <span class="num cell-pct" :class="dirClass(q['涨跌幅'])">
                  {{ pctS(q['涨跌幅']) }}
                </span>
              </span>
              <span class="num cell-price" :class="dirClass(q['涨跌幅'])">
                {{ price(q['最新价']) }}
              </span>
              <span class="num cell-chg" :class="dirClass(q['涨跌幅'])">
                {{ chg(q) }}
              </span>
            </el-button>
            <el-card v-else class="pc-record-card cell" shadow="never">
              <span class="cell-top">
                <span class="cell-name">{{ q['名称'] }}</span>
                <span class="num cell-pct" :class="dirClass(q['涨跌幅'])">
                  {{ pctS(q['涨跌幅']) }}
                </span>
              </span>
              <span class="num cell-price" :class="dirClass(q['涨跌幅'])">
                {{ price(q['最新价']) }}
              </span>
              <span class="num cell-chg" :class="dirClass(q['涨跌幅'])">
                {{ chg(q) }}
              </span>
            </el-card>
          </template>
        </div>
      </section>
    </template>
  </div>
</template>
