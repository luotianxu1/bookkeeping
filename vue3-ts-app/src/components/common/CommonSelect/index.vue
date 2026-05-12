<script setup lang="ts">
// 公共下拉框组件：封装标签、选项和 v-model。
defineProps<{
  label: string
  modelValue: string
  options: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <label class="common-field">
    <span>{{ label }}</span>
    <select
      class="common-field-control"
      :value="modelValue"
      @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    >
      <option v-for="option in options" :key="option" :value="option">
        {{ option }}
      </option>
    </select>
  </label>
</template>

<style scoped lang="scss" src="./style.scss"></style>
