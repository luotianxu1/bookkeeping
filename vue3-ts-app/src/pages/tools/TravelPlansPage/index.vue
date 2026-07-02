<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import { deleteTravelPlan, getTravelPlans, type TravelPlan } from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

type FilterTab = 'traveling' | 'completed'

const router = useRouter()
const activeTab = ref<FilterTab>('traveling')
const plans = ref<TravelPlan[]>([])
const isLoading = ref(false)
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
    return
  }

  isLoading.value = true
  loadError.value = ''
  try {
    plans.value = await getTravelPlans({ userId: currentUser.id, status: 'all' })
  } catch (error) {
    plans.value = []
    loadError.value = error instanceof Error ? error.message : '旅行列表加载失败'
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
    const message = error instanceof Error ? error.message : '删除旅行失败'
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
</script>

<template>
  <section class="travel-plans-page">
    <PageHeader title="旅行管理" back-to="/tools" back-label="返回工具页">
      <button class="header-edit-button" type="button" @click="toggleEditing">
        {{ isEditing ? '完成' : '编辑' }}
      </button>
    </PageHeader>

    <div class="travel-filter-tabs" role="tablist" aria-label="旅行状态筛选">
      <button :class="['filter-tab', { active: activeTab === 'traveling' }]" type="button" @click="activeTab = 'traveling'">
        未出行
      </button>
      <button :class="['filter-tab', { active: activeTab === 'completed' }]" type="button" @click="activeTab = 'completed'">
        已出行
      </button>
    </div>

    <div class="trip-list">
      <article
        v-for="plan in visiblePlans"
        :key="plan.id"
        :class="['trip-card', { 'trip-card-clickable': !isEditing }]"
        :tabindex="isEditing ? -1 : 0"
        :role="isEditing ? undefined : 'button'"
        @click="handleCardClick(plan.id)"
        @keydown="handleCardKeydown($event, plan.id)"
      >
        <span class="trip-card-title">{{ plan.name }}</span>
        <span class="trip-card-date">{{ formatDateRange(plan) }}</span>

        <div v-if="isEditing" class="trip-card-actions">
          <button class="trip-action-button" type="button" @click.stop="openEditPage(plan.id)">修改</button>
          <button class="trip-action-button trip-action-danger" type="button" @click.stop="openDeleteModal(plan)">
            删除
          </button>
        </div>
      </article>
    </div>

    <div v-if="!isLoading && visiblePlans.length === 0" class="travel-empty">
      <strong>还没有旅行计划</strong>
      <span>{{ loadError || '先新增一个旅行，再继续规划路线和费用。' }}</span>
    </div>

    <div v-if="isLoading" class="travel-loading">正在同步旅行数据...</div>

    <FloatingAddButton aria-label="新增旅行" storage-key="travel-plans" @click="openCreatePage" />

    <CommonModal v-model="showDeleteModal" title="删除旅行" size="compact" :show-close="!isDeleting">
      <div class="travel-delete-content">
        <p>确定删除“{{ deletingPlan?.name ?? '' }}”吗？</p>
        <span>删除后会一并清空同行人、日期、路线和费用数据，且无法恢复。</span>
      </div>

      <template #footer>
        <div class="travel-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeleting" @click="closeDeleteModal()">取消</CommonButton>
          <CommonButton :disabled="isDeleting" @click="confirmDeletePlan">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonFeedback v-model="showFeedbackModal" :message="feedbackMessage" :type="feedbackType" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
