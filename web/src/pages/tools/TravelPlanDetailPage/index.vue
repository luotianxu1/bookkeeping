<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  getTravelPlan,
  type TravelPlanDetail,
} from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getErrorMessage } from '@/api/request'
import { useTravelMaps, type TravelMapPoint } from './useTravelMaps'
import {
  addDays,
  calculateDayCount,
  expenseAmountText,
  expenseNote,
  formatDayTabLabel,
  formatDate,
  formatMetric,
  formatStartTime,
  itineraryDescription,
  overviewExpenseRowAmount,
  routeCountText,
  routeTransferSummary,
  typeTone,
} from './travelFormat'
import { openOverviewNavigation } from './travelNavigation'
import { buildOverviewEntries, prependPreviousAccommodation } from './travelPoints'
import { useTravelPlanEditor, type ModalType } from './useTravelPlanEditor'

type DetailTab = 'overview' | 'route' | 'expense'
type FocusMapTarget = TravelMapPoint & {
  key: string
}

type RouteDisplayDay = {
  dayIndex: number
  itineraries: TravelPlanDetail['days'][number]['itineraries']
  expenses: TravelPlanDetail['days'][number]['expenses']
}

const route = useRoute()
const router = useRouter()
const currentPlanId = computed(() => {
  const raw = route.params.planId
  return typeof raw === 'string' ? Number(raw) : NaN
})

const activeTab = ref<DetailTab>('overview')
const activeDayIndex = ref(1)
const activeOverviewDayIndex = ref(0)
const isEditingCurrentPage = ref(false)
const detail = ref<TravelPlanDetail | null>(null)
const isLoading = ref(true)
const loadError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const {
  overviewMapRef,
  routeMapRef,
  overviewMapMessage,
  routeMapMessage,
  renderOverviewMap,
  renderRouteMap,
  destroyMaps,
  focusPoint,
} = useTravelMaps({
  overviewPoints: () => overviewMapPoints.value,
  routePoints: () => currentRouteMapPoints.value,
  hasPlanStartDate: () => !!detail.value?.startDate,
})

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  const tab = route.query.tab
  if (tab === 'route' || tab === 'expense' || tab === 'overview') {
    activeTab.value = tab
  }
  void loadDetail()
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  destroyMaps()
})

const currentDay = computed(() => {
  const days = detail.value?.days ?? []
  const target = days.find((item) => item.dayIndex === activeDayIndex.value)
  if (target) {
    return target
  }

  if (!detail.value) {
    return days[0] ?? null
  }

  return null
})

const orderedDays = computed(() =>
  [...(detail.value?.days ?? [])].sort((left, right) => left.dayIndex - right.dayIndex),
)

const displayedHeaderTitle = computed(() => detail.value?.name || '旅行详情')

const resolvedDayCount = computed(() => {
  if (!detail.value) {
    return 0
  }

  const apiDayCount = Number(detail.value.overview?.dayCount ?? 0)
  const derivedFromDate = calculateDayCount(detail.value.startDate, detail.value.endDate)
  return Math.max(apiDayCount, derivedFromDate, orderedDays.value.length)
})

const routeDateItems = computed(() => {
  if (!detail.value) {
    return orderedDays.value.map((day) => ({
      key: day.id,
      dayIndex: day.dayIndex,
      label: formatDayTabLabel(day),
      day,
    }))
  }

  const totalDays = resolvedDayCount.value
  if (totalDays <= 0) {
    return orderedDays.value.map((day) => ({
      key: day.id,
      dayIndex: day.dayIndex,
      label: formatDayTabLabel(day),
      day,
    }))
  }

  return Array.from({ length: totalDays }, (_, index) => {
    const dayIndex = index + 1
    const matchedDay = orderedDays.value.find((day) => day.dayIndex === dayIndex) ?? null
    return {
      key: matchedDay?.id ?? dayIndex,
      dayIndex,
      label: matchedDay?.travelDate
        ? formatDate(matchedDay.travelDate)
        : formatDate(addDays(detail.value?.startDate, index)) || `Day ${dayIndex}`,
      day: matchedDay,
    }
  })
})

const currentRouteDateLabel = computed(
  () => routeDateItems.value.find((item) => item.dayIndex === activeDayIndex.value)?.label || '',
)

const currentRouteDay = computed<RouteDisplayDay>(() => currentDay.value ?? ({
  dayIndex: activeDayIndex.value,
  itineraries: [],
  expenses: [],
}))

