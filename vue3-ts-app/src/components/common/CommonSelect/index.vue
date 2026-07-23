<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

// 公共下拉框组件：封装标签、选项和 v-model。
export type CommonSelectOption = string | {
  label: string
  value: string
  disabled?: boolean
}

type NormalizedOption = {
  label: string
  value: string
  disabled: boolean
}

const props = defineProps<{
  label: string
  modelValue: string
  options: CommonSelectOption[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLButtonElement | null>(null)
const menuRef = ref<HTMLElement | null>(null)
const isOpen = ref(false)
const activeIndex = ref(-1)
const menuStyle = ref<Record<string, string>>({})
const selectId = `common-select-${Math.random().toString(36).slice(2)}`
const labelId = `${selectId}-label`
const valueId = `${selectId}-value`
const listboxId = `${selectId}-listbox`

const normalizedOptions = computed<NormalizedOption[]>(() => (
  props.options.map((option) => ({
    label: getOptionLabel(option),
    value: getOptionValue(option),
    disabled: isOptionDisabled(option),
  }))
))

const selectedIndex = computed(() => (
  normalizedOptions.value.findIndex((option) => option.value === props.modelValue)
))

const selectedOption = computed(() => (
  selectedIndex.value >= 0 ? normalizedOptions.value[selectedIndex.value] : null
))

const selectedLabel = computed(() => selectedOption.value?.label || '请选择')

watch(() => props.disabled, (disabled) => {
  if (disabled) {
    closeMenu()
  }
})

watch(normalizedOptions, async () => {
  if (!isOpen.value) {
    return
  }
  const selected = selectedIndex.value
  activeIndex.value = selected >= 0 && !normalizedOptions.value[selected]?.disabled
    ? selected
    : getFirstEnabledIndex()
  await nextTick()
  updateMenuPosition()
})

watch(isOpen, async (nextOpen) => {
  if (nextOpen) {
    await nextTick()
    updateMenuPosition()
    window.addEventListener('resize', updateMenuPosition)
    window.addEventListener('scroll', updateMenuPosition, true)
    return
  }

  window.removeEventListener('resize', updateMenuPosition)
  window.removeEventListener('scroll', updateMenuPosition, true)
  menuStyle.value = {}
})

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  window.removeEventListener('resize', updateMenuPosition)
  window.removeEventListener('scroll', updateMenuPosition, true)
})

function getOptionLabel(option: CommonSelectOption) {
  return typeof option === 'string' ? option : option.label
}

function getOptionValue(option: CommonSelectOption) {
  return typeof option === 'string' ? option : option.value
}

function isOptionDisabled(option: CommonSelectOption) {
  return typeof option === 'string' ? false : option.disabled === true
}

function openMenu() {
  if (props.disabled || normalizedOptions.value.length === 0) {
    return
  }
  const selected = selectedIndex.value
  activeIndex.value = selected >= 0 && !normalizedOptions.value[selected]?.disabled
    ? selected
    : getFirstEnabledIndex()
  isOpen.value = true
  void nextTick(scrollActiveOptionIntoView)
}

function closeMenu() {
  isOpen.value = false
}

function toggleMenu() {
  if (isOpen.value) {
    closeMenu()
    return
  }
  openMenu()
}

function selectOption(index: number) {
  const option = normalizedOptions.value[index]
  if (!option || option.disabled) {
    return
  }
  emit('update:modelValue', option.value)
  closeMenu()
}

function activateOption(index: number) {
  const option = normalizedOptions.value[index]
  if (!option || option.disabled) {
    return
  }
  activeIndex.value = index
}

function getFirstEnabledIndex() {
  return normalizedOptions.value.findIndex((option) => !option.disabled)
}

function getLastEnabledIndex() {
  for (let index = normalizedOptions.value.length - 1; index >= 0; index -= 1) {
    if (!normalizedOptions.value[index].disabled) {
      return index
    }
  }
  return -1
}

function moveActiveIndex(step: 1 | -1) {
  const options = normalizedOptions.value
  if (options.length === 0) {
    return
  }
  let nextIndex = activeIndex.value
  for (let count = 0; count < options.length; count += 1) {
    nextIndex = (nextIndex + step + options.length) % options.length
    if (!options[nextIndex].disabled) {
      activeIndex.value = nextIndex
      void nextTick(scrollActiveOptionIntoView)
      return
    }
  }
}

