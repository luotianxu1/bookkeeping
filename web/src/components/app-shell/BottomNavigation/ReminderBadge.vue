<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getReminders } from '@/api/modules/reminder'
import { useVisibilityPolling } from '@/composables/useVisibilityPolling'

const count = ref<number | null>(null)
let disposed = false
let refreshing = false
async function refresh() {
  if (refreshing || disposed) return
  refreshing = true
  try {
    const result = await getReminders()
    if (!disposed) count.value = Number.isFinite(result.unreadCount) ? result.unreadCount : null
  } catch { if (!disposed) count.value = null }
  finally { refreshing = false }
}
onMounted(() => { void refresh() })
onBeforeUnmount(() => { disposed = true })
useVisibilityPolling(refresh, 60_000)
</script>

<template>
  <span v-if="count && count > 0" class="reminder-badge" :aria-label="`${count} 条未读提醒`">{{ count > 99 ? '99+' : count }}</span>
</template>

<style scoped>
.reminder-badge { position: absolute; top: 0; left: calc(50% + 8px); min-width: 16px; padding: 1px 4px; border-radius: 10px; background: var(--color-danger); color: white; font-size: 10px; line-height: 14px; text-align: center; }
</style>
