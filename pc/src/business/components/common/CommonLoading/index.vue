<script setup lang="ts">
withDefaults(defineProps<{ visible?: boolean; text?: string }>(), {
  visible: true,
  text: '加载中',
})
</script>
<template>
  <div v-if="visible" class="pc-loading" role="status" :aria-label="text">
    <el-skeleton :rows="3" animated />
  </div>
</template>
