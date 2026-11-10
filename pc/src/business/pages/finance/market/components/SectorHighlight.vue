<script setup lang="ts">
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

// 行业、概念和海外板块的领涨、领跌排名表。
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

/* 领涨 / 领跌 TOP6（接口已按涨跌幅降序） */
const topGainers = computed(() => props.sectors.slice(0, 6))
const topLosers = computed(() => props.sectors.slice(-6).reverse())
</script>
<template>
  <section class="desktop-workspace">
    <PagePanel :title="title">
      <template #actions>
        <el-button
          v-if="showMore !== false"
          text
          type="primary"
          @click="emit('more')"
        >
          全部板块
        </el-button>
      </template>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
      >
        <el-button link type="primary" @click="emit('retry')">重试</el-button>
      </el-alert>
      <div class="desktop-columns">
        <DesktopTable :rows="topGainers" :loading="loading" title="领涨板块">
          <el-table-column
            prop="名称"
            label="板块"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {SectorRow} -->
          <el-table-column label="涨跌幅" min-width="150">
            <template #default="{ row }: { row: SectorRow }">
              <span :class="dirClass(row['涨跌幅'])">
                {{ pct(row['涨跌幅']) }}
              </span>
            </template>
          </el-table-column>
          <!-- @vue-generic {SectorRow} -->
          <el-table-column label="成交额" min-width="150">
            <template #default="{ row }: { row: SectorRow }">
              {{ amt(row['总成交额'], currency ?? '') }}
            </template>
          </el-table-column>
        </DesktopTable>
        <DesktopTable :rows="topLosers" :loading="loading" title="领跌板块">
          <el-table-column
            prop="名称"
            label="板块"
            min-width="140"
            sortable="custom"
            show-overflow-tooltip
          />
          <!-- @vue-generic {SectorRow} -->
          <el-table-column label="涨跌幅" min-width="150">
            <template #default="{ row }: { row: SectorRow }">
              <span :class="dirClass(row['涨跌幅'])">
                {{ pct(row['涨跌幅']) }}
              </span>
            </template>
          </el-table-column>
          <!-- @vue-generic {SectorRow} -->
          <el-table-column label="成交额" min-width="150">
            <template #default="{ row }: { row: SectorRow }">
              {{ amt(row['总成交额'], currency ?? '') }}
            </template>
          </el-table-column>
        </DesktopTable>
      </div>
    </PagePanel>
  </section>
</template>
