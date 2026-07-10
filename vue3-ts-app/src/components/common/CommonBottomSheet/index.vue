<script setup lang="ts">
// 公共底部抽屉：用于移动端筛选、选择器和确认面板。
import { computed, onBeforeUnmount, ref, watch } from 'vue'

let bodyLockCount = 0

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  closeOnOverlay?: boolean
  showClose?: boolean
}>(), {
  closeOnOverlay: true,
  showClose: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  close: []
}>()

const isOpen = computed(() => props.modelValue)
const hasLockedBody = ref(false)

function closeSheet() {
  emit('update:modelValue', false)
  emit('close')
}

function handleOverlayClick() {
  if (props.closeOnOverlay) {
    closeSheet()
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && isOpen.value) {
    closeSheet()
  }
}

watch(
  isOpen,
  (nextOpen) => {
    if (nextOpen) {
      window.addEventListener('keydown', handleKeydown)
      lockBodyScroll()
      return
    }

    window.removeEventListener('keydown', handleKeydown)
    unlockBodyScroll()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  unlockBodyScroll()
})

function lockBodyScroll() {
  if (hasLockedBody.value) {
    return
  }
  bodyLockCount += 1
  hasLockedBody.value = true
  document.body.style.overflow = 'hidden'
}

function unlockBodyScroll() {
  if (!hasLockedBody.value) {
    return
  }
  bodyLockCount = Math.max(0, bodyLockCount - 1)
  hasLockedBody.value = false
  if (bodyLockCount === 0) {
    document.body.style.overflow = ''
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="bottom-sheet-fade">
      <div v-if="isOpen" class="common-bottom-sheet-root" role="dialog" aria-modal="true">
        <button
          class="common-bottom-sheet-overlay"
          type="button"
          aria-label="关闭抽屉"
          @click="handleOverlayClick"
        ></button>

        <Transition name="bottom-sheet-slide">
          <section v-if="isOpen" class="common-bottom-sheet-panel">
            <header class="common-bottom-sheet-header">
              <span class="common-bottom-sheet-handle" aria-hidden="true"></span>
              <div class="common-bottom-sheet-title-row">
                <h2>{{ title }}</h2>
                <button
                  v-if="showClose"
                  class="common-bottom-sheet-close"
                  type="button"
                  aria-label="关闭"
                  @click="closeSheet"
                >
                  ×
                </button>
              </div>
            </header>

            <div class="common-bottom-sheet-body">
              <slot />
            </div>

            <footer v-if="$slots.footer" class="common-bottom-sheet-footer">
              <slot name="footer" />
            </footer>
          </section>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss" src="./style.scss"></style>
