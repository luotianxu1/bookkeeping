// 分类树构建：分类分组、排序号分配与可删除判断（纯函数）。
import type { Category } from '@/api/modules/finance'

export type CategoryGroup = {
  parent: Category
  children: Category[]
}

export function buildCategoryGroups(type: 'expense' | 'income', categories: Category[]) {
  const roots = categories.filter((item) => item.type === type && !item.parentId)
  return roots.map<CategoryGroup>((parent) => ({
    parent,
    children: categories.filter((item) => item.parentId === parent.id),
  }))
}

export function getNextCategorySortOrder(type: 'expense' | 'income', parentId: number | null, categories: Category[]) {
  const siblingSortOrders = categories
    .filter((item) => item.type === type && (item.parentId ?? null) === parentId)
    .map((item) => item.sortOrder ?? 0)
  return (siblingSortOrders.length ? Math.max(...siblingSortOrders) : 0) + 10
}

export function canCategoryBeDeleted(category: Category, categories: Category[]) {
  return !category.system && !categories.some((item) => item.parentId === category.id)
}

export function isHiddenFixedExpenseCategory(category: Category) {
  return category.system && category.type === 'expense' && category.name === '固定支出'
}