const displayedCurrentDayItineraries = computed(() =>
  prependPreviousAccommodation(currentRouteDay.value.itineraries ?? [], orderedDays.value, currentRouteDay.value.dayIndex),
)

const currentRouteMapPoints = computed<TravelMapPoint[]>(() =>
  displayedCurrentDayItineraries.value
    .filter(
      (item): item is TravelPlanDetail['days'][number]['itineraries'][number] & { longitude: number; latitude: number } =>
        typeof item.longitude === 'number' && Number.isFinite(item.longitude)
        && typeof item.latitude === 'number' && Number.isFinite(item.latitude),
    )
    .map((item, index) => ({
      index: index + 1,
      title: item.title,
      longitude: item.longitude,
      latitude: item.latitude,
    })),
)

const currentRouteMapFocusTargets = computed<FocusMapTarget[]>(() =>
  displayedCurrentDayItineraries.value
    .filter(
      (item): item is TravelPlanDetail['days'][number]['itineraries'][number] & { longitude: number; latitude: number } =>
        typeof item.longitude === 'number' && Number.isFinite(item.longitude)
        && typeof item.latitude === 'number' && Number.isFinite(item.latitude),
    )
    .map((item, index) => ({
      key: item.id > 0 ? String(item.id) : `route-${index}-${item.title}`,
      index: index + 1,
      title: item.title,
      longitude: item.longitude,
      latitude: item.latitude,
    })),
)

const overviewMapPoints = computed<TravelMapPoint[]>(() => {
  let pointIndex = 0
  const seen = new Set<string>()

  const days = activeOverviewDayIndex.value === 0
    ? orderedDays.value
    : orderedDays.value.filter((day) => day.dayIndex === activeOverviewDayIndex.value)

  return days.flatMap((day) =>
    prependPreviousAccommodation(day.itineraries ?? [], orderedDays.value, day.dayIndex)
      .filter(
        (item): item is TravelPlanDetail['days'][number]['itineraries'][number] & { longitude: number; latitude: number } =>
          typeof item.longitude === 'number' && Number.isFinite(item.longitude)
          && typeof item.latitude === 'number' && Number.isFinite(item.latitude)
          && (() => {
            const pointKey = item.poiId?.trim()
              || `${item.title.trim()}-${item.longitude}-${item.latitude}`
            if (seen.has(pointKey)) {
              return false
            }
            seen.add(pointKey)
            return true
          })(),
      )
      .map((item) => {
        pointIndex += 1
        return {
          index: pointIndex,
          title: item.title,
          longitude: item.longitude,
          latitude: item.latitude,
        }
      }),
  )
})

const overviewMapFocusTargets = computed<FocusMapTarget[]>(() => {
  let pointIndex = 0
  const seen = new Set<string>()

  const days = activeOverviewDayIndex.value === 0
    ? orderedDays.value
    : orderedDays.value.filter((day) => day.dayIndex === activeOverviewDayIndex.value)

  return days.flatMap((day) =>
    prependPreviousAccommodation(day.itineraries ?? [], orderedDays.value, day.dayIndex)
      .filter(
        (item): item is TravelPlanDetail['days'][number]['itineraries'][number] & { longitude: number; latitude: number } =>
          typeof item.longitude === 'number' && Number.isFinite(item.longitude)
          && typeof item.latitude === 'number' && Number.isFinite(item.latitude)
          && (() => {
            const pointKey = item.poiId?.trim()
              || `${item.title.trim()}-${item.longitude}-${item.latitude}`
            if (seen.has(pointKey)) {
              return false
            }
            seen.add(pointKey)
            return true
          })(),
      )
      .map((item) => {
        pointIndex += 1
        return {
          key: item.id > 0 ? String(item.id) : `${item.title}-${item.longitude}-${item.latitude}`,
          index: pointIndex,
          title: item.title,
          longitude: item.longitude,
          latitude: item.latitude,
        }
      }),
  )
})

const summaryMeta = computed(() => {
  if (!detail.value) {
    return '暂无旅行信息'
  }
  const start = formatDate(detail.value.startDate)
  const end = formatDate(detail.value.endDate)
  const travelerCount = detail.value.overview?.travelerCount ?? Math.max((detail.value.companions?.length ?? 0) + 1, 1)
  const datePart = start && end ? `${start} - ${end}` : '日期待补充'
  return `${datePart} · ${resolvedDayCount.value} 天 · ${travelerCount} 人同行`
})

