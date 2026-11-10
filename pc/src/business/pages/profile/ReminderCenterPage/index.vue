<script setup lang="ts">
import { formatPercentageText } from '@/business/utils/percentage'

import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'

import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSwitch from '@/business/components/common/CommonSwitch/index.vue'

import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import {
  getReminderSettings,
  saveReminderSettings,
  type Reminder,
  type ReminderSettings,
  type ReminderType,
} from '@/business/api/modules/reminder'
import { getErrorMessage } from '@/business/api/request'
import { useReminderCenter } from '@/business/composables/useReminderCenter'

const router = useRouter()
const {
  items,
  unreadCount,
  loading,
  busy,
  refreshing,
  error,
  refresh,
  reload,
  act,
} = useReminderCenter()
const tab = ref('unread')
const category = ref<ReminderType | 'all'>('all')
const labels: Record<ReminderType, string> = {
  todo: '待办',
  anniversary: '纪念日',
  renewal: '固定支出',
  liability: '还款',
  investment: '定投',
  budget: '预算',
  reimbursement: '报销',
  term_deposit: '定期存款',
}
const urgencyLabels = {
  failed: '扣款失败',
  overdue: '已到期',
  today: '今天',
  upcoming: '即将到期',
  warning: '已用 80.0%',
  exceeded: '已用 100.0%',
}
const snoozedCount = computed(
  () => items.value.filter((item) => item.snoozedUntil).length,
)
const visibleItems = computed(() =>
  items.value.filter((item) => {
    if (category.value !== 'all' && item.type !== category.value) return false
    if (tab.value === 'snoozed') return !!item.snoozedUntil
    if (item.snoozedUntil) return false
    return tab.value === 'all' || !item.read
  }),
)
const showSettings = ref(false)
const settings = ref<ReminderSettings | null>(null)
const settingsLoading = ref(false)
const settingsSaving = ref(false)
const settingsError = ref('')
const feedback = ref('')
const snoozeTarget = ref<Reminder | null>(null)
const settingOptions = [
  { key: 'todoEnabled', label: '待办事项' },
  { key: 'anniversaryEnabled', label: '纪念日' },
  { key: 'renewalEnabled', label: '固定支出与扣款失败' },
  { key: 'liabilityEnabled', label: '还款账单' },
  { key: 'investmentEnabled', label: '定投计划' },
  { key: 'budgetEnabled', label: '预算预警与超支' },
  { key: 'reimbursementEnabled', label: '报销待到账' },
  { key: 'termDepositEnabled', label: '定期存款到期' },
] as const

function formatDate(value: string, allDay = false) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: '2-digit',
    day: '2-digit',
    year: 'numeric',
    ...(allDay ? {} : { hour: '2-digit', minute: '2-digit' }),
    hour12: false,
  }).format(new Date(value))
}

async function openItem(item: Reminder) {
  if (busy.value) return
  if (!item.read && !(await act([item.key], 'read'))) return
  await router.push(item.targetPath)
}

async function readAll() {
  // 仅处理用户当前看到的未读集合；请求期间新出现的提醒保留未读。
  const keys = visibleItems.value
    .filter((item) => !item.read)
    .map((item) => item.key)
  if (await act(keys, 'read')) feedback.value = '当前列表已全部标为已读'
}

async function snooze(minutes: number) {
  if (!snoozeTarget.value) return
  if (await act([snoozeTarget.value.key], 'snooze', minutes)) {
    snoozeTarget.value = null
    feedback.value = '已安排稍后提醒'
  }
}

async function openSettings() {
  showSettings.value = true
  settings.value = null
  settingsError.value = ''
  settingsLoading.value = true
  try {
    settings.value = await getReminderSettings()
  } catch (cause) {
    settingsError.value = getErrorMessage(cause, '设置加载失败')
  } finally {
    settingsLoading.value = false
  }
}

async function saveSettings() {
  if (!settings.value || settingsSaving.value) return
  settingsSaving.value = true
  settingsError.value = ''
  try {
    await saveReminderSettings(settings.value)
    showSettings.value = false
    feedback.value = '提醒设置已保存'
    await reload()
  } catch (cause) {
    settingsError.value = getErrorMessage(cause, '设置保存失败')
  } finally {
    settingsSaving.value = false
  }
}

