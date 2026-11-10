<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'
import PagePanel from '@/business/components/PagePanel.vue'

import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  createFoodOrder,
  getFoodCategories,
  getFoodDishes,
  type FoodCategory,
  type FoodDish,
} from '@/business/api/modules/food'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import {
  getFoodDishDetailPath,
  getFoodMenuDetailPath,
  isDarkFoodPath,
  useFoodSelection,
} from '../shared'
import { getErrorMessage } from '@/business/api/request'

const route = useRoute()
const router = useRouter()

const isDark = computed(() => isDarkFoodPath(route.path))
const categories = ref<FoodCategory[]>([])
const dishes = ref<FoodDish[]>([])
const currentCategoryId = ref<number | null>(null)
const keyword = ref('')
const pageError = ref('')
const isLoading = ref(true)
const isSubmitting = ref(false)
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const selectedPanelOpen = ref(false)
const canCreateMenu = computed(() => route.query.entry === 'menu')

const {
  selectedCount,
  clearSelection,
  isSelected,
  resolveSelectedDishes,
  toggleDish,
} = useFoodSelection()

const filteredDishes = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return dishes.value.filter((dish) => {
    const inCategory =
      currentCategoryId.value == null ||
      dish.categoryId === currentCategoryId.value
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

const selectedDishes = computed(() => resolveSelectedDishes(dishes.value))
const selectedMinutes = computed(() =>
  selectedDishes.value.reduce(
    (sum, item) => sum + Number(item.cookMinutes ?? 0),
    0,
  ),
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
  clearSelection()
  selectedPanelOpen.value = false
  void loadPage()
})

watch(
  () => route.query.categoryId,
  (value) => {
    const next = Number(value)
    currentCategoryId.value =
      Number.isFinite(next) && next > 0 ? next : currentCategoryId.value
  },
)

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看菜品列表'
    isLoading.value = false
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
    } else {
      currentCategoryId.value = null
    }
  } catch (error) {
    pageError.value = getErrorMessage(error, '菜品列表加载失败')
  } finally {
    isLoading.value = false
  }
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
    const order = await createFoodOrder({
      userId: currentUser.id,
      dishIds: selectedDishes.value.map((item) => item.id),
    })
    clearSelection()
    selectedPanelOpen.value = false
    void router.push(getFoodMenuDetailPath(order.id))
  } catch (error) {
    showFeedback(getErrorMessage(error, '新增菜单失败'), 'error')
  } finally {
    isSubmitting.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}

type DesktopRow = (typeof filteredDishes.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace">
    <PageHeader title="菜品管理">
      <template #right>
        <el-button type="primary" @click="router.push('/food/dishes/new')">
          新增菜品
        </el-button>
        <el-button @click="router.push('/food/dishes?entry=menu')">
          新建菜单
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    />
    <div :class="canCreateMenu ? 'desktop-main-aside' : 'desktop-stack'">
      <DesktopTable title="菜品库" :rows="filteredDishes" :loading="isLoading">
        <template #toolbar>
          <el-input
            v-model="keyword"
            placeholder="搜索"
            clearable
            style="width: 260px"
          />
          <el-select
            v-model="currentCategoryId"
            placeholder="全部分类"
            clearable
            @clear="currentCategoryId = null"
          >
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </template>
        <!-- @vue-generic {DesktopRow} -->
        <el-table-column
          label="菜名"
          prop="name"
          sortable="custom"
          min-width="180"
          show-overflow-tooltip
        />
        <!-- @vue-generic {DesktopRow} -->
        <el-table-column
          label="分类"
          prop="categoryName"
          sortable="custom"
          min-width="140"
          show-overflow-tooltip
        />
        <!-- @vue-generic {DesktopRow} -->
        <el-table-column label="食材" min-width="240">
          <template #default="{ row }: { row: DesktopRow }">
            {{ row.ingredientPreview.join(' / ') }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DesktopRow} -->
        <el-table-column label="口味" min-width="160">
          <template #default="{ row }: { row: DesktopRow }">
            {{ row.tasteTags.join(' / ') }}
          </template>
        </el-table-column>
        <!-- @vue-generic {DesktopRow} -->
        <el-table-column label="烹饪时间" min-width="120">
          <template #default="{ row }: { row: DesktopRow }">
            {{ row.cookMinutes }} 分钟
          </template>
        </el-table-column>
        <!-- @vue-generic {DesktopRow} -->
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }: { row: DesktopRow }">
            <div class="pc-row-actions">
              <el-button link type="primary" @click="openDetail(row.id)">
                详情
              </el-button>
              <el-button
                v-if="canCreateMenu"
                link
                type="primary"
                @click="toggleDish(row.id)"
              >
                {{ isSelected(row.id) ? '取消选择' : '选择' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </DesktopTable>
      <PagePanel
        v-if="canCreateMenu"
        title="当前菜单"
        class="desktop-menu-selection"
      >
        <template #actions>
          <el-button text :disabled="!selectedCount" @click="clearSelection">
            清空
          </el-button>
        </template>
        <div class="desktop-selection-summary">
          <strong>{{ selectedCount }} 道菜</strong>
          <span>预计 {{ selectedMinutes }} 分钟</span>
        </div>
        <p v-if="selectedSummary" class="desktop-note">{{ selectedSummary }}</p>
        <el-empty
          v-if="!selectedCount"
          description="从菜品库选择菜品"
          :image-size="64"
        />
        <ul v-else class="desktop-selection-list">
          <li v-for="dish in selectedDishes" :key="dish.id">
            <div>
              <strong>{{ dish.name }}</strong>
              <small>
                {{ dish.categoryName }} · {{ dish.cookMinutes }} 分钟
              </small>
            </div>
            <el-button
              text
              type="danger"
              :aria-label="'移除' + dish.name"
              @click="toggleDish(dish.id)"
            >
              移除
            </el-button>
          </li>
        </ul>
        <el-button
          type="primary"
          class="desktop-selection-submit"
          :loading="isSubmitting"
          :disabled="!selectedCount"
          @click="confirmOrder"
        >
          生成菜单
        </el-button>
      </PagePanel>
    </div>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
