<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import SavingsGoalCard from '@/components/finance/SavingsGoalCard/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonEmpty from '@/components/common/CommonEmpty/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import { getSavingsGoals, getSavingsGoalEntries, type SavingsGoal, type SavingsGoalEntry } from '@/api/modules/finance'
import { useAsyncSection } from '@/composables/useAsyncSection'

const route = useRoute()
const goalId = computed(() => Number(route.params.id))
const goal = ref<SavingsGoal | null>(null)
const rows = ref<SavingsGoalEntry[]>([])
const cursor = ref<number | null>(null)
const nextCursor = ref<number | null>(null)
const history = useAsyncSection(async signal => {
  const id = goalId.value, beforeId = cursor.value
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('无效的储蓄目标')
  const [goals, page] = await Promise.all([
    goal.value ? Promise.resolve([goal.value]) : getSavingsGoals(signal),
    getSavingsGoalEntries(id, beforeId, signal),
  ])
  const currentGoal = goals.find(item => item.id === id)
  if (!currentGoal) throw new Error('储蓄目标不存在或已删除')
  return { goal: currentGoal, page, beforeId }
}, result => {
  goal.value = result.goal
  rows.value = result.beforeId === null ? result.page.entries : [...rows.value, ...result.page.entries]
  nextCursor.value = result.page.nextBeforeId
}, '存取记录加载失败')

function refresh() {
  history.reset()
  goal.value = null
  rows.value = []
  cursor.value = null
  nextCursor.value = null
  void history.run()
}
function loadMore() {
  if (nextCursor.value === null || history.loading.value) return
  cursor.value = nextCursor.value
  void history.run()
}
function money(value: number) {
  const currency = goal.value?.currencyCode ?? 'CNY'
  try { return new Intl.NumberFormat('zh-CN', { style: 'currency', currency, minimumFractionDigits: 2 }).format(value) }
  catch { return `${currency} ${Number(value).toFixed(2)}` }
}
watch(() => route.params.id, refresh, { immediate: true })
</script>

<template>
  <section class="savings-history-page" aria-label="存取记录">
    <PageHeader title="存取记录" back-to="/finance/savings-goals" back-label="返回储蓄目标" prefer-back-to>
      <template #right>
        <CommonHeaderActionButton label="刷新存取记录" :disabled="history.loading.value" @click="refresh">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20 7v5h-5M4 17v-5h5M5.5 7a7 7 0 0 1 12-2L20 8M4 16l2.5 3a7 7 0 0 0 12-2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </CommonHeaderActionButton>
      </template>
    </PageHeader>
    <div class="history-content" :aria-busy="history.loading.value">
      <SavingsGoalCard v-if="goal" :goal="goal" />
      <CommonLoading v-if="history.loading.value && !history.ready.value" />
      <p v-if="history.error.value" class="error" role="alert">
        {{ history.error.value }} <button :disabled="history.loading.value" @click="history.run">重试</button>
      </p>
      <CommonEmpty v-if="history.ready.value && !rows.length" title="暂无存取记录" />
      <article v-for="entry in rows" :key="entry.id" class="entry-row">
        <div class="entry-summary">
          <strong>{{ entry.type === 'deposit' ? '存入' : '取出' }}</strong>
          <AmountText :tone="entry.type === 'deposit' ? 'positive' : 'negative'" :value="`${entry.type === 'deposit' ? '+' : '−'}${money(entry.amount)}`" />
        </div>
        <p class="hint">{{ entry.occurredOn }} · {{ entry.transactionId ? '关联记账' : '手动分配' }}</p>
        <p v-if="entry.remark" class="remark">{{ entry.remark }}</p>
      </article>
      <CommonButton v-if="nextCursor !== null" variant="secondary" :disabled="history.loading.value" @click="loadMore">
        {{ history.loading.value ? '加载中…' : '加载更多' }}
      </CommonButton>
    </div>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
