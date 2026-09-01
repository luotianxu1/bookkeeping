<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  executeTodayScheduledTask,
  getTodayScheduledTasks,
  type ScheduledTask,
} from '@/api/modules/finance'

const tasks = ref<ScheduledTask[]>([])
const isLoading = ref(false)
const isRefreshing = ref(false)
const executingTaskKey = ref('')
const pageError = ref('')
const showFeedback = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const summary = computed(() => {
  const completed = tasks.value.filter((task) => task.status === 'success').length
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
    pageError.value = error instanceof Error ? error.message : '任务状态加载失败'
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
    feedbackMessage.value = error instanceof Error ? error.message : '任务执行失败'
    feedbackType.value = 'error'
    showFeedback.value = true
  } finally {
    executingTaskKey.value = ''
  }
}
</script>

<template>
  <section class="task-management-page" aria-label="任务管理">
    <CommonFeedback v-model="showFeedback" :message="feedbackMessage" :type="feedbackType" />
    <PageHeader title="任务管理" back-to="/profile" back-label="返回我的" :prefer-back-to="true">
      <template #right>
        <CommonHeaderRefreshButton label="刷新任务状态" :loading="isRefreshing" @click="loadTasks(true)" />
      </template>
    </PageHeader>

    <section class="task-summary" aria-label="今日任务摘要">
      <div>
        <span>今日计划任务</span>
        <strong>{{ tasks.length }}</strong>
      </div>
      <div>
        <span>已完成</span>
        <strong class="success">{{ summary.completed }}</strong>
      </div>
      <div>
        <span>待执行</span>
        <strong :class="{ danger: summary.pending > 0 }">{{ summary.pending }}</strong>
      </div>
    </section>

    <p v-if="pageError" class="task-message task-message--error">{{ pageError }}</p>
    <CommonLoading v-else-if="isLoading" text="正在加载今日任务..." />

    <section v-else class="task-list" aria-label="今日任务列表">
      <article v-for="task in tasks" :key="taskKey(task)" class="task-card">
        <div class="task-card-top">
          <div>
            <h2>{{ task.taskLabel }}</h2>
            <p>{{ task.scheduleLabel }}</p>
          </div>
          <span :class="['task-status', `task-status--${task.status}`]">{{ task.statusLabel }}</span>
        </div>
        <p v-if="detailText(task)" :class="['task-detail', { 'task-detail--error': task.errorMessage }]">
          {{ detailText(task) }}
        </p>
        <footer class="task-card-footer">
          <span v-if="task.startedAt">开始 {{ formatTime(task.startedAt) }}</span>
          <span v-if="task.finishedAt">完成 {{ formatTime(task.finishedAt) }}</span>
          <CommonButton
            class="task-execute-button"
            size="sm"
            :disabled="!task.executable || isExecutingTask(task)"
            @click="executeTask(task)"
          >
            {{ isExecutingTask(task) ? '执行中...' : '执行' }}
          </CommonButton>
        </footer>
      </article>

      <p v-if="tasks.length === 0" class="task-message">暂无今日计划任务</p>
    </section>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
