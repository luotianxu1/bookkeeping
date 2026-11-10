<script setup lang="ts">
import AmountText from '@/components/common/AmountText/index.vue'
import type { SavingsGoal } from '@/api/modules/finance'

defineProps<{ goal: SavingsGoal }>()

function money(value: number | null, currency: string) {
  if (value === null) return '--'
  try { return new Intl.NumberFormat('zh-CN', { style: 'currency', currency, minimumFractionDigits: 2 }).format(value) }
  catch { return `${currency} ${Number(value).toFixed(2)}` }
}
function statusText(goal: SavingsGoal) {
  return { saving: '储蓄中', completed: '已达成', overdue: '已逾期' }[goal.status]
}
</script>

<template>
  <article class="savings-card" :aria-label="goal.name">
    <div class="card-heading">
      <h2>{{ goal.name }}</h2>
      <span class="goal-status" :class="goal.status">{{ statusText(goal) }}</span>
      <slot name="actions" />
    </div>
    <RouterLink class="account-link" :to="`/finance/accounts/cash/${goal.accountId}`">{{ goal.accountName || '关联账户' }} <span>查看账户 ›</span></RouterLink>
    <p v-if="goal.accountAvailable" class="hint">账户可分配 {{ money(goal.accountAvailableAmount, goal.currencyCode) }} · 目标合计预留 {{ money(goal.accountReservedAmount, goal.currencyCode) }}</p>
    <div class="goal-amount"><span>已存 </span><AmountText tag="strong" :value="money(goal.currentAmount, goal.currencyCode)" /><span> / {{ money(goal.targetAmount, goal.currencyCode) }}</span></div>
    <div class="goal-progress" role="progressbar" :aria-label="`${goal.name}完成进度`" :aria-valuenow="Number(goal.progressPercent)" :aria-valuemin="0" :aria-valuemax="100">
      <span :style="{ width: `${goal.progressPercent}%` }"></span>
    </div>
    <div class="goal-meta"><span>{{ goal.progressPercent }}%</span><span>{{ goal.status === 'completed' ? '已达到目标金额' : `还差 ${money(goal.remainingAmount, goal.currencyCode)}` }}</span></div>
    <p v-if="!goal.accountAvailable" class="error">关联账户不可用，请重新选择现金账户。已存金额和存取记录仍保留。</p>
    <div class="goal-plan">
      <span>{{ goal.targetDate ? `截止 ${goal.targetDate}` : '未设置截止日期' }}</span>
      <strong v-if="goal.status !== 'completed' && goal.monthlyNeeded !== null">每月建议 {{ money(goal.monthlyNeeded, goal.currencyCode) }}</strong>
      <span v-else-if="goal.status === 'overdue'" class="overdue">调整截止日期后可重新规划</span>
    </div>
    <p v-if="goal.remark" class="remark">{{ goal.remark }}</p>
  </article>
</template>

<style scoped lang="scss" src="./style.scss"></style>
