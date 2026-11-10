<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed, onMounted, ref } from 'vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import FloatingAddButton from '@/business/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import {
  createTodoItem,
  deleteTodoItem,
  getTodoItems,
  updateTodoItem,
  updateTodoItemStatus,
  type TodoItem,
  type TodoStatus,
} from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'
import { useReminderTarget } from '@/business/composables/useReminderTarget'

type TodoTab = 'all' | 'today' | 'completed'

const tabOptions = [
  { label: '今天', value: 'today' },
  { label: '全部', value: 'all' },
  { label: '已完成', value: 'completed' },
]

const weekLabels = ['日', '一', '二', '三', '四', '五', '六']

const activeTab = ref<TodoTab>('today')
const todoItems = ref<TodoItem[]>([])
const isLoading = ref(true)
const reminderTargetId = useReminderTarget(isLoading, 'todo')
if (reminderTargetId) activeTab.value = 'all'
const isSaving = ref(false)
const isDeleting = ref(false)
const updatingTodoId = ref<number | null>(null)
const pageError = ref('')
const formError = ref('')
const showTodoModal = ref(false)
const showDeleteModal = ref(false)
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const expandedTodoId = ref<number | null>(reminderTargetId)
const editingTodo = ref<TodoItem | null>(null)
const deletingTodo = ref<TodoItem | null>(null)
const formTitle = ref('')
const formDueAt = ref(buildDefaultDateTimeLocal())
const formRemark = ref('')

const pendingItems = computed(() =>
  todoItems.value.filter((item) => !isCompleted(item)),
)
const completedCount = computed(
  () => todoItems.value.length - pendingItems.value.length,
)
const dueTodayCount = computed(
  () => pendingItems.value.filter((item) => isToday(item.dueAt)).length,
)

const visibleItems = computed(() => {
  const filtered = todoItems.value.filter((item) => {
    if (activeTab.value === 'today') {
      return isToday(item.dueAt)
    }
    if (activeTab.value === 'completed') {
      return isCompleted(item)
    }
    return true
  })

  return [...filtered].sort((left, right) => {
    if (activeTab.value !== 'completed') {
      const completionDiff =
        Number(isCompleted(left)) - Number(isCompleted(right))
      if (completionDiff !== 0) {
        return completionDiff
      }
    }

    if (isCompleted(left) && isCompleted(right)) {
      const completedDiff = getCompletedTime(right) - getCompletedTime(left)
      if (completedDiff !== 0) {
        return completedDiff
      }
    }

    const dueDiff = toTime(left.dueAt) - toTime(right.dueAt)
    if (dueDiff !== 0) {
      return dueDiff
    }

    const sortDiff = (left.sortOrder ?? 0) - (right.sortOrder ?? 0)
    if (sortDiff !== 0) {
      return sortDiff
    }

    return left.id - right.id
  })
})

const emptyMessage = computed(() => {
  if (activeTab.value === 'today') {
    return '今天还没有待办事项'
  }
  if (activeTab.value === 'completed') {
    return '还没有已完成事项'
  }
  return '暂无待办事项'
})

onMounted(() => {
  void loadTodoItems()
})

async function loadTodoItems() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看待办事项'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    todoItems.value = await getTodoItems({
      userId: currentUser.id,
      status: 'all',
    })
  } catch (error) {
    pageError.value = getErrorMessage(error, '待办事项加载失败')
  } finally {
    isLoading.value = false
  }
}

function openCreateModal() {
  editingTodo.value = null
  resetForm()
  showTodoModal.value = true
}

function openEditModal(item: TodoItem) {
  expandedTodoId.value = item.id
  editingTodo.value = item
  formTitle.value = item.title
  formDueAt.value = toDateTimeLocalValue(item.dueAt)
  formRemark.value = item.remark ?? ''
  formError.value = ''
  showTodoModal.value = true
}

function closeTodoModal(force = false) {
  if (isSaving.value && !force) {
    return
  }

  showTodoModal.value = false
  editingTodo.value = null
  resetForm()
}

function resetForm() {
  formTitle.value = ''
  formDueAt.value = buildDefaultDateTimeLocal()
  formRemark.value = ''
  formError.value = ''
}

function openDeleteModal(item: TodoItem) {
  expandedTodoId.value = item.id
  deletingTodo.value = item
  showDeleteModal.value = true
}

