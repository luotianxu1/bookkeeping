<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  createFoodIngredient,
  deleteFoodIngredient,
  getFoodCategories,
  getFoodIngredients,
  type FoodCategory,
  type FoodIngredient,
  type SaveFoodIngredientParams,
  updateFoodIngredient,
} from '@/api/modules/food'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getFoodHomePath } from '../shared'

const route = useRoute()
const router = useRouter()

const categories = ref<FoodCategory[]>([])
const ingredients = ref<FoodIngredient[]>([])
const selectedCategoryId = ref<number | null>(null)
const keyword = ref('')
const pageError = ref('')
const isLoading = ref(false)
const isManageMode = ref(false)
const isSaving = ref(false)
const isDeleting = ref(false)
const showEditModal = ref(false)
const showDeleteConfirmModal = ref(false)
const editingIngredient = ref<FoodIngredient | null>(null)
const deletingIngredient = ref<FoodIngredient | null>(null)
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const formError = ref('')
const deleteError = ref('')
const formName = ref('')
const formCategoryId = ref('')
const formNote = ref('')

const ingredientModalTitle = computed(() => (editingIngredient.value ? '修改食材' : '新增食材'))

const categoryOptions = computed<CommonSelectOption[]>(() =>
  categories.value.map((item) => ({
    label: item.name,
    value: String(item.id),
  })),
)

const filteredIngredients = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return ingredients.value.filter((item) => {
    const categoryMatched = selectedCategoryId.value == null || item.categoryId === selectedCategoryId.value
    if (!categoryMatched) {
      return false
    }

    if (!search) {
      return true
    }

    return [item.name, item.categoryName, item.note ?? '']
      .join(' ')
      .toLowerCase()
      .includes(search)
  })
})

const groupedIngredients = computed(() =>
  categories.value
    .map((category) => ({
      category,
      items: filteredIngredients.value.filter((item) => item.categoryId === category.id),
    }))
    .filter((group) => group.items.length > 0),
)

onMounted(() => {
  void loadPage()
})

watch(
  () => route.query.categoryId,
  (value) => {
    const categoryId = Number(value)
    selectedCategoryId.value = Number.isFinite(categoryId) && categoryId > 0 ? categoryId : null
  },
)

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看食材列表'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [categoryList, ingredientList] = await Promise.all([
      getFoodCategories({
        userId: currentUser.id,
        categoryType: 'ingredient',
      }),
      getFoodIngredients({
        userId: currentUser.id,
        status: 'all',
      }),
    ])

    categories.value = categoryList
    ingredients.value = ingredientList
    const routeCategoryId = Number(route.query.categoryId)
    if (Number.isFinite(routeCategoryId) && routeCategoryId > 0) {
      selectedCategoryId.value = routeCategoryId
      return
    }
    selectedCategoryId.value = categoryList[0]?.id ?? null
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '食材列表加载失败'
  } finally {
    isLoading.value = false
  }
}

function selectCategory(categoryId: number) {
  selectedCategoryId.value = categoryId > 0 ? categoryId : null
  void router.replace({
    path: route.path,
    query: categoryId > 0 ? { categoryId } : {},
  })
}

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function openEditModal(item: FoodIngredient) {
  editingIngredient.value = item
  formName.value = item.name
  formCategoryId.value = String(item.categoryId)
  formNote.value = item.note ?? ''
  formError.value = ''
  showEditModal.value = true
}

function openCreateModal() {
  editingIngredient.value = null
  formName.value = ''
  formCategoryId.value = String(selectedCategoryId.value ?? categories.value[0]?.id ?? '')
  formNote.value = ''
  formError.value = ''
  showEditModal.value = true
}

function closeEditModal() {
  editingIngredient.value = null
  formError.value = ''
  showEditModal.value = false
}

function openDeleteConfirmModal(item: FoodIngredient) {
  deletingIngredient.value = item
  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function closeDeleteConfirmModal() {
  deletingIngredient.value = null
  deleteError.value = ''
  showDeleteConfirmModal.value = false
}

async function saveIngredient() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || isSaving.value) {
    return
  }

  const categoryId = Number(formCategoryId.value)
  const trimmedName = formName.value.trim()

  if (!trimmedName || !Number.isFinite(categoryId) || categoryId <= 0) {
    formError.value = '请先填写完整的基础信息'
    return
  }

  isSaving.value = true
  formError.value = ''

  try {
    const editingTarget = editingIngredient.value
    const payload: SaveFoodIngredientParams = {
      userId: editingTarget?.userId ?? currentUser.id,
      categoryId,
      name: trimmedName,
      stockAmount: editingTarget?.stockAmount ?? 0,
      unit: editingTarget?.unit ?? '项',
      reorderLevel: editingTarget?.reorderLevel ?? 0,
      storageLocation: null,
      status: editingTarget?.status ?? 'enough',
      note: formNote.value.trim() || null,
      sortOrder: editingTarget?.sortOrder ?? ingredients.value.length * 10 + 10,
    }

    const isEditing = editingTarget !== null
    if (isEditing) {
      await updateFoodIngredient(editingTarget.id, payload)
    } else {
      await createFoodIngredient(payload)
    }
    await loadPage()
    closeEditModal()
    showFeedback(isEditing ? '食材已修改' : '食材已新增', 'success')
  } catch (error) {
    const message = error instanceof Error ? error.message : (editingIngredient.value ? '食材修改失败' : '食材新增失败')
    formError.value = message
    showFeedback(message, 'error')
  } finally {
    isSaving.value = false
  }
}

