<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  deleteFoodOrder,
  getFoodOrders,
  type FoodOrder,
} from '@/business/api/modules/food'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getFoodMenuDetailPath } from '../shared'
import { getErrorMessage } from '@/business/api/request'

const router = useRouter()

const orders = ref<FoodOrder[]>([])
const keyword = ref('')
const isLoading = ref(true)
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
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    orders.value = await getFoodOrders({
      userId: currentUser.id,
    })
  } catch (error) {
    pageError.value = getErrorMessage(error, '菜单列表加载失败')
  } finally {
    isLoading.value = false
  }
}

function formatPlannedDate(value: string) {
  return value.replace(/-/g, '.')
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
    const message = getErrorMessage(error, '菜单删除失败')
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

type DesktopRow = (typeof filteredOrders.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace">
    <PageHeader title="菜单订单">
      <template #right>
        <el-button
          type="primary"
          @click="router.push('/food/dishes?entry=menu')"
        >
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
    <DesktopTable title="菜单订单" :rows="filteredOrders" :loading="isLoading">
      <template #toolbar>
        <el-input
          v-model="keyword"
          placeholder="搜索"
          clearable
          style="width: 260px"
        />
      </template>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="名称"
        prop="title"
        sortable="custom"
        min-width="200"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="日期" min-width="140">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatPlannedDate(row.plannedFor) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="菜品" min-width="280">
        <template #default="{ row }: { row: DesktopRow }">
          {{ row.dishNames.join(' / ') }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="预计用时" min-width="120">
        <template #default="{ row }: { row: DesktopRow }">
          {{ row.totalCookMinutes }} 分钟
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="备注"
        prop="remark"
        sortable="custom"
        min-width="180"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button link type="primary" @click="openOrderDetail(row.id)">
              详情
            </el-button>
            <el-button link type="danger" @click="openDeleteConfirmModal(row)">
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :show-close="false"
    >
      <p class="menu-delete-message">
        确认删除“{{ deletingOrder?.title }}”吗？
      </p>
      <p v-if="deleteError" class="menu-form-error">{{ deleteError }}</p>
      <template #footer>
        <div class="pc-toolbar menu-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteConfirmModal"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="isDeleting" @click="confirmDeleteOrder">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
