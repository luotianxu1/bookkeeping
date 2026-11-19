<script setup lang="ts">
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'
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
      Number.isFinite(next) && next > 0 ? next : null
  },
  { immediate: true },
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
  <section class="desktop-workspace desktop-list-workspace stagger-in">
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
      <DesktopRecordList
        title="菜品库"
        :rows="filteredDishes"
        :loading="isLoading"
        empty-title="没有菜品"
        empty-hint="换个分类或关键词试试，或新增一道菜"
      >
        <template #toolbar>
          <el-input
            v-model="keyword"
            placeholder="搜索"
            clearable
            style="width: 260px"
          />
          <el-select
            v-model="currentCategoryId"
            aria-label="菜品分类"
            placeholder="全部分类"
            clearable
            :value-on-clear="null"
          >
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </template>
        <template #default="{ row }: { row: DesktopRow }">
          <article
            class="record-card dish-record-card"
            :class="{ 'is-selected': isSelected(row.id) }"
          >
            <div class="record-card-head">
              <div class="record-card-title">
                <span>{{ row.name }}</span>
              </div>
              <div class="record-card-aside">
                <el-tag size="small" effect="plain">
                  {{ row.categoryName }}
                </el-tag>
              </div>
            </div>
            <dl class="record-card-body">
              <div>
                <dt>食材</dt>
                <dd>{{ row.ingredientPreview.join(' / ') || '未填写' }}</dd>
              </div>
              <div>
                <dt>口味</dt>
                <dd>{{ row.tasteTags.join(' / ') || '未填写' }}</dd>
              </div>
              <div>
                <dt>烹饪时间</dt>
                <dd>{{ row.cookMinutes }} 分钟</dd>
              </div>
            </dl>
            <div class="record-card-foot">
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
            </div>
          </article>
        </template>
      </DesktopRecordList>
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

<style scoped>
.dish-record-card.is-selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary);
}
</style>
