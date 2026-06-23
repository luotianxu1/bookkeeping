<script setup lang="ts">
// 公共输入框组件：封装标签、输入框和 v-model。
defineProps<{
  label: string
  modelValue: string
  placeholder?: string
  inputType?: string
  inputMode?: 'none' | 'text' | 'tel' | 'url' | 'email' | 'numeric' | 'decimal' | 'search'
  disabled?: boolean
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <label class="common-field">
    <span>{{ label }}</span>
    <input
      class="common-field-control"
      :type="inputType ?? 'text'"
      :inputmode="inputMode"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
  </label>
</template>

<style scoped lang="scss" src="./style.scss"></style>
