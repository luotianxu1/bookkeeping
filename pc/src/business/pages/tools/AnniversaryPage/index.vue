<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed, onMounted, ref } from 'vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import FloatingAddButton from '@/business/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import {
  createAnniversary,
  deleteAnniversary,
  getAnniversaries,
  updateAnniversary,
  type Anniversary,
} from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'
import { useReminderTarget } from '@/business/composables/useReminderTarget'

type AnniversaryTab = 'month' | 'all'

type DisplayAnniversary = Anniversary & {
  badgeLabel: string
  badgeTone: 'today' | 'upcoming' | 'expired'
  daysValue: number
  isExpiredThisYear: boolean
  monthDayLabel: string
}

const tabOptions = [
  { label: '本月', value: 'month' },
  { label: '全部', value: 'all' },
]

const activeTab = ref<AnniversaryTab>('month')
const anniversaries = ref<Anniversary[]>([])
const isLoading = ref(true)
const reminderTargetId = useReminderTarget(isLoading, 'anniversary')
if (reminderTargetId) activeTab.value = 'all'
const isSaving = ref(false)
const isDeleting = ref(false)
const pageError = ref('')
const formError = ref('')
const showAnniversaryModal = ref(false)
const showDeleteModal = ref(false)
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const expandedAnniversaryId = ref<number | null>(reminderTargetId)
const editingAnniversary = ref<Anniversary | null>(null)
const deletingAnniversary = ref<Anniversary | null>(null)
const formTitle = ref('')
const formDate = ref(buildTodayDate())
const formRemark = ref('')

const displayAnniversaries = computed<DisplayAnniversary[]>(() =>
  anniversaries.value
    .map((item) => {
      const anniversaryDate = parseDate(item.anniversaryDate)
      const today = getToday()
      const occurrenceThisYear = buildOccurrence(
        anniversaryDate,
        today.getFullYear(),
      )
      const nextOccurrence =
        occurrenceThisYear >= today
          ? occurrenceThisYear
          : buildOccurrence(anniversaryDate, today.getFullYear() + 1)
      const daysUntilNext = diffDays(today, nextOccurrence)
      const daysPassed = diffDays(occurrenceThisYear, today)
      const isExpiredThisYear = occurrenceThisYear < today

      let badgeTone: DisplayAnniversary['badgeTone'] = 'upcoming'
      let badgeLabel = `还有 ${daysUntilNext} 天`
      let daysValue = daysUntilNext

      if (sameDate(occurrenceThisYear, today)) {
        badgeTone = 'today'
        badgeLabel = '就是今天'
        daysValue = 0
      } else if (isExpiredThisYear) {
        badgeTone = 'expired'
        badgeLabel = `已过 ${daysPassed} 天`
        daysValue = daysPassed
      }

      return {
        ...item,
        badgeLabel,
        badgeTone,
        daysValue,
        isExpiredThisYear,
        monthDayLabel: formatMonthDay(anniversaryDate),
      }
    })
    .sort((left, right) =>
      compareAnniversaryOrder(left, right, activeTab.value),
    ),
)

const upcomingCount = computed(
  () =>
    displayAnniversaries.value.filter(
      (item) => !item.isExpiredThisYear || item.daysValue === 0,
    ).length,
)
const monthCount = computed(
  () =>
    displayAnniversaries.value.filter((item) =>
      isCurrentMonth(item.anniversaryDate),
    ).length,
)

const visibleItems = computed(() =>
  displayAnniversaries.value.filter((item) => {
    if (activeTab.value === 'month') {
      return isCurrentMonth(item.anniversaryDate)
    }
    return true
  }),
)

const emptyMessage = computed(() => {
  if (activeTab.value === 'month') {
    return '本月还没有纪念日'
  }
  return '还没有记录纪念日'
})

onMounted(() => {
  void loadAnniversaries()
})

async function loadAnniversaries() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看纪念日'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    anniversaries.value = await getAnniversaries({
      userId: currentUser.id,
      scope: 'all',
    })
  } catch (error) {
    pageError.value = getErrorMessage(error, '纪念日加载失败')
  } finally {
    isLoading.value = false
  }
}

