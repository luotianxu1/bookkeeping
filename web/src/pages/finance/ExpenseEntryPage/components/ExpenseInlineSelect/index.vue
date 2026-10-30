<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

export type ExpenseInlineSelectValue = string | number | null

export type ExpenseInlineSelectOption = {
  label: string
  value: string | number
  disabled?: boolean
  description?: string
}

const props = defineProps<{
  label: string
  modelValue: ExpenseInlineSelectValue
  options: ExpenseInlineSelectOption[]
  disabled?: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: ExpenseInlineSelectValue]
}>()

const rootRef = ref<HTMLElement | null>(null)
const isOpen = ref(false)
const activeIndex = ref(-1)
const selectId = `expense-inline-select-${Math.random().toString(36).slice(2)}`
const labelId = `${selectId}-label`
const valueId = `${selectId}-value`
const listboxId = `${selectId}-listbox`

const normalizedOptions = computed(() => props.options.map((option) => ({
  label: option.label,
  value: option.value,
  disabled: option.disabled === true,
  description: option.description,
  optionId: `${selectId}-option-${String(option.value)}`,
})))

const selectedIndex = computed(() => (
  normalizedOptions.value.findIndex((option) => option.value === props.modelValue)
))

const selectedLabel = computed(() => {
  const selected = selectedIndex.value >= 0 ? normalizedOptions.value[selectedIndex.value] : null
  return selected?.label || props.placeholder || '请选择'
})

const selectedDescription = computed(() => {
  const selected = selectedIndex.value >= 0 ? normalizedOptions.value[selectedIndex.value] : null
  return selected?.description || ''
})

const isDisabled = computed(() => props.disabled === true || normalizedOptions.value.length === 0)

watch([() => props.modelValue, normalizedOptions, () => props.disabled], () => {
  if (!isOpen.value) {
    return
  }
  activeIndex.value = getPreferredActiveIndex()
})

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
})

function openMenu() {
  if (isDisabled.value) {
    return
  }
  activeIndex.value = getPreferredActiveIndex()
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

function getPreferredActiveIndex() {
  const selected = selectedIndex.value
  if (selected >= 0 && !normalizedOptions.value[selected]?.disabled) {
    return selected
  }
  return getFirstEnabledIndex()
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
  const root = rootRef.value
  if (!root || activeIndex.value < 0) {
    return
  }
  root
    .querySelector<HTMLElement>(`[data-option-index="${activeIndex.value}"]`)
    ?.scrollIntoView({ block: 'nearest' })
}

function handleKeydown(event: KeyboardEvent) {
  if (isDisabled.value) {
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
  if (!root || root.contains(event.target as Node)) {
    return
  }
  closeMenu()
}
</script>

<template>
  <div ref="rootRef" class="expense-inline-control expense-inline-select">
    <button
      type="button"
      class="expense-inline-select-trigger"
      :disabled="isDisabled"
      :aria-labelledby="`${labelId} ${valueId}`"
      aria-haspopup="listbox"
      :aria-expanded="isOpen"
      :aria-controls="listboxId"
      :aria-activedescendant="isOpen && activeIndex >= 0 ? normalizedOptions[activeIndex]?.optionId : undefined"
      @click="toggleMenu"
      @keydown="handleKeydown"
    >
      <span :id="labelId" class="sr-only">{{ label }}</span>
      <span :id="valueId" class="expense-inline-select-value">
        <span class="expense-inline-select-main">{{ selectedLabel }}</span>
        <span v-if="selectedDescription" class="expense-inline-select-sub">{{ selectedDescription }}</span>
      </span>
      <span class="expense-inline-select-arrow" aria-hidden="true"></span>
    </button>

    <Transition name="expense-inline-select-menu">
      <div
        v-if="isOpen"
        :id="listboxId"
        class="expense-inline-select-menu"
        role="listbox"
        :aria-labelledby="labelId"
      >
        <button
          v-for="(option, index) in normalizedOptions"
          :key="option.optionId"
          type="button"
          :id="option.optionId"
          :class="[
            'expense-inline-select-option',
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
          <span class="expense-inline-select-main">{{ option.label }}</span>
          <span v-if="option.description" class="expense-inline-select-sub">{{ option.description }}</span>
          <span v-if="option.value === modelValue" class="expense-inline-select-check" aria-hidden="true"></span>
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped lang="scss" src="./style.scss"></style>