function closeDeleteModal(force = false) {
  if (isDeleting.value && !force) {
    return
  }

  showDeleteModal.value = false
  deletingTodo.value = null
}

async function saveTodoItem() {
  if (isSaving.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const trimmedTitle = formTitle.value.trim()
  const trimmedRemark = formRemark.value.trim()
  const isEditing = Boolean(editingTodo.value)

  if (!currentUser) {
    formError.value = '请先登录后再保存待办事项'
    return
  }
  if (!trimmedTitle) {
    formError.value = '请输入事项标题'
    return
  }
  if (!formDueAt.value) {
    formError.value = '请选择截止时间'
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    const payload = {
      userId: currentUser.id,
      title: trimmedTitle,
      dueAt: normalizeDateTimeLocal(formDueAt.value),
      remark: trimmedRemark || null,
      sortOrder: editingTodo.value?.sortOrder ?? getNextSortOrder(),
      status: editingTodo.value?.status ?? 'pending',
    }

    if (editingTodo.value) {
      await updateTodoItem(editingTodo.value.id, payload)
    } else {
      await createTodoItem(payload)
    }

    closeTodoModal(true)
    showFeedback(isEditing ? '待办事项已更新' : '待办事项已新增', 'success')
    await loadTodoItems()
  } catch (error) {
    const message = getErrorMessage(error, '待办事项保存失败')
    formError.value = message
    showFeedback(message, 'error')
  } finally {
    isSaving.value = false
  }
}

async function toggleTodoStatus(item: TodoItem) {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || updatingTodoId.value === item.id) {
    return
  }

  const nextStatus: TodoStatus = isCompleted(item) ? 'pending' : 'completed'
  updatingTodoId.value = item.id

  try {
    const updatedItem = await updateTodoItemStatus(item.id, {
      userId: currentUser.id,
      status: nextStatus,
    })

    todoItems.value = todoItems.value.map((todoItem) =>
      todoItem.id === updatedItem.id ? updatedItem : todoItem,
    )
    showFeedback(
      nextStatus === 'completed' ? '已标记为完成' : '已恢复为待办',
      'success',
    )
  } catch (error) {
    const message = getErrorMessage(error, '状态更新失败')
    showFeedback(message, 'error')
  } finally {
    updatingTodoId.value = null
  }
}

function toggleExpandedTodo(itemId: number) {
  expandedTodoId.value = expandedTodoId.value === itemId ? null : itemId
}

