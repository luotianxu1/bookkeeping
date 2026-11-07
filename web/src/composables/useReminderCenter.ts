import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { actOnReminders, getReminders, type Reminder, type ReminderAction } from '@/api/modules/reminder'
import { getErrorMessage } from '@/api/request'
import { useVisibilityPolling } from './useVisibilityPolling'

export function useReminderCenter() {
  const items = ref<Reminder[]>([])
  const loading = ref(true)
  const busy = ref(false)
  const refreshing = ref(false)
  const error = ref('')
  let revision = 0
  let disposed = false
  const unreadCount = computed(() => items.value.filter(item => !item.read && !item.snoozedUntil).length)

  async function refresh() {
    if (busy.value || refreshing.value || disposed) return
    const currentRevision = ++revision
    refreshing.value = true
    try {
      const result = await getReminders()
      if (!disposed && currentRevision === revision) {
        items.value = result.items
        error.value = ''
      }
    } catch (cause) {
      if (!disposed && currentRevision === revision) error.value = getErrorMessage(cause, '提醒加载失败，请重试')
    } finally {
      if (!disposed && currentRevision === revision) {
        loading.value = false
        refreshing.value = false
      }
    }
  }

  async function act(keys: string[], action: ReminderAction, snoozeMinutes?: number) {
    if (busy.value || !keys.length || disposed) return false
    busy.value = true
    ++revision // 丢弃操作前在途的列表响应，防止已读状态被覆盖。
    refreshing.value = false
    let succeeded = false
    try {
      for (let offset = 0; offset < keys.length; offset += 200) {
        await actOnReminders(keys.slice(offset, offset + 200), action, snoozeMinutes)
      }
      succeeded = true
      error.value = ''
    } catch (cause) {
      error.value = getErrorMessage(cause, '操作失败，请重试')
    } finally {
      busy.value = false
    }
    // 部分批次成功时也重新读取，以服务端状态为准；保留操作失败提示。
    const actionError = error.value
    await refresh()
    if (actionError) error.value = actionError
    return succeeded
  }

  async function reload() {
    ++revision
    refreshing.value = false
    await refresh()
  }

  onMounted(() => { void refresh() })
  onBeforeUnmount(() => { disposed = true; ++revision })
  useVisibilityPolling(refresh, 60_000)
  return { items, unreadCount, loading, busy, refreshing, error, refresh, reload, act }
}