const currentDayOverviewEntries = computed(() =>
  orderedDays.value.map((day) => ({
    ...day,
    entries: buildOverviewEntries(prependPreviousAccommodation(day.itineraries ?? [], orderedDays.value, day.dayIndex)),
  })),
)

const overviewDateItems = computed(() => [
  { key: 0, dayIndex: 0, label: '全部' },
  ...routeDateItems.value.map((item) => ({
    key: item.key,
    dayIndex: item.dayIndex,
    label: item.label,
  })),
])

const filteredOverviewEntries = computed(() => {
  if (activeOverviewDayIndex.value === 0) {
    return currentDayOverviewEntries.value
  }
  return currentDayOverviewEntries.value.filter((day) => day.dayIndex === activeOverviewDayIndex.value)
})

const overviewExpenses = computed(() => detail.value?.expenses ?? [])

const hasOverviewExpenseData = computed(() => {
  if (!detail.value) {
    return false
  }

  const expenseCount = detail.value.overview?.expenseCount ?? detail.value.expenses.length
  return expenseCount > 0
})

const hasOverviewExpenseRows = computed(() => overviewExpenses.value.length > 0)

// ---------- 行程编辑弹窗 / 地点搜索 / 路程计算（见 useTravelPlanEditor） ----------

const {
  showAddModal,
  showEditModal,
  showDeleteModal,
  selectedModalType,
  selectedTransportMode,
  searchKeyword,
  selectedSearchResultId,
  startTime,
  expenseTitle,
  expenseAmount,
  modalRemark,
  editingTarget,
  deletingTarget,
  searchStackRef,
  expenseTitleSearchStackRef,
  isSearchingLocation,
  isCalculatingRoute,
  isSavingItinerary,
  isSavingExpense,
  isDeletingItem,
  modalError,
  editModalError,
  showExpenseTitleResults,
  routePreviewDistanceText,
  routePreviewDurationText,
  visibleSearchResults,
  expenseTitleOptions,
  itineraryTypeOptions,
  transportModeOptions,
  expenseTypeOptions,
  openEditItemModal,
  openDeleteItemModal,
  closeEditItemModal,
  closeDeleteItemModal,
  closeModal,
  toggleAddModal,
  openCreateExpenseModal,
  saveModal,
  saveEditModal,
  confirmDeleteItem,
  selectSearchResult,
  triggerLocationSearch,
  openExpenseTitleResults,
  selectExpenseTitleOption,
  handleDocumentPointerDown,
  itineraryEditTarget,
  expenseEditTarget,
} = useTravelPlanEditor({
  detail,
  currentDay,
  orderedDays,
  activeDayIndex,
  routeDateItems,
  currentPlanId,
  displayedDayItineraries: displayedCurrentDayItineraries,
  loadDetail,
  showFeedback,
})

// ---------- 页面骨架动作 ----------

function setTab(tab: DetailTab) {
  activeTab.value = tab
  void router.replace({
    path: route.path,
    query: {
      ...route.query,
      tab,
    },
  })
}

function setDay(dayIndex: number) {
  activeDayIndex.value = dayIndex
  void refreshMapForActiveTab()
}

function setOverviewDay(dayIndex: number) {
  activeOverviewDayIndex.value = dayIndex
  void refreshMapForActiveTab()
}

async function refreshMapForActiveTab() {
  await nextTick()

  if (activeTab.value === 'overview') {
    await renderOverviewMap()
  }

  if (activeTab.value === 'route') {
    await renderRouteMap()
  }
}

function focusMapPoint(target: FocusMapTarget | null) {
  if (!target) {
    return
  }

  focusPoint(activeTab.value === 'overview' ? 'overview' : 'route', target)
}

function focusOverviewEntry(title: string) {
  const normalizedTitle = title.trim()
  if (!normalizedTitle) {
    return
  }
  const target = overviewMapFocusTargets.value.find((item) => item.title.trim() === normalizedTitle) ?? null
  focusMapPoint(target)
}

function focusRouteEntry(item: TravelPlanDetail['days'][number]['itineraries'][number]) {
  const target = currentRouteMapFocusTargets.value.find((candidate) => candidate.key === String(item.id))
    ?? currentRouteMapFocusTargets.value.find((candidate) =>
      candidate.title === item.title
      && candidate.longitude === item.longitude
      && candidate.latitude === item.latitude,
    )
    ?? null
  focusMapPoint(target)
}

