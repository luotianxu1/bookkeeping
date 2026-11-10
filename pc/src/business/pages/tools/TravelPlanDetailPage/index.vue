<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import PagePanel from '@/business/components/PagePanel.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  getTravelPlan,
  type TravelPlanDetail,
} from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'
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
  routeCountText,
  routeTransferSummary,
} from './travelFormat'
import { openOverviewNavigation } from './travelNavigation'
import {
  buildOverviewEntries,
  prependPreviousAccommodation,
} from './travelPoints'
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
  [...(detail.value?.days ?? [])].sort(
    (left, right) => left.dayIndex - right.dayIndex,
  ),
)

const displayedHeaderTitle = computed(() => detail.value?.name || '旅行详情')

const resolvedDayCount = computed(() => {
  if (!detail.value) {
    return 0
  }

  const apiDayCount = Number(detail.value.overview?.dayCount ?? 0)
  const derivedFromDate = calculateDayCount(
    detail.value.startDate,
    detail.value.endDate,
  )
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
    const matchedDay =
      orderedDays.value.find((day) => day.dayIndex === dayIndex) ?? null
    return {
      key: matchedDay?.id ?? dayIndex,
      dayIndex,
      label: matchedDay?.travelDate
        ? formatDate(matchedDay.travelDate)
        : formatDate(addDays(detail.value?.startDate, index)) ||
          `Day ${dayIndex}`,
      day: matchedDay,
    }
  })
})

const currentRouteDateLabel = computed(
  () =>
    routeDateItems.value.find((item) => item.dayIndex === activeDayIndex.value)
      ?.label || '',
)

const currentRouteDay = computed<RouteDisplayDay>(
  () =>
    currentDay.value ?? {
      dayIndex: activeDayIndex.value,
      itineraries: [],
      expenses: [],
    },
)

const displayedCurrentDayItineraries = computed(() =>
  prependPreviousAccommodation(
    currentRouteDay.value.itineraries ?? [],
    orderedDays.value,
    currentRouteDay.value.dayIndex,
  ),
)