async function confirmDeleteIngredient() {
  if (!deletingIngredient.value || isDeleting.value) {
    return
  }

  const deletingIngredientId = deletingIngredient.value.id
  isDeleting.value = true
  deleteError.value = ''

  try {
    await deleteFoodIngredient(deletingIngredientId)
    closeDeleteConfirmModal()
    if (editingIngredient.value?.id === deletingIngredientId) {
      closeEditModal()
    }
    await loadPage()
    showFeedback('食材已删除', 'success')
  } catch (error) {
    const message = error instanceof Error ? error.message : '食材删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}
</script>

<template>
  <section class="food-ingredient-page" aria-label="食材列表">
    <CommonLoading v-if="isLoading || isSaving" :text="isSaving ? '食材保存中...' : '食材加载中...'" />

    <template v-if="!pageError">
      <header class="page-head">
        <PageHeader title="食材列表" :back-to="getFoodHomePath()" />
        <CommonButton
          class="manage-link"
          variant="secondary"
          size="sm"
          :class="{ active: isManageMode }"
          @click="toggleManageMode"
        >
          {{ isManageMode ? '完成' : '修改' }}
        </CommonButton>
      </header>

      <div class="search-bar">
        <input v-model.trim="keyword" type="search" placeholder="搜索食材、备注" />
        <span>筛选</span>
      </div>

      <div class="category-row">
        <button
          type="button"
          :class="['category-pill', { active: selectedCategoryId === null }]"
          @click="selectCategory(0)"
        >
          全部
        </button>
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          :class="['category-pill', { active: selectedCategoryId === category.id }]"
          @click="selectCategory(category.id)"
        >
          {{ category.name }}
        </button>
      </div>

      <section class="ingredient-groups">
        <article v-for="group in groupedIngredients" :key="group.category.id" class="ingredient-card">
          <div class="group-head">
            <strong>{{ group.category.name }}</strong>
            <span>{{ group.items.length }} 项</span>
          </div>

          <div v-for="item in group.items" :key="item.id" class="ingredient-item">
            <button
              type="button"
              :class="['ingredient-row', 'ingredient-row-button', { editable: isManageMode }]"
              :disabled="!isManageMode"
              @click="openEditModal(item)"
            >
              <div class="ingredient-copy">
                <strong>{{ item.name }}</strong>
                <em>{{ item.note || '适合做工作日晚餐和快手菜' }}</em>
              </div>
            </button>

            <button
              v-if="isManageMode"
              class="ingredient-delete-trigger"
              type="button"
              @click="openDeleteConfirmModal(item)"
            >
              ×
            </button>
          </div>
        </article>

        <article v-if="groupedIngredients.length === 0" class="empty-state-card">
          <strong>还没有可展示的食材</strong>
          <span>可以先完善食材分类，或者切换到“全部”查看已记录食材。</span>
        </article>
      </section>

      <FloatingAddButton
        aria-label="新增食材"
        storage-key="food-ingredient-add"
        @click="openCreateModal"
      />
    </template>

    <p v-else class="page-error">{{ pageError }}</p>

    <CommonModal
      v-model="showEditModal"
      :title="ingredientModalTitle"
      size="compact"
      :show-close="false"
    >
      <div class="ingredient-form">
        <CommonInput v-model="formName" label="食材名称" placeholder="例如：番茄" />
        <CommonSelect v-model="formCategoryId" label="食材分类" :options="categoryOptions" />

        <label class="ingredient-form-field">
          <span>备注</span>
          <textarea v-model.trim="formNote" rows="3" placeholder="补充采购、保存或使用建议"></textarea>
        </label>

        <p v-if="formError" class="ingredient-form-error">{{ formError }}</p>
      </div>

      <template #footer>
        <div class="ingredient-modal-actions">
          <CommonButton variant="secondary" :disabled="isSaving" @click="closeEditModal">
            取消
          </CommonButton>
          <CommonButton :disabled="isSaving" @click="saveIngredient">
            {{ isSaving ? '保存中...' : editingIngredient ? '保存修改' : '保存新增' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :show-close="false"
    >
      <p class="ingredient-delete-message">确认删除“{{ deletingIngredient?.name }}”吗？</p>
      <p v-if="deleteError" class="ingredient-form-error">{{ deleteError }}</p>

      <template #footer>
        <div class="ingredient-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeleting" @click="closeDeleteConfirmModal">
            取消
          </CommonButton>
          <CommonButton :disabled="isDeleting" @click="confirmDeleteIngredient">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonFeedback v-model="feedbackVisible" :message="feedbackMessage" :type="feedbackType" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
