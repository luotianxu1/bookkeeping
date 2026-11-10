<script setup lang="ts">
import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed } from 'vue'
import ExpenseInlineSelect from './ExpenseInlineSelect/index.vue'
import type { SavingsGoal } from '@/business/api/modules/finance'

export interface SavingsAllocationDraft {
  goalId: number | null
  amount: string
}
const props = defineProps<{
  modelValue: SavingsAllocationDraft[]
  goals: SavingsGoal[]
  label: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: SavingsAllocationDraft[]]
}>()
const unused = computed(() =>
  props.goals.filter(
    (goal) => !props.modelValue.some((row) => row.goalId === goal.id),
  ),
)
function update(index: number, change: Partial<SavingsAllocationDraft>) {
  emit(
    'update:modelValue',
    props.modelValue.map((row, i) =>
      i === index ? { ...row, ...change } : row,
    ),
  )
}
function add() {
  if (unused.value[0])
    emit('update:modelValue', [
      ...props.modelValue,
      { goalId: unused.value[0].id, amount: '' },
    ])
}
function options(index: number) {
  return props.goals
    .filter(
      (goal) =>
        !props.modelValue.some(
          (row, i) => i !== index && row.goalId === goal.id,
        ),
    )
    .map((goal) => ({
      value: goal.id,
      label: goal.name,
      description: `已存 ${Number(goal.currentAmount).toFixed(2)} ${goal.currencyCode}`,
    }))
}
</script>

<template>
  <div
    v-if="goals.length || modelValue.length"
    class="pc-form-grid savings-allocation-fields"
  >
    <div class="allocation-heading">
      <span>{{ label }}（可选）</span>
      <el-button
        native-type="button"
        :disabled="disabled || !unused.length"
        :aria-label="`添加${label}`"
        @click="add"
      >
        ＋ 添加
      </el-button>
    </div>
    <div v-for="(row, index) in modelValue" :key="index" class="allocation-row">
      <ExpenseInlineSelect
        :model-value="row.goalId"
        :label="`${label} ${index + 1}`"
        :options="options(index)"
        :disabled="disabled"
        @update:model-value="update(index, { goalId: Number($event) })"
      />
      <DesktopInput
        :value="row.amount"
        inputmode="decimal"
        placeholder="金额"
        :aria-label="`${label}金额 ${index + 1}`"
        :disabled="disabled"
        @input="
          update(index, { amount: ($event.target as HTMLInputElement).value })
        "
      />
      <el-button
        native-type="button"
        :aria-label="`移除${label} ${index + 1}`"
        :disabled="disabled"
        @click="
          emit(
            'update:modelValue',
            modelValue.filter((_, i) => i !== index),
          )
        "
      >
        ×
      </el-button>
    </div>
  </div>
</template>