function scrollActiveOptionIntoView() {
  const menu = menuRef.value
  if (!menu || activeIndex.value < 0) {
    return
  }
  menu
    .querySelector<HTMLElement>(`[data-option-index="${activeIndex.value}"]`)
    ?.scrollIntoView({ block: 'nearest' })
}

function updateMenuPosition() {
  const trigger = triggerRef.value
  const menu = menuRef.value
  if (!trigger || !menu) {
    return
  }

  const rect = trigger.getBoundingClientRect()
  const gap = 6
  const viewportPadding = 12
  const menuWidth = rect.width
  const spaceBelow = window.innerHeight - rect.bottom - viewportPadding
  const spaceAbove = rect.top - viewportPadding
  const preferredMenuHeight = Math.min(240, Math.max(0, menu.scrollHeight))
  const placeBelow = spaceBelow >= preferredMenuHeight || spaceBelow >= spaceAbove
  const availableSpace = Math.max(80, placeBelow ? spaceBelow : spaceAbove)
  const menuHeight = Math.min(preferredMenuHeight, availableSpace)
  const rawLeft = rect.left
  const left = Math.min(
    Math.max(viewportPadding, rawLeft),
    Math.max(viewportPadding, window.innerWidth - viewportPadding - menuWidth),
  )

  menuStyle.value = {
    position: 'fixed',
    top: placeBelow
      ? `${rect.bottom + gap}px`
      : `${Math.max(viewportPadding, rect.top - gap - menuHeight)}px`,
    left: `${left}px`,
    width: `${menuWidth}px`,
    maxHeight: `${menuHeight}px`,
  }
}

function handleControlKeydown(event: KeyboardEvent) {
  if (props.disabled) {
    return
  }
  if (event.key === 'Tab') {
    closeMenu()
    return
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    closeMenu()
    return
  }
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    if (!isOpen.value) {
      openMenu()
      return
    }
    if (activeIndex.value >= 0) {
      selectOption(activeIndex.value)
    }
    return
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!isOpen.value) {
      openMenu()
      return
    }
    moveActiveIndex(1)
    return
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (!isOpen.value) {
      openMenu()
      return
    }
    moveActiveIndex(-1)
    return
  }
  if (event.key === 'Home') {
    event.preventDefault()
    activeIndex.value = getFirstEnabledIndex()
    void nextTick(scrollActiveOptionIntoView)
    return
  }
  if (event.key === 'End') {
    event.preventDefault()
    activeIndex.value = getLastEnabledIndex()
    void nextTick(scrollActiveOptionIntoView)
  }
}

function handleDocumentPointerDown(event: PointerEvent) {
  const root = rootRef.value
  const menu = menuRef.value
  const target = event.target as Node
  if (!root || root.contains(target) || (menu && menu.contains(target))) {
    return
  }
  closeMenu()
}
</script>

<template>
  <div ref="rootRef" class="common-field">
    <span :id="labelId">{{ label }}</span>
    <div class="common-select">
      <button
        ref="triggerRef"
        type="button"
        :class="['common-field-control', 'common-select-control', { open: isOpen }]"
        :disabled="disabled"
        :aria-labelledby="`${labelId} ${valueId}`"
        aria-haspopup="listbox"
        :aria-expanded="isOpen"
        :aria-controls="listboxId"
        @click="toggleMenu"
        @keydown="handleControlKeydown"
      >
        <span :id="valueId" class="common-select-value">{{ selectedLabel }}</span>
        <span class="common-select-arrow" aria-hidden="true"></span>
      </button>

      <Teleport to="body">
        <Transition name="common-select-menu">
          <div
            v-if="isOpen"
            :id="listboxId"
            ref="menuRef"
            class="common-select-menu"
            :style="menuStyle"
            role="listbox"
            :aria-labelledby="labelId"
            @keydown="handleControlKeydown"
          >
            <button
              v-for="(option, index) in normalizedOptions"
              :key="option.value"
              type="button"
              :class="[
                'common-select-option',
                {
                  active: index === activeIndex,
                  selected: option.value === modelValue,
                },
              ]"
              role="option"
              :aria-selected="option.value === modelValue"
              :aria-disabled="option.disabled"
              :disabled="option.disabled"
              :data-option-index="index"
              @click="selectOption(index)"
              @mouseenter="activateOption(index)"
            >
              <span>{{ option.label }}</span>
              <span v-if="option.value === modelValue" class="common-select-check" aria-hidden="true"></span>
            </button>
          </div>
        </Transition>
      </Teleport>
    </div>
  </div>
</template>

<style scoped lang="scss" src="./style.scss"></style>
