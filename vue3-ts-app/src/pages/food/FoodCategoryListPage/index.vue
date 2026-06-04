<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  createFoodCategory,
  deleteFoodCategory,
  getFoodCategories,
  updateFoodCategory,
  type FoodCategory,
  type SaveFoodCategoryParams,
} from '@/api/modules/food'
import { getStoredCurrentUser } from '@/utils/current-user'
import {
  getFoodCategoryCreatePath,
  getFoodCategoryPath,
  getFoodMenuPath,
  isDarkFoodPath,
  mapIconToneToClass,
} from '../shared'

const route = useRoute()
const router = useRouter()

const isDark = computed(() => isDarkFoodPath(route.path))
const showModal = computed(() => route.path.includes('/categories/new'))
const categoryModalTitle = computed(() => (editingCategory.value ? '修改菜品分类' : '新增菜品分类'))
const categories = ref<FoodCategory[]>([])
const keyword = ref('')
const pageError = ref('')
const isLoading = ref(false)
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
const formIconText = ref('汤')
const formIconTone = ref<'blue' | 'purple' | 'orange' | 'sky' | 'green'>('blue')

const iconOptions = [
  { text: '凉', tone: 'blue' },
  { text: '锅', tone: 'orange' },
  { text: '拌', tone: 'green' },
]

const filteredCategories = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!search) {
    return categories.value
  }
  return categories.value.filter((item) =>
    [item.name, item.description ?? ''].join(' ').toLowerCase().includes(search),
  )
})

onMounted(() => {
  void loadCategories()
})

async function loadCategories() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看菜品分类'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    categories.value = await getFoodCategories({
      userId: currentUser.id,
      categoryType: 'dish',
    })
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '分类加载失败'
  } finally {
    isLoading.value = false
  }
}

function openCategory(categoryId: number) {
  void router.push({
    path: getFoodMenuPath(isDark.value),
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
  void router.push(getFoodCategoryCreatePath(isDark.value))
}

function openEditModal(category: FoodCategory) {
  editingCategory.value = category
  formName.value = category.name
  formDescription.value = category.description ?? ''
  formIconText.value = category.iconText
  formIconTone.value = category.iconTone as 'blue' | 'purple' | 'orange' | 'sky' | 'green'
  void router.push(getFoodCategoryCreatePath(isDark.value))
}

function closeCategoryModal() {
  editingCategory.value = null
  resetForm()
  void router.push(getFoodCategoryPath(isDark.value))
}

function resetForm() {
  formName.value = ''
  formDescription.value = ''
  formIconText.value = '汤'
  formIconTone.value = 'blue'
}

function selectIconOption(text: string, tone: 'blue' | 'purple' | 'orange' | 'sky' | 'green') {
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
      categoryType: 'dish',
      name,
      iconText: formIconText.value,
      iconTone: formIconTone.value,
      description: formDescription.value.trim() || null,
      sortOrder: editingCategory.value?.sortOrder ?? categories.value.length * 10 + 10,
      status: editingCategory.value?.status ?? 'active',
    }

    if (editingCategory.value) {
      await updateFoodCategory(editingCategory.value.id, payload)
      showFeedback('分类已修改', 'success')
    } else {
      await createFoodCategory(payload)
      showFeedback('分类已保存', 'success')
    }

    await loadCategories()
    closeCategoryModal()
  } catch (error) {
    showFeedback(error instanceof Error ? error.message : '保存失败', 'error')
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
    showFeedback('分类已删除', 'success')
    await loadCategories()
  } catch (error) {
    const message = error instanceof Error ? error.message : '删除失败'
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
</script>

<template>
  <section :class="['food-category-page', { dark: isDark }]" aria-label="菜品分类">
    <CommonLoading v-if="isLoading || isSaving" :text="isSaving ? '分类保存中...' : '分类加载中...'" />

    <template v-if="!pageError">
      <header class="page-head">
        <PageHeader title="菜品分类" :back-to="getFoodMenuPath(isDark)" />
        <CommonButton
          v-if="!showModal"
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
        <input v-model.trim="keyword" type="search" placeholder="搜索分类名称" />
        <span>筛选</span>
      </div>

      <div class="category-list">
        <div
          v-for="category in filteredCategories"
          :key="category.id"
          class="category-item"
        >
          <button
            type="button"
            :class="['category-row', { editable: isManageMode }]"
            @click="handleCategoryClick(category)"
          >
            <div :class="['category-icon', mapIconToneToClass(category.iconTone)]">
              {{ category.iconText }}
            </div>

            <div class="category-copy">
              <strong>{{ category.name }}</strong>
              <span>{{ category.itemCount }} 道菜 · {{ category.description }}</span>
            </div>

            <em>{{ isManageMode ? '编辑' : '>' }}</em>
          </button>

          <button
            v-if="isManageMode"
            class="category-delete-trigger"
            type="button"
            :disabled="!canDeleteCategory(category)"
            @click="openDeleteConfirmModal(category)"
          >
            ×
          </button>
        </div>
      </div>

      <FloatingAddButton
        v-if="!showModal"
        aria-label="新增菜品分类"
        storage-key="food-dish-category-add"
        @click="openCreateModal"
      />

      <div v-if="showModal" class="modal-mask">
        <section class="modal-card">
          <h2>{{ categoryModalTitle }}</h2>

          <label class="field-block">
            <span>分类名称</span>
            <input v-model.trim="formName" type="text" placeholder="例如：清爽凉菜" />
          </label>

          <div class="field-block">
            <span>分类图标</span>
            <div class="icon-row">
              <button
                v-for="option in iconOptions"
                :key="option.text"
                type="button"
                :class="['icon-pill', mapIconToneToClass(option.tone), { active: formIconText === option.text }]"
                @click="selectIconOption(option.text, option.tone as 'blue' | 'purple' | 'orange' | 'sky' | 'green')"
              >
                {{ option.text }}
              </button>
            </div>
          </div>

          <label class="field-block">
            <span>分类说明</span>
            <textarea v-model.trim="formDescription" rows="3" placeholder="城市蓝色的菜品更适合夏夜场景"></textarea>
          </label>

          <div class="modal-actions">
            <CommonButton variant="secondary" @click="closeCategoryModal">
              取消
            </CommonButton>
            <CommonButton :disabled="isSaving" @click="saveCategory">
              {{ editingCategory ? '保存修改' : '保存' }}
            </CommonButton>
          </div>
        </section>
      </div>

      <CommonModal
        v-model="showDeleteConfirmModal"
        title="确认删除"
        size="compact"
        :show-close="false"
      >
        <p class="delete-message">确认删除“{{ deletingCategory?.name }}”吗？</p>
        <p v-if="deleteError" class="form-error">{{ deleteError }}</p>

        <template #footer>
          <div class="modal-actions">
            <CommonButton variant="secondary" :disabled="isDeleting" @click="closeDeleteConfirmModal">
              取消
            </CommonButton>
            <CommonButton :disabled="isDeleting" @click="confirmDeleteCategory">
              {{ isDeleting ? '删除中...' : '确认删除' }}
            </CommonButton>
          </div>
        </template>
      </CommonModal>
    </template>

    <p v-else class="page-error">{{ pageError }}</p>

    <CommonFeedback v-model="feedbackVisible" :message="feedbackMessage" :type="feedbackType" />
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