function openCreateModal() {
  editingAnniversary.value = null
  resetForm()
  showAnniversaryModal.value = true
}

function openEditModal(item: Anniversary) {
  expandedAnniversaryId.value = item.id
  editingAnniversary.value = item
  formTitle.value = item.title
  formDate.value = normalizeDateValue(item.anniversaryDate)
  formRemark.value = item.remark ?? ''
  formError.value = ''
  showAnniversaryModal.value = true
}

function closeAnniversaryModal(force = false) {
  if (isSaving.value && !force) {
    return
  }

  showAnniversaryModal.value = false
  editingAnniversary.value = null
  resetForm()
}

function resetForm() {
  formTitle.value = ''
  formDate.value = buildTodayDate()
  formRemark.value = ''
  formError.value = ''
}

function toggleExpandedAnniversary(itemId: number) {
  expandedAnniversaryId.value =
    expandedAnniversaryId.value === itemId ? null : itemId
}

function openDeleteModal(item: Anniversary) {
  expandedAnniversaryId.value = item.id
  deletingAnniversary.value = item
  showDeleteModal.value = true
}

function closeDeleteModal(force = false) {
  if (isDeleting.value && !force) {
    return
  }

  showDeleteModal.value = false
  deletingAnniversary.value = null
}

async function saveAnniversary() {
  if (isSaving.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const trimmedTitle = formTitle.value.trim()
  const trimmedRemark = formRemark.value.trim()
  const isEditing = Boolean(editingAnniversary.value)

  if (!currentUser) {
    formError.value = '请先登录后再保存纪念日'
    return
  }
  if (!trimmedTitle) {
    formError.value = '请输入纪念日名称'
    return
  }
  if (!formDate.value) {
    formError.value = '请选择纪念日期'
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      title: trimmedTitle,
      anniversaryDate: normalizeDateValue(formDate.value),
      remark: trimmedRemark || null,
      sortOrder: editingAnniversary.value?.sortOrder ?? getNextSortOrder(),
    }

    if (editingAnniversary.value) {
      await updateAnniversary(editingAnniversary.value.id, payload)
    } else {
      await createAnniversary(payload)
    }

    closeAnniversaryModal(true)
    showFeedback(isEditing ? '纪念日已更新' : '纪念日已新增', 'success')
    await loadAnniversaries()
  } catch (error) {
    const message = getErrorMessage(error, '纪念日保存失败')
    formError.value = message
    showFeedback(message, 'error')
  } finally {
    isSaving.value = false
  }
}

