<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  createFoodDish,
  getFoodCategories,
  type FoodCategory,
} from '@/api/modules/food'
import { getStoredCurrentUser } from '@/utils/current-user'
import {
  buildDefaultFoodCover,
  getFoodDishDetailPath,
  getFoodDishListPath,
  isDarkFoodPath,
} from '../shared'

type EditableIngredient = {
  ingredientName: string
  amount: string
}

const route = useRoute()
const router = useRouter()

const isDark = computed(() => isDarkFoodPath(route.path))
const categories = ref<FoodCategory[]>([])
const isLoading = ref(false)
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
  { ingredientName: '牛腩', amount: '500g' },
  { ingredientName: '番茄', amount: '2个' },
  { ingredientName: '洋葱 / 调味料', amount: '适量' },
])
const steps = ref<string[]>([
  '牛肉焯水后沥干，番茄切块，洋葱切丝备用。',
  '锅中翻炒洋葱和番茄，加入牛肉和清水，小火炖煮 30 分钟。',
  '加入盐和黑胡椒调味，收汁后装盘，可搭配米饭或意面。',
])

const selectedCategory = computed(() => categories.value.find((item) => item.id === categoryId.value) ?? null)
const categoryOptions = computed<CommonSelectOption[]>(() =>
  categories.value.map((category) => ({
    label: category.name,
    value: String(category.id),
  })),
)
const categorySelectValue = computed({
  get: () => categoryId.value == null ? '' : String(categoryId.value),
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
    pageError.value = error instanceof Error ? error.message : '分类加载失败'
  } finally {
    isLoading.value = false
  }
}

function addIngredientRow() {
  ingredients.value.push({ ingredientName: '', amount: '' })
}

function removeIngredientRow(index: number) {
  ingredients.value.splice(index, 1)
}

function addStepRow() {
  steps.value.push('')
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

  const normalizedSteps = steps.value.map((item) => item.trim()).filter(Boolean)

  if (normalizedIngredients.length === 0 || normalizedSteps.length === 0) {
    showFeedback('请至少填写一项食材和一个步骤', 'error')
    return
  }

  const { coverText, coverTone } = buildDefaultFoodCover(trimmedName, selectedCategory.value?.iconTone ?? 'blue')

  isSaving.value = true
  try {
    const created = await createFoodDish({
      userId: currentUser.id,
      categoryId: categoryId.value,
      name: trimmedName,
      subtitle: selectedCategory.value ? `${selectedCategory.value.description ?? '家常风味'}，${cookMinutes.value}分钟上桌` : null,
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
    showFeedback(error instanceof Error ? error.message : '菜品保存失败', 'error')
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
  <section :class="['food-create-page', { dark: isDark }]" aria-label="新增菜品">
    <CommonLoading v-if="isLoading || isSaving" :text="isSaving ? '菜品保存中...' : '页面加载中...'" />

    <template v-if="!pageError">
      <PageHeader title="新增菜品" class="page-head" :back-to="getFoodDishListPath(isDark)" />

      <section class="form-card">
        <h2>基础信息</h2>

        <label class="field-block">
          <span>菜品名称</span>
          <input v-model.trim="name" type="text" placeholder="例如：番茄牛腩锅" />
        </label>

        <div class="field-grid">
          <div class="field-block">
            <CommonSelect
              v-model="categorySelectValue"
              label="分类"
              :options="categoryOptions"
            />
          </div>

          <label class="field-block">
            <span>口味标签</span>
            <input v-model.trim="tasteTags" type="text" placeholder="酸甜 / 浓郁" />
          </label>
        </div>

        <label class="field-block">
          <span>预计时间</span>
          <input v-model.trim="cookMinutes" type="number" min="1" />
        </label>

        <label class="field-block">
          <span>菜品图片</span>
          <div class="upload-box">
            <strong>上传成品图</strong>
            <span>支持 3 张图片，点菜页会展示主图</span>
          </div>
        </label>

        <label class="field-block">
          <span>菜品介绍</span>
          <textarea v-model.trim="description" rows="3" placeholder="描述这道菜的口感、适合场景与搭配建议"></textarea>
        </label>
      </section>

      <section class="form-card">
        <div class="section-head">
          <h2>所需食材</h2>
          <button type="button" class="ghost-link" @click="addIngredientRow">添加一项</button>
        </div>

        <div v-for="(item, index) in ingredients" :key="`ingredient-${index}`" class="mini-row">
          <input v-model.trim="item.ingredientName" type="text" placeholder="食材名称" />
          <input v-model.trim="item.amount" type="text" placeholder="数量" />
          <button type="button" class="row-remove" @click="removeIngredientRow(index)">
            删除
          </button>
        </div>
      </section>

      <section class="form-card">
        <div class="section-head">
          <h2>做法步骤</h2>
          <button type="button" class="ghost-link" @click="addStepRow">添加步骤</button>
        </div>

        <div v-for="(_, index) in steps" :key="`step-${index}`" class="step-box">
          <span>{{ index + 1 }}</span>
          <textarea v-model.trim="steps[index]" rows="2" placeholder="填写步骤内容"></textarea>
          <button type="button" class="row-remove step-remove" @click="removeStepRow(index)">
            删除
          </button>
        </div>
      </section>

      <div class="action-bar">
        <CommonButton variant="secondary" @click="router.push(getFoodDishListPath(isDark))">
          取消
        </CommonButton>
        <CommonButton :disabled="isSaving" @click="saveDish">
          保存菜品
        </CommonButton>
      </div>
    </template>

    <p v-else class="page-error">{{ pageError }}</p>

    <CommonFeedback v-model="feedbackVisible" :message="feedbackMessage" :type="feedbackType" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
