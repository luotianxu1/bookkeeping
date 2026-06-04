<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { deleteFoodOrder, getFoodOrders, type FoodOrder } from '@/api/modules/food'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getFoodHomePath, getFoodMenuDetailPath } from '../shared'

const router = useRouter()

const orders = ref<FoodOrder[]>([])
const keyword = ref('')
const isLoading = ref(false)
const isDeleting = ref(false)
const isManageMode = ref(false)
const pageError = ref('')
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const showDeleteConfirmModal = ref(false)
const deleteError = ref('')
const deletingOrder = ref<FoodOrder | null>(null)

const filteredOrders = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return orders.value.filter((order) => {
    if (!search) {
      return true
    }

    return [order.title, order.remark ?? '', order.dishNames.join(' ')]
      .join(' ')
      .toLowerCase()
      .includes(search)
  })
})

onMounted(() => {
  void loadOrders()
})

async function loadOrders() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看菜单列表'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    orders.value = await getFoodOrders({
      userId: currentUser.id,
    })
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '菜单列表加载失败'
  } finally {
    isLoading.value = false
  }
}

function formatPlannedDate(value: string) {
  return value.replace(/-/g, '.')
}

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function openOrderDetail(orderId: number) {
  if (isManageMode.value) {
    return
  }
  void router.push(getFoodMenuDetailPath(orderId))
}

function openDeleteConfirmModal(order: FoodOrder) {
  deletingOrder.value = order
  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function closeDeleteConfirmModal() {
  deletingOrder.value = null
  deleteError.value = ''
  showDeleteConfirmModal.value = false
}

async function confirmDeleteOrder() {
  if (!deletingOrder.value || isDeleting.value) {
    return
  }

  isDeleting.value = true
  deleteError.value = ''

  try {
    await deleteFoodOrder(deletingOrder.value.id)
    closeDeleteConfirmModal()
    showFeedback('菜单已删除', 'success')
    await loadOrders()
  } catch (error) {
    const message = error instanceof Error ? error.message : '菜单删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}
</script>

<template>
  <section class="food-menu-page" aria-label="菜单列表">
    <CommonLoading v-if="isLoading" text="菜单加载中..." />

    <template v-if="!pageError">
      <header class="page-head">
        <PageHeader title="菜单列表" :back-to="getFoodHomePath()" />
        <CommonButton
          class="manage-link"
          variant="secondary"
          size="sm"
          :class="{ active: isManageMode }"
          @click="toggleManageMode"
        >
          {{ isManageMode ? '完成' : '管理' }}
        </CommonButton>
      </header>

      <div class="search-bar">
        <input v-model.trim="keyword" type="search" placeholder="搜索菜单名称、备注、菜品" />
        <span>筛选</span>
      </div>

      <section class="menu-list">
        <button
          v-for="order in filteredOrders"
          :key="order.id"
          type="button"
          :class="['menu-card', { editable: isManageMode }]"
          @click="openOrderDetail(order.id)"
        >
          <div class="menu-card-head">
            <div class="menu-copy">
              <strong>{{ order.title }}</strong>
              <span>{{ formatPlannedDate(order.plannedFor) }} · {{ order.dishCount }} 道菜</span>
            </div>
          </div>

          <p class="menu-dishes">{{ order.dishNames.join(' / ') }}</p>

          <div class="menu-meta">
            <span>预计 {{ order.totalCookMinutes }} 分钟</span>
          </div>

          <p v-if="order.remark" class="menu-remark">{{ order.remark }}</p>

          <button
            v-if="isManageMode"
            class="menu-delete-trigger"
            type="button"
            @click.stop="openDeleteConfirmModal(order)"
          >
            ×
          </button>
        </button>

        <article v-if="filteredOrders.length === 0" class="empty-state-card">
          <strong>还没有菜单记录</strong>
          <span>可以先去点菜，选好菜品后生成一份新的菜单。</span>
        </article>
      </section>
    </template>

    <p v-else class="page-error">{{ pageError }}</p>

    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :show-close="false"
    >
      <p class="menu-delete-message">确认删除“{{ deletingOrder?.title }}”吗？</p>
      <p v-if="deleteError" class="menu-form-error">{{ deleteError }}</p>

      <template #footer>
        <div class="menu-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeleting" @click="closeDeleteConfirmModal">
            取消
          </CommonButton>
          <CommonButton :disabled="isDeleting" @click="confirmDeleteOrder">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonFeedback v-model="feedbackVisible" :message="feedbackMessage" :type="feedbackType" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
