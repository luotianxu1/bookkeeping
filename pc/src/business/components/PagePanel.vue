<script setup lang="ts">
defineProps<{ title?: string; loading?: boolean }>()
</script>

<template>
  <section class="desktop-panel" :aria-label="title" :aria-busy="loading">
    <header v-if="title || $slots.actions" class="desktop-panel-header">
      <h2>{{ title }}</h2>
      <div class="desktop-panel-actions"><slot name="actions" /></div>
    </header>
    <div class="desktop-panel-body"><slot /></div>
  </section>
</template>