async function confirmDeleteAnniversary() {
  const currentUser = getStoredCurrentUser()
  const anniversary = deletingAnniversary.value

  if (!currentUser || !anniversary) {
    return
  }

  isDeleting.value = true

  try {
    await deleteAnniversary(anniversary.id, currentUser.id)
    closeDeleteModal(true)
    showFeedback('纪念日已删除', 'success')
    await loadAnniversaries()
  } catch (error) {
    const message = getErrorMessage(error, '删除失败')
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function getNextSortOrder() {
  return (
    anniversaries.value.reduce(
      (maxOrder, item) => Math.max(maxOrder, item.sortOrder ?? 0),
      0,
    ) + 10
  )
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function formatCreatedHint(dateText: string) {
  const date = parseDate(dateText)
  return `纪念于 ${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function isCurrentMonth(dateText: string) {
  const date = parseDate(dateText)
  return date.getMonth() === getToday().getMonth()
}

function compareAnniversaryOrder(
  left: DisplayAnniversary,
  right: DisplayAnniversary,
  tab: AnniversaryTab,
) {
  if (tab === 'month') {
    const monthDiff =
      getOccurrenceTime(left.anniversaryDate) -
      getOccurrenceTime(right.anniversaryDate)
    if (monthDiff !== 0) {
      return monthDiff
    }
  } else {
    const nextDiff =
      getNextOccurrenceTime(left.anniversaryDate) -
      getNextOccurrenceTime(right.anniversaryDate)
    if (nextDiff !== 0) {
      return nextDiff
    }
  }

  const sortDiff = (left.sortOrder ?? 0) - (right.sortOrder ?? 0)
  if (sortDiff !== 0) {
    return sortDiff
  }

  return left.id - right.id
}

function getOccurrenceTime(dateText: string) {
  const date = parseDate(dateText)
  return buildOccurrence(date, getToday().getFullYear()).getTime()
}

function getNextOccurrenceTime(dateText: string) {
  const date = parseDate(dateText)
  const today = getToday()
  const occurrenceThisYear = buildOccurrence(date, today.getFullYear())
  return (
    occurrenceThisYear >= today
      ? occurrenceThisYear
      : buildOccurrence(date, today.getFullYear() + 1)
  ).getTime()
}

function buildTodayDate() {
  const today = getToday()
  return `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`
}

function normalizeDateValue(value: string) {
  return value.slice(0, 10)
}

function parseDate(value: string) {
  return new Date(`${normalizeDateValue(value)}T00:00:00`)
}

function buildOccurrence(source: Date, year: number) {
  const month = source.getMonth()
  const day = source.getDate()
  const lastDay = new Date(year, month + 1, 0).getDate()
  return new Date(year, month, Math.min(day, lastDay))
}

function diffDays(start: Date, end: Date) {
  const milliseconds = end.getTime() - start.getTime()
  return Math.round(milliseconds / 86400000)
}

function sameDate(left: Date, right: Date) {
  return (
    left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate()
  )
}

function getToday() {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

function formatMonthDay(date: Date) {
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

type DesktopRow = (typeof visibleItems.value)[number]
</script>
<template>
  <section class="pc-page">
    <PageHeader title="纪念日">
      <template #right>
        <el-button type="primary" @click="openCreateModal">
          新增纪念日
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
    <DesktopTable :rows="visibleItems" :loading="isLoading">
      <template #toolbar>
        <SegmentedControl
          v-model="activeTab"
          :options="tabOptions"
          label="纪念日筛选"
        />
      </template>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="纪念日"
        prop="title"
        min-width="220"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="日期"
        prop="anniversaryDate"
        min-width="160"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="距离" min-width="140">
        <template #default="{ row }: { row: DesktopRow }">
          <el-tag>{{ row.badgeLabel }}</el-tag>
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="备注"
        prop="remark"
        min-width="240"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button link type="primary" @click="openEditModal(row)">
              编辑
            </el-button>
            <el-button link type="danger" @click="openDeleteModal(row)">
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal
      v-model="showAnniversaryModal"
      :title="editingAnniversary ? '编辑纪念日' : '新增纪念日'"
      :show-close="!isSaving"
    >
      <div class="pc-form-grid anniversary-form">
        <p v-if="formError" class="anniversary-form-error">{{ formError }}</p>
        <CommonInput
          v-model="formTitle"
          label="纪念日名称"
          placeholder="例如：恋爱纪念日"
        />
        <CommonInput v-model="formDate" label="日期" input-type="date" />
        <label class="pc-field anniversary-remark-field">
          <span>备注</span>
          <DesktopInput
            v-model="formRemark"
            rows="4"
            placeholder="可以记录想一起做什么，或者提前准备的礼物"
            type="textarea"
          ></DesktopInput>
        </label>
      </div>
      <template #footer>
        <div class="pc-toolbar anniversary-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSaving"
            @click="closeAnniversaryModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSaving"
            @click="saveAnniversary"
          >
            {{ isSaving ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showDeleteModal"
      title="删除纪念日"
      size="compact"
      :show-close="!isDeleting"
    >
      <div class="anniversary-delete-content">
        <p>确定删除“{{ deletingAnniversary?.title ?? '' }}”吗？</p>
        <span>删除后将无法恢复。</span>
      </div>
      <template #footer>
        <div class="pc-toolbar anniversary-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isDeleting"
            @click="confirmDeleteAnniversary"
          >
            {{ isDeleting ? '删除中...' : '删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
