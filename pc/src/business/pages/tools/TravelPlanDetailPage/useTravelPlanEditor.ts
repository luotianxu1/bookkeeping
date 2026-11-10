// 行程编辑弹窗（新增/修改/删除）、高德地点搜索与路程自动计算的状态与动作。
// 从 index.vue 拆出：页面组件保留骨架渲染，这里集中管理弹窗相关的全部状态。
import {
  computed,
  onBeforeUnmount,
  ref,
  watch,
  type ComputedRef,
  type Ref,
} from 'vue'
import {
  createTravelPlanDay,
  createTravelPlanExpense,
  createTravelPlanItinerary,
  deleteTravelPlanExpense,
  deleteTravelPlanItinerary,
  type TravelExpenseType,
  type TravelItineraryType,
  type TravelPlanDetail,
  updateTravelPlanExpense,
  updateTravelPlanItinerary,
} from '@/business/api/modules/tool'
import { getApiBaseUrl } from '@/business/api/api-base-url'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'
import { parseAmapLocation } from './useTravelMaps'
import {
  addDays,
  formatDistance,
  formatDuration,
  formatStartTime,
  expenseTypeLabel,
  normalizeTimeValue,
} from './travelFormat'
import type { CommonSelectOption } from '@/business/components/common/CommonSelect/index.vue'

export type ModalType = 'transport' | 'scenic' | 'dining' | 'accommodation'

type DayEntry = TravelPlanDetail['days'][number]
type ItineraryItem = DayEntry['itineraries'][number]
type ExpenseItem = DayEntry['expenses'][number]

