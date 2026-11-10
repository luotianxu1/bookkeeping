<script setup lang="ts">
import { onMounted, shallowRef } from 'vue'
import { Plus } from '@lucide/vue'
withDefaults(defineProps<{ ariaLabel?: string; storageKey?: string }>(), {
  ariaLabel: '新增',
})
const emit = defineEmits<{ click: [] }>()
const target = shallowRef<HTMLElement | null>(null)
onMounted(() => {
  target.value = document.querySelector('.business-content .pc-page-actions')
})
</script>
<template>
  <Teleport :to="target || 'body'" :disabled="!target">
    <el-button
      type="primary"
      class="pc-add-button"
      :aria-label="ariaLabel"
      @click="emit('click')"
    >
      <Plus :size="16" />
      {{ ariaLabel }}
    </el-button>
  </Teleport>
</template>
