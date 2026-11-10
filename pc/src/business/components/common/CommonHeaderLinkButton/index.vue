<script setup lang="ts">
import { useRouter } from 'vue-router'
defineProps<{ to: string; label: string }>()
const router = useRouter()
</script>
<template>
  <el-button :aria-label="label" @click="router.push(to)">
    <slot />
    {{ label }}
  </el-button>
</template>