const currentRouteMapPoints = computed<TravelMapPoint[]>(() =>
  displayedCurrentDayItineraries.value
    .filter(
      (
        item,
      ): item is TravelPlanDetail['days'][number]['itineraries'][number] & {
        longitude: number
        latitude: number
      } =>
        typeof item.longitude === 'number' &&
        Number.isFinite(item.longitude) &&
        typeof item.latitude === 'number' &&
        Number.isFinite(item.latitude),
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
      (
        item,
      ): item is TravelPlanDetail['days'][number]['itineraries'][number] & {
        longitude: number
        latitude: number
      } =>
        typeof item.longitude === 'number' &&
        Number.isFinite(item.longitude) &&
        typeof item.latitude === 'number' &&
        Number.isFinite(item.latitude),
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

  const days =
    activeOverviewDayIndex.value === 0
      ? orderedDays.value
      : orderedDays.value.filter(
          (day) => day.dayIndex === activeOverviewDayIndex.value,
        )

  return days.flatMap((day) =>
    prependPreviousAccommodation(
      day.itineraries ?? [],
      orderedDays.value,
      day.dayIndex,
    )
      .filter(
        (
          item,
        ): item is TravelPlanDetail['days'][number]['itineraries'][number] & {
          longitude: number
          latitude: number
        } =>
          typeof item.longitude === 'number' &&
          Number.isFinite(item.longitude) &&
          typeof item.latitude === 'number' &&
          Number.isFinite(item.latitude) &&
          (() => {
            const pointKey =
              item.poiId?.trim() ||
              `${item.title.trim()}-${item.longitude}-${item.latitude}`
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

  const days =
    activeOverviewDayIndex.value === 0
      ? orderedDays.value
      : orderedDays.value.filter(
          (day) => day.dayIndex === activeOverviewDayIndex.value,
        )

  return days.flatMap((day) =>
    prependPreviousAccommodation(
      day.itineraries ?? [],
      orderedDays.value,
      day.dayIndex,
    )
      .filter(
        (
          item,
        ): item is TravelPlanDetail['days'][number]['itineraries'][number] & {
          longitude: number
          latitude: number
        } =>
          typeof item.longitude === 'number' &&
          Number.isFinite(item.longitude) &&
          typeof item.latitude === 'number' &&
          Number.isFinite(item.latitude) &&
          (() => {
            const pointKey =
              item.poiId?.trim() ||
              `${item.title.trim()}-${item.longitude}-${item.latitude}`
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
          key:
            item.id > 0
              ? String(item.id)
              : `${item.title}-${item.longitude}-${item.latitude}`,
          index: pointIndex,
          title: item.title,
          longitude: item.longitude,
          latitude: item.latitude,
        }
      }),
  )
})

const currentDayOverviewEntries = computed(() =>
  orderedDays.value.map((day) => ({
    ...day,
    entries: buildOverviewEntries(
      prependPreviousAccommodation(
        day.itineraries ?? [],
        orderedDays.value,
        day.dayIndex,
      ),
    ),
  })),
)

const filteredOverviewEntries = computed(() => {
  if (activeOverviewDayIndex.value === 0) {
    return currentDayOverviewEntries.value
  }
  return currentDayOverviewEntries.value.filter(
    (day) => day.dayIndex === activeOverviewDayIndex.value,
  )
})

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
  const target =
    overviewMapFocusTargets.value.find(
      (item) => item.title.trim() === normalizedTitle,
    ) ?? null
  focusMapPoint(target)
}

function focusRouteEntry(
  item: TravelPlanDetail['days'][number]['itineraries'][number],
) {
  const target =
    currentRouteMapFocusTargets.value.find(
      (candidate) => candidate.key === String(item.id),
    ) ??
    currentRouteMapFocusTargets.value.find(
      (candidate) =>
        candidate.title === item.title &&
        candidate.longitude === item.longitude &&
        candidate.latitude === item.latitude,
    ) ??
    null
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
    const matchedDay = detail.value.days?.find(
      (item) => item.dayIndex === preferredDayIndex,
    )
    activeDayIndex.value =
      matchedDay?.dayIndex ??
      detail.value.days?.[0]?.dayIndex ??
      preferredDayIndex ??
      1
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
  () =>
    [
      activeTab.value,
      detail.value?.id,
      overviewMapPoints.value.length,
      activeOverviewDayIndex.value,
    ] as const,
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
  () =>
    [
      activeTab.value,
      currentDay.value?.id,
      currentRouteMapPoints.value.length,
      currentRouteDateLabel.value,
    ] as const,
  async ([tab]) => {
    if (tab !== 'route') {
      return
    }
    await nextTick()
    await renderRouteMap()
  },
  { immediate: true },
)

type ItineraryRow = (typeof displayedCurrentDayItineraries.value)[number]
type ExpenseRow = NonNullable<typeof currentDay.value>['expenses'][number]
</script>
<template>
  <section class="desktop-workspace desktop-travel">
    <PageHeader :title="displayedHeaderTitle">
      <template #right>
        <el-button
          @click="
            () => {
              setTab('overview')
              goToEditPage()
            }
          "
        >
          修改旅行
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <CommonLoading v-if="isLoading" />
    <template v-if="detail">
      <MetricStrip
        :items="[
          { label: '旅行天数', value: resolvedDayCount },
          {
            label: '总费用',
            value: formatMetric(detail.overview?.totalExpenseAmount),
          },
          {
            label: '人均费用',
            value: formatMetric(detail.overview?.perPersonExpenseAmount),
          },
          { label: '同行人数', value: detail.overview?.travelerCount ?? 1 },
        ]"
      />
      <el-tabs
        :model-value="activeTab"
        class="desktop-tabs"
        @tab-change="setTab($event as DetailTab)"
      >
        <el-tab-pane label="旅行概览" name="overview">
          <div v-if="activeTab === 'overview'" class="desktop-columns">
            <el-card
              class="pc-record-card route-map-card overview-map-card"
              shadow="never"
            >
              <div class="route-map-label route-map-label-left">全程地图</div>
              <div class="route-map-label route-map-label-center">
                {{ routeCountText(overviewMapPoints.length) }}
              </div>

              <div ref="overviewMapRef" class="route-map-canvas"></div>
              <span v-if="overviewMapMessage" class="route-map-empty-message">
                {{ overviewMapMessage }}
              </span>
              <span class="route-map-spot route-map-spot-right">
                {{ overviewMapLastSpot() }}
              </span>
            </el-card>
            <PagePanel title="每日行程">
              <div class="pc-grid overview-day-list">
                <el-card
                  v-for="day in filteredOverviewEntries"
                  :key="day.id"
                  class="pc-record-card overview-day"
                  shadow="never"
                >
                  <h3>Day {{ day.dayIndex }}</h3>
                  <div v-if="day.entries.length > 0" class="overview-timeline">
                    <template
                      v-for="(entry, index) in day.entries"
                      :key="`${day.id}-${index}`"
                    >
                      <div v-if="entry.kind === 'item'" class="timeline-row">
                        <div class="timeline-rail">
                          <span
                            :class="['rail-top', { hidden: index === 0 }]"
                          ></span>
                          <span
                            :class="['rail-dot', `tone-${entry.tone}`]"
                          ></span>
                          <span
                            :class="[
                              'rail-bottom',
                              { hidden: index === day.entries.length - 1 },
                            ]"
                          ></span>
                        </div>
                        <el-card
                          :class="['timeline-card', `tone-${entry.tone}`]"
                          role="button"
                          tabindex="0"
                          @click="focusOverviewEntry(entry.title)"
                          @keydown.enter.prevent="
                            focusOverviewEntry(entry.title)
                          "
                          @keydown.space.prevent="
                            focusOverviewEntry(entry.title)
                          "
                          shadow="never"
                          class="pc-record-card"
                        >
                          <h4>{{ entry.title }}</h4>
                          <p>{{ entry.description }}</p>
                        </el-card>
                      </div>
                      <div v-else class="timeline-transfer-row">
                        <div class="timeline-rail transfer-only">
                          <span class="rail-transfer"></span>
                        </div>
                        <div class="timeline-transfer-text">
                          <span>{{ entry.text }}</span>
                          <el-button
                            v-if="entry.navigation"
                            native-type="button"
                            class="timeline-transfer-action"
                            @click="openOverviewNavigation(entry)"
                          >
                            导航
                          </el-button>
                        </div>
                      </div>
                    </template>
                  </div>
                  <div v-else class="day-empty-text">
                    这一天还没有添加行程。
                  </div>
                </el-card>
              </div>
            </PagePanel>
          </div>
        </el-tab-pane>
        <el-tab-pane label="行程安排" name="route">
          <div v-if="activeTab === 'route'" class="desktop-results">
            <div class="desktop-filter-bar">
              <el-select
                :model-value="activeDayIndex"
                aria-label="旅行日期"
                @update:model-value="setDay"
              >
                <el-option
                  v-for="item in routeDateItems"
                  :key="item.key"
                  :label="item.label"
                  :value="item.dayIndex"
                />
              </el-select>
              <el-button type="primary" @click="toggleAddModal">
                添加行程
              </el-button>
            </div>
            <el-card class="pc-record-card route-map-card" shadow="never">
              <div class="route-map-label route-map-label-left">
                {{ currentRouteDateLabel || `Day ${activeDayIndex}` }}
              </div>
              <div class="route-map-label route-map-label-center">
                {{ routeCountText(currentDay?.itineraries?.length ?? 0) }}
              </div>

              <div ref="routeMapRef" class="route-map-canvas"></div>
              <span v-if="routeMapMessage" class="route-map-empty-message">
                {{ routeMapMessage }}
              </span>
              <span class="route-map-spot route-map-spot-right">
                {{ routeMapLastSpot() }}
              </span>
            </el-card>
            <DesktopTable
              :rows="displayedCurrentDayItineraries"
              :loading="false"
              title="当天行程"
            >
              <el-table-column
                prop="title"
                label="地点 / 活动"
                min-width="240"
                sortable="custom"
                show-overflow-tooltip
              />
              <!-- @vue-generic {ItineraryRow} -->
              <el-table-column label="时间" min-width="150">
                <template #default="{ row }: { row: ItineraryRow }">
                  {{ formatStartTime(row.startTime) }}
                </template>
              </el-table-column>
              <!-- @vue-generic {ItineraryRow} -->
              <el-table-column label="安排" min-width="220">
                <template #default="{ row }: { row: ItineraryRow }">
                  {{ itineraryDescription(row) }}
                </template>
              </el-table-column>
              <!-- @vue-generic {ItineraryRow} -->
              <el-table-column label="交通" min-width="180">
                <template #default="{ row }: { row: ItineraryRow }">
                  {{ routeTransferSummary(row) }}
                </template>
              </el-table-column>
              <!-- @vue-generic {ItineraryRow} -->
              <el-table-column fixed="right" label="操作" width="190">
                <template #default="{ row }: { row: ItineraryRow }">
                  <el-button link type="primary" @click="focusRouteEntry(row)">
                    定位
                  </el-button>
                  <el-button
                    v-if="row.id > 0"
                    link
                    type="primary"
                    @click="
                      openEditItemModal(
                        itineraryEditTarget(
                          currentDay!.id,
                          currentDay!.dayIndex,
                          row,
                        ),
                      )
                    "
                  >
                    修改
                  </el-button>
                  <el-button
                    v-if="row.id > 0"
                    link
                    type="danger"
                    @click="
                      openDeleteItemModal(
                        itineraryEditTarget(
                          currentDay!.id,
                          currentDay!.dayIndex,
                          row,
                        ),
                      )
                    "
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </DesktopTable>
          </div>
        </el-tab-pane>
        <el-tab-pane label="费用清单" name="expense">
          <div class="desktop-filter-bar">
            <el-select
              :model-value="activeDayIndex"
              aria-label="费用日期"
              @update:model-value="setDay"
            >
              <el-option
                v-for="item in routeDateItems"
                :key="item.key"
                :label="item.label"
                :value="item.dayIndex"
              />
            </el-select>
            <el-button type="primary" @click="openCreateExpenseModal">
              添加费用
            </el-button>
          </div>
          <DesktopTable
            :rows="currentDay?.expenses || []"
            :loading="false"
            title="旅行费用"
          >
            <el-table-column
              prop="title"
              label="费用项目"
              min-width="240"
              sortable="custom"
              show-overflow-tooltip
            />
            <!-- @vue-generic {ExpenseRow} -->
            <el-table-column label="金额" min-width="150">
              <template #default="{ row }: { row: ExpenseRow }">
                {{ expenseAmountText(row.amount) }}
              </template>
            </el-table-column>
            <!-- @vue-generic {ExpenseRow} -->
            <el-table-column label="备注" min-width="260">
              <template #default="{ row }: { row: ExpenseRow }">
                {{ expenseNote(row) }}
              </template>
            </el-table-column>
            <!-- @vue-generic {ExpenseRow} -->
            <el-table-column fixed="right" label="操作" width="160">
              <template #default="{ row }: { row: ExpenseRow }">
                <el-button
                  link
                  type="primary"
                  @click="
                    openEditItemModal(
                      expenseEditTarget(
                        currentDay!.id,
                        currentDay!.dayIndex,
                        row,
                      ),
                    )
                  "
                >
                  修改
                </el-button>
                <el-button
                  link
                  type="danger"
                  @click="
                    openDeleteItemModal(
                      expenseEditTarget(
                        currentDay!.id,
                        currentDay!.dayIndex,
                        row,
                      ),
                    )
                  "
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </DesktopTable>
        </el-tab-pane>
      </el-tabs>
    </template>
    <CommonModal
      v-model="showAddModal"
      title="添加行程"
      size="compact"
      :show-close="false"
    >
      <div class="pc-form-grid investment-add-modal-form travel-add-modal-form">
        <label class="pc-field investment-add-modal-field">
          <span>行程类型</span>
          <div class="type-pill-row">
            <el-button
              v-for="type in ['transport', 'scenic', 'dining', 'accommodation']"
              :key="type"
              :class="['type-pill', { active: selectedModalType === type }]"
              native-type="button"
              @click="selectedModalType = type as ModalType"
            >
              {{
                type === 'transport'
                  ? '交通'
                  : type === 'scenic'
                    ? '景区'
                    : type === 'dining'
                      ? '餐饮'
                      : '住宿'
              }}
            </el-button>
          </div>
        </label>
        <div class="investment-search-dropdown">
          <div ref="searchStackRef" class="travel-search-hitbox">
            <label
              class="pc-field investment-search-field"
              aria-label="输入地点名称或地址"
            >
              <DesktopInput
                v-model="searchKeyword"
                type="search"
                inputmode="search"
                placeholder="输入地点名称或地址"
                @keydown.enter.prevent="triggerLocationSearch()"
              />
              <el-button
                native-type="button"
                :disabled="isSearchingLocation"
                @click="triggerLocationSearch()"
              >
                <span aria-hidden="true">⌕</span>
                {{ isSearchingLocation ? '搜索中' : '搜索' }}
              </el-button>
            </label>
            <div
              v-if="visibleSearchResults.length > 0"
              class="investment-search-results"
              aria-label="搜索结果"
            >
              <el-button
                v-for="result in visibleSearchResults"
                :key="result.id"
                :class="[
                  'investment-search-result-item',
                  { active: selectedSearchResultId === result.id },
                ]"
                native-type="button"
                @click="selectSearchResult(result)"
                class="pc-content-button"
              >
                <strong>{{ result.title }}</strong>
                <span>{{ result.address }}</span>
              </el-button>
            </div>
          </div>
        </div>
        <CommonInput v-model="startTime" label="开始时间" input-type="time" />
        <CommonSelect
          v-model="selectedTransportMode"
          label="交通方式"
          :options="transportModeOptions"
        />
        <div class="transport-route-preview">
          <strong>自动计算</strong>
          <span v-if="isCalculatingRoute">正在计算路程与时长...</span>
          <span
            v-else-if="routePreviewDistanceText || routePreviewDurationText"
          >
            路程 {{ routePreviewDistanceText || '暂无' }}，预计时间
            {{ routePreviewDurationText || '暂无' }}
          </span>
          <span v-else>选择地点后将自动计算上一站到当前站的路程与时长</span>
        </div>
        <label class="pc-field investment-add-modal-field">
          <span>备注</span>
          <DesktopInput
            v-model="modalRemark"
            class="investment-field-control travel-modal-textarea"
            rows="3"
            type="textarea"
          ></DesktopInput>
        </label>
        <p v-if="modalError" class="travel-modal-error">{{ modalError }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar investment-add-modal-actions">
          <el-button
            class="investment-modal-button secondary"
            native-type="button"
            :disabled="isSavingItinerary"
            @click="closeModal"
          >
            取消
          </el-button>
          <el-button
            class="investment-modal-button primary"
            native-type="button"
            :disabled="isSavingItinerary"
            @click="saveModal"
            type="primary"
          >
            {{ isSavingItinerary ? '保存中...' : '保存' }}
          </el-button>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showEditModal"
      :title="editingTarget?.type === 'itinerary' ? '修改行程' : '修改费用'"
      size="compact"
      :show-close="false"
    >
      <div class="pc-form-grid investment-add-modal-form travel-add-modal-form">
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
              <label
                class="pc-field investment-search-field"
                aria-label="输入地点名称或地址"
              >
                <DesktopInput
                  v-model="searchKeyword"
                  type="search"
                  inputmode="search"
                  placeholder="输入地点名称或地址"
                  @keydown.enter.prevent="triggerLocationSearch()"
                />
                <el-button
                  native-type="button"
                  :disabled="isSearchingLocation"
                  @click="triggerLocationSearch()"
                >
                  <span aria-hidden="true">⌕</span>
                  {{ isSearchingLocation ? '搜索中' : '搜索' }}
                </el-button>
              </label>
              <div
                v-if="visibleSearchResults.length > 0"
                class="investment-search-results"
                aria-label="搜索结果"
              >
                <el-button
                  v-for="result in visibleSearchResults"
                  :key="result.id"
                  :class="[
                    'investment-search-result-item',
                    { active: selectedSearchResultId === result.id },
                  ]"
                  native-type="button"
                  @click="selectSearchResult(result)"
                  class="pc-content-button"
                >
                  <strong>{{ result.title }}</strong>
                  <span>{{ result.address }}</span>
                </el-button>
              </div>
            </div>
          </div>
          <CommonInput v-model="startTime" label="开始时间" input-type="time" />
          <CommonSelect
            v-model="selectedTransportMode"
            label="交通方式"
            :options="transportModeOptions"
          />
          <div class="transport-route-preview">
            <strong>自动计算</strong>
            <span v-if="isCalculatingRoute">正在计算路程与时长...</span>
            <span
              v-else-if="routePreviewDistanceText || routePreviewDurationText"
            >
              路程 {{ routePreviewDistanceText || '暂无' }}，预计时间
              {{ routePreviewDurationText || '暂无' }}
            </span>
            <span v-else>选择地点后将自动计算上一站到当前站的路程与时长</span>
          </div>
        </template>
        <template v-else>
          <div class="investment-search-dropdown">
            <div ref="expenseTitleSearchStackRef" class="travel-search-hitbox">
              <label
                class="pc-field investment-search-field"
                aria-label="费用名称搜索"
              >
                <DesktopInput
                  v-model="expenseTitle"
                  type="search"
                  inputmode="search"
                  placeholder="搜索当天行程作为费用名称"
                  @focus="openExpenseTitleResults()"
                  @input="openExpenseTitleResults()"
                />
                <el-button
                  native-type="button"
                  @click="openExpenseTitleResults()"
                >
                  <span aria-hidden="true">⌕</span>
                  选择
                </el-button>
              </label>
              <div
                v-if="showExpenseTitleResults && expenseTitleOptions.length > 0"
                class="investment-search-results"
                aria-label="费用名称候选"
              >
                <el-button
                  v-for="option in expenseTitleOptions"
                  :key="option.id"
                  class="pc-content-button investment-search-result-item"
                  native-type="button"
                  @click="selectExpenseTitleOption(option)"
                >
                  <strong>{{ option.title }}</strong>
                  <span>{{ option.meta }}</span>
                </el-button>
              </div>
            </div>
          </div>
          <CommonInput
            v-model="expenseAmount"
            label="费用金额"
            placeholder="请输入金额"
            input-type="number"
            input-mode="decimal"
          />
        </template>
        <label class="pc-field investment-add-modal-field">
          <span>备注</span>
          <DesktopInput
            v-model="modalRemark"
            class="investment-field-control travel-modal-textarea"
            rows="3"
            type="textarea"
          ></DesktopInput>
        </label>
        <p v-if="editModalError" class="travel-modal-error">
          {{ editModalError }}
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar investment-add-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSavingItinerary || isSavingExpense"
            @click="closeEditItemModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            :disabled="isSavingItinerary || isSavingExpense"
            @click="saveEditModal"
          >
            {{ isSavingItinerary || isSavingExpense ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showDeleteModal"
      title="删除内容"
      size="compact"
      :show-close="!isDeletingItem"
    >
      <div class="travel-delete-content">
        <p>
          确定删除
          {{ deletingTarget?.type === 'itinerary' ? '这条行程' : '这条费用' }}
          吗？
        </p>
        <span>删除后无法恢复。</span>
      </div>
      <template #footer>
        <div class="pc-toolbar investment-add-modal-actions">
          <el-button
            class="investment-modal-button secondary"
            native-type="button"
            :disabled="isDeletingItem"
            @click="closeDeleteItemModal()"
          >
            取消
          </el-button>
          <el-button
            class="investment-modal-button primary"
            native-type="button"
            :disabled="isDeletingItem"
            @click="confirmDeleteItem"
            type="primary"
          >
            {{ isDeletingItem ? '删除中...' : '确认删除' }}
          </el-button>
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
