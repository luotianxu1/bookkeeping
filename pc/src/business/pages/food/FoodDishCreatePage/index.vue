<script setup lang="ts">
import PagePanel from '@/business/components/PagePanel.vue'

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  createFoodDish,
  getFoodCategories,
  type FoodCategory,
} from '@/business/api/modules/food'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import {
  buildDefaultFoodCover,
  getFoodDishDetailPath,
  getFoodDishListPath,
  isDarkFoodPath,
} from '../shared'
import { getErrorMessage } from '@/business/api/request'

type EditableIngredient = {
  uid: number
  ingredientName: string
  amount: string
}

type EditableStep = {
  uid: number
  text: string
}

// 行级自增 uid：保证删除中间行时 v-for 复用正确的组件状态
let rowUidCounter = 0
function nextRowUid() {
  rowUidCounter += 1
  return rowUidCounter
}

const route = useRoute()
const router = useRouter()

const isDark = computed(() => isDarkFoodPath(route.path))
const categories = ref<FoodCategory[]>([])
const isLoading = ref(true)
const isSaving = ref(false)
const pageError = ref('')
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const name = ref('')
const categoryId = ref<number | null>(null)
const tasteTags = ref('')
const description = ref('')
const cookMinutes = ref('45')
const ingredients = ref<EditableIngredient[]>([
  { uid: nextRowUid(), ingredientName: '牛腩', amount: '500g' },
  { uid: nextRowUid(), ingredientName: '番茄', amount: '2个' },
  { uid: nextRowUid(), ingredientName: '洋葱 / 调味料', amount: '适量' },
])
const steps = ref<EditableStep[]>([
  { uid: nextRowUid(), text: '牛肉焯水后沥干，番茄切块，洋葱切丝备用。' },
  {
    uid: nextRowUid(),
    text: '锅中翻炒洋葱和番茄，加入牛肉和清水，小火炖煮 30 分钟。',
  },
  {
    uid: nextRowUid(),
    text: '加入盐和黑胡椒调味，收汁后装盘，可搭配米饭或意面。',
  },
])

const selectedCategory = computed(
  () => categories.value.find((item) => item.id === categoryId.value) ?? null,
)
const categoryOptions = computed<CommonSelectOption[]>(() =>
  categories.value.map((category) => ({
    label: category.name,
    value: String(category.id),
  })),
)
const categorySelectValue = computed({
  get: () => (categoryId.value == null ? '' : String(categoryId.value)),
  set: (value: string) => {
    const parsed = Number(value)
    categoryId.value = Number.isFinite(parsed) && parsed > 0 ? parsed : null
  },
})

onMounted(() => {
  void loadCategories()
})

async function loadCategories() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后新增菜品'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    categories.value = await getFoodCategories({
      userId: currentUser.id,
      categoryType: 'dish',
    })
    categoryId.value = categories.value[0]?.id ?? null
  } catch (error) {
    pageError.value = getErrorMessage(error, '分类加载失败')
  } finally {
    isLoading.value = false
  }
}

function addIngredientRow() {
  ingredients.value.push({ uid: nextRowUid(), ingredientName: '', amount: '' })
}

function removeIngredientRow(index: number) {
  ingredients.value.splice(index, 1)
}

function addStepRow() {
  steps.value.push({ uid: nextRowUid(), text: '' })
}

function removeStepRow(index: number) {
  steps.value.splice(index, 1)
}

