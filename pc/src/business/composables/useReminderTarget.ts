import { nextTick, watch, type Ref } from 'vue'
import { useRoute } from 'vue-router'

/** 来源页加载后定位提醒对应记录；目标被删除时保留正常列表。 */
export function useReminderTarget(loading: Ref<boolean>, prefix: string) {
  const route = useRoute()
  const raw = route.query.reminderId
  const targetId =
    typeof raw === 'string' && /^\d+$/.test(raw) ? Number(raw) : null
  watch(loading, async (value) => {
    if (value || !targetId) return
    await nextTick()
    document
      .getElementById(`${prefix}-${targetId}`)
      ?.scrollIntoView({ block: 'center' })
  })
  return targetId
}
