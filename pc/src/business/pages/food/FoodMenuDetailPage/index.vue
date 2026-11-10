<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getFoodOrder,
  type FoodOrder,
  type FoodOrderDishItem,
} from '@/business/api/modules/food'
import { getFoodDishDetailPath } from '../shared'
import { getErrorMessage } from '@/business/api/request'

const route = useRoute()
const router = useRouter()

const order = ref<FoodOrder | null>(null)
const isLoading = ref(true)
const pageError = ref('')
const groupedDishes = computed(() => {
  if (!order.value) {
    return []
  }

  const groups = new Map<string, FoodOrderDishItem[]>()
  order.value.dishes.forEach((dish) => {
    const categoryName = dish.categoryName?.trim() || '未分类'
    const items = groups.get(categoryName)
    if (items) {
      items.push(dish)
      return
    }
    groups.set(categoryName, [dish])
  })

  return Array.from(groups.entries()).map(([categoryName, dishes]) => ({
    categoryName,
    dishes,
  }))
})

onMounted(() => {
  void loadDetail()
})

async function loadDetail() {
  const orderId = Number(route.params.orderId)
  if (!Number.isFinite(orderId)) {
    pageError.value = '菜单参数无效'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    order.value = await getFoodOrder(orderId)
  } catch (error) {
    pageError.value = getErrorMessage(error, '菜单详情加载失败')
  } finally {
    isLoading.value = false
  }
}

function formatPlannedDate(value: string) {
  return value.replace(/-/g, '.')
}

function openDishDetail(dishId: number) {
  void router.push(getFoodDishDetailPath(false, dishId))
}

const desktopDishes = computed(() =>
  groupedDishes.value.flatMap((group) =>
    group.dishes.map((dish) => ({ ...dish, categoryName: group.categoryName })),
  ),
)
type DishRow = (typeof desktopDishes.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader :title="order?.title || '菜单详情'">
      <template #right></template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <CommonLoading v-if="isLoading" />
    <template v-if="order">
      <MetricStrip
        :items="[
          { label: '用餐日期', value: formatPlannedDate(order.plannedFor) },
          { label: '菜品数量', value: order.dishCount },
          { label: '预计用时（分钟）', value: order.totalCookMinutes },
        ]"
      />
      <DesktopTable
        :rows="desktopDishes"
        :loading="false"
        title="菜单菜品"
        search-placeholder="搜索菜名"
        searchable
      >
        <el-table-column
          prop="dishName"
          label="菜品"
          min-width="260"
          sortable="custom"
          show-overflow-tooltip
        />
        <el-table-column
          prop="categoryName"
          label="分类"
          min-width="200"
          sortable="custom"
          show-overflow-tooltip
        />
        <!-- @vue-generic {DishRow} -->
        <el-table-column fixed="right" label="操作" width="150">
          <template #default="{ row }: { row: DishRow }">
            <el-button link type="primary" @click="openDishDetail(row.dishId)">
              查看做法
            </el-button>
          </template>
        </el-table-column>
      </DesktopTable>
      <PagePanel title="备注">
        <p>{{ order.remark || '—' }}</p>
      </PagePanel>
    </template>
  </section>
</template>
