<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  createTravelPlanDay,
  createTravelPlanExpense,
  createTravelPlanItinerary,
  deleteTravelPlanExpense,
  deleteTravelPlanItinerary,
  getTravelPlan,
  type TravelExpenseType,
  type TravelItineraryType,
  type TravelPlanDetail,
  updateTravelPlanExpense,
  updateTravelPlanItinerary,
} from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

type DetailTab = 'overview' | 'route' | 'expense'
type ModalType = 'transport' | 'scenic' | 'dining' | 'accommodation'
type TravelTone = 'blue' | 'green' | 'orange' | 'violet'
type TravelMapPoint = {
  index: number
  title: string
  longitude: number
  latitude: number
}

type TravelSearchResult = {
  id: string
  title: string
  address: string
  longitude?: number | null
  latitude?: number | null
}

type ExpenseTitleOption = {
  id: string
  title: string
  meta: string
}

type RouteDisplayDay = {
  dayIndex: number
  itineraries: TravelPlanDetail['days'][number]['itineraries']
  expenses: TravelPlanDetail['days'][number]['expenses']
}

type TransportMode = 'driving' | 'walking' | 'riding'

type AMapInstance = {
  clearMap: () => void
  add: (overlays: unknown[]) => void
  setFitView: (overlays?: unknown[], immediately?: boolean, avoid?: number[]) => void
  setZoomAndCenter: (zoom: number, center: [number, number]) => void
  destroy: () => void
}

type AMapConstructor = {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => AMapInstance
  Marker: new (options: Record<string, unknown>) => unknown
  Polyline: new (options: Record<string, unknown>) => unknown
  Pixel: new (x: number, y: number) => unknown
}

type EditTarget =
  | {
      type: 'itinerary'
      item: TravelPlanDetail['days'][number]['itineraries'][number]
      dayId: number
      dayIndex: number
    }
  | {
      type: 'expense'
      item: TravelPlanDetail['days'][number]['expenses'][number]
      dayId: number
      dayIndex: number
      isNew?: boolean
    }

declare global {
  interface Window {
    AMap?: AMapConstructor
  }
}

let amapScriptPromise: Promise<AMapConstructor> | null = null
let searchDebounceTimer: number | null = null
let searchRequestSequence = 0
let routeMapContainer: HTMLDivElement | null = null
let overviewMapContainer: HTMLDivElement | null = null

const route = useRoute()
const router = useRouter()
const currentPlanId = computed(() => {
  const raw = route.params.planId
  return typeof raw === 'string' ? Number(raw) : NaN
})

const activeTab = ref<DetailTab>('overview')
const activeDayIndex = ref(1)
const isEditingCurrentPage = ref(false)
const showAddModal = ref(false)
const showEditModal = ref(false)
const showDeleteModal = ref(false)
const selectedModalType = ref<ModalType>('scenic')
const selectedTransportMode = ref<TransportMode>('driving')
const searchKeyword = ref('')
const selectedSearchResultId = ref('')
const selectedSearchResult = ref<TravelSearchResult | null>(null)
const searchResults = ref<TravelSearchResult[]>([])
const startTime = ref('')
const expenseTitle = ref('')
const expenseAmount = ref('')
const modalRemark = ref('')
const editingTarget = ref<EditTarget | null>(null)
const deletingTarget = ref<EditTarget | null>(null)
const searchStackRef = ref<HTMLDivElement | null>(null)
const expenseTitleSearchStackRef = ref<HTMLDivElement | null>(null)
const overviewMapRef = ref<HTMLDivElement | null>(null)
const routeMapRef = ref<HTMLDivElement | null>(null)
const overviewMapMessage = ref('')
const routeMapMessage = ref('')
const detail = ref<TravelPlanDetail | null>(null)
const isLoading = ref(false)
const isSearchingLocation = ref(false)
const isCalculatingRoute = ref(false)
const isSavingItinerary = ref(false)
const isSavingExpense = ref(false)
const isDeletingItem = ref(false)
const modalError = ref('')
const editModalError = ref('')
const loadError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const amapWebJsKey = String(import.meta.env.VITE_AMAP_WEB_JS_KEY ?? '').trim()
const amapWebServiceKey = String(import.meta.env.VITE_AMAP_WEB_SERVICE_KEY ?? import.meta.env.VITE_AMAP_WEB_JS_KEY ?? '').trim()
let overviewMapInstance: AMapInstance | null = null
let routeMapInstance: AMapInstance | null = null

