<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { getFoodDish, type FoodDish } from '@/api/modules/food'
import { getFoodDishListPath, isDarkFoodPath, mapCoverToneToClass } from '../shared'

const route = useRoute()

const isDark = computed(() => isDarkFoodPath(route.path))
const dish = ref<FoodDish | null>(null)
const isLoading = ref(false)
const pageError = ref('')

onMounted(() => {
  void loadDetail()
})

async function loadDetail() {
  const dishId = Number(route.params.dishId)
  if (!Number.isFinite(dishId)) {
    pageError.value = '菜品参数无效'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    dish.value = await getFoodDish(dishId)
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '菜品详情加载失败'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <section :class="['food-detail-page', { dark: isDark }]" aria-label="菜品详情">
    <CommonLoading v-if="isLoading" text="详情加载中..." />

    <template v-if="dish && !pageError">
      <PageHeader title="菜品详情" class="page-head" :back-to="getFoodDishListPath(isDark)" />

      <section :class="['hero-card', mapCoverToneToClass(dish.coverTone)]">
        <div>
          <h2>{{ dish.name }}</h2>
          <p>{{ dish.subtitle }}</p>
        </div>

        <div class="hero-pills">
          <span>{{ dish.cookMinutes }}分钟</span>
        </div>
      </section>

      <section class="info-card">
        <strong>菜品介绍</strong>
        <p>{{ dish.description }}</p>
        <div class="tag-row">
          <span v-for="tag in dish.highlightTags" :key="tag">{{ tag }}</span>
        </div>
      </section>

      <section class="info-card">
        <strong>所需食材</strong>
        <div class="ingredient-list">
          <div v-for="item in dish.ingredients" :key="`${item.ingredientName}-${item.amount}`" class="ingredient-row">
            <span>{{ item.ingredientName }}</span>
            <strong>{{ item.amount }}</strong>
          </div>
        </div>
      </section>

      <section class="info-card">
        <strong>做法步骤</strong>
        <div class="step-list">
          <p v-for="step in dish.steps" :key="step.stepNo">{{ step.stepNo }}. {{ step.content }}</p>
        </div>
      </section>
    </template>

    <p v-else class="page-error">{{ pageError }}</p>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
