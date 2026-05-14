<script setup lang="ts">
// 公共悬浮新增按钮：用于页面右下角的新增操作入口。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const BUTTON_SIZE = 52
const DEFAULT_RIGHT = 24
const DEFAULT_BOTTOM = 124
const STORAGE_PREFIX = 'bookkeeping_floating_add_button_position'
const DRAG_THRESHOLD = 4

const props = withDefaults(defineProps<{
  ariaLabel?: string
  storageKey?: string
}>(), {
  ariaLabel: '新增',
  storageKey: '',
})

const emit = defineEmits<{
  click: []
}>()

const position = ref(defaultPosition())
const dragStart = ref({
  pointerId: 0,
  pointerX: 0,
  pointerY: 0,
  originX: 0,
  originY: 0,
  moved: false,
  active: false,
})

const resolvedStorageKey = computed(() => `${STORAGE_PREFIX}:${props.storageKey || props.ariaLabel}`)
const buttonStyle = computed(() => ({
  left: `${position.value.x}px`,
  top: `${position.value.y}px`,
}))

onMounted(() => {
  position.value = loadStoredPosition()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})

function defaultPosition() {
  if (typeof window === 'undefined') {
    return { x: 0, y: 0 }
  }

  return clampPosition({
    x: window.innerWidth - DEFAULT_RIGHT - BUTTON_SIZE,
    y: window.innerHeight - DEFAULT_BOTTOM - BUTTON_SIZE,
  })
}

function loadStoredPosition() {
  const raw = window.localStorage.getItem(resolvedStorageKey.value)
  if (!raw) {
    return defaultPosition()
  }

  try {
    const parsed = JSON.parse(raw) as { x?: unknown; y?: unknown }
    if (typeof parsed.x === 'number' && typeof parsed.y === 'number') {
      return clampPosition({ x: parsed.x, y: parsed.y })
    }
  } catch {
    window.localStorage.removeItem(resolvedStorageKey.value)
  }

  return defaultPosition()
}

function savePosition() {
  window.localStorage.setItem(resolvedStorageKey.value, JSON.stringify(position.value))
}

function clampPosition(nextPosition: { x: number; y: number }) {
  if (typeof window === 'undefined') {
    return nextPosition
  }

  return {
    x: Math.min(Math.max(nextPosition.x, 8), window.innerWidth - BUTTON_SIZE - 8),
    y: Math.min(Math.max(nextPosition.y, 8), window.innerHeight - BUTTON_SIZE - 8),
  }
}

function handlePointerDown(event: PointerEvent) {
  if (event.button !== 0) {
    return
  }

  const target = event.currentTarget as HTMLButtonElement
  target.setPointerCapture(event.pointerId)
  dragStart.value = {
    pointerId: event.pointerId,
    pointerX: event.clientX,
    pointerY: event.clientY,
    originX: position.value.x,
    originY: position.value.y,
    moved: false,
    active: true,
  }
}

function handlePointerMove(event: PointerEvent) {
  if (!dragStart.value.active || event.pointerId !== dragStart.value.pointerId) {
    return
  }

  const offsetX = event.clientX - dragStart.value.pointerX
  const offsetY = event.clientY - dragStart.value.pointerY
  if (Math.abs(offsetX) > DRAG_THRESHOLD || Math.abs(offsetY) > DRAG_THRESHOLD) {
    dragStart.value.moved = true
  }

  position.value = clampPosition({
    x: dragStart.value.originX + offsetX,
    y: dragStart.value.originY + offsetY,
  })
}

function handlePointerUp(event: PointerEvent) {
  if (!dragStart.value.active || event.pointerId !== dragStart.value.pointerId) {
    return
  }

  const wasMoved = dragStart.value.moved
  dragStart.value.active = false
  if (wasMoved) {
    savePosition()
  }
}

function handleClick() {
  if (dragStart.value.moved) {
    dragStart.value.moved = false
    return
  }

  emit('click')
}

function handleResize() {
  position.value = clampPosition(position.value)
  savePosition()
}
</script>

<template>
  <button
    class="floating-add-button"
    type="button"
    :aria-label="ariaLabel"
    :style="buttonStyle"
    @click="handleClick"
    @pointerdown="handlePointerDown"
    @pointermove="handlePointerMove"
    @pointerup="handlePointerUp"
    @pointercancel="handlePointerUp"
  >
    +
  </button>
</template>

<style scoped lang="scss" src="./style.scss"></style>
