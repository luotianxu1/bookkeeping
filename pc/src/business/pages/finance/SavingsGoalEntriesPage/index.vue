<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import DesktopTable from '@/business/components/DesktopTable.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import AmountText from '@/business/components/common/AmountText/index.vue'
import { getSavingsGoals, getSavingsGoalEntries, type SavingsGoal, type SavingsGoalEntry } from '@/business/api/modules/finance'
import { useAsyncSection } from '@/business/composables/useAsyncSection'
import { formatPercentage } from '@/business/utils/percentage'

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
function money(value: number | null) {
  if (value === null) return '--'
  const currency = goal.value?.currencyCode ?? 'CNY'
  try { return new Intl.NumberFormat('zh-CN', { style: 'currency', currency, minimumFractionDigits: 2 }).format(value) }
  catch { return `${currency} ${Number(value).toFixed(2)}` }
}
watch(() => route.params.id, refresh, { immediate: true })
</script>

<template>
  <section class="desktop-workspace desktop-list-workspace" aria-label="存取记录">
    <PageHeader title="存取记录" back-to="/finance/savings-goals" back-label="返回储蓄目标">
      <template #right>
        <el-button :loading="history.loading.value" @click="refresh">刷新存取记录</el-button>
      </template>
    </PageHeader>
    <el-card v-if="goal" class="savings-goal-card" shadow="never">
      <div class="goal-title">
        <h2>{{ goal.name }}</h2>
        <el-tag :type="goal.status === 'overdue' ? 'warning' : goal.status === 'completed' ? 'success' : 'primary'">
          {{ { saving: '储蓄中', completed: '已达成', overdue: '已逾期' }[goal.status] }}
        </el-tag>
      </div>
      <RouterLink class="account-link" :to="`/finance/accounts/cash/${goal.accountId}`">
        {{ goal.accountName || '关联账户' }} · {{ goal.currencyCode }} <span>查看账户 ›</span>
      </RouterLink>
      <p v-if="goal.accountAvailable" class="goal-hint">
        账户可分配 {{ money(goal.accountAvailableAmount) }} · 目标合计预留 {{ money(goal.accountReservedAmount) }}
      </p>
      <div class="goal-amount">
        <span>已存 </span><AmountText tag="strong" :value="money(goal.currentAmount)" /><span> / {{ money(goal.targetAmount) }}</span>
      </div>
      <el-progress :percentage="Number(goal.progressPercent)" :format="formatPercentage" />
      <p class="goal-hint">{{ goal.status === 'completed' ? '已达到目标金额' : `还差 ${money(goal.remainingAmount)}` }}</p>
      <el-alert v-if="!goal.accountAvailable" title="关联账户不可用，请重新选择现金账户。已存金额和存取记录仍保留。" type="warning" :closable="false" show-icon />
      <div class="goal-plan">
        <span>{{ goal.targetDate ? `截止 ${goal.targetDate}` : '未设置截止日期' }}</span>
        <strong v-if="goal.status !== 'completed' && goal.monthlyNeeded !== null">每月建议 {{ money(goal.monthlyNeeded) }}</strong>
        <span v-else-if="goal.status === 'overdue'">调整截止日期后可重新规划</span>
      </div>
      <p v-if="goal.remark" class="goal-hint">{{ goal.remark }}</p>
    </el-card>
    <el-alert v-if="history.error.value" type="error" :closable="false" show-icon>
      <template #title>{{ history.error.value }}</template>
      <el-button link type="primary" :disabled="history.loading.value" @click="history.run">重试</el-button>
    </el-alert>
    <DesktopTable
      v-if="history.loading.value || history.ready.value"
      title="存取明细"
      :rows="rows"
      :loading="history.loading.value && !history.ready.value"
      :paginate="false"
    >
      <!-- @vue-generic {SavingsGoalEntry} -->
      <el-table-column label="记录日期" prop="occurredOn" min-width="140" />
      <!-- @vue-generic {SavingsGoalEntry} -->
      <el-table-column label="类型" width="100">
        <template #default="{ row }: { row: SavingsGoalEntry }">
          {{ row.type === 'deposit' ? '存入' : '取出' }}
        </template>
      </el-table-column>
      <!-- @vue-generic {SavingsGoalEntry} -->
      <el-table-column label="金额" min-width="160" align="right">
        <template #default="{ row }: { row: SavingsGoalEntry }">
          <AmountText :tone="row.type === 'deposit' ? 'positive' : 'negative'" :value="`${row.type === 'deposit' ? '+' : '−'}${money(row.amount)}`" />
        </template>
      </el-table-column>
      <!-- @vue-generic {SavingsGoalEntry} -->
      <el-table-column label="来源" min-width="130">
        <template #default="{ row }: { row: SavingsGoalEntry }">
          {{ row.transactionId ? '关联记账' : '手动分配' }}
        </template>
      </el-table-column>
      <!-- @vue-generic {SavingsGoalEntry} -->
      <el-table-column label="备注" prop="remark" min-width="240" show-overflow-tooltip />
    </DesktopTable>
    <div v-if="history.ready.value" class="history-footer" :aria-busy="history.loading.value">
      <span>{{ rows.length ? `已加载 ${rows.length} 条记录` : '暂无存取记录' }}</span>
      <el-button v-if="nextCursor !== null" :loading="history.loading.value" @click="loadMore">加载更多</el-button>
    </div>
  </section>
</template>

<style scoped>
.savings-goal-card :deep(.el-card__body) { display: grid; gap: 12px; }
.savings-goal-card h2, .savings-goal-card p { margin: 0; overflow-wrap: anywhere; }
.goal-title { display: flex; align-items: center; gap: 12px; }
.goal-title h2 { min-width: 0; font-size: 18px; }
.goal-title .el-tag { flex-shrink: 0; }
.account-link { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--el-color-primary); text-decoration: none; overflow-wrap: anywhere; }
.account-link span { flex-shrink: 0; }
.goal-amount { display: flex; align-items: baseline; flex-wrap: wrap; gap: 6px; color: var(--el-text-color-secondary); }
.goal-amount strong { font-size: 24px; }
.goal-hint, .goal-plan { font-size: 13px; color: var(--el-text-color-secondary); }
.goal-plan { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px 16px; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.goal-plan strong { font-weight: 500; }
.history-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; color: var(--el-text-color-secondary); font-size: 13px; }
</style>
