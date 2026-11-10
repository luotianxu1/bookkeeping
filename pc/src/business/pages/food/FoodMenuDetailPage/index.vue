<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getFoodOrder,
  type FoodOrder,
  type FoodOrderDishItem,
} from '@/business/api/modules/food'
import { getFoodDishDetailPath, getFoodMenuPath } from '../shared'
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
</script>

<template>
  <section class="pc-page food-menu-detail-page" aria-label="菜单详情">
    <CommonLoading v-if="isLoading" text="菜单详情加载中..." />
    <template v-if="order && !pageError">
      <PageHeader
        title="菜单详情"
        class="page-head"
        :back-to="getFoodMenuPath()"
      />
      <el-card class="pc-record-card hero-card" shadow="never">
        <div class="hero-head">
          <div class="hero-copy">
            <h2>{{ order.title }}</h2>
            <p>{{ formatPlannedDate(order.plannedFor) }}</p>
          </div>
        </div>
        <div class="hero-metrics">
          <span>{{ order.dishCount }} 道菜</span>
          <span>预计 {{ order.totalCookMinutes }} 分钟</span>
        </div>
      </el-card>
      <el-card class="pc-record-card info-card" shadow="never">
        <strong>菜单菜品</strong>
        <div class="dish-groups">
          <section
            v-for="group in groupedDishes"
            :key="group.categoryName"
            class="dish-group"
          >
            <h3>{{ group.categoryName }}</h3>
            <div class="pc-grid dish-list">
              <el-button
                v-for="dish in group.dishes"
                :key="dish.dishId"
                native-type="button"
                class="pc-content-button dish-row"
                @click="openDishDetail(dish.dishId)"
              >
                <span>{{ dish.dishName }}</span>
              </el-button>
            </div>
          </section>
        </div>
      </el-card>
      <el-card
        v-if="order.remark"
        class="pc-record-card info-card"
        shadow="never"
      >
        <strong>备注说明</strong>
        <p>{{ order.remark }}</p>
      </el-card>
    </template>
    <p v-else class="page-error">{{ pageError }}</p>
  </section>
</template>