async function confirmDeleteTodo() {
  const currentUser = getStoredCurrentUser()
  const target = deletingTodo.value
  if (!currentUser || !target) {
    return
  }

  isDeleting.value = true

  try {
    await deleteTodoItem(target.id, currentUser.id)
    closeDeleteModal(true)
    showFeedback('待办事项已删除', 'success')
    await loadTodoItems()
  } catch (error) {
    const message = getErrorMessage(error, '删除失败')
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function getNextSortOrder() {
  return (
    todoItems.value.reduce(
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

function isCompleted(item: TodoItem) {
  return item.status === 'completed'
}

function isToday(value: string) {
  const target = new Date(value)
  const now = new Date()
  return (
    target.getFullYear() === now.getFullYear() &&
    target.getMonth() === now.getMonth() &&
    target.getDate() === now.getDate()
  )
}

function isOverdue(item: TodoItem) {
  return !isCompleted(item) && toTime(item.dueAt) < Date.now()
}

function getCompletedTime(item: TodoItem) {
  return toTime(item.completedAt ?? item.updatedAt)
}

function getStatusLabel(item: TodoItem) {
  if (isCompleted(item)) {
    return '已完成'
  }
  if (isOverdue(item)) {
    return '已逾期'
  }
  if (isToday(item.dueAt)) {
    return '今天截止'
  }
  return '待处理'
}

function toTime(value?: string | null) {
  if (!value) {
    return 0
  }

  const time = new Date(value).getTime()
  return Number.isNaN(time) ? 0 : time
}

function formatDueLabel(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '截止时间待确认'
  }

  const now = new Date()
  const tomorrow = new Date(now)
  tomorrow.setDate(now.getDate() + 1)

  const timeText = `${padNumber(date.getHours())}:${padNumber(date.getMinutes())}`
  if (sameDay(date, now)) {
    return `今天 ${timeText}`
  }
  if (sameDay(date, tomorrow)) {
    return `明天 ${timeText}`
  }

  const diffDays = Math.floor(
    (startOfDay(date).getTime() - startOfDay(now).getTime()) / 86400000,
  )
  if (diffDays > 0 && diffDays < 7) {
    return `周${weekLabels[date.getDay()]} ${timeText}`
  }
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日 ${timeText}`
  }
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${timeText}`
}

function buildDefaultDateTimeLocal() {
  const nextHour = new Date()
  nextHour.setMinutes(0, 0, 0)
  nextHour.setHours(nextHour.getHours() + 1)
  return toDateTimeLocalValue(nextHour)
}

function toDateTimeLocalValue(value: string | Date) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return (
    [
      date.getFullYear(),
      padNumber(date.getMonth() + 1),
      padNumber(date.getDate()),
    ].join('-') +
    `T${padNumber(date.getHours())}:${padNumber(date.getMinutes())}`
  )
}

function normalizeDateTimeLocal(value: string) {
  return value.length === 16 ? `${value}:00` : value
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function sameDay(left: Date, right: Date) {
  return (
    left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate()
  )
}

function padNumber(value: number) {
  return String(value).padStart(2, '0')
}

type DesktopRow = (typeof visibleItems.value)[number]
</script>
<template>
  <section class="pc-page">
    <PageHeader title="待办事项">
      <template #right>
        <el-button type="primary" @click="openCreateModal">
          新增待办事项
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
    <div class="pc-statistics">
      <el-card shadow="never">
        <small>待处理</small>
        <strong>{{ pendingItems.length }}</strong>
      </el-card>
      <el-card shadow="never">
        <small>今日截止</small>
        <strong>{{ dueTodayCount }}</strong>
      </el-card>
      <el-card shadow="never">
        <small>已完成</small>
        <strong>{{ completedCount }}</strong>
      </el-card>
    </div>
    <DesktopTable :rows="visibleItems" :loading="isLoading">
      <template #toolbar>
        <SegmentedControl
          v-model="activeTab"
          :options="tabOptions"
          label="待办筛选"
        />
      </template>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="事项"
        prop="title"
        min-width="240"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="截止时间" min-width="180">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatDueLabel(row.dueAt) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }: { row: DesktopRow }">
          <el-tag
            :type="
              isCompleted(row) ? 'success' : isOverdue(row) ? 'danger' : 'info'
            "
          >
            {{ getStatusLabel(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="备注"
        prop="remark"
        min-width="200"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button
              link
              type="primary"
              :disabled="updatingTodoId === row.id"
              @click="toggleTodoStatus(row)"
            >
              {{ isCompleted(row) ? '恢复待办' : '标记完成' }}
            </el-button>
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
      v-model="showTodoModal"
      :title="editingTodo ? '编辑待办事项' : '新增待办事项'"
      :show-close="!isSaving"
    >
      <div class="pc-form-grid todo-form">
        <p v-if="formError" class="todo-form-error">{{ formError }}</p>
        <CommonInput
          v-model="formTitle"
          label="事项标题"
          placeholder="例如：周五前确认餐厅预订"
        />
        <CommonInput
          v-model="formDueAt"
          label="截止时间"
          input-type="datetime-local"
        />
        <label class="pc-field todo-remark-field">
          <span>备注</span>
          <DesktopInput
            v-model="formRemark"
            rows="4"
            placeholder="可以记录地点、预算或需要一起准备的东西"
            type="textarea"
          ></DesktopInput>
        </label>
      </div>
      <template #footer>
        <div class="pc-toolbar todo-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSaving"
            @click="closeTodoModal()"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSaving"
            @click="saveTodoItem"
          >
            {{ isSaving ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showDeleteModal"
      title="删除待办事项"
      size="compact"
      :show-close="!isDeleting"
    >
      <div class="todo-delete-content">
        <p>确定删除“{{ deletingTodo?.title ?? '' }}”吗？</p>
        <span>删除后将无法恢复。</span>
      </div>
      <template #footer>
        <div class="pc-toolbar todo-modal-actions">
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
            @click="confirmDeleteTodo"
          >
            {{ isDeleting ? '删除中...' : '删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
