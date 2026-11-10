<script setup lang="ts">
import CommonModal from '../CommonModal/index.vue'
withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    message: string
    confirmText?: string
    cancelText?: string
    loading?: boolean
    error?: string
  }>(),
  { confirmText: '确认', cancelText: '取消' },
)
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
}>()
function cancel() {
  emit('update:modelValue', false)
  emit('cancel')
}
</script>
<template>
  <CommonModal
    :model-value="modelValue"
    :title="title"
    size="compact"
    :show-close="!loading"
    @update:model-value="emit('update:modelValue', $event)"
    @close="emit('cancel')"
  >
    <p>{{ message }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer>
      <el-button :disabled="loading" @click="cancel">
        {{ cancelText }}
      </el-button>
      <el-button type="primary" :loading="loading" @click="emit('confirm')">
        {{ confirmText }}
      </el-button>
    </template>
  </CommonModal>
</template>