const itineraryTypeOptions: CommonSelectOption[] = [
  { label: '交通', value: 'transport' },
  { label: '景区', value: 'scenic' },
  { label: '餐饮', value: 'dining' },
  { label: '住宿', value: 'accommodation' },
]

const transportModeOptions: CommonSelectOption[] = [
  { label: '驾车', value: 'driving' },
  { label: '步行', value: 'walking' },
  { label: '骑车', value: 'riding' },
]

const expenseTypeOptions: CommonSelectOption[] = [
  { label: '交通', value: 'transport' },
  { label: '景区', value: 'scenic' },
  { label: '餐饮', value: 'dining' },
  { label: '住宿', value: 'accommodation' },
  { label: '其他', value: 'other' },
]

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
  if (searchDebounceTimer) {
    window.clearTimeout(searchDebounceTimer)
    searchDebounceTimer = null
  }
  overviewMapInstance?.destroy()
  overviewMapInstance = null
  overviewMapContainer = null
  routeMapInstance?.destroy()
  routeMapInstance = null
  routeMapContainer = null
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

const summaryHeading = computed(() => detail.value?.name || '旅行详情')

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

const displayedCurrentDayItineraries = computed(() => {
  const day = currentRouteDay.value
  const currentItems = day.itineraries ?? []
  const previousDay = orderedDays.value.find((item) => item.dayIndex === day.dayIndex - 1)
  const previousLastItem = previousDay?.itineraries?.[previousDay.itineraries.length - 1]

  if (!previousLastItem || previousLastItem.type !== 'accommodation') {
    return currentItems
  }

  const alreadyStartsWithSameAccommodation = currentItems.some((item, index) => {
    if (index !== 0) {
      return false
    }
    return item.id === previousLastItem.id
      || (
        item.type === 'accommodation'
        && item.title === previousLastItem.title
        && item.poiId === previousLastItem.poiId
      )
  })

  if (alreadyStartsWithSameAccommodation) {
    return currentItems
  }

  return [
    {
      ...previousLastItem,
      id: -previousLastItem.id,
      remark: previousLastItem.remark || previousLastItem.address || previousLastItem.poiName || '延续前一晚住宿',
    },
    ...currentItems,
  ]
})

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

const overviewMapPoints = computed<TravelMapPoint[]>(() => {
  let pointIndex = 0
  const seen = new Set<string>()

  return orderedDays.value.flatMap((day) =>
    (day.itineraries ?? [])
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
    entries: buildOverviewEntries(day.itineraries ?? []),
  })),
)

const overviewExpenses = computed(() => detail.value?.expenses ?? [])

const hasOverviewExpenseData = computed(() => {
  if (!detail.value) {
    return false
  }

  const expenseCount = detail.value.overview?.expenseCount ?? detail.value.expenses.length
  return expenseCount > 0
})

const hasOverviewExpenseRows = computed(() => overviewExpenses.value.length > 0)

const visibleSearchResults = computed(() => searchResults.value)
const showExpenseTitleResults = ref(false)
const expenseTitleOptions = computed<ExpenseTitleOption[]>(() => {
  const items = displayedCurrentDayItineraries.value
  const keyword = expenseTitle.value.trim().toLowerCase()
  const unique = new Map<string, ExpenseTitleOption>()

  items.forEach((item) => {
    const title = item.title.trim()
    if (!title) {
      return
    }
    const meta = `${formatStartTime(item.startTime)} · ${expenseTypeLabel(item.type)}`
    if (keyword && !title.toLowerCase().includes(keyword) && !meta.toLowerCase().includes(keyword)) {
      return
    }
    if (!unique.has(title)) {
      unique.set(title, {
        id: `${item.id}-${title}`,
        title,
        meta,
      })
    }
  })

  return [...unique.values()]
})
const routePreviewDistanceText = ref('')
const routePreviewDurationText = ref('')
const routeDistanceMeters = ref<number | null>(null)
const routeDurationSeconds = ref<number | null>(null)

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
}

function toggleCurrentPageEditor() {
  isEditingCurrentPage.value = !isEditingCurrentPage.value
}

