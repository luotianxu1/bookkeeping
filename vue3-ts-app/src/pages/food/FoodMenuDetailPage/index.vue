<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getFoodOrder, type FoodOrder, type FoodOrderDishItem } from '@/api/modules/food'
import { getFoodDishDetailPath, getFoodMenuPath } from '../shared'

const route = useRoute()
const router = useRouter()

const order = ref<FoodOrder | null>(null)
const isLoading = ref(false)
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
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    order.value = await getFoodOrder(orderId)
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '菜单详情加载失败'
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
  <section class="food-menu-detail-page" aria-label="菜单详情">
    <CommonLoading v-if="isLoading" text="菜单详情加载中..." />

    <template v-if="order && !pageError">
      <PageHeader title="菜单详情" class="page-head" :back-to="getFoodMenuPath()" />

      <section class="hero-card">
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
      </section>

      <section class="info-card">
        <strong>菜单菜品</strong>
        <div class="dish-groups">
          <section
            v-for="group in groupedDishes"
            :key="group.categoryName"
            class="dish-group"
          >
            <h3>{{ group.categoryName }}</h3>
            <div class="dish-list">
              <button
                v-for="dish in group.dishes"
                :key="dish.dishId"
                type="button"
                class="dish-row"
                @click="openDishDetail(dish.dishId)"
              >
                <span>{{ dish.dishName }}</span>
              </button>
            </div>
          </section>
        </div>
      </section>

      <section v-if="order.remark" class="info-card">
        <strong>备注说明</strong>
        <p>{{ order.remark }}</p>
      </section>
    </template>

    <p v-else class="page-error">{{ pageError }}</p>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
