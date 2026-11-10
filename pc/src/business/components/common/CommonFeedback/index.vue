<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue'
import { ElMessage, type MessageHandler } from 'element-plus'
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    message: string
    type?: 'success' | 'error'
    duration?: number
  }>(),
  { type: 'success', duration: 2500 },
)
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
let toast: MessageHandler | undefined
watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      toast?.close()
      return
    }
    toast?.close()
    toast = ElMessage({
      message: props.message,
      type: props.type,
      duration: props.duration,
      onClose: () => emit('update:modelValue', false),
    })
  },
  { immediate: true },
)
onBeforeUnmount(() => toast?.close())
</script>
<template></template>