function openEditItemModal(target: EditTarget) {
  editingTarget.value = target
  editModalError.value = ''
  showEditModal.value = true
  if (target.type === 'itinerary') {
    selectedModalType.value = target.item.type as ModalType
    selectedSearchResult.value = {
      id: target.item.poiId || target.item.title,
      title: target.item.poiName || target.item.title,
      address: target.item.address || '',
      longitude: target.item.longitude ?? null,
      latitude: target.item.latitude ?? null,
    }
    selectedSearchResultId.value = selectedSearchResult.value.id
    searchKeyword.value = selectedSearchResult.value.title
    startTime.value = target.item.startTime ? target.item.startTime.slice(0, 5) : ''
    selectedTransportMode.value = (target.item.transportMode as TransportMode | null) || 'driving'
    routeDistanceMeters.value = target.item.distanceMeters ?? null
    routeDurationSeconds.value = target.item.durationSeconds ?? null
    routePreviewDistanceText.value = formatDistance(target.item.distanceMeters)
    routePreviewDurationText.value = formatDuration(target.item.durationSeconds)
    modalRemark.value = target.item.remark || ''
    searchResults.value = []
    void calculateTransportRoute()
  } else {
    selectedModalType.value = target.item.type as ModalType
    expenseTitle.value = target.item.title || ''
    showExpenseTitleResults.value = false
    expenseAmount.value = String(target.item.amount ?? '')
    modalRemark.value = target.item.remark || ''
  }
}

function openDeleteItemModal(target: EditTarget) {
  deletingTarget.value = target
  showDeleteModal.value = true
}

function closeEditItemModal(force = false) {
  if ((isSavingItinerary.value || isSavingExpense.value) && !force) {
    return
  }
  showEditModal.value = false
  editingTarget.value = null
  resetEditForm()
}

function closeDeleteItemModal(force = false) {
  if (isDeletingItem.value && !force) {
    return
  }
  showDeleteModal.value = false
  deletingTarget.value = null
}

function resetEditForm() {
  selectedModalType.value = 'scenic'
  searchKeyword.value = ''
  selectedSearchResultId.value = ''
  selectedSearchResult.value = null
  searchResults.value = []
  startTime.value = ''
  selectedTransportMode.value = 'driving'
  routeDistanceMeters.value = null
  routeDurationSeconds.value = null
  routePreviewDistanceText.value = ''
  routePreviewDurationText.value = ''
  expenseTitle.value = ''
  expenseAmount.value = ''
  modalRemark.value = ''
  showExpenseTitleResults.value = false
  editModalError.value = ''
}

async function loadDetail(silent = false) {
  const currentUser = getStoredCurrentUser()
  const planId = Number(route.params.planId ?? 1)
  const preferredDayIndex = activeDayIndex.value
  if (!currentUser || Number.isNaN(planId)) {
    loadError.value = '请先登录后查看旅行详情'
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
      loadError.value = error instanceof Error ? error.message : '旅行详情加载失败'
    }
  } finally {
    if (!silent) {
      isLoading.value = false
    }
  }
}

