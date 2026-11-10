<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import {
  createFoodOrder,
  getFoodDishes,
  getFoodHome,
  type FoodDish,
  type FoodHomeData,
  type FoodManagementCard,
} from '@/business/api/modules/food'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import {
  getFoodDishListPath,
  getFoodDishMenuPath,
  getFoodMenuDetailPath,
  getFoodMenuPath,
} from '../shared'
import { getErrorMessage } from '@/business/api/request'

const router = useRouter()

const homeData = ref<FoodHomeData | null>(null)
const isLoading = ref(true)
const pageError = ref('')
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const publishedDishes = ref<FoodDish[]>([])

const managementCards = computed<FoodManagementCard[]>(() => {
  const cards = homeData.value?.managementCards ?? []
  return [
    {
      key: 'menus',
      title: '菜单列表',
      description: '查看已生成菜单与安排',
      count: 0,
      path: getFoodMenuPath(),
    },
    ...cards,
  ]
})

onMounted(() => {
  void loadPage()
})

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看餐饮页面'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    homeData.value = await getFoodHome(currentUser.id)
  } catch (error) {
    pageError.value = getErrorMessage(error, '餐饮首页加载失败')
  } finally {
    isLoading.value = false
  }
}

async function createTodayMenu() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    showFeedback('请先登录后再生成菜单', 'error')
    return
  }

  try {
    if (publishedDishes.value.length === 0) {
      publishedDishes.value = await getFoodDishes({
        userId: currentUser.id,
        status: 'published',
      })
    }

    const picks = [...publishedDishes.value]
      .sort(() => Math.random() - 0.5)
      .slice(0, 3)
    if (picks.length === 0) {
      showFeedback('暂无可用菜品', 'error')
      return
    }

    await createFoodOrder({
      userId: currentUser.id,
      dishIds: picks.map((item) => item.id),
    })
    showFeedback('今日菜单已生成', 'success')
    await loadPage()
  } catch (error) {
    showFeedback(getErrorMessage(error, '生成菜单失败'), 'error')
  }
}

function navigateByCard(card: FoodManagementCard) {
  void router.push(resolveManagementPath(card))
}

function goRecentMenu(orderId: number) {
  void router.push(getFoodMenuDetailPath(orderId))
}

function goMenu() {
  void router.push(getFoodDishMenuPath())
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}

function resolveManagementPath(card: Pick<FoodManagementCard, 'key' | 'path'>) {
  if (card.key === 'dishes') {
    return getFoodDishListPath()
  }

  if (card.key === 'menus') {
    return getFoodMenuPath()
  }

  if (card.key === 'dish-categories') {
    return '/food/categories'
  }

  if (card.key === 'ingredients') {
    return '/food/ingredients'
  }

  if (card.key === 'ingredient-categories') {
    return '/food/ingredient-categories'
  }

  if (card.path === '/food/admin/ingredients') {
    return '/food/ingredients'
  }

  if (card.path === '/food/admin/ingredient-categories') {
    return '/food/ingredient-categories'
  }

  return card.path
}

type MenuRow = NonNullable<typeof homeData.value>['recentMenus'][number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="家庭餐饮">
      <template #right>
        <el-button @click="createTodayMenu">今日菜单</el-button>
        <el-button type="primary" @click="goMenu">挑选菜品</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <template v-if="homeData">
      <div class="desktop-shortcuts">
        <el-button
          v-for="card in managementCards"
          :key="card.key"
          @click="navigateByCard(card)"
        >
          {{ card.title }}
        </el-button>
      </div>
      <DesktopTable
        :rows="homeData.recentMenus"
        :loading="isLoading"
        title="最近菜单"
      >
        <el-table-column
          prop="title"
          label="菜单"
          min-width="260"
          sortable="custom"
          show-overflow-tooltip
        />
        <el-table-column
          prop="summary"
          label="摘要"
          min-width="400"
          sortable="custom"
          show-overflow-tooltip
        />
        <!-- @vue-generic {MenuRow} -->
        <el-table-column fixed="right" label="操作" width="150">
          <template #default="{ row }: { row: MenuRow }">
            <el-button link type="primary" @click="goRecentMenu(row.orderId)">
              查看菜单
            </el-button>
          </template>
        </el-table-column>
      </DesktopTable>
    </template>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
