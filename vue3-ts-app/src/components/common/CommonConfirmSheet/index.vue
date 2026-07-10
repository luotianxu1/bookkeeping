<script setup lang="ts">
// 公共确认抽屉：用于删除等破坏性操作确认。
import CommonBottomSheet from '@/components/common/CommonBottomSheet/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  loading?: boolean
  error?: string
}>(), {
  confirmText: '确认',
  cancelText: '取消',
  loading: false,
  error: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  cancel: []
  confirm: []
}>()

function closeSheet() {
  if (props.loading) {
    return
  }
  emit('update:modelValue', false)
  emit('cancel')
}
</script>

<template>
  <CommonBottomSheet
    :model-value="modelValue"
    :title="title"
    :close-on-overlay="!loading"
    @update:model-value="emit('update:modelValue', $event)"
    @close="emit('cancel')"
  >
    <div class="common-confirm-sheet-content">
      <p>{{ message }}</p>
      <p v-if="error" class="common-confirm-sheet-error">{{ error }}</p>
    </div>

    <template #footer>
      <div class="common-confirm-sheet-actions">
        <CommonButton variant="secondary" :disabled="loading" @click="closeSheet">
          {{ cancelText }}
        </CommonButton>
        <CommonButton variant="primary" :disabled="loading" @click="emit('confirm')">
          {{ loading ? '处理中...' : confirmText }}
        </CommonButton>
      </div>
    </template>
  </CommonBottomSheet>
</template>

<style scoped lang="scss" src="./style.scss"></style>