watch(
  () => [activeTab.value, detail.value?.id, overviewMapPoints.value.length] as const,
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

watch(searchKeyword, (value) => {
  const keyword = value.trim()

  if (!keyword) {
    selectedSearchResult.value = null
    selectedSearchResultId.value = ''
    searchResults.value = []
    if (searchDebounceTimer) {
      window.clearTimeout(searchDebounceTimer)
      searchDebounceTimer = null
    }
    return
  }

  if (selectedSearchResult.value && selectedSearchResult.value.title !== keyword) {
    selectedSearchResult.value = null
    selectedSearchResultId.value = ''
  }

  searchResults.value = []
})

watch(selectedTransportMode, () => {
  if (selectedSearchResult.value) {
    void calculateTransportRoute()
  }
})

function toggleAddModal() {
  if (!showAddModal.value) {
    resetModalForm()
  }
  showAddModal.value = !showAddModal.value
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

function typeTone(type: TravelTone | ModalType | string) {
  switch (type) {
    case 'blue':
    case 'transport':
      return 'blue'
    case 'green':
    case 'scenic':
      return 'green'
    case 'orange':
    case 'dining':
      return 'orange'
    default:
      return 'violet'
  }
}

function expenseAmountText(amount: number) {
  return `¥ ${amount}`
}

function itineraryEditTarget(dayId: number, dayIndex: number, item: TravelPlanDetail['days'][number]['itineraries'][number]): EditTarget {
  return { type: 'itinerary', dayId, dayIndex, item }
}

function expenseEditTarget(dayId: number, dayIndex: number, item: TravelPlanDetail['days'][number]['expenses'][number]): EditTarget {
  return { type: 'expense', dayId, dayIndex, item }
}

function openCreateExpenseModal() {
  const day = currentDay.value
  if (!day?.id) {
    showFeedback('当前日期还没有生成行程日，暂时无法新增费用', 'error')
    return
  }

  openEditItemModal({
    type: 'expense',
    dayId: day.id,
    dayIndex: day.dayIndex,
    isNew: true,
    item: {
      id: 0,
      travelPlanId: detail.value?.id ?? 0,
      travelPlanDayId: day.id,
      type: 'other',
      title: '',
      amount: 0,
      remark: '',
      sortOrder: (day.expenses?.length ?? 0) + 1,
      createdAt: '',
      updatedAt: '',
    },
  })
}

function closeModal() {
  modalError.value = ''
  searchResults.value = []
  showExpenseTitleResults.value = false
  showAddModal.value = false
}

async function saveModal() {
  const currentUser = getStoredCurrentUser()
  const keyword = searchKeyword.value.trim()

  if (!currentUser) {
    modalError.value = '请先登录后再保存行程'
    return
  }

  if (!keyword) {
    modalError.value = '请输入并选择行程地点'
    return
  }

  if (!selectedSearchResult.value) {
    modalError.value = '请从高德搜索结果中选择一个地点'
    return
  }

  isSavingItinerary.value = true
  modalError.value = ''

  try {
    let dayId = currentDay.value?.id
    if (!dayId) {
      const createdDay = await createTravelPlanDay(currentPlanId.value, {
        userId: currentUser.id,
        dayIndex: activeDayIndex.value,
        title: `第 ${activeDayIndex.value} 天`,
        travelDate: (
          routeDateItems.value.find((item) => item.dayIndex === activeDayIndex.value)?.day?.travelDate
          ?? addDays(detail.value?.startDate, Math.max(activeDayIndex.value - 1, 0))
        ) || undefined,
        sortOrder: activeDayIndex.value,
      })
      dayId = createdDay.id
    }

    await createTravelPlanItinerary(dayId, {
      userId: currentUser.id,
      type: selectedModalType.value,
      title: selectedSearchResult.value.title,
      poiName: selectedSearchResult.value.title,
      poiId: selectedSearchResult.value.id,
      address: selectedSearchResult.value.address,
      longitude: selectedSearchResult.value.longitude ?? undefined,
      latitude: selectedSearchResult.value.latitude ?? undefined,
      startTime: normalizeTimeValue(startTime.value),
      transportMode: selectedTransportMode.value,
      distanceMeters: routeDistanceMeters.value,
      durationSeconds: routeDurationSeconds.value,
      remark: modalRemark.value.trim() || undefined,
      sortOrder: ((currentDay.value?.itineraries?.length ?? 0) + 1),
    })

    await loadDetail(true)
    closeModal()
    resetModalForm()
  } catch (error) {
    modalError.value = error instanceof Error ? error.message : '保存行程失败'
  } finally {
    isSavingItinerary.value = false
  }
}

async function saveEditModal() {
  const currentUser = getStoredCurrentUser()
  const target = editingTarget.value
  if (!currentUser || !target) {
    editModalError.value = '请先登录后再保存'
    return
  }

  if (target.type === 'itinerary') {
    if (!selectedSearchResult.value) {
      editModalError.value = '请先选择行程地点'
      return
    }

    isSavingItinerary.value = true
    editModalError.value = ''
    try {
      await updateTravelPlanItinerary(target.item.id, {
        userId: currentUser.id,
        type: selectedModalType.value as TravelItineraryType,
        title: selectedSearchResult.value.title,
        poiName: selectedSearchResult.value.title,
        poiId: selectedSearchResult.value.id,
        address: selectedSearchResult.value.address,
        longitude: selectedSearchResult.value.longitude ?? undefined,
        latitude: selectedSearchResult.value.latitude ?? undefined,
        startTime: normalizeTimeValue(startTime.value),
        transportMode: selectedTransportMode.value,
        distanceMeters: routeDistanceMeters.value,
        durationSeconds: routeDurationSeconds.value,
        remark: modalRemark.value.trim() || undefined,
        sortOrder: target.item.sortOrder,
      })
      closeEditItemModal(true)
      showFeedback('行程已更新', 'success')
      await loadDetail(true)
    } catch (error) {
      editModalError.value = error instanceof Error ? error.message : '行程保存失败'
    } finally {
      isSavingItinerary.value = false
    }
    return
  }

  const amount = Number(expenseAmount.value)
  if (!Number.isFinite(amount) || amount < 0) {
    editModalError.value = '请输入正确的费用金额'
    return
  }

  isSavingExpense.value = true
  editModalError.value = ''
  try {
    if (!expenseTitle.value.trim()) {
      editModalError.value = '请输入费用名称'
      return
    }

    const payload = {
      userId: currentUser.id,
      type: selectedModalType.value as TravelExpenseType,
      title: expenseTitle.value.trim(),
      amount,
      remark: modalRemark.value.trim() || undefined,
      sortOrder: target.item.sortOrder,
    }

    if (target.isNew) {
      await createTravelPlanExpense(target.dayId, payload)
    } else {
      await updateTravelPlanExpense(target.item.id, payload)
    }
    closeEditItemModal(true)
    showFeedback(target.isNew ? '费用已新增' : '费用已更新', 'success')
    await loadDetail(true)
  } catch (error) {
    editModalError.value = error instanceof Error ? error.message : '费用保存失败'
  } finally {
    isSavingExpense.value = false
  }
}

async function confirmDeleteItem() {
  const currentUser = getStoredCurrentUser()
  const target = deletingTarget.value
  if (!currentUser || !target) {
    return
  }

  isDeletingItem.value = true
  try {
    if (target.type === 'itinerary') {
      await deleteTravelPlanItinerary(target.item.id, currentUser.id)
      showFeedback('行程已删除', 'success')
    } else {
      await deleteTravelPlanExpense(target.item.id, currentUser.id)
      showFeedback('费用已删除', 'success')
    }
    closeDeleteItemModal(true)
    await loadDetail()
  } catch (error) {
    showFeedback(error instanceof Error ? error.message : '删除失败', 'error')
  } finally {
    isDeletingItem.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function selectSearchResult(result: TravelSearchResult) {
  selectedSearchResult.value = result
  selectedSearchResultId.value = result.id
  searchKeyword.value = result.title
  searchResults.value = []
  void calculateTransportRoute()
}

function resetModalForm() {
  selectedModalType.value = 'scenic'
  searchKeyword.value = ''
  selectedSearchResultId.value = ''
  selectedSearchResult.value = null
  searchResults.value = []
  startTime.value = ''
  selectedTransportMode.value = 'driving'
  routeDistanceMeters.value = null
  routeDurationSeconds.value = null
  routePreviewDistanceText.value = ''
  routePreviewDurationText.value = ''
  modalRemark.value = ''
  expenseTitle.value = ''
  showExpenseTitleResults.value = false
  modalError.value = ''
}

function handleDocumentPointerDown(event: PointerEvent) {
  if (!showAddModal.value && !showEditModal.value) {
    return
  }

  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  if (searchResults.value.length > 0) {
    if (searchStackRef.value?.contains(target)) {
      return
    }
    searchResults.value = []
  }

  if (showExpenseTitleResults.value) {
    if (expenseTitleSearchStackRef.value?.contains(target)) {
      return
    }
    showExpenseTitleResults.value = false
  }
}

function overviewExpenseRowAmount(item: { amount: number }) {
  return `¥ ${item.amount}`
}

function formatMetric(value: number | undefined) {
  return `¥ ${(value ?? 0).toLocaleString('zh-CN')}`
}

function formatDayTabLabel(day: TravelPlanDetail['days'][number]) {
  if (day.travelDate) {
    return formatDate(day.travelDate)
  }
  return `Day ${day.dayIndex}`
}

function formatDate(dateText?: string | null) {
  return dateText ? dateText.split('-').join('.') : ''
}

function calculateDayCount(startDate?: string | null, endDate?: string | null) {
  if (!startDate || !endDate) {
    return 0
  }

  const start = new Date(`${startDate}T00:00:00`)
  const end = new Date(`${endDate}T00:00:00`)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end < start) {
    return 0
  }

  const millisecondsPerDay = 24 * 60 * 60 * 1000
  return Math.floor((end.getTime() - start.getTime()) / millisecondsPerDay) + 1
}

function addDays(startDate?: string | null, offset = 0) {
  if (!startDate) {
    return ''
  }

  const nextDate = new Date(`${startDate}T00:00:00`)
  if (Number.isNaN(nextDate.getTime())) {
    return ''
  }

  nextDate.setDate(nextDate.getDate() + offset)
  const year = nextDate.getFullYear()
  const month = `${nextDate.getMonth() + 1}`.padStart(2, '0')
  const day = `${nextDate.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

function normalizeTimeValue(value: string) {
  return value ? `${value}:00` : undefined
}

function formatStartTime(time?: string | null) {
  if (!time) {
    return '待定'
  }
  return time.slice(0, 5)
}

function formatDistance(distanceMeters?: number | null) {
  if (!distanceMeters || distanceMeters <= 0) {
    return ''
  }
  if (distanceMeters >= 1000) {
    return `${(distanceMeters / 1000).toFixed(1)} km`
  }
  return `${distanceMeters} m`
}

function formatDuration(durationSeconds?: number | null) {
  if (!durationSeconds || durationSeconds <= 0) {
    return ''
  }
  const totalMinutes = Math.round(durationSeconds / 60)
  if (totalMinutes < 60) {
    return `${totalMinutes} 分钟`
  }
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return minutes > 0 ? `${hours} 小时 ${minutes} 分钟` : `${hours} 小时`
}

function getPreviousRoutePoint() {
  const items = currentDay.value?.itineraries ?? []
  if (editingTarget.value?.type === 'itinerary' && editingTarget.value.item.id) {
    const currentIndex = items.findIndex((item) => item.id === editingTarget.value?.item.id)
    if (currentIndex > 0) {
      return items[currentIndex - 1]
    }
    if (currentIndex === 0) {
      const previousDay = orderedDays.value.find((item) => item.dayIndex === (currentDay.value?.dayIndex ?? 0) - 1)
      const previousLastItem = previousDay?.itineraries?.[previousDay.itineraries.length - 1] ?? null
      return previousLastItem?.type === 'accommodation' ? previousLastItem : null
    }
  }

  if (items.length > 0) {
    return items[items.length - 1] ?? null
  }

  const previousDay = orderedDays.value.find((item) => item.dayIndex === (currentDay.value?.dayIndex ?? 0) - 1)
  const previousLastItem = previousDay?.itineraries?.[previousDay.itineraries.length - 1] ?? null
  return previousLastItem?.type === 'accommodation' ? previousLastItem : null
}

async function calculateTransportRoute() {
  const destination = selectedSearchResult.value
  const previous = getPreviousRoutePoint()
  const originLng = previous?.longitude
  const originLat = previous?.latitude
  const destinationLng = destination?.longitude
  const destinationLat = destination?.latitude

  if (
    typeof originLng !== 'number' || typeof originLat !== 'number'
    || typeof destinationLng !== 'number' || typeof destinationLat !== 'number'
  ) {
    routeDistanceMeters.value = null
    routeDurationSeconds.value = null
    routePreviewDistanceText.value = ''
    routePreviewDurationText.value = ''
    return
  }

  const path = selectedTransportMode.value === 'driving'
    ? 'https://restapi.amap.com/v5/direction/driving'
    : selectedTransportMode.value === 'walking'
      ? 'https://restapi.amap.com/v5/direction/walking'
      : 'https://restapi.amap.com/v5/direction/bicycling'

  const params = new URLSearchParams({
    key: amapWebServiceKey,
    origin: `${originLng},${originLat}`,
    destination: `${destinationLng},${destinationLat}`,
  })

  try {
    isCalculatingRoute.value = true
    const response = await fetch(`${path}?${params.toString()}`)
    const payload = await response.json() as {
      status?: string
      route?: {
        paths?: Array<{
          distance?: string
          duration?: string
        }>
      }
      data?: {
        paths?: Array<{
          distance?: string
          duration?: string
        }>
      }
    }

    const pathItem = payload.route?.paths?.[0] ?? payload.data?.paths?.[0]
    const distance = Number(pathItem?.distance ?? 0)
    const duration = Number(pathItem?.duration ?? 0)

    routeDistanceMeters.value = Number.isFinite(distance) && distance > 0 ? distance : null
    routeDurationSeconds.value = Number.isFinite(duration) && duration > 0 ? duration : null
    routePreviewDistanceText.value = formatDistance(routeDistanceMeters.value)
    routePreviewDurationText.value = formatDuration(routeDurationSeconds.value)
  } catch {
    routeDistanceMeters.value = null
    routeDurationSeconds.value = null
    routePreviewDistanceText.value = ''
    routePreviewDurationText.value = ''
  } finally {
    isCalculatingRoute.value = false
  }
}

function itineraryDescription(item: TravelPlanDetail['days'][number]['itineraries'][number]) {
  return item.remark || item.address || item.poiName || '待补充详细说明'
}

function expenseNote(item: TravelPlanDetail['days'][number]['expenses'][number]) {
  return expenseTypeLabel(item.type)
}

function expenseTypeLabel(type: string) {
  if (type === 'transport') {
    return '交通'
  }
  if (type === 'scenic') {
    return '景区'
  }
  if (type === 'dining') {
    return '餐饮'
  }
  if (type === 'accommodation') {
    return '住宿'
  }
  return '其他'
}

function routeCountText(itineraryCount: number) {
  return `${itineraryCount} 个点位`
}

function routeMapLastSpot() {
  const items = displayedCurrentDayItineraries.value
  return items[items.length - 1]?.title || '当天终点'
}

function overviewMapLastSpot() {
  const items = overviewMapPoints.value
  return items[items.length - 1]?.title || '旅行终点'
}

function routeTransferSummary(item: TravelPlanDetail['days'][number]['itineraries'][number]) {
  const parts = [formatDistance(item.distanceMeters), formatDuration(item.durationSeconds)].filter(Boolean)
  if (parts.length === 0) {
    return '路程待补充'
  }
  return `路程 ${parts.join(' · ')}`
}

function openExpenseTitleResults() {
  showExpenseTitleResults.value = true
}

function selectExpenseTitleOption(option: ExpenseTitleOption) {
  expenseTitle.value = option.title
  showExpenseTitleResults.value = false
}

async function loadAmapSearchResults(keyword: string) {
  if (!amapWebServiceKey) {
    searchResults.value = []
    return
  }

  const currentRequest = ++searchRequestSequence
  const query = new URLSearchParams({
    key: amapWebServiceKey,
    keywords: keyword,
    offset: '8',
    page: '1',
    extensions: 'base',
  })

  try {
    isSearchingLocation.value = true
    const response = await fetch(`https://restapi.amap.com/v3/place/text?${query.toString()}`)
    const payload = await response.json() as {
      status?: string
      info?: string
      pois?: Array<{
        id?: string
        name?: string
        address?: string
        pname?: string
        cityname?: string
        adname?: string
        location?: string
      }>
    }

    if (currentRequest !== searchRequestSequence) {
      return
    }

    if (payload.status !== '1') {
      searchResults.value = []
      return
    }

    searchResults.value = (payload.pois ?? []).map((poi) => {
      const [longitude, latitude] = parseAmapLocation(poi.location)
      const areaText = [poi.pname, poi.cityname, poi.adname].filter(Boolean).join(' ')
      return {
        id: poi.id || `${poi.name}-${poi.location || areaText}`,
        title: poi.name || keyword,
        address: poi.address || areaText || '暂无地址信息',
        longitude,
        latitude,
      }
    })
  } catch {
    if (currentRequest === searchRequestSequence) {
      searchResults.value = []
    }
  } finally {
    if (currentRequest === searchRequestSequence) {
      isSearchingLocation.value = false
    }
  }
}

function triggerLocationSearch() {
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    searchResults.value = []
    return
  }

  if (searchDebounceTimer) {
    window.clearTimeout(searchDebounceTimer)
    searchDebounceTimer = null
  }

  void loadAmapSearchResults(keyword)
}

function parseAmapLocation(location?: string | null) {
  if (!location) {
    return [null, null] as const
  }

  const [longitudeText, latitudeText] = location.split(',')
  const longitude = Number(longitudeText)
  const latitude = Number(latitudeText)

  return [
    Number.isFinite(longitude) ? longitude : null,
    Number.isFinite(latitude) ? latitude : null,
  ] as const
}

async function renderTravelMap(kind: 'overview' | 'route') {
  const mapRef = kind === 'overview' ? overviewMapRef.value : routeMapRef.value
  if (!mapRef) {
    return
  }

  const points = kind === 'overview' ? overviewMapPoints.value : currentRouteMapPoints.value
  const messageRef = kind === 'overview' ? overviewMapMessage : routeMapMessage

  if (!amapWebJsKey) {
    messageRef.value = '请先配置高德地图 Key'
    return
  }

  try {
    const AMap = await ensureAmap()
    let mapContainer = kind === 'overview' ? overviewMapContainer : routeMapContainer
    let mapInstance = kind === 'overview' ? overviewMapInstance : routeMapInstance

    if (mapContainer !== mapRef) {
      mapInstance?.destroy()
      mapInstance = null
      mapContainer = mapRef
    }

    if (!mapInstance) {
      mapInstance = new AMap.Map(mapRef, {
        viewMode: '2D',
        zoom: 11,
        center: points[0]
          ? [points[0].longitude, points[0].latitude]
          : [116.397428, 39.90923],
      })
    }

    mapInstance.clearMap()

    if (points.length === 0) {
      messageRef.value = kind === 'overview' ? '全程暂无可定位的行程点' : '当天暂无可定位的行程点'
      if (detail.value?.startDate) {
        mapInstance.setZoomAndCenter(5, [104.066301, 30.572961])
      }
      if (kind === 'overview') {
        overviewMapInstance = mapInstance
        overviewMapContainer = mapContainer
      } else {
        routeMapInstance = mapInstance
        routeMapContainer = mapContainer
      }
      return
    }

    messageRef.value = ''
    const overlays = points.map((point) => new AMap.Marker({
      position: [point.longitude, point.latitude],
      anchor: 'center',
      offset: new AMap.Pixel(0, 0),
      content: `<div class="travel-map-marker">${point.index}</div>`,
      title: point.title,
    }))

    if (points.length > 1) {
      overlays.push(
        new AMap.Polyline({
          path: points.map((point) => [point.longitude, point.latitude]),
          strokeColor: '#2563EB',
          strokeWeight: 4,
          strokeOpacity: 0.9,
          lineJoin: 'round',
          lineCap: 'round',
        }),
      )
    }

    mapInstance.add(overlays)
    mapInstance.setFitView(overlays, true, [28, 52, 28, 28])

    if (kind === 'overview') {
      overviewMapInstance = mapInstance
      overviewMapContainer = mapContainer
    } else {
      routeMapInstance = mapInstance
      routeMapContainer = mapContainer
    }
  } catch (error) {
    messageRef.value = error instanceof Error ? error.message : '高德地图加载失败'
  }
}

async function renderOverviewMap() {
  await renderTravelMap('overview')
}

async function renderRouteMap() {
  await renderTravelMap('route')
}

async function ensureAmap() {
  if (window.AMap) {
    return window.AMap
  }

  if (!amapScriptPromise) {
    amapScriptPromise = new Promise<AMapConstructor>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${amapWebJsKey}`
      script.async = true
      script.onload = () => {
        if (window.AMap) {
          resolve(window.AMap)
          return
        }
        reject(new Error('高德地图初始化失败'))
      }
      script.onerror = () => reject(new Error('高德地图脚本加载失败'))
      document.head.appendChild(script)
    })
  }

  return amapScriptPromise
}

function buildOverviewEntries(itineraries: TravelPlanDetail['days'][number]['itineraries']) {
  return itineraries.flatMap((item, index) => {
    const nextItem = itineraries[index + 1]
    const tone = typeTone(item.type)
    const entries: Array<
      | { kind: 'item'; tone: TravelTone; title: string; description: string }
      | { kind: 'transfer'; text: string }
    > = [
      {
        kind: 'item',
        tone,
        title: item.title,
        description: itineraryDescription(item),
      },
    ]

    if (nextItem) {
      entries.push({
        kind: 'transfer',
        text: routeTransferSummary(nextItem),
      })
    }

    return entries
  })
}
</script>

<template>
  <section class="travel-detail-page">
    <PageHeader :title="displayedHeaderTitle" back-to="/tools/travel-plans" back-label="返回旅行列表">
      <button class="header-action-button" type="button" @click="goToEditPage">
        {{ activeTab === 'overview' ? '修改旅行' : isEditingCurrentPage ? '完成' : '编辑' }}
      </button>
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
      <div class="summary-card">
        <h2>{{ summaryHeading }}</h2>
        <p>{{ summaryMeta }}</p>
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

      <div class="overview-day-list">
        <article v-for="day in currentDayOverviewEntries" :key="day.id" class="overview-day">
          <h3>Day {{ day.dayIndex }}</h3>

          <div v-if="day.entries.length > 0" class="overview-timeline">
            <template v-for="(entry, index) in day.entries" :key="`${day.id}-${index}`">
              <div v-if="entry.kind === 'item'" class="timeline-row">
                <div class="timeline-rail">
                  <span :class="['rail-top', { hidden: index === 0 }]"></span>
                  <span :class="['rail-dot', `tone-${entry.tone}`]"></span>
                  <span :class="['rail-bottom', { hidden: index === day.entries.length - 1 }]"></span>
                </div>

                <div :class="['timeline-card', `tone-${entry.tone}`]">
                  <h4>{{ entry.title }}</h4>
                  <p>{{ entry.description }}</p>
                </div>
              </div>

              <div v-else class="timeline-transfer-row">
                <div class="timeline-rail transfer-only">
                  <span class="rail-transfer"></span>
                </div>
                <div class="timeline-transfer-text">{{ entry.text }}</div>
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
                <strong>{{ item.title }}</strong>
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
