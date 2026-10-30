<script setup lang="ts">
import { computed } from 'vue'
import dailyIcon from '@/assets/category-icons/daily.png'
import entertainmentIcon from '@/assets/category-icons/entertainment.png'
import foodIcon from '@/assets/category-icons/food.png'
import investmentIncomeIcon from '@/assets/category-icons/investment-income.png'
import otherIcon from '@/assets/category-icons/other.png'
import renewalIcon from '@/assets/category-icons/renewal.png'
import salaryIcon from '@/assets/category-icons/salary.png'
import shoppingIcon from '@/assets/category-icons/shopping.png'
import transportIcon from '@/assets/category-icons/transport.png'

type CategoryIconKey =
  | 'food'
  | 'daily'
  | 'transport'
  | 'entertainment'
  | 'shopping'
  | 'salary'
  | 'investment-income'
  | 'renewal'
  | 'other'

const props = withDefaults(defineProps<{
  icon?: string | null
  fallbackIcon?: string | null
  color?: string | null
  fallbackColor?: string | null
  name?: string | null
  size?: number
}>(), {
  icon: '',
  fallbackIcon: '',
  color: '',
  fallbackColor: '',
  name: '',
  size: 28,
})

const iconSources: Record<CategoryIconKey, string> = {
  food: foodIcon,
  daily: dailyIcon,
  transport: transportIcon,
  entertainment: entertainmentIcon,
  shopping: shoppingIcon,
  salary: salaryIcon,
  'investment-income': investmentIncomeIcon,
  renewal: renewalIcon,
  other: otherIcon,
}

const colorAliases: Record<string, string> = {
  blue: '#3B82F6',
  purple: '#8B5CF6',
  orange: '#F97316',
  sky: '#38BDF8',
  green: '#10B981',
}

function isCategoryIconKey(value: string): value is CategoryIconKey {
  return Object.prototype.hasOwnProperty.call(iconSources, value)
}

const key = computed<CategoryIconKey>(() => {
  const normalizedIcon = props.icon?.trim().toLowerCase() ?? ''
  if (isCategoryIconKey(normalizedIcon)) return normalizedIcon

  const normalizedFallbackIcon = props.fallbackIcon?.trim().toLowerCase() ?? ''
  if (isCategoryIconKey(normalizedFallbackIcon)) return normalizedFallbackIcon

  return 'other'
})

const backgroundColor = computed(() => {
  const value = props.color?.trim() || props.fallbackColor?.trim() || ''
  return colorAliases[value.toLowerCase()] ?? (value || 'transparent')
})
const iconUrl = computed(() => iconSources[key.value])
</script>

<template>
  <span
    class="category-icon-shell"
    :style="{ width: `${size}px`, height: `${size}px`, backgroundColor }"
  >
    <img
      class="category-icon-image"
      :src="iconUrl"
      :width="size"
      :height="size"
      :alt="name ? `${name}分类图标` : ''"
      :aria-hidden="!name"
    />
  </span>
</template>

<style scoped lang="scss">
.category-icon-shell {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 28%;
}

.category-icon-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
</style>
