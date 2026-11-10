<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FloatingAddButton from '@/business/components/common/FloatingAddButton/index.vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import CategoryIcon from '@/business/components/common/CategoryIcon/index.vue'
import {
  createFoodCategory,
  deleteFoodCategory,
  getFoodCategories,
  updateFoodCategory,
  type FoodCategory,
  type SaveFoodCategoryParams,
} from '@/business/api/modules/food'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import {
  getFoodHomePath,
  getFoodIngredientCategoryCreatePath,
  getFoodIngredientCategoryPath,
  getFoodIngredientPath,
} from '../shared'
import { getErrorMessage } from '@/business/api/request'

const route = useRoute()
const router = useRouter()

const showModal = computed(() => route.path.includes('/new'))
const headerBackPath = computed(() =>
  showModal.value ? getFoodIngredientCategoryPath() : getFoodHomePath(),
)
const categoryModalTitle = computed(() =>
  editingCategory.value ? '修改食材分类' : '新增食材分类',
)
const categories = ref<FoodCategory[]>([])
const keyword = ref('')
const pageError = ref('')
const isLoading = ref(true)
const isSaving = ref(false)
const isDeleting = ref(false)
const isManageMode = ref(false)
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const deleteError = ref('')
const showDeleteConfirmModal = ref(false)
const editingCategory = ref<FoodCategory | null>(null)
const deletingCategory = ref<FoodCategory | null>(null)

const formName = ref('')
const formDescription = ref('')
const formIconText = ref('daily')
const formIconTone = ref<'blue' | 'purple' | 'orange' | 'sky' | 'green'>(
  'green',
)

const iconOptions: {
  text: string
  tone: typeof formIconTone.value
  label: string
}[] = [
  { text: 'food', label: '食材', tone: 'orange' },
  { text: 'daily', label: '日用', tone: 'green' },
  { text: 'other', label: '其他', tone: 'purple' },
]

const filteredCategories = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!search) {
    return categories.value
  }
  return categories.value.filter((item) =>
    [item.name, item.description ?? '']
      .join(' ')
      .toLowerCase()
      .includes(search),
  )
})

onMounted(() => {
  void loadCategories()
})

async function loadCategories() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看食材分类'
    isLoading.value = false
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    categories.value = await getFoodCategories({
      userId: currentUser.id,
      categoryType: 'ingredient',
    })
  } catch (error) {
    pageError.value = getErrorMessage(error, '食材分类加载失败')
  } finally {
    isLoading.value = false
  }
}

function openCategory(categoryId: number) {
  void router.push({
    path: getFoodIngredientPath(),
    query: { categoryId },
  })
}

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function handleCategoryClick(category: FoodCategory) {
  if (isManageMode.value) {
    openEditModal(category)
    return
  }

  openCategory(category.id)
}

function openCreateModal() {
  editingCategory.value = null
  resetForm()
  void router.push(getFoodIngredientCategoryCreatePath())
}

function openEditModal(category: FoodCategory) {
  editingCategory.value = category
  formName.value = category.name
  formDescription.value = category.description ?? ''
  formIconText.value = category.iconText
  formIconTone.value = category.iconTone as
    | 'blue'
    | 'purple'
    | 'orange'
    | 'sky'
    | 'green'
  void router.push(getFoodIngredientCategoryCreatePath())
}

function closeCategoryModal() {
  editingCategory.value = null
  resetForm()
  void router.push(getFoodIngredientCategoryPath())
}

function resetForm() {
  formName.value = ''
  formDescription.value = ''
  formIconText.value = 'daily'
  formIconTone.value = 'green'
}

function selectIconOption(
  text: string,
  tone: 'blue' | 'purple' | 'orange' | 'sky' | 'green',
) {
  formIconText.value = text
  formIconTone.value = tone
}

