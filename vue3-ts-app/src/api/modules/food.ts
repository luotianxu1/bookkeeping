import { foodRequest, requestDelete, requestGet, requestPost, requestPut } from '@/api/request'

export type FoodCategoryType = 'dish' | 'ingredient'
export type FoodCategoryStatus = 'active' | 'archived'
export type FoodDishStatus = 'published' | 'pending' | 'draft'
export type FoodOrderStatus = 'planned' | 'preparing' | 'served'

export interface FoodManagementCard {
  key: string
  title: string
  description: string
  count: number
  path: string
}

export interface FoodRecentMenu {
  orderId: number
  title: string
  summary: string
  actionLabel: string
}

export interface FoodHomeData {
  heroTitle: string
  managementCards: FoodManagementCard[]
  recentMenus: FoodRecentMenu[]
}

export interface FoodCategory {
  id: number
  userId: number
  categoryType: FoodCategoryType | string
  name: string
  iconText: string
  iconTone: string
  description?: string | null
  sortOrder: number
  status: FoodCategoryStatus | string
  itemCount: number
  createdAt: string
  updatedAt: string
}

export interface FoodDishIngredient {
  id?: number
  ingredientId?: number | null
  ingredientName: string
  amount: string
  sortOrder?: number
}

export interface FoodDishStep {
  id?: number
  stepNo?: number
  content: string
}

export interface FoodDish {
  id: number
  userId: number
  categoryId: number
  categoryName: string
  name: string
  subtitle?: string | null
  description?: string | null
  tasteTags: string[]
  highlightTags: string[]
  cookMinutes: number
  servingCount: number
  coverTone: string
  coverText: string
  status: FoodDishStatus | string
  sortOrder: number
  ingredientPreview: string[]
  ingredients: FoodDishIngredient[]
  steps: FoodDishStep[]
  createdAt: string
  updatedAt: string
}

export interface FoodOrder {
  id: number
  userId: number
  title: string
  plannedFor: string
  remark?: string | null
  totalCookMinutes: number
  servingCount: number
  status: FoodOrderStatus | string
  dishCount: number
  dishNames: string[]
  createdAt: string
  updatedAt: string
}

export interface FoodCategoryQuery {
  userId: number
  categoryType?: FoodCategoryType
  keyword?: string
  status?: FoodCategoryStatus | 'all'
}

export interface FoodDishQuery {
  userId: number
  categoryId?: number
  keyword?: string
  status?: FoodDishStatus | 'all'
}

export interface FoodIngredient {
  id: number
  userId: number
  categoryId: number
  categoryName: string
  name: string
  stockAmount: number
  unit: string
  reorderLevel: number
  storageLocation?: string | null
  status: 'enough' | 'low' | 'urgent' | string
  note?: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface SaveFoodIngredientParams {
  userId: number
  categoryId: number
  name: string
  stockAmount?: number
  unit?: string
  reorderLevel?: number
  storageLocation?: string | null
  status?: 'enough' | 'low' | 'urgent' | string
  note?: string | null
  sortOrder?: number
}

export interface FoodIngredientQuery {
  userId: number
  categoryId?: number
  keyword?: string
  status?: 'enough' | 'low' | 'urgent' | 'all'
}

export interface FoodOrderQuery {
  userId: number
  keyword?: string
  status?: FoodOrderStatus | 'all'
}

export interface SaveFoodCategoryParams {
  userId: number
  categoryType: FoodCategoryType
  name: string
  iconText: string
  iconTone: string
  description?: string | null
  sortOrder?: number
  status?: FoodCategoryStatus | string
}

export interface SaveFoodDishParams {
  userId: number
  categoryId: number
  name: string
  subtitle?: string | null
  description?: string | null
  tasteTags?: string[]
  highlightTags?: string[]
  cookMinutes: number
  servingCount: number
  coverTone: string
  coverText: string
  status?: FoodDishStatus | string
  sortOrder?: number
  ingredients: FoodDishIngredient[]
  steps: FoodDishStep[]
}

export interface CreateFoodOrderParams {
  userId: number
  title?: string | null
  plannedFor?: string | null
  remark?: string | null
  dishIds: number[]
}

export function getFoodHome(userId: number) {
  return requestGet<FoodHomeData>(foodRequest, '/api/tools/food/home', {
    params: { userId },
  })
}

export function getFoodCategories(params: FoodCategoryQuery) {
  return requestGet<FoodCategory[]>(foodRequest, '/api/tools/food/categories', {
    params,
  })
}

export function createFoodCategory(params: SaveFoodCategoryParams) {
  return requestPost<FoodCategory, SaveFoodCategoryParams>(foodRequest, '/api/tools/food/categories', params)
}

export function updateFoodCategory(id: number, params: SaveFoodCategoryParams) {
  return requestPut<FoodCategory, SaveFoodCategoryParams>(foodRequest, `/api/tools/food/categories/${id}`, params)
}

export function deleteFoodCategory(id: number) {
  return requestDelete<void>(foodRequest, `/api/tools/food/categories/${id}`)
}

export function getFoodIngredients(params: FoodIngredientQuery) {
  return requestGet<FoodIngredient[]>(foodRequest, '/api/tools/food/ingredients', {
    params,
  })
}

export function createFoodIngredient(params: SaveFoodIngredientParams) {
  return requestPost<FoodIngredient, SaveFoodIngredientParams>(foodRequest, '/api/tools/food/ingredients', params)
}

export function updateFoodIngredient(id: number, params: SaveFoodIngredientParams) {
  return requestPut<FoodIngredient, SaveFoodIngredientParams>(foodRequest, `/api/tools/food/ingredients/${id}`, params)
}

export function deleteFoodIngredient(id: number) {
  return requestDelete<void>(foodRequest, `/api/tools/food/ingredients/${id}`)
}

export function getFoodDishes(params: FoodDishQuery) {
  return requestGet<FoodDish[]>(foodRequest, '/api/tools/food/dishes', {
    params,
  })
}

export function getFoodDish(id: number) {
  return requestGet<FoodDish>(foodRequest, `/api/tools/food/dishes/${id}`)
}

export function createFoodDish(params: SaveFoodDishParams) {
  return requestPost<FoodDish, SaveFoodDishParams>(foodRequest, '/api/tools/food/dishes', params)
}

export function updateFoodDish(id: number, params: SaveFoodDishParams) {
  return requestPut<FoodDish, SaveFoodDishParams>(foodRequest, `/api/tools/food/dishes/${id}`, params)
}

export function getFoodOrders(params: FoodOrderQuery) {
  return requestGet<FoodOrder[]>(foodRequest, '/api/tools/food/orders', {
    params,
  })
}

export function createFoodOrder(params: CreateFoodOrderParams) {
  return requestPost<FoodOrder, CreateFoodOrderParams>(foodRequest, '/api/tools/food/orders', params)
}
