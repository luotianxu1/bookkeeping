<script setup lang="ts">
withDefaults(defineProps<{ threshold?: number }>(), { threshold: 240 })
</script>
<template>
  <el-backtop target=".page-container" :visibility-height="threshold" />
</template>