type ReminderRow = (typeof visibleItems.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="统一提醒">
      <template #right>
        <el-button :disabled="busy" @click="openSettings">提醒设置</el-button>
        <el-button :loading="refreshing" @click="refresh">刷新</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="refresh">重试</el-button>
    </el-alert>
    <p v-if="feedback" role="status">{{ feedback }}</p>
    <DesktopTable :rows="visibleItems" :loading="loading" title="提醒列表">
      <template #toolbar>
        <SegmentedControl
          v-model="tab"
          :options="[
            { label: `未读 ${unreadCount}`, value: 'unread' },
            { label: '全部', value: 'all' },
            { label: `稍后 ${snoozedCount}`, value: 'snoozed' },
          ]"
          label="提醒状态"
        />
        <div class="pc-toolbar reminder-toolbar">
          <span class="reminder-type-select">
            <el-select
              v-model="category"
              aria-label="提醒类型"
              style="width: 200px"
            >
              <el-option value="all" label="全部类型" />
              <el-option
                v-for="(label, key) in labels"
                :key="key"
                :value="key"
                :label="label"
              />
            </el-select>
          </span>
          <el-button
            v-if="tab !== 'snoozed'"
            native-type="button"
            :disabled="busy || !visibleItems.some((item) => !item.read)"
            @click="readAll"
          >
            全部已读
          </el-button>
        </div>
      </template>
      <!-- @vue-generic {ReminderRow} -->
      <el-table-column label="类型" min-width="150">
        <template #default="{ row }: { row: ReminderRow }">
          {{ labels[row.type] }}
        </template>
      </el-table-column>
      <!-- @vue-generic {ReminderRow} -->
      <el-table-column label="提醒" min-width="360">
        <template #default="{ row }: { row: ReminderRow }">
          <el-button
            link
            type="primary"
            @click="openItem(row)"
            :disabled="busy"
          >
            {{ row.title }}
          </el-button>
          <p class="desktop-note">
            {{ formatPercentageText(row.description) }}
          </p>
        </template>
      </el-table-column>
      <!-- @vue-generic {ReminderRow} -->
      <el-table-column label="时间" min-width="180">
        <template #default="{ row }: { row: ReminderRow }">
          {{ formatDate(row.dueAt, row.allDay) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {ReminderRow} -->
      <el-table-column label="紧急程度" min-width="150">
        <template #default="{ row }: { row: ReminderRow }">
          {{ urgencyLabels[row.urgency] }}
        </template>
      </el-table-column>
      <!-- @vue-generic {ReminderRow} -->
      <el-table-column label="状态" min-width="150">
        <template #default="{ row }: { row: ReminderRow }">
          {{ row.read ? '已读' : '未读' }}
        </template>
      </el-table-column>
      <!-- @vue-generic {ReminderRow} -->
      <el-table-column fixed="right" label="操作" width="210">
        <template #default="{ row }: { row: ReminderRow }">
          <el-button
            v-if="row.snoozedUntil"
            link
            type="primary"
            :disabled="busy"
            @click="act([row.key], 'resume')"
          >
            恢复提醒
          </el-button>
          <template v-else>
            <el-button
              link
              type="primary"
              :disabled="busy"
              @click="act([row.key], row.read ? 'unread' : 'read')"
            >
              {{ row.read ? '标为未读' : '标为已读' }}
            </el-button>
            <el-button link :disabled="busy" @click="snoozeTarget = row">
              稍后提醒
            </el-button>
          </template>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonModal
      v-model="showSettings"
      title="提醒设置"
      :close-on-overlay="!settingsSaving"
      :show-close="!settingsSaving"
    >
      <CommonLoading v-if="settingsLoading" />
      <p v-if="settingsError" class="reminder-error" role="alert">
        {{ settingsError }}
      </p>
      <CommonButton
        v-if="!settings && !settingsLoading"
        variant="secondary"
        @click="openSettings"
      >
        重新加载
      </CommonButton>
      <div v-if="settings" class="reminder-settings">
        <label class="pc-field reminder-days">
          提前天数
          <DesktopInput
            v-model.number="settings.advanceDays"
            type="number"
            min="0"
            max="30"
            step="1"
            aria-label="提前提醒天数"
          />
        </label>
        <p class="reminder-note">
          0 表示从当天开始提醒，最多提前 30 天。逾期待办和未处理账单会持续保留。
        </p>
        <CommonSwitch
          v-for="option in settingOptions"
          :key="option.key"
          v-model="settings[option.key]"
          :label="option.label"
        />
      </div>
      <template #footer>
        <div class="pc-toolbar reminder-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="settingsSaving"
            @click="showSettings = false"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="
              !settings ||
              settingsSaving ||
              !Number.isInteger(settings.advanceDays) ||
              settings.advanceDays < 0 ||
              settings.advanceDays > 30
            "
            @click="saveSettings"
          >
            {{ settingsSaving ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      :model-value="!!snoozeTarget"
      title="稍后提醒"
      :show-close="!busy"
      :close-on-overlay="!busy"
      @update:model-value="
        (value) => {
          if (!value) snoozeTarget = null
        }
      "
    >
      <div class="reminder-snooze-content">
        <p>{{ snoozeTarget?.title }}</p>
        <p v-if="error" class="reminder-error" role="alert">{{ error }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar reminder-modal-actions">
          <CommonButton
            :disabled="busy"
            variant="secondary"
            @click="snooze(60)"
          >
            1 小时后
          </CommonButton>
          <CommonButton
            :disabled="busy"
            variant="primary"
            @click="snooze(1440)"
          >
            24 小时后
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
