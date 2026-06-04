<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  createFoodOrder,
  getFoodCategories,
  getFoodDishes,
  type FoodCategory,
  type FoodDish,
} from '@/api/modules/food'
import { getStoredCurrentUser } from '@/utils/current-user'
import {
  getFoodCreatePath,
  getFoodDishDetailPath,
  getFoodHomePath,
  isDarkFoodPath,
  mapCoverToneToClass,
  useFoodSelection,
} from '../shared'

const route = useRoute()
const router = useRouter()

const isDark = computed(() => isDarkFoodPath(route.path))
const categories = ref<FoodCategory[]>([])
const dishes = ref<FoodDish[]>([])
const currentCategoryId = ref<number | null>(null)
const keyword = ref('')
const pageError = ref('')
const isLoading = ref(false)
const isSubmitting = ref(false)
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const {
  selectedCount,
  addDish,
  clearSelection,
  isSelected,
  resolveSelectedDishes,
} = useFoodSelection()

const visibleCategories = computed(() => {
  if (currentCategoryId.value == null) {
    return categories.value
  }
  return categories.value.filter((item) => item.id === currentCategoryId.value)
})

const filteredDishes = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return dishes.value.filter((dish) => {
    const inCategory = currentCategoryId.value == null || dish.categoryId === currentCategoryId.value
    if (!inCategory) {
      return false
    }

    if (!search) {
      return true
    }

    return [dish.name, dish.subtitle ?? '', dish.ingredientPreview.join(' ')]
      .join(' ')
      .toLowerCase()
      .includes(search)
  })
})

const groupedDishes = computed(() =>
  visibleCategories.value
    .map((category) => ({
      category,
      dishes: filteredDishes.value.filter((dish) => dish.categoryId === category.id),
    }))
    .filter((section) => section.dishes.length > 0),
)

const selectedDishes = computed(() => resolveSelectedDishes(dishes.value))
const selectedMinutes = computed(() =>
  selectedDishes.value.reduce((sum, item) => sum + Number(item.cookMinutes ?? 0), 0),
)
const selectedSummary = computed(() => {
  const counts = new Map<string, number>()
  selectedDishes.value.forEach((dish) => {
    counts.set(dish.categoryName, (counts.get(dish.categoryName) ?? 0) + 1)
  })
  return Array.from(counts.entries())
    .map(([label, count]) => `${label}${count}`)
    .join(' · ')
})

onMounted(() => {
  void loadPage()
})

watch(
  () => route.query.categoryId,
  (value) => {
    const next = Number(value)
    currentCategoryId.value = Number.isFinite(next) && next > 0 ? next : currentCategoryId.value
  },
)

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看菜品列表'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [categoryList, dishList] = await Promise.all([
      getFoodCategories({
        userId: currentUser.id,
        categoryType: 'dish',
      }),
      getFoodDishes({
        userId: currentUser.id,
        status: 'published',
      }),
    ])

    categories.value = categoryList
    dishes.value = dishList

    const routeCategory = Number(route.query.categoryId)
    if (Number.isFinite(routeCategory) && routeCategory > 0) {
      currentCategoryId.value = routeCategory
    } else if (categoryList[0]) {
      currentCategoryId.value = categoryList[0].id
    }
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '菜品列表加载失败'
  } finally {
    isLoading.value = false
  }
}

function selectCategory(categoryId: number) {
  currentCategoryId.value = categoryId
}

function openDetail(dishId: number) {
  void router.push(getFoodDishDetailPath(isDark.value, dishId))
}

async function confirmOrder() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || selectedDishes.value.length === 0 || isSubmitting.value) {
    return
  }

  isSubmitting.value = true
  try {
    await createFoodOrder({
      userId: currentUser.id,
      dishIds: selectedDishes.value.map((item) => item.id),
    })
    clearSelection()
    showFeedback('菜单已创建', 'success')
  } catch (error) {
    showFeedback(error instanceof Error ? error.message : '下单失败', 'error')
  } finally {
    isSubmitting.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}
</script>

<template>
  <section :class="['food-list-page', { dark: isDark }]" aria-label="菜品列表">
    <CommonLoading v-if="isLoading" text="菜品加载中..." />

    <template v-if="!pageError">
      <header class="page-head">
        <PageHeader title="菜品列表" :back-to="getFoodHomePath(isDark)" />
      </header>

      <div class="search-bar">
        <input v-model.trim="keyword" type="search" placeholder="搜索菜名、口味、食材" />
        <span>筛选</span>
      </div>

      <section class="menu-layout">
        <aside class="category-rail">
          <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            :class="['category-tab', { active: category.id === currentCategoryId }]"
            @click="selectCategory(category.id)"
          >
            {{ category.name }}
          </button>
        </aside>

        <div class="dish-sections">
          <article v-for="section in groupedDishes" :key="section.category.id" class="dish-card">
            <strong class="section-title">{{ section.category.name }}</strong>

            <article
              v-for="dish in section.dishes"
              :key="dish.id"
              class="dish-row"
              @click="openDetail(dish.id)"
            >
              <div :class="['dish-cover', mapCoverToneToClass(dish.coverTone)]">
                <span>{{ dish.coverText }}</span>
              </div>

              <div class="dish-copy">
                <strong>{{ dish.name }}</strong>
                <span>{{ dish.subtitle }}</span>
                <em>{{ dish.ingredientPreview.join(' / ') }}</em>
              </div>

              <CommonButton
                class="add-button"
                variant="secondary"
                size="sm"
                @click.stop="addDish(dish.id)"
              >
                {{ isSelected(dish.id) ? '已选' : '+' }}
              </CommonButton>
            </article>
          </article>
        </div>
      </section>

      <section v-if="selectedCount > 0" class="selected-bar">
        <div>
          <strong>已选 {{ selectedCount }} 道菜</strong>
          <span>{{ selectedSummary }} · 预计{{ selectedMinutes }}分钟</span>
        </div>
        <CommonButton :disabled="isSubmitting" @click="confirmOrder">确认下单</CommonButton>
      </section>

      <FloatingAddButton
        aria-label="新增菜品"
        storage-key="food-dish-add"
        @click="router.push(getFoodCreatePath(isDark))"
      />
    </template>

    <p v-else class="page-error">{{ pageError }}</p>

    <CommonFeedback v-model="feedbackVisible" :message="feedbackMessage" :type="feedbackType" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
