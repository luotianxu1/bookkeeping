<script setup lang="ts">
import { computed } from 'vue'
import ExpenseInlineSelect from './ExpenseInlineSelect/index.vue'
import type { SavingsGoal } from '@/api/modules/finance'

export interface SavingsAllocationDraft { goalId: number | null; amount: string }
const props = defineProps<{ modelValue: SavingsAllocationDraft[]; goals: SavingsGoal[]; label: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: SavingsAllocationDraft[]] }>()
const unused = computed(() => props.goals.filter(goal => !props.modelValue.some(row => row.goalId === goal.id)))
function update(index: number, change: Partial<SavingsAllocationDraft>) {
  emit('update:modelValue', props.modelValue.map((row, i) => i === index ? { ...row, ...change } : row))
}
function add() {
  if (unused.value[0]) emit('update:modelValue', [...props.modelValue, { goalId: unused.value[0].id, amount: '' }])
}
function options(index: number) {
  return props.goals.filter(goal => !props.modelValue.some((row, i) => i !== index && row.goalId === goal.id))
    .map(goal => ({ value: goal.id, label: goal.name, description: `已存 ${Number(goal.currentAmount).toFixed(2)} ${goal.currencyCode}` }))
}
</script>

<template>
  <div v-if="goals.length || modelValue.length" class="savings-allocation-fields">
    <div class="allocation-heading"><span>{{ label }}（可选）</span><button type="button" :disabled="disabled || !unused.length" :aria-label="`添加${label}`" @click="add">＋ 添加</button></div>
    <div v-for="(row, index) in modelValue" :key="index" class="allocation-row">
      <ExpenseInlineSelect :model-value="row.goalId" :label="`${label} ${index + 1}`" :options="options(index)" :disabled="disabled" @update:model-value="update(index, { goalId: Number($event) })" />
      <input :value="row.amount" inputmode="decimal" placeholder="金额" :aria-label="`${label}金额 ${index + 1}`" :disabled="disabled" @input="update(index, { amount: ($event.target as HTMLInputElement).value })" />
      <button type="button" :aria-label="`移除${label} ${index + 1}`" :disabled="disabled" @click="emit('update:modelValue', modelValue.filter((_, i) => i !== index))">×</button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.savings-allocation-fields { display: grid; gap: 6px; border-top: 1px solid var(--color-bg-soft-2); padding: 6px 0; font-size: 12px; }
.allocation-heading, .allocation-row { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.allocation-heading button { color: var(--color-brand); background: transparent; padding: 4px; font: inherit; }
.allocation-row :deep(.expense-inline-select) { min-width: 0; flex: 1; }
.allocation-row input { width: 84px; min-width: 0; padding: 6px 8px; border: 1px solid var(--color-border-soft); border-radius: 8px; background: var(--color-bg-card); color: var(--color-text-primary); font: inherit; text-align: right; }
.allocation-row > button { width: 24px; height: 28px; background: transparent; color: var(--color-danger); font-size: 18px; }
button { cursor: pointer; border: 0; } button:disabled { opacity: .4; cursor: default; }
</style>
