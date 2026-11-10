<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'
import FloatingAddButton from '@/business/components/common/FloatingAddButton/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import {
  createFoodIngredient,
  deleteFoodIngredient,
  getFoodCategories,
  getFoodIngredients,
  type FoodCategory,
  type FoodIngredient,
  type SaveFoodIngredientParams,
  updateFoodIngredient,
} from '@/business/api/modules/food'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getFoodHomePath } from '../shared'
import { getErrorMessage } from '@/business/api/request'

const route = useRoute()
const router = useRouter()

const categories = ref<FoodCategory[]>([])
const ingredients = ref<FoodIngredient[]>([])
const selectedCategoryId = ref<number | null>(null)
const keyword = ref('')
const pageError = ref('')
const isLoading = ref(true)
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

const ingredientModalTitle = computed(() =>
  editingIngredient.value ? '修改食材' : '新增食材',
)

const categoryOptions = computed<CommonSelectOption[]>(() =>
  categories.value.map((item) => ({
    label: item.name,
    value: String(item.id),
  })),
)

const filteredIngredients = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return ingredients.value.filter((item) => {
    const categoryMatched =
      selectedCategoryId.value == null ||
      item.categoryId === selectedCategoryId.value
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
      items: filteredIngredients.value.filter(
        (item) => item.categoryId === category.id,
      ),
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
    selectedCategoryId.value =
      Number.isFinite(categoryId) && categoryId > 0 ? categoryId : null
  },
)

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看食材列表'
    isLoading.value = false
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
    pageError.value = getErrorMessage(error, '食材列表加载失败')
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
  formCategoryId.value = String(
    selectedCategoryId.value ?? categories.value[0]?.id ?? '',
  )
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
    const message = getErrorMessage(
      error,
      editingIngredient.value ? '食材修改失败' : '食材新增失败',
    )
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
    const message = getErrorMessage(error, '食材删除失败')
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

type DesktopRow = (typeof filteredIngredients.value)[number]
</script>
<template>
  <section class="pc-page">
    <PageHeader title="食材管理">
      <template #right>
        <el-button type="primary" @click="openCreateModal">新增食材</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    />
    <DesktopTable :rows="filteredIngredients" :loading="isLoading">
      <template #toolbar>
        <el-input
          v-model="keyword"
          placeholder="搜索"
          clearable
          style="width: 260px"
        />
        <el-select
          v-model="selectedCategoryId"
          placeholder="全部分类"
          clearable
          @clear="selectedCategoryId = null"
        >
          <el-option
            v-for="category in categories"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </el-select>
      </template>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="食材"
        prop="name"
        min-width="180"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="分类"
        prop="categoryName"
        min-width="140"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="备注"
        prop="note"
        min-width="300"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button link type="primary" @click="openEditModal(row)">
              编辑
            </el-button>
            <el-button link type="danger" @click="openDeleteConfirmModal(row)">
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonModal
      v-model="showEditModal"
      :title="ingredientModalTitle"
      size="compact"
      :show-close="false"
    >
      <div class="pc-form-grid ingredient-form">
        <CommonInput
          v-model="formName"
          label="食材名称"
          placeholder="例如：番茄"
        />
        <CommonSelect
          v-model="formCategoryId"
          label="食材分类"
          :options="categoryOptions"
        />
        <label class="pc-field ingredient-form-field">
          <span>备注</span>
          <DesktopInput
            v-model.trim="formNote"
            rows="3"
            placeholder="补充采购、保存或使用建议"
            type="textarea"
          ></DesktopInput>
        </label>
        <p v-if="formError" class="ingredient-form-error">{{ formError }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar ingredient-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSaving"
            @click="closeEditModal"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="isSaving" @click="saveIngredient">
            {{
              isSaving
                ? '保存中...'
                : editingIngredient
                  ? '保存修改'
                  : '保存新增'
            }}
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
      <p class="ingredient-delete-message">
        确认删除“{{ deletingIngredient?.name }}”吗？
      </p>
      <p v-if="deleteError" class="ingredient-form-error">{{ deleteError }}</p>
      <template #footer>
        <div class="pc-toolbar ingredient-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteConfirmModal"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="isDeleting" @click="confirmDeleteIngredient">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
  </section>
</template>
