<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonEmpty from '@/components/common/CommonEmpty/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import CommonHeaderRefreshButton from '@/components/common/CommonHeaderRefreshButton/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import { getReminderSettings, saveReminderSettings, type Reminder, type ReminderSettings, type ReminderType } from '@/api/modules/reminder'
import { getErrorMessage } from '@/api/request'
import { useReminderCenter } from '@/composables/useReminderCenter'

const router = useRouter()
const { items, unreadCount, loading, busy, refreshing, error, refresh, reload, act } = useReminderCenter()
const tab = ref('unread')
const category = ref<ReminderType | 'all'>('all')
const labels: Record<ReminderType, string> = { todo: '待办', anniversary: '纪念日', renewal: '固定支出', liability: '还款', investment: '定投', budget: '预算' }
const urgencyLabels = { failed: '扣款失败', overdue: '已到期', today: '今天', upcoming: '即将到期', warning: '已用 80%', exceeded: '已用 100%' }
const snoozedCount = computed(() => items.value.filter(item => item.snoozedUntil).length)
const visibleItems = computed(() => items.value.filter(item => {
  if (category.value !== 'all' && item.type !== category.value) return false
  if (tab.value === 'snoozed') return !!item.snoozedUntil
  if (item.snoozedUntil) return false
  return tab.value === 'all' || !item.read
}))
const showSettings = ref(false)
const settings = ref<ReminderSettings | null>(null)
const settingsLoading = ref(false)
const settingsSaving = ref(false)
const settingsError = ref('')
const feedback = ref('')
const snoozeTarget = ref<Reminder | null>(null)
const settingOptions = [
  { key: 'todoEnabled', label: '待办事项' }, { key: 'anniversaryEnabled', label: '纪念日' },
  { key: 'renewalEnabled', label: '固定支出与扣款失败' }, { key: 'liabilityEnabled', label: '还款账单' },
  { key: 'investmentEnabled', label: '定投计划' },
  { key: 'budgetEnabled', label: '预算预警与超支' },
] as const

function formatDate(value: string, allDay = false) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', year: 'numeric',
    ...(allDay ? {} : { hour: '2-digit', minute: '2-digit' }), hour12: false,
  }).format(new Date(value))
}

async function openItem(item: Reminder) {
  if (busy.value) return
  if (!item.read && !await act([item.key], 'read')) return
  await router.push(item.targetPath)
}

async function readAll() {
  // 仅处理用户当前看到的未读集合；请求期间新出现的提醒保留未读。
  const keys = visibleItems.value.filter(item => !item.read).map(item => item.key)
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
  try { settings.value = await getReminderSettings() }
  catch (cause) { settingsError.value = getErrorMessage(cause, '设置加载失败') }
  finally { settingsLoading.value = false }
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
  } catch (cause) { settingsError.value = getErrorMessage(cause, '设置保存失败') }
  finally { settingsSaving.value = false }
}
</script>

