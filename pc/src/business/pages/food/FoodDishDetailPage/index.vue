<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import { getFoodDish, type FoodDish } from '@/business/api/modules/food'
import {
  getFoodDishListPath,
  isDarkFoodPath,
  mapCoverToneToClass,
} from '../shared'
import { getErrorMessage } from '@/business/api/request'

const route = useRoute()

const isDark = computed(() => isDarkFoodPath(route.path))
const dish = ref<FoodDish | null>(null)
const isLoading = ref(true)
const pageError = ref('')

onMounted(() => {
  void loadDetail()
})

async function loadDetail() {
  const dishId = Number(route.params.dishId)
  if (!Number.isFinite(dishId)) {
    pageError.value = '菜品参数无效'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    dish.value = await getFoodDish(dishId)
  } catch (error) {
    pageError.value = getErrorMessage(error, '菜品详情加载失败')
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <section
    :class="['food-detail-page', { dark: isDark }]"
    aria-label="菜品详情"
    class="pc-page"
  >
    <CommonLoading v-if="isLoading" text="详情加载中..." />
    <template v-if="dish && !pageError">
      <PageHeader
        title="菜品详情"
        class="page-head"
        :back-to="getFoodDishListPath(isDark)"
      />
      <el-card
        :class="['hero-card', mapCoverToneToClass(dish.coverTone)]"
        shadow="never"
        class="pc-record-card"
      >
        <div>
          <h2>{{ dish.name }}</h2>
          <p>{{ dish.subtitle }}</p>
        </div>
        <div class="hero-pills">
          <span>{{ dish.cookMinutes }}分钟</span>
        </div>
      </el-card>
      <el-card class="pc-record-card info-card" shadow="never">
        <strong>菜品介绍</strong>
        <p>{{ dish.description }}</p>
        <div class="tag-row">
          <span v-for="tag in dish.highlightTags" :key="tag">{{ tag }}</span>
        </div>
      </el-card>
      <el-card class="pc-record-card info-card" shadow="never">
        <strong>所需食材</strong>
        <div class="pc-grid ingredient-list">
          <div
            v-for="item in dish.ingredients"
            :key="`${item.ingredientName}-${item.amount}`"
            class="ingredient-row"
          >
            <span>{{ item.ingredientName }}</span>
            <strong>{{ item.amount }}</strong>
          </div>
        </div>
      </el-card>
      <el-card class="pc-record-card info-card" shadow="never">
        <strong>做法步骤</strong>
        <div class="pc-grid step-list">
          <p v-for="step in dish.steps" :key="step.stepNo">
            {{ step.stepNo }}. {{ step.content }}
          </p>
        </div>
      </el-card>
    </template>
    <p v-else class="page-error">{{ pageError }}</p>
  </section>
</template>