async function saveCategory() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser || isSaving.value) {
    return
  }

  const name = formName.value.trim()
  if (!name) {
    showFeedback('请输入分类名称', 'error')
    return
  }

  isSaving.value = true
  try {
    const payload: SaveFoodCategoryParams = {
      userId: editingCategory.value?.userId ?? currentUser.id,
      categoryType: 'ingredient',
      name,
      iconText: formIconText.value,
      iconTone: formIconTone.value,
      description: formDescription.value.trim() || null,
      sortOrder:
        editingCategory.value?.sortOrder ?? categories.value.length * 10 + 10,
      status: editingCategory.value?.status ?? 'active',
    }

    if (editingCategory.value) {
      await updateFoodCategory(editingCategory.value.id, payload)
      showFeedback('食材分类已修改', 'success')
    } else {
      await createFoodCategory(payload)
      showFeedback('食材分类已保存', 'success')
    }

    await loadCategories()
    closeCategoryModal()
  } catch (error) {
    showFeedback(getErrorMessage(error, '保存失败'), 'error')
  } finally {
    isSaving.value = false
  }
}

function openDeleteConfirmModal(category: FoodCategory) {
  deletingCategory.value = category
  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function closeDeleteConfirmModal() {
  deletingCategory.value = null
  deleteError.value = ''
  showDeleteConfirmModal.value = false
}

async function confirmDeleteCategory() {
  if (!deletingCategory.value || isDeleting.value) {
    return
  }

  const deletingCategoryId = deletingCategory.value.id
  isDeleting.value = true
  deleteError.value = ''

  try {
    await deleteFoodCategory(deletingCategoryId)
    closeDeleteConfirmModal()
    if (editingCategory.value?.id === deletingCategoryId) {
      closeCategoryModal()
    }
    showFeedback('食材分类已删除', 'success')
    await loadCategories()
  } catch (error) {
    const message = getErrorMessage(error, '删除失败')
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeleting.value = false
  }
}

function canDeleteCategory(category: FoodCategory) {
  return category.itemCount === 0
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}

type DesktopRow = (typeof filteredCategories.value)[number]
</script>
<template>
  <section class="pc-page">
    <PageHeader title="食材分类">
      <template #right>
        <el-button type="primary" @click="openCreateModal">新增分类</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    />
    <DesktopTable :rows="filteredCategories" :loading="isLoading">
      <template #toolbar>
        <el-input
          v-model="keyword"
          placeholder="搜索"
          clearable
          style="width: 260px"
        />
      </template>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="分类名称"
        prop="name"
        min-width="200"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="描述"
        prop="description"
        min-width="320"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="数量"
        prop="itemCount"
        min-width="120"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button link type="primary" @click="openCategory(row.id)">
              查看
            </el-button>
            <el-button link type="primary" @click="openEditModal(row)">
              编辑
            </el-button>
            <el-button
              link
              type="danger"
              :disabled="!canDeleteCategory(row)"
              @click="openDeleteConfirmModal(row)"
            >
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonModal
      :model-value="showModal"
      :title="categoryModalTitle"
      @update:model-value="!$event && closeCategoryModal()"
    >
      <el-form label-position="top" class="pc-form-grid">
        <el-form-item label="分类名称">
          <el-input v-model="formName" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formDescription" />
        </el-form-item>
        <el-form-item label="图标">
          <div class="pc-toolbar">
            <el-button
              v-for="option in iconOptions"
              :key="option.text"
              :type="formIconText === option.text ? 'primary' : 'default'"
              @click="selectIconOption(option.text, option.tone)"
            >
              <CategoryIcon
                :icon="option.text"
                :color="option.tone"
                :size="24"
              />
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeCategoryModal">取消</el-button>
        <el-button type="primary" :loading="isSaving" @click="saveCategory">
          保存
        </el-button>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showDeleteConfirmModal"
      title="确认删除"
      size="compact"
      :show-close="false"
    >
      <p class="delete-message">确认删除“{{ deletingCategory?.name }}”吗？</p>
      <p v-if="deleteError" class="form-error">{{ deleteError }}</p>
      <template #footer>
        <div class="pc-toolbar modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteConfirmModal"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="isDeleting" @click="confirmDeleteCategory">
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
