<script setup lang="ts">
// 公共弹窗组件：提供统一遮罩、容器、标题和底部操作区。
import { computed, onBeforeUnmount, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  closeOnOverlay?: boolean
  showClose?: boolean
  size?: 'default' | 'compact'
}>(), {
  closeOnOverlay: true,
  showClose: true,
  size: 'default',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  close: []
}>()

const isOpen = computed(() => props.modelValue)

function closeModal() {
  emit('update:modelValue', false)
  emit('close')
}

function handleOverlayClick() {
  if (props.closeOnOverlay) {
    closeModal()
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && isOpen.value) {
    closeModal()
  }
}

watch(
  isOpen,
  (nextOpen) => {
    if (nextOpen) {
      window.addEventListener('keydown', handleKeydown)
      document.body.style.overflow = 'hidden'
      return
    }

    window.removeEventListener('keydown', handleKeydown)
    document.body.style.overflow = ''
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="isOpen" class="common-modal-root" role="dialog" aria-modal="true">
        <button
          class="common-modal-overlay"
          type="button"
          aria-label="关闭弹窗"
          @click="handleOverlayClick"
        ></button>

        <Transition name="modal-pop">
          <section v-if="isOpen" :class="['common-modal-panel', `common-modal-panel-${size}`]">
            <header class="common-modal-header">
              <h2>{{ title }}</h2>
              <button
                v-if="showClose"
                class="common-modal-close"
                type="button"
                aria-label="关闭"
                @click="closeModal"
              >
                ×
              </button>
            </header>

            <div class="common-modal-body">
              <slot />
            </div>

            <footer class="common-modal-footer">
              <slot name="footer" />
            </footer>
          </section>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss" src="./style.scss"></style>