<template>
  <section class="reminder-page" aria-label="统一提醒">
    <PageHeader title="统一提醒" back-to="/profile" prefer-back-to>
      <template #right>
        <div class="reminder-header-actions">
          <CommonHeaderRefreshButton label="刷新提醒" :loading="refreshing" @click="refresh" />
          <CommonHeaderActionButton label="提醒设置" :disabled="busy" @click="openSettings">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 15.2A3.2 3.2 0 1 0 12 8.8A3.2 3.2 0 0 0 12 15.2Z" stroke="currentColor" stroke-width="1.8" />
              <path d="M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 1 1 16.96 19.71L16.9 19.65A1.65 1.65 0 0 0 15.08 19.32A1.65 1.65 0 0 0 14.08 20.83V21A2 2 0 1 1 10.08 21V20.91A1.65 1.65 0 0 0 9 19.4A1.65 1.65 0 0 0 7.18 19.73L7.12 19.79A2 2 0 1 1 4.29 16.96L4.35 16.9A1.65 1.65 0 0 0 4.68 15.08A1.65 1.65 0 0 0 3.17 14.08H3A2 2 0 1 1 3 10.08H3.09A1.65 1.65 0 0 0 4.6 9A1.65 1.65 0 0 0 4.27 7.18L4.21 7.12A2 2 0 1 1 7.04 4.29L7.1 4.35A1.65 1.65 0 0 0 8.92 4.68H9A1.65 1.65 0 0 0 10 3.17V3A2 2 0 1 1 14 3V3.09A1.65 1.65 0 0 0 15 4.6A1.65 1.65 0 0 0 16.82 4.27L16.88 4.21A2 2 0 1 1 19.71 7.04L19.65 7.1A1.65 1.65 0 0 0 19.32 8.92V9A1.65 1.65 0 0 0 20.83 10H21A2 2 0 1 1 21 14H20.91A1.65 1.65 0 0 0 19.4 15Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </CommonHeaderActionButton>
        </div>
      </template>
    </PageHeader>
    <SegmentedControl v-model="tab" :options="[
      { label: `未读 ${unreadCount}`, value: 'unread' },
      { label: '全部', value: 'all' },
      { label: `稍后 ${snoozedCount}`, value: 'snoozed' },
    ]" label="提醒状态" />
    <div class="reminder-toolbar">
      <span class="reminder-type-select">
        <select v-model="category" aria-label="提醒类型">
          <option value="all">全部类型</option>
          <option v-for="(label, key) in labels" :key="key" :value="key">{{ label }}</option>
        </select>
      </span>
      <button v-if="tab !== 'snoozed'" type="button" :disabled="busy || !visibleItems.some(item => !item.read)" @click="readAll">全部已读</button>
    </div>
    <p v-if="error" class="reminder-error" role="alert">{{ error }} <button type="button" :disabled="busy" @click="refresh">重试</button></p>
    <p v-if="feedback" class="reminder-note" role="status">{{ feedback }}</p>
    <CommonLoading v-if="loading" text="正在整理提醒..." />
    <section v-else class="reminder-list" aria-label="提醒列表">
      <article v-for="item in visibleItems" :key="item.key" :class="['reminder-card', { 'is-read': item.read }]">
        <div class="reminder-card-heading">
          <span>{{ labels[item.type] }}<i v-if="!item.read" aria-label="未读" /></span>
          <span :class="['reminder-status', item.urgency]">{{ urgencyLabels[item.urgency] }}</span>
        </div>
        <button class="reminder-open" type="button" :disabled="busy" @click="openItem(item)">
          <h2>{{ item.title }}</h2>
          <p>{{ item.description }}</p>
          <span class="reminder-card-meta">
            <time :datetime="item.dueAt">{{ formatDate(item.dueAt, item.allDay) }}</time>
            <span class="reminder-link">查看详情 →</span>
          </span>
        </button>
        <p v-if="item.snoozedUntil" class="reminder-note">{{ formatDate(item.snoozedUntil) }} 再次提醒</p>
        <footer>
          <button v-if="item.snoozedUntil" type="button" :disabled="busy" @click="act([item.key], 'resume')">恢复提醒</button>
          <template v-else>
            <button type="button" :disabled="busy" @click="act([item.key], item.read ? 'unread' : 'read')">{{ item.read ? '标为未读' : '标为已读' }}</button>
            <button type="button" :disabled="busy" @click="snoozeTarget = item">稍后提醒</button>
          </template>
        </footer>
      </article>
      <CommonEmpty v-if="!visibleItems.length && !error" :title="tab === 'unread' ? '暂时没有未读提醒' : '暂无提醒'"
        description="符合提醒设置的待办、纪念日和账单会显示在这里。" />
    </section>

    <CommonModal v-model="showSettings" title="提醒设置" :close-on-overlay="!settingsSaving" :show-close="!settingsSaving">
      <CommonLoading v-if="settingsLoading" />
      <p v-if="settingsError" class="reminder-error" role="alert">{{ settingsError }}</p>
      <CommonButton v-if="!settings && !settingsLoading" variant="secondary" @click="openSettings">重新加载</CommonButton>
      <div v-if="settings" class="reminder-settings">
        <label class="reminder-days">提前天数
          <input v-model.number="settings.advanceDays" type="number" min="0" max="30" step="1" aria-label="提前提醒天数" />
        </label>
        <p class="reminder-note">0 表示从当天开始提醒，最多提前 30 天。逾期待办和未处理账单会持续保留。</p>
        <CommonSwitch v-for="option in settingOptions" :key="option.key" v-model="settings[option.key]" :label="option.label" />
      </div>
      <template #footer>
        <div class="reminder-modal-actions">
          <CommonButton variant="secondary" :disabled="settingsSaving" @click="showSettings = false">取消</CommonButton>
          <CommonButton variant="primary" :disabled="!settings || settingsSaving || !Number.isInteger(settings.advanceDays) || settings.advanceDays < 0 || settings.advanceDays > 30" @click="saveSettings">
            {{ settingsSaving ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal :model-value="!!snoozeTarget" title="稍后提醒" :show-close="!busy" :close-on-overlay="!busy" @update:model-value="value => { if (!value) snoozeTarget = null }">
      <div class="reminder-snooze-content">
        <p>{{ snoozeTarget?.title }}</p>
        <p v-if="error" class="reminder-error" role="alert">{{ error }}</p>
      </div>
      <template #footer>
        <div class="reminder-modal-actions">
          <CommonButton :disabled="busy" variant="secondary" @click="snooze(60)">1 小时后</CommonButton>
          <CommonButton :disabled="busy" variant="primary" @click="snooze(1440)">24 小时后</CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
