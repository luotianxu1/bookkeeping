<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onMounted, ref } from 'vue'

import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  executeTodayScheduledTask,
  getTodayScheduledTasks,
  type ScheduledTask,
} from '@/business/api/modules/finance'
import { getErrorMessage } from '@/business/api/request'

const tasks = ref<ScheduledTask[]>([])
const isLoading = ref(true)
const isRefreshing = ref(false)
const executingTaskKey = ref('')
const pageError = ref('')
const showFeedback = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const summary = computed(() => {
  const completed = tasks.value.filter(
    (task) => task.status === 'success',
  ).length
  const pending = tasks.value.filter((task) => task.status === 'pending').length
  return { completed, pending }
})

onMounted(() => {
  void loadTasks()
})

async function loadTasks(forceRefresh = false) {
  if (forceRefresh) {
    isRefreshing.value = true
  } else {
    isLoading.value = tasks.value.length === 0
  }
  pageError.value = ''

  try {
    tasks.value = await getTodayScheduledTasks()
  } catch (error) {
    pageError.value = getErrorMessage(error, '任务状态加载失败')
  } finally {
    isLoading.value = false
    isRefreshing.value = false
  }
}

function formatTime(value?: string | null) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function detailText(task: ScheduledTask) {
  if (task.errorMessage) {
    return task.errorMessage
  }
  if (task.resultMessage) {
    return task.resultMessage
  }
  if (task.status === 'pending') {
    return '等待计划时间执行'
  }
  return ''
}

function taskKey(task: ScheduledTask) {
  return `${task.taskName}-${task.triggerName}`
}

function isExecutingTask(task: ScheduledTask) {
  return executingTaskKey.value === taskKey(task)
}

async function executeTask(task: ScheduledTask) {
  if (!task.executable || isExecutingTask(task)) {
    return
  }

  executingTaskKey.value = taskKey(task)
  try {
    await executeTodayScheduledTask(task.taskName, task.triggerName)
    feedbackMessage.value = `${task.taskLabel}执行完成`
    feedbackType.value = 'success'
    showFeedback.value = true
    await loadTasks(true)
  } catch (error) {
    feedbackMessage.value = getErrorMessage(error, '任务执行失败')
    feedbackType.value = 'error'
    showFeedback.value = true
  } finally {
    executingTaskKey.value = ''
  }
}

type TaskRow = (typeof tasks.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="任务管理">
      <template #right>
        <el-button :loading="isRefreshing" @click="loadTasks(true)">
          刷新状态
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <MetricStrip
      :items="[
        { label: '今日计划任务', value: tasks.length },
        { label: '已完成', value: summary.completed },
        { label: '待执行', value: summary.pending },
      ]"
    />
    <DesktopTable
      :rows="tasks"
      :loading="isLoading"
      title="今日计划任务"
      search-placeholder="搜索任务"
      searchable
    >
      <el-table-column
        prop="taskLabel"
        label="任务"
        min-width="220"
        sortable="custom"
        show-overflow-tooltip
      />
      <el-table-column
        prop="scheduleLabel"
        label="计划"
        min-width="180"
        sortable="custom"
        show-overflow-tooltip
      />
      <el-table-column
        prop="statusLabel"
        label="状态"
        min-width="140"
        sortable="custom"
        show-overflow-tooltip
      />
      <!-- @vue-generic {TaskRow} -->
      <el-table-column label="结果" min-width="260">
        <template #default="{ row }: { row: TaskRow }">
          {{ detailText(row) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {TaskRow} -->
      <el-table-column label="开始时间" min-width="170">
        <template #default="{ row }: { row: TaskRow }">
          {{ row.startedAt ? formatTime(row.startedAt) : '—' }}
        </template>
      </el-table-column>
      <!-- @vue-generic {TaskRow} -->
      <el-table-column label="完成时间" min-width="170">
        <template #default="{ row }: { row: TaskRow }">
          {{ row.finishedAt ? formatTime(row.finishedAt) : '—' }}
        </template>
      </el-table-column>
      <!-- @vue-generic {TaskRow} -->
      <el-table-column fixed="right" label="操作" width="150">
        <template #default="{ row }: { row: TaskRow }">
          <el-button
            link
            type="primary"
            :disabled="!row.executable || isExecutingTask(row)"
            :loading="isExecutingTask(row)"
            @click="executeTask(row)"
          >
            执行
          </el-button>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonFeedback
      v-model="showFeedback"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
