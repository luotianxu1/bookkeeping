<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import {
  createFoodOrder,
  getFoodDishes,
  getFoodHome,
  type FoodDish,
  type FoodHomeData,
  type FoodManagementCard,
} from '@/api/modules/food'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getFoodDishListPath, getFoodDishMenuPath, getFoodMenuDetailPath, getFoodMenuPath } from '../shared'

const router = useRouter()

const homeData = ref<FoodHomeData | null>(null)
const isLoading = ref(false)
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
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    homeData.value = await getFoodHome(currentUser.id)
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '餐饮首页加载失败'
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

    const picks = [...publishedDishes.value].sort(() => Math.random() - 0.5).slice(0, 3)
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
    showFeedback(error instanceof Error ? error.message : '生成菜单失败', 'error')
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
</script>

<template>
  <section class="food-home-page" aria-label="餐饮首页">
    <CommonLoading v-if="isLoading" text="餐饮页面加载中..." />

    <template v-if="!pageError && homeData">
      <section class="hero-card">
        <div class="hero-title-wrap">
          <h2>{{ homeData.heroTitle }}</h2>
        </div>

        <div class="hero-actions">
          <CommonButton class="hero-action hero-action-primary" @click="goMenu">
            去点菜
          </CommonButton>
          <CommonButton class="hero-action hero-action-secondary" variant="secondary" @click="createTodayMenu">
            今日菜单
          </CommonButton>
        </div>
      </section>

      <section class="section-block">
        <h2>管理中心</h2>
        <div class="card-grid">
          <button v-for="card in managementCards" :key="card.key" type="button" class="manager-card"
            @click="navigateByCard(card)">
            <strong>{{ card.title }}</strong>
            <span>{{ card.description }}</span>
          </button>
        </div>
      </section>

      <section class="section-block">
        <div class="section-head">
          <h2>最近菜单</h2>
          <RouterLink class="section-link" :to="getFoodMenuPath()">查看全部</RouterLink>
        </div>

        <button v-for="menu in homeData.recentMenus" :key="menu.orderId" type="button" class="recent-row"
          @click="goRecentMenu(menu.orderId)">
          <div>
            <strong>{{ menu.title }}</strong>
            <span>{{ menu.summary }}</span>
          </div>
        </button>
      </section>
    </template>

    <p v-else class="page-error">{{ pageError }}</p>

    <CommonFeedback v-model="feedbackVisible" :message="feedbackMessage" :type="feedbackType" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
