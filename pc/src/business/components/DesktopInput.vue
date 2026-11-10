<script setup lang="ts">
import { computed, ref } from 'vue'
import type { InputInstance } from 'element-plus'
defineOptions({ inheritAttrs: false })
const props = defineProps<{
  modelValue?: string | number | null
  value?: string | number | null
  type?: string
  modelModifiers?: { number?: boolean; trim?: boolean; lazy?: boolean }
}>()
const emit = defineEmits<{
  'update:modelValue': [value: any]
  input: [event: Event]
  change: [event: Event]
  search: [event: Event]
}>()
const control = ref<InputInstance>()
const dateType = computed(() =>
  ['date', 'datetime-local', 'month'].includes(props.type || ''),
)
function eventFor(value: string | number, name: string) {
  const event = new Event(name, { bubbles: true })
  Object.defineProperty(event, 'target', {
    value: control.value?.input || { value: String(value) },
  })
  return event
}
function update(value: string | number | null) {
  let next: string | number = value ?? ''
  if (props.modelModifiers?.trim && typeof next === 'string') next = next.trim()
  if (props.modelModifiers?.number && next !== '') next = Number(next)
  emit('update:modelValue', next)
}
function dateChange(value: string | null) {
  update(value)
  emit('input', eventFor(value || '', 'input'))
  emit('change', eventFor(value || '', 'change'))
}
defineExpose({ focus: () => control.value?.focus() })
</script>
<template>
  <el-date-picker
    v-if="dateType"
    v-bind="$attrs"
    :model-value="modelValue ?? value"
    :type="
      type === 'datetime-local'
        ? 'datetime'
        : type === 'month'
          ? 'month'
          : 'date'
    "
    :value-format="
      type === 'datetime-local'
        ? 'YYYY-MM-DDTHH:mm'
        : type === 'month'
          ? 'YYYY-MM'
          : 'YYYY-MM-DD'
    "
    @update:model-value="dateChange"
  />
  <el-input
    v-else
    ref="control"
    v-bind="$attrs"
    :type="type || 'text'"
    :step="
      type === 'number'
        ? (($attrs.step as string | number | undefined) ?? 'any')
        : undefined
    "
    :model-value="modelValue ?? value"
    @update:model-value="update"
    @input="emit('input', eventFor($event, 'input'))"
    @change="emit('change', eventFor($event, 'change'))"
    @keyup.enter="emit('search', eventFor(modelValue ?? value ?? '', 'search'))"
  >
    <slot />
  </el-input>
</template>