export type EditTarget =
  | { type: 'itinerary'; item: ItineraryItem; dayId: number; dayIndex: number }
  | {
      type: 'expense'
      item: ExpenseItem
      dayId: number
      dayIndex: number
      isNew?: boolean
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

type RouteDateItem = {
  key: number
  dayIndex: number
  label: string
  day: DayEntry | null
}

let searchDebounceTimer: number | null = null
let searchRequestSequence = 0

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

export function useTravelPlanEditor(deps: {
  detail: Ref<TravelPlanDetail | null>
  currentDay: ComputedRef<DayEntry | null>
  orderedDays: ComputedRef<DayEntry[]>
  activeDayIndex: Ref<number>
  routeDateItems: ComputedRef<RouteDateItem[]>
  currentPlanId: ComputedRef<number>
  /** 当前线路页展示的行程列表（含前晚住宿延续），供费用名称候选使用 */
  displayedDayItineraries: ComputedRef<ItineraryItem[]>
  loadDetail: (silent?: boolean) => Promise<void>
  showFeedback: (message: string, type: 'success' | 'error') => void
}) {
  const {
    detail,
    currentDay,
    orderedDays,
    activeDayIndex,
    routeDateItems,
    currentPlanId,
    displayedDayItineraries,
    loadDetail,
    showFeedback,
  } = deps

  const showAddModal = ref(false)
  const showEditModal = ref(false)
  const showDeleteModal = ref(false)
  const selectedModalType = ref<ModalType>('scenic')
  const selectedTransportMode = ref<'driving' | 'walking' | 'riding'>('driving')
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
  const isSearchingLocation = ref(false)
  const isCalculatingRoute = ref(false)
  const isSavingItinerary = ref(false)
  const isSavingExpense = ref(false)
  const isDeletingItem = ref(false)
  const modalError = ref('')
  const editModalError = ref('')
  const showExpenseTitleResults = ref(false)
  const routePreviewDistanceText = ref('')
  const routePreviewDurationText = ref('')
  const routeDistanceMeters = ref<number | null>(null)
  const routeDurationSeconds = ref<number | null>(null)

  const visibleSearchResults = computed(() => searchResults.value)

  const expenseTitleOptions = computed<ExpenseTitleOption[]>(() => {
    const items = displayedDayItineraries.value
    const keyword = expenseTitle.value.trim().toLowerCase()
    const unique = new Map<string, ExpenseTitleOption>()

    items.forEach((item) => {
      const title = item.title.trim()
      if (!title) {
        return
      }
      const meta = `${formatStartTime(item.startTime)} · ${expenseTypeLabel(item.type)}`
      if (
        keyword &&
        !title.toLowerCase().includes(keyword) &&
        !meta.toLowerCase().includes(keyword)
      ) {
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

  // 路线规划 / 地点搜索的 Key 保存在 tool-service，前端经 /tool-api 代理调用
  const amapProxyBaseUrl = `${getApiBaseUrl('tool').replace(/\/$/, '')}/api/tool/amap`

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

  function resetEditForm() {
    resetModalForm()
    expenseAmount.value = ''
    editModalError.value = ''
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
      startTime.value = target.item.startTime
        ? target.item.startTime.slice(0, 5)
        : ''
      selectedTransportMode.value =
        (target.item.transportMode as
          | 'driving'
          | 'walking'
          | 'riding'
          | null) || 'driving'
      routeDistanceMeters.value = target.item.distanceMeters ?? null
      routeDurationSeconds.value = target.item.durationSeconds ?? null
      routePreviewDistanceText.value = formatDistance(
        target.item.distanceMeters,
      )
      routePreviewDurationText.value = formatDuration(
        target.item.durationSeconds,
      )
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

  function closeModal() {
    modalError.value = ''
    searchResults.value = []
    showExpenseTitleResults.value = false
    showAddModal.value = false
  }

  function toggleAddModal() {
    if (!showAddModal.value) {
      resetModalForm()
    }
    showAddModal.value = !showAddModal.value
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
          travelDate:
            (routeDateItems.value.find(
              (item) => item.dayIndex === activeDayIndex.value,
            )?.day?.travelDate ??
              addDays(
                detail.value?.startDate,
                Math.max(activeDayIndex.value - 1, 0),
              )) ||
            undefined,
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
        sortOrder: (currentDay.value?.itineraries?.length ?? 0) + 1,
      })

      await loadDetail(true)
      closeModal()
      resetModalForm()
    } catch (error) {
      modalError.value = getErrorMessage(error, '保存行程失败')
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
        editModalError.value = getErrorMessage(error, '行程保存失败')
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
      editModalError.value = getErrorMessage(error, '费用保存失败')
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
      showFeedback(getErrorMessage(error, '删除失败'), 'error')
    } finally {
      isDeletingItem.value = false
    }
  }

  function selectSearchResult(result: TravelSearchResult) {
    selectedSearchResult.value = result
    selectedSearchResultId.value = result.id
    searchKeyword.value = result.title
    searchResults.value = []
    void calculateTransportRoute()
  }

  function getPreviousRoutePoint() {
    const items = currentDay.value?.itineraries ?? []
    if (
      editingTarget.value?.type === 'itinerary' &&
      editingTarget.value.item.id
    ) {
      const currentIndex = items.findIndex(
        (item) => item.id === editingTarget.value?.item.id,
      )
      if (currentIndex > 0) {
        return items[currentIndex - 1]
      }
      if (currentIndex === 0) {
        const previousDay = orderedDays.value.find(
          (item) => item.dayIndex === (currentDay.value?.dayIndex ?? 0) - 1,
        )
        const previousLastItem =
          previousDay?.itineraries?.[previousDay.itineraries.length - 1] ?? null
        return previousLastItem?.type === 'accommodation'
          ? previousLastItem
          : null
      }
    }

    if (items.length > 0) {
      return items[items.length - 1] ?? null
    }

    const previousDay = orderedDays.value.find(
      (item) => item.dayIndex === (currentDay.value?.dayIndex ?? 0) - 1,
    )
    const previousLastItem =
      previousDay?.itineraries?.[previousDay.itineraries.length - 1] ?? null
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
      typeof originLng !== 'number' ||
      typeof originLat !== 'number' ||
      typeof destinationLng !== 'number' ||
      typeof destinationLat !== 'number'
    ) {
      routeDistanceMeters.value = null
      routeDurationSeconds.value = null
      routePreviewDistanceText.value = ''
      routePreviewDurationText.value = ''
      return
    }

    const mode =
      selectedTransportMode.value === 'driving'
        ? 'driving'
        : selectedTransportMode.value === 'walking'
          ? 'walking'
          : 'bicycling'

    const params = new URLSearchParams({
      origin: `${originLng},${originLat}`,
      destination: `${destinationLng},${destinationLat}`,
    })

    try {
      isCalculatingRoute.value = true
      const response = await fetch(
        `${amapProxyBaseUrl}/direction?mode=${mode}&${params.toString()}`,
      )
      const payload = (await response.json()) as {
        status?: string
        errcode?: number | string
        route?: {
          paths?: Array<{
            distance?: string | number
            duration?: string | number
          }>
        }
        data?: {
          paths?: Array<{
            distance?: string | number
            duration?: string | number
          }>
        }
      }

      if (payload.status === '0' || payload.errcode) {
        throw new Error('路线计算失败')
      }

      const pathItem = payload.route?.paths?.[0] ?? payload.data?.paths?.[0]
      const distance = Number(pathItem?.distance ?? 0)
      const duration = Number(pathItem?.duration ?? 0)

      routeDistanceMeters.value =
        Number.isFinite(distance) && distance > 0 ? distance : null
      routeDurationSeconds.value =
        Number.isFinite(duration) && duration > 0 ? duration : null
      routePreviewDistanceText.value = formatDistance(routeDistanceMeters.value)
      routePreviewDurationText.value = formatDuration(
        routeDurationSeconds.value,
      )
    } catch {
      routeDistanceMeters.value = null
      routeDurationSeconds.value = null
      routePreviewDistanceText.value = ''
      routePreviewDurationText.value = ''
    } finally {
      isCalculatingRoute.value = false
    }
  }

  async function loadAmapSearchResults(keyword: string) {
    const currentRequest = ++searchRequestSequence
    const query = new URLSearchParams({
      keywords: keyword,
      offset: '8',
      page: '1',
      extensions: 'base',
    })

    try {
      isSearchingLocation.value = true
      const response = await fetch(
        `${amapProxyBaseUrl}/place/text?${query.toString()}`,
      )
      const payload = (await response.json()) as {
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
        const areaText = [poi.pname, poi.cityname, poi.adname]
          .filter(Boolean)
          .join(' ')
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

  function openExpenseTitleResults() {
    showExpenseTitleResults.value = true
  }

  function selectExpenseTitleOption(option: ExpenseTitleOption) {
    expenseTitle.value = option.title
    showExpenseTitleResults.value = false
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

    if (
      selectedSearchResult.value &&
      selectedSearchResult.value.title !== keyword
    ) {
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

  onBeforeUnmount(() => {
    if (searchDebounceTimer) {
      window.clearTimeout(searchDebounceTimer)
      searchDebounceTimer = null
    }
  })

  function itineraryEditTarget(
    dayId: number,
    dayIndex: number,
    item: ItineraryItem,
  ): EditTarget {
    return { type: 'itinerary', dayId, dayIndex, item }
  }

  function expenseEditTarget(
    dayId: number,
    dayIndex: number,
    item: ExpenseItem,
  ): EditTarget {
    return { type: 'expense', dayId, dayIndex, item }
  }

  return {
    // 状态
    showAddModal,
    showEditModal,
    showDeleteModal,
    selectedModalType,
    selectedTransportMode,
    searchKeyword,
    selectedSearchResultId,
    selectedSearchResult,
    searchResults,
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
    // 选项
    itineraryTypeOptions,
    transportModeOptions,
    expenseTypeOptions,
    // 动作
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
  }
}