async function saveDish() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    showFeedback('请先登录后保存菜品', 'error')
    return
  }

  const trimmedName = name.value.trim()
  if (!trimmedName || !categoryId.value) {
    showFeedback('请先填写完整的基础信息', 'error')
    return
  }

  const normalizedIngredients = ingredients.value
    .map((item) => ({
      ingredientName: item.ingredientName.trim(),
      amount: item.amount.trim(),
    }))
    .filter((item) => item.ingredientName && item.amount)

  const normalizedSteps = steps.value
    .map((item) => item.text.trim())
    .filter(Boolean)

  if (normalizedIngredients.length === 0 || normalizedSteps.length === 0) {
    showFeedback('请至少填写一项食材和一个步骤', 'error')
    return
  }

  const { coverText, coverTone } = buildDefaultFoodCover(
    trimmedName,
    selectedCategory.value?.iconTone ?? 'blue',
  )

  isSaving.value = true
  try {
    const created = await createFoodDish({
      userId: currentUser.id,
      categoryId: categoryId.value,
      name: trimmedName,
      subtitle: selectedCategory.value
        ? `${selectedCategory.value.description ?? '家常风味'}，${cookMinutes.value}分钟上桌`
        : null,
      description: description.value.trim() || null,
      tasteTags: tasteTags.value
        .split(/[、,/\s]+/)
        .map((item) => item.trim())
        .filter(Boolean),
      highlightTags: ['适合晚餐', '家常菜', '新上架'],
      cookMinutes: Number(cookMinutes.value) || 1,
      coverTone,
      coverText,
      ingredients: normalizedIngredients,
      steps: normalizedSteps.map((item) => ({ content: item })),
    })

    showFeedback('菜品已保存', 'success')
    await router.replace(getFoodDishDetailPath(isDark.value, created.id))
  } catch (error) {
    showFeedback(getErrorMessage(error, '菜品保存失败'), 'error')
  } finally {
    isSaving.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}
</script>
<template>
  <section class="desktop-workspace">
    <PageHeader title="新增菜品">
      <template #right>
        <el-button @click="router.push(getFoodDishListPath(isDark))">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="isSaving"
          :disabled="isLoading || !!pageError"
          @click="saveDish"
        >
          保存菜品
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    ></el-alert>
    <div class="desktop-main-aside">
      <div class="desktop-results">
        <PagePanel title="基本信息">
          <el-form label-position="top">
            <div class="desktop-form-grid">
              <el-form-item label="菜品名称">
                <el-input v-model.trim="name" />
              </el-form-item>
              <CommonSelect
                v-model="categorySelectValue"
                label="分类"
                :options="categoryOptions"
              />
              <el-form-item label="口味标签">
                <el-input v-model.trim="tasteTags" />
              </el-form-item>
              <el-form-item label="烹饪时间（分钟）">
                <el-input v-model.trim="cookMinutes" type="number" min="1" />
              </el-form-item>
              <el-form-item label="菜品介绍" class="desktop-span-all">
                <el-input
                  v-model.trim="description"
                  type="textarea"
                  :rows="3"
                />
              </el-form-item>
            </div>
          </el-form>
        </PagePanel>
        <PagePanel title="做法步骤">
          <template #actions>
            <el-button @click="addStepRow">添加步骤</el-button>
          </template>
          <div class="desktop-recipe-steps">
            <div
              v-for="(item, index) in steps"
              :key="item.uid"
              class="desktop-recipe-step"
            >
              <el-tag>{{ index + 1 }}</el-tag>
              <el-input
                v-model.trim="item.text"
                type="textarea"
                :rows="2"
                placeholder="填写步骤"
              />
              <el-button
                link
                type="danger"
                @click="removeStepRow(steps.indexOf(item))"
              >
                删除
              </el-button>
            </div>
          </div>
        </PagePanel>
      </div>
      <PagePanel title="所需食材">
        <template #actions>
          <el-button @click="addIngredientRow">添加食材</el-button>
        </template>
        <div
          v-for="item in ingredients"
          :key="item.uid"
          class="desktop-ingredient-row"
        >
          <el-input v-model.trim="item.ingredientName" placeholder="食材" />
          <el-input v-model.trim="item.amount" placeholder="数量" />
          <el-button
            link
            type="danger"
            @click="removeIngredientRow(ingredients.indexOf(item))"
          >
            删除
          </el-button>
        </div>
      </PagePanel>
    </div>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