function toggleCurrentPageEditor() {
  isEditingCurrentPage.value = !isEditingCurrentPage.value
}

function goToEditPage() {
  if (activeTab.value === 'route' || activeTab.value === 'expense') {
    toggleCurrentPageEditor()
    return
  }
  if (!Number.isFinite(currentPlanId.value)) {
    return
  }
  void router.push(`/tools/travel-plans/${currentPlanId.value}/edit`)
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

async function loadDetail(silent = false) {
  const currentUser = getStoredCurrentUser()
  const planId = Number(route.params.planId ?? 1)
  const preferredDayIndex = activeDayIndex.value
  if (!currentUser || Number.isNaN(planId)) {
    loadError.value = '请先登录后查看旅行详情'
    isLoading.value = false
    return
  }

  if (!silent) {
    isLoading.value = true
    loadError.value = ''
  }

  try {
    detail.value = await getTravelPlan(planId)
    const matchedDay = detail.value.days?.find((item) => item.dayIndex === preferredDayIndex)
    activeDayIndex.value = matchedDay?.dayIndex ?? detail.value.days?.[0]?.dayIndex ?? preferredDayIndex ?? 1
  } catch (error) {
    if (!silent) {
      detail.value = null
      loadError.value = getErrorMessage(error, '旅行详情加载失败')
    }
  } finally {
    if (!silent) {
      isLoading.value = false
    }
  }
}

function routeMapLastSpot() {
  const items = displayedCurrentDayItineraries.value
  return items[items.length - 1]?.title || '当天终点'
}

function overviewMapLastSpot() {
  const items = overviewMapPoints.value
  return items[items.length - 1]?.title || '旅行终点'
}

watch(
  () => [activeTab.value, detail.value?.id, overviewMapPoints.value.length, activeOverviewDayIndex.value] as const,
  async ([tab]) => {
    if (tab !== 'overview') {
      return
    }
    await nextTick()
    await renderOverviewMap()
  },
  { immediate: true },
)

watch(
  () => [activeTab.value, currentDay.value?.id, currentRouteMapPoints.value.length, currentRouteDateLabel.value] as const,
  async ([tab]) => {
    if (tab !== 'route') {
      return
    }
    await nextTick()
    await renderRouteMap()
  },
  { immediate: true },
)
</script>

<template>
  <section class="travel-detail-page">
    <PageHeader :title="displayedHeaderTitle" back-to="/tools/travel-plans" back-label="返回旅行列表">
      <template #right>
        <div class="header-action-group">
          <button class="header-action-button" type="button" @click="goToEditPage">
            {{ activeTab === 'overview' ? '修改旅行' : isEditingCurrentPage ? '完成' : '编辑' }}
          </button>
        </div>
      </template>
    </PageHeader>

    <div class="detail-segment" role="tablist" aria-label="旅行详情分页">
      <button :class="['segment-button', { active: activeTab === 'overview' }]" type="button" @click="setTab('overview')">
        概览
      </button>
      <button :class="['segment-button', { active: activeTab === 'route' }]" type="button" @click="setTab('route')">
        线路
      </button>
      <button :class="['segment-button', { active: activeTab === 'expense' }]" type="button" @click="setTab('expense')">
        费用
      </button>
    </div>

    <div v-if="isLoading" class="detail-empty">正在加载旅行详情...</div>

    <div v-else-if="!detail" class="detail-empty">
      <strong>没有获取到旅行详情</strong>
      <span>{{ loadError || '请返回列表后重新进入。' }}</span>
    </div>

    <section v-else-if="activeTab === 'overview'" class="overview-panel">
      <div class="overview-summary-bar">
        <span>{{ summaryMeta }}</span>
      </div>

      <div class="route-map-card overview-map-card">
        <div class="route-map-label route-map-label-left">全程地图</div>
        <div class="route-map-label route-map-label-center">{{ routeCountText(overviewMapPoints.length) }}</div>
        <svg class="route-map-svg-fallback" viewBox="0 0 320 150" aria-hidden="true">
          <path d="M18 96C48 60 84 36 118 54C138 64 164 102 198 90C242 74 262 26 301 34" fill="none" stroke="#2563EB" stroke-width="4" stroke-linecap="round"/>
          <circle cx="18" cy="96" r="4" fill="#2563EB" />
          <circle cx="118" cy="54" r="4" fill="#22C55E" />
          <circle cx="198" cy="90" r="4" fill="#FB923C" />
          <circle cx="301" cy="34" r="4" fill="#8B5CF6" />
        </svg>
        <div ref="overviewMapRef" class="route-map-canvas"></div>
        <span v-if="overviewMapMessage" class="route-map-empty-message">{{ overviewMapMessage }}</span>
        <span class="route-map-spot route-map-spot-right">{{ overviewMapLastSpot() }}</span>
      </div>

      <div class="day-editor-bar">
        <div class="day-tab-row">
          <button
            v-for="item in overviewDateItems"
            :key="item.key"
            :class="['day-tab', { active: activeOverviewDayIndex === item.dayIndex }]"
            type="button"
            @click="setOverviewDay(item.dayIndex)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div class="overview-day-list">
        <article v-for="day in filteredOverviewEntries" :key="day.id" class="overview-day">
          <h3>Day {{ day.dayIndex }}</h3>

          <div v-if="day.entries.length > 0" class="overview-timeline">
            <template v-for="(entry, index) in day.entries" :key="`${day.id}-${index}`">
              <div v-if="entry.kind === 'item'" class="timeline-row">
                <div class="timeline-rail">
                  <span :class="['rail-top', { hidden: index === 0 }]"></span>
                  <span :class="['rail-dot', `tone-${entry.tone}`]"></span>
                  <span :class="['rail-bottom', { hidden: index === day.entries.length - 1 }]"></span>
                </div>

                <div
                  :class="['timeline-card', `tone-${entry.tone}`]"
                  role="button"
                  tabindex="0"
                  @click="focusOverviewEntry(entry.title)"
                  @keydown.enter.prevent="focusOverviewEntry(entry.title)"
                  @keydown.space.prevent="focusOverviewEntry(entry.title)"
                >
                  <h4>{{ entry.title }}</h4>
                  <p>{{ entry.description }}</p>
                </div>
              </div>

              <div v-else class="timeline-transfer-row">
                <div class="timeline-rail transfer-only">
                  <span class="rail-transfer"></span>
                </div>
                <div class="timeline-transfer-text">
                  <span>{{ entry.text }}</span>
                  <button
                    v-if="entry.navigation"
                    type="button"
                    class="timeline-transfer-action"
                    @click="openOverviewNavigation(entry)"
                  >
                    导航
                  </button>
                </div>
              </div>
            </template>
          </div>

          <div v-else class="day-empty-text">这一天还没有添加行程。</div>
        </article>
      </div>

      <section v-if="hasOverviewExpenseData" class="overview-expense-section">
        <div class="overview-expense-head">
          <span>费用概览</span>
        </div>

        <div class="overview-expense-summary-card">
          <div class="overview-expense-metrics">
            <div class="overview-expense-metric">
              <span>总花销</span>
              <strong>{{ formatMetric(detail.overview?.totalExpenseAmount) }}</strong>
            </div>
            <div class="overview-expense-metric align-end">
              <span>个人花销</span>
              <strong class="accent">{{ formatMetric(detail.overview?.perPersonExpenseAmount) }}</strong>
            </div>
          </div>
          <p class="overview-expense-summary-text">
            {{ resolvedDayCount }} 天行程共记录
            {{ detail.overview?.expenseCount ?? detail.expenses.length }} 笔费用。
          </p>
        </div>

        <div v-if="hasOverviewExpenseRows" class="overview-expense-card">
          <div class="expense-list-head">
            <span>费用清单</span>
            <span>{{ `共 ${overviewExpenses.length} 笔` }}</span>
          </div>
          <div class="overview-expense-rows">
            <div
              v-for="row in overviewExpenses"
              :key="row.id"
              class="overview-expense-row"
            >
              <div class="overview-expense-left">
                <strong>{{ row.title }}</strong>
                <span>{{ expenseNote(row) }}</span>
              </div>
              <span class="overview-expense-amount">{{ overviewExpenseRowAmount(row) }}</span>
            </div>
          </div>
        </div>
      </section>
    </section>

    <section v-else-if="activeTab === 'route'" class="route-panel">
      <div class="route-map-card">
        <div class="route-map-label route-map-label-left">{{ currentRouteDateLabel || `Day ${activeDayIndex}` }}</div>
        <div class="route-map-label route-map-label-center">{{ routeCountText(currentDay?.itineraries?.length ?? 0) }}</div>
        <svg class="route-map-svg-fallback" viewBox="0 0 320 150" aria-hidden="true">
          <path d="M18 96C48 60 84 36 118 54C138 64 164 102 198 90C242 74 262 26 301 34" fill="none" stroke="#2563EB" stroke-width="4" stroke-linecap="round"/>
          <circle cx="18" cy="96" r="4" fill="#2563EB" />
          <circle cx="118" cy="54" r="4" fill="#22C55E" />
          <circle cx="198" cy="90" r="4" fill="#FB923C" />
          <circle cx="301" cy="34" r="4" fill="#8B5CF6" />
        </svg>
        <div ref="routeMapRef" class="route-map-canvas"></div>
        <span v-if="routeMapMessage" class="route-map-empty-message">{{ routeMapMessage }}</span>
        <span class="route-map-spot route-map-spot-right">{{ routeMapLastSpot() }}</span>
      </div>

      <div class="day-editor-bar">
        <div class="day-bar-head">
          <span>日期</span>
        </div>

        <div class="day-tab-row">
          <button
            v-for="item in routeDateItems"
            :key="item.key"
            :class="['day-tab', { active: activeDayIndex === item.dayIndex }]"
            type="button"
            @click="setDay(item.dayIndex)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div class="route-editor-panel">
        <div class="route-editor-head">
          <h3>Day {{ currentDay?.dayIndex || activeDayIndex }} 行程编辑</h3>
        </div>

        <div v-if="displayedCurrentDayItineraries.length > 0" class="route-editor-list">
          <article
            v-for="(item, index) in displayedCurrentDayItineraries"
            :key="item.id"
            class="route-editor-item-wrap"
          >
            <div v-if="index > 0 && routeTransferSummary(item)" class="route-transfer-line">
              <span>{{ routeTransferSummary(item) }}</span>
            </div>

            <article class="route-editor-card">
              <span :class="['order-badge', `tone-${typeTone(item.type)}`]">{{ index + 1 }}</span>
              <div class="route-editor-content">
                <button type="button" class="route-editor-title-button" @click="focusRouteEntry(item)">
                  {{ item.title }}
                </button>
                <p>{{ formatStartTime(item.startTime) }} · {{ itineraryDescription(item) }}</p>
              </div>
              <div v-if="isEditingCurrentPage && item.id > 0" class="route-editor-actions">
                <button class="mini-outline-button" type="button" @click="openEditItemModal(itineraryEditTarget(currentDay!.id, currentDay!.dayIndex, item))">
                  修改
                </button>
                <button class="mini-outline-button muted" type="button" @click="openDeleteItemModal(itineraryEditTarget(currentDay!.id, currentDay!.dayIndex, item))">
                  删除
                </button>
              </div>
            </article>
          </article>
        </div>
        <div v-else class="route-empty-text">这一天还没有添加行程。</div>
      </div>

      <button class="add-action-button" type="button" @click="toggleAddModal">+ 添加当天行程</button>
    </section>

    <section v-else class="expense-panel">
      <div class="expense-summary-wrap">
        <h4>费用总览</h4>
        <div class="expense-metrics">
          <div class="metric-card">
            <span>总金额</span>
            <strong>{{ formatMetric(detail.overview?.totalExpenseAmount) }}</strong>
          </div>
          <div class="metric-card">
            <span>人均</span>
            <strong>{{ formatMetric(detail.overview?.perPersonExpenseAmount) }}</strong>
          </div>
        </div>
        <p>
          {{ resolvedDayCount }} 天行程共记录
          {{ detail.overview?.expenseCount ?? detail.expenses.length }} 笔费用，默认按
          {{ detail.overview?.travelerCount ?? 1 }} 人计算。
        </p>
      </div>

      <div class="day-editor-bar compact">
        <div class="day-tab-row">
          <button
            v-for="item in routeDateItems"
            :key="item.key"
            :class="['day-tab', { active: activeDayIndex === item.dayIndex }]"
            type="button"
            @click="setDay(item.dayIndex)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div v-if="(currentDay?.expenses?.length ?? 0) > 0" class="expense-editor-list">
        <article v-for="item in currentDay?.expenses ?? []" :key="item.id" class="expense-editor-card">
          <div class="expense-card-top">
            <div class="expense-card-left">
              <strong>{{ item.title }}</strong>
              <span>{{ expenseNote(item) }}</span>
            </div>
            <div class="expense-card-right">
              <span class="expense-card-amount">{{ expenseAmountText(item.amount) }}</span>
              <div v-if="isEditingCurrentPage" class="expense-card-actions">
                <button class="mini-outline-button" type="button" @click="openEditItemModal(expenseEditTarget(currentDay!.id, currentDay!.dayIndex, item))">修改</button>
                <button class="mini-outline-button muted" type="button" @click="openDeleteItemModal(expenseEditTarget(currentDay!.id, currentDay!.dayIndex, item))">删除</button>
              </div>
            </div>
          </div>
        </article>
      </div>
      <div v-else class="detail-empty inline">这一天还没有费用记录。</div>

      <button class="add-action-button" type="button" @click="openCreateExpenseModal">
        + 为 Day {{ currentDay?.dayIndex || activeDayIndex }} 增加费用
      </button>
    </section>

    <CommonModal v-model="showAddModal" title="添加行程" size="compact" :show-close="false">
      <div class="investment-add-modal-form travel-add-modal-form">
        <label class="investment-add-modal-field">
          <span>行程类型</span>
          <div class="type-pill-row">
            <button
              v-for="type in ['transport', 'scenic', 'dining', 'accommodation']"
              :key="type"
              :class="['type-pill', { active: selectedModalType === type }]"
              type="button"
              @click="selectedModalType = type as ModalType"
            >
              {{ type === 'transport' ? '交通' : type === 'scenic' ? '景区' : type === 'dining' ? '餐饮' : '住宿' }}
            </button>
          </div>
        </label>

        <div class="investment-search-dropdown">
          <div ref="searchStackRef" class="travel-search-hitbox">
            <label class="investment-search-field" aria-label="输入地点名称或地址">
              <input
                v-model="searchKeyword"
                type="search"
                inputmode="search"
                placeholder="输入地点名称或地址"
                @keydown.enter.prevent="triggerLocationSearch()"
              />
              <button type="button" :disabled="isSearchingLocation" @click="triggerLocationSearch()">
                <span aria-hidden="true">⌕</span>
                {{ isSearchingLocation ? '搜索中' : '搜索' }}
              </button>
            </label>

            <div v-if="visibleSearchResults.length > 0" class="investment-search-results" aria-label="搜索结果">
              <button
                v-for="result in visibleSearchResults"
                :key="result.id"
                :class="['investment-search-result-item', { active: selectedSearchResultId === result.id }]"
                type="button"
                @click="selectSearchResult(result)"
              >
                <strong>{{ result.title }}</strong>
                <span>{{ result.address }}</span>
              </button>
            </div>
          </div>
        </div>

        <CommonInput v-model="startTime" label="开始时间" input-type="time" />

        <CommonSelect v-model="selectedTransportMode" label="交通方式" :options="transportModeOptions" />
        <div class="transport-route-preview">
          <strong>自动计算</strong>
          <span v-if="isCalculatingRoute">正在计算路程与时长...</span>
          <span v-else-if="routePreviewDistanceText || routePreviewDurationText">
            路程 {{ routePreviewDistanceText || '暂无' }}，预计时间 {{ routePreviewDurationText || '暂无' }}
          </span>
          <span v-else>选择地点后将自动计算上一站到当前站的路程与时长</span>
        </div>

        <label class="investment-add-modal-field">
          <span>备注</span>
          <textarea v-model="modalRemark" class="investment-field-control travel-modal-textarea" rows="3"></textarea>
        </label>

        <p v-if="modalError" class="travel-modal-error">{{ modalError }}</p>
      </div>

      <template #footer>
        <div class="investment-add-modal-actions">
          <button class="investment-modal-button secondary" type="button" :disabled="isSavingItinerary" @click="closeModal">取消</button>
          <button class="investment-modal-button primary" type="button" :disabled="isSavingItinerary" @click="saveModal">
            {{ isSavingItinerary ? '保存中...' : '保存' }}
          </button>
        </div>
      </template>
    </CommonModal>

    <CommonModal v-model="showEditModal" :title="editingTarget?.type === 'itinerary' ? '修改行程' : '修改费用'" size="compact" :show-close="false">
      <div class="investment-add-modal-form travel-add-modal-form">
        <CommonSelect
          v-if="editingTarget?.type === 'itinerary'"
          v-model="selectedModalType"
          label="行程类型"
          :options="itineraryTypeOptions"
        />
        <CommonSelect
          v-else
          v-model="selectedModalType"
          label="费用类型"
          :options="expenseTypeOptions"
        />

        <template v-if="editingTarget?.type === 'itinerary'">
          <div class="investment-search-dropdown">
            <div ref="searchStackRef" class="travel-search-hitbox">
              <label class="investment-search-field" aria-label="输入地点名称或地址">
                <input
                  v-model="searchKeyword"
                  type="search"
                  inputmode="search"
                  placeholder="输入地点名称或地址"
                  @keydown.enter.prevent="triggerLocationSearch()"
                />
                <button type="button" :disabled="isSearchingLocation" @click="triggerLocationSearch()">
                  <span aria-hidden="true">⌕</span>
                  {{ isSearchingLocation ? '搜索中' : '搜索' }}
                </button>
              </label>

              <div v-if="visibleSearchResults.length > 0" class="investment-search-results" aria-label="搜索结果">
                <button
                  v-for="result in visibleSearchResults"
                  :key="result.id"
                  :class="['investment-search-result-item', { active: selectedSearchResultId === result.id }]"
                  type="button"
                  @click="selectSearchResult(result)"
                >
                  <strong>{{ result.title }}</strong>
                  <span>{{ result.address }}</span>
                </button>
              </div>
            </div>
          </div>

          <CommonInput v-model="startTime" label="开始时间" input-type="time" />

          <CommonSelect v-model="selectedTransportMode" label="交通方式" :options="transportModeOptions" />
          <div class="transport-route-preview">
            <strong>自动计算</strong>
            <span v-if="isCalculatingRoute">正在计算路程与时长...</span>
            <span v-else-if="routePreviewDistanceText || routePreviewDurationText">
              路程 {{ routePreviewDistanceText || '暂无' }}，预计时间 {{ routePreviewDurationText || '暂无' }}
            </span>
            <span v-else>选择地点后将自动计算上一站到当前站的路程与时长</span>
          </div>
        </template>

        <template v-else>
          <div class="investment-search-dropdown">
            <div ref="expenseTitleSearchStackRef" class="travel-search-hitbox">
              <label class="investment-search-field" aria-label="费用名称搜索">
                <input
                  v-model="expenseTitle"
                  type="search"
                  inputmode="search"
                  placeholder="搜索当天行程作为费用名称"
                  @focus="openExpenseTitleResults()"
                  @input="openExpenseTitleResults()"
                />
                <button type="button" @click="openExpenseTitleResults()">
                  <span aria-hidden="true">⌕</span>
                  选择
                </button>
              </label>

              <div v-if="showExpenseTitleResults && expenseTitleOptions.length > 0" class="investment-search-results" aria-label="费用名称候选">
                <button
                  v-for="option in expenseTitleOptions"
                  :key="option.id"
                  class="investment-search-result-item"
                  type="button"
                  @click="selectExpenseTitleOption(option)"
                >
                  <strong>{{ option.title }}</strong>
                  <span>{{ option.meta }}</span>
                </button>
              </div>
            </div>
          </div>
          <CommonInput v-model="expenseAmount" label="费用金额" placeholder="请输入金额" input-type="number" input-mode="decimal" />
        </template>

        <label class="investment-add-modal-field">
          <span>备注</span>
          <textarea v-model="modalRemark" class="investment-field-control travel-modal-textarea" rows="3"></textarea>
        </label>

        <p v-if="editModalError" class="travel-modal-error">{{ editModalError }}</p>
      </div>

      <template #footer>
        <div class="investment-add-modal-actions">
          <CommonButton variant="secondary" :disabled="isSavingItinerary || isSavingExpense" @click="closeEditItemModal()">取消</CommonButton>
          <CommonButton :disabled="isSavingItinerary || isSavingExpense" @click="saveEditModal">
            {{ isSavingItinerary || isSavingExpense ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal v-model="showDeleteModal" title="删除内容" size="compact" :show-close="!isDeletingItem">
      <div class="travel-delete-content">
        <p>
          确定删除
          {{ deletingTarget?.type === 'itinerary' ? '这条行程' : '这条费用' }}
          吗？
        </p>
        <span>删除后无法恢复。</span>
      </div>

      <template #footer>
        <div class="investment-add-modal-actions">
          <button class="investment-modal-button secondary" type="button" :disabled="isDeletingItem" @click="closeDeleteItemModal()">
            取消
          </button>
          <button class="investment-modal-button primary" type="button" :disabled="isDeletingItem" @click="confirmDeleteItem">
            {{ isDeletingItem ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </template>
    </CommonModal>
  </section>

  <CommonFeedback v-model="showFeedbackModal" :message="feedbackMessage" :type="feedbackType" />
</template>

<style scoped lang="scss" src="./style.scss"></style>
