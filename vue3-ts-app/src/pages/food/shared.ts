import { computed, ref } from 'vue'
import type { FoodDish } from '@/api/modules/food'

const selectedDishIds = ref<number[]>([])

export function useFoodSelection() {
  const selectedIds = computed(() => selectedDishIds.value)
  const selectedCount = computed(() => selectedDishIds.value.length)

  function addDish(dishId: number) {
    if (!selectedDishIds.value.includes(dishId)) {
      selectedDishIds.value = [...selectedDishIds.value, dishId]
    }
  }

  function removeDish(dishId: number) {
    selectedDishIds.value = selectedDishIds.value.filter((id) => id !== dishId)
  }

  function toggleDish(dishId: number) {
    if (selectedDishIds.value.includes(dishId)) {
      removeDish(dishId)
      return
    }
    addDish(dishId)
  }

  function clearSelection() {
    selectedDishIds.value = []
  }

  function isSelected(dishId: number) {
    return selectedDishIds.value.includes(dishId)
  }

  function resolveSelectedDishes(allDishes: FoodDish[]) {
    return allDishes.filter((dish) => selectedDishIds.value.includes(dish.id))
  }

  return {
    selectedIds,
    selectedCount,
    addDish,
    removeDish,
    toggleDish,
    clearSelection,
    isSelected,
    resolveSelectedDishes,
  }
}

export function isDarkFoodPath(_path: string) {
  return false
}

export function getFoodHomePath(_isDark = false) {
  return '/food'
}

export function getFoodMenuPath(_isDark = false) {
  return '/food/menu'
}

export function getFoodMenuDetailPath(orderId: number) {
  return `/food/menu/${orderId}`
}

export function getFoodDishListPath(_isDark = false) {
  return '/food/dishes'
}

export function getFoodDishMenuPath() {
  return '/food/dishes?entry=menu'
}

export function getFoodDishDetailPath(_isDark: boolean, dishId: number) {
  return `/food/dishes/${dishId}`
}

export function getFoodCreatePath(_isDark = false) {
  return '/food/dishes/new'
}

export function getFoodCategoryPath(_isDark = false) {
  return '/food/categories'
}

export function getFoodCategoryCreatePath(_isDark = false) {
  return '/food/categories/new'
}

export function getFoodIngredientPath(_isDark = false) {
  return '/food/ingredients'
}

export function getFoodIngredientCategoryPath(_isDark = false) {
  return '/food/ingredient-categories'
}

export function getFoodIngredientCategoryCreatePath(_isDark = false) {
  return '/food/ingredient-categories/new'
}

export function formatDishCountLabel(count: number) {
  return `${count} 道菜`
}

export function mapIconToneToClass(tone: string) {
  switch (tone) {
    case 'purple':
      return 'tone-purple'
    case 'orange':
      return 'tone-orange'
    case 'green':
      return 'tone-green'
    case 'sky':
      return 'tone-sky'
    default:
      return 'tone-blue'
  }
}

export function mapCoverToneToClass(tone: string) {
  switch (tone) {
    case 'sunset':
    case 'amber':
    case 'brown':
    case 'gold':
    case 'red':
    case 'pumpkin':
      return 'cover-warm'
    case 'mint':
    case 'green':
    case 'jade':
    case 'olive':
      return 'cover-green'
    case 'dessert':
    case 'cream':
    case 'choco':
    case 'lemon':
      return 'cover-dessert'
    default:
      return 'cover-blue'
  }
}

export function buildDefaultFoodCover(name: string, iconTone: string) {
  const compact = name.replace(/\s+/g, '').slice(0, 4)
  const tone = mapIconToneToCover(iconTone)
  return {
    coverText: compact || '新菜品',
    coverTone: tone,
  }
}

function mapIconToneToCover(iconTone: string) {
  switch (iconTone) {
    case 'purple':
      return 'dessert'
    case 'orange':
      return 'sunset'
    case 'green':
      return 'mint'
    case 'sky':
      return 'cream'
    default:
      return 'blue'
  }
}
