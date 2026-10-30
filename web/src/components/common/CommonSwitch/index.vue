<script setup lang="ts">
// 公共开关组件：封装开/关状态与标签文案。
defineProps<{
  label: string
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>

<template>
  <label class="common-switch-row">
    <span>{{ label }}</span>
    <button
      type="button"
      :class="['common-switch', { active: modelValue }]"
      role="switch"
      :aria-checked="modelValue"
      @click="emit('update:modelValue', !modelValue)"
    >
      <span class="common-switch-thumb"></span>
    </button>
  </label>
</template>

<style scoped lang="scss" src="./style.scss"></style>
