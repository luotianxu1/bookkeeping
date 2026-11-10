<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft } from '@lucide/vue'
defineProps<{
  title: string
  backTo?: string
  backLabel?: string
  preferBackTo?: boolean
}>()
const router = useRouter()
const route = useRoute()
</script>
<template>
  <header class="pc-page-header">
    <div class="pc-page-title">
      <el-button
        v-if="Object.keys(route.params).length"
        text
        :aria-label="backLabel || '返回'"
        @click="router.push(backTo || '/overview')"
      >
        <ArrowLeft :size="16" />
      </el-button>
      <h1>{{ title }}</h1>
    </div>
    <div class="pc-page-actions">
      <slot name="right"><slot /></slot>
    </div>
  </header>
</template>
