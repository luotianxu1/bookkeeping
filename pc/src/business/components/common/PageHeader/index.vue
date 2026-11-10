<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft } from '@lucide/vue'
const props = defineProps<{
  title: string
  backTo?: string
  backLabel?: string
  preferBackTo?: boolean
}>()
const router = useRouter()
const route = useRoute()
function goBack() {
  if (props.backTo) return router.push(props.backTo)
  if (router.options.history.state.back) return router.back()
  const parent = route.path.slice(0, route.path.lastIndexOf('/'))
  return router.push(
    router.resolve(parent).matched.length ? parent : '/overview',
  )
}
</script>
<template>
  <header class="pc-page-header">
    <div class="pc-page-title">
      <el-button
        v-if="Object.keys(route.params).length"
        text
        :aria-label="backLabel || '返回'"
        @click="goBack"
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
