<script setup lang="ts">
import MetricStrip from '@/business/components/MetricStrip.vue'
import DesktopRecordList from '@/business/components/DesktopRecordList.vue'

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
  <section class="desktop-workspace stagger-in">
    <PageHeader title="任务管理" />
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
    <DesktopRecordList
      :rows="tasks"
      :loading="isLoading"
      title="今日计划任务"
      search-placeholder="搜索任务"
      searchable
      empty-title="今天没有计划任务"
      empty-hint="任务会在计划时间自动执行"
    >
      <template #default="{ row }: { row: TaskRow }">
        <article class="record-card">
          <div class="record-card-head">
            <div class="record-card-title">
              <span>{{ row.taskLabel }}</span>
            </div>
            <div class="record-card-aside">
              <el-tag
                size="small"
                :type="
                  row.status === 'success'
                    ? 'success'
                    : row.status === 'pending'
                      ? 'info'
                      : 'danger'
                "
              >
                {{ row.statusLabel }}
              </el-tag>
            </div>
          </div>
          <dl class="record-card-body">
            <div>
              <dt>计划</dt>
              <dd>{{ row.scheduleLabel }}</dd>
            </div>
            <div>
              <dt>开始</dt>
              <dd>{{ row.startedAt ? formatTime(row.startedAt) : '—' }}</dd>
            </div>
            <div>
              <dt>完成</dt>
              <dd>{{ row.finishedAt ? formatTime(row.finishedAt) : '—' }}</dd>
            </div>
          </dl>
          <p v-if="detailText(row)" class="record-card-note">
            {{ detailText(row) }}
          </p>
          <div class="record-card-foot">
            <el-button
              link
              type="primary"
              :disabled="!row.executable || isExecutingTask(row)"
              :loading="isExecutingTask(row)"
              @click="executeTask(row)"
            >
              执行
            </el-button>
          </div>
        </article>
      </template>
    </DesktopRecordList>
    <CommonFeedback
      v-model="showFeedback"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
