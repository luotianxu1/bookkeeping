<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonHeaderActionButton from '@/business/components/common/CommonHeaderActionButton/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import FloatingAddButton from '@/business/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  deleteTravelPlan,
  getTravelPlans,
  type TravelPlan,
} from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'

type FilterTab = 'traveling' | 'completed'

const router = useRouter()
const activeTab = ref<FilterTab>('traveling')
const plans = ref<TravelPlan[]>([])
const isLoading = ref(true)
const isEditing = ref(false)
const isDeleting = ref(false)
const loadError = ref('')
const showDeleteModal = ref(false)
const deletingPlan = ref<TravelPlan | null>(null)
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

function hasDeparted(plan: TravelPlan) {
  const today = getTodayStart()
  const startDate = parseDateValue(plan.startDate)
  if (startDate) {
    return startDate.getTime() <= today.getTime()
  }

  const endDate = parseDateValue(plan.endDate)
  if (endDate) {
    return endDate.getTime() < today.getTime()
  }

  return plan.status === 'completed'
}

function parseDateValue(value?: string | null) {
  if (!value) {
    return null
  }

  const parsed = new Date(`${value}T00:00:00`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

function getTodayStart() {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

const visiblePlans = computed(() => {
  if (activeTab.value === 'completed') {
    return plans.value.filter((item) => hasDeparted(item))
  }

  return plans.value.filter((item) => !hasDeparted(item))
})

onMounted(() => {
  void loadPlans()
})

async function loadPlans() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    loadError.value = '请先登录后查看旅行管理'
    isLoading.value = false
    return
  }

  isLoading.value = true
  loadError.value = ''
  try {
    plans.value = await getTravelPlans({
      userId: currentUser.id,
      status: 'all',
    })
  } catch (error) {
    plans.value = []
    loadError.value = getErrorMessage(error, '旅行列表加载失败')
  } finally {
    isLoading.value = false
  }
}

function openCreatePage() {
  void router.push('/tools/travel-plans/new')
}

function toggleEditing() {
  isEditing.value = !isEditing.value
}

function openDetail(planId: number) {
  void router.push(`/tools/travel-plans/${planId}`)
}

function openEditPage(planId: number) {
  void router.push(`/tools/travel-plans/${planId}/edit`)
}

function handleCardClick(planId: number) {
  if (isEditing.value) {
    return
  }
  openDetail(planId)
}

function handleCardKeydown(event: KeyboardEvent, planId: number) {
  if (isEditing.value) {
    return
  }

  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    openDetail(planId)
  }
}

function openDeleteModal(plan: TravelPlan) {
  deletingPlan.value = plan
  showDeleteModal.value = true
}

function closeDeleteModal(force = false) {
  if (isDeleting.value && !force) {
    return
  }

  showDeleteModal.value = false
  deletingPlan.value = null
}

async function confirmDeletePlan() {
  const currentUser = getStoredCurrentUser()
  const target = deletingPlan.value
  if (!currentUser || !target) {
    return
  }

  isDeleting.value = true
  try {
    await deleteTravelPlan(target.id, currentUser.id)
    closeDeleteModal(true)
    showFeedback(`已删除旅行“${target.name}”`, 'success')
    await loadPlans()
    if (plans.value.length === 0) {
      isEditing.value = false
    }
  } catch (error) {
    const message = getErrorMessage(error, '删除旅行失败')
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function formatDateRange(plan: TravelPlan) {
  const start = plan.startDate ? plan.startDate.split('-').join('.') : ''
  const end = plan.endDate ? plan.endDate.split('-').join('.') : ''
  if (!start || !end) {
    return plan.destination ?? ''
  }
  return `${start} - ${end}`
}

type DesktopRow = (typeof visiblePlans.value)[number]
</script>
<template>
  <section class="pc-page">
    <PageHeader title="旅行计划">
      <template #right>
        <el-button type="primary" @click="openCreatePage">新增旅行</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
    />
    <DesktopTable :rows="visiblePlans" :loading="isLoading">
      <template #toolbar>
        <SegmentedControl
          v-model="activeTab"
          :options="[
            { value: 'traveling', label: '进行中' },
            { value: 'completed', label: '已完成' },
          ]"
          label="旅行状态"
        />
      </template>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="名称"
        prop="name"
        min-width="200"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="目的地"
        prop="destination"
        min-width="200"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="开始日期"
        prop="startDate"
        min-width="140"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="结束日期"
        prop="endDate"
        min-width="140"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button link type="primary" @click="openDetail(row.id)">
              详情
            </el-button>
            <el-button link type="primary" @click="openEditPage(row.id)">
              编辑
            </el-button>
            <el-button link type="danger" @click="openDeleteModal(row)">
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonModal
      v-model="showDeleteModal"
      title="删除旅行"
      size="compact"
      :show-close="!isDeleting"
    >
      <div class="travel-delete-content">
        <p>确定删除“{{ deletingPlan?.name ?? '' }}”吗？</p>
        <span>删除后会一并清空同行人、日期、路线和费用数据，且无法恢复。</span>
      </div>
      <template #footer>
        <div class="pc-toolbar travel-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteModal()"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="isDeleting" @click="confirmDeletePlan">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
