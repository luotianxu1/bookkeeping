<script setup lang="ts">
// 分类管理页：使用财务模块分类接口完成列表、新增、修改和删除。
import { computed, onMounted, ref, watch } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import {
  createCategory,
  deleteCategory,
  getCategories,
  updateCategory,
  type Category,
  type SaveCategoryParams,
} from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'

type CategoryGroup = {
  parent: Category
  children: Category[]
}

const tabOptions = ['全部', '支出', '收入']
const activeTab = ref(tabOptions[0])
const ROOT_PARENT_VALUE = 'root'

const isManageMode = ref(false)
const categories = ref<Category[]>([])
const showCategoryModal = ref(false)
const showDeleteConfirmModal = ref(false)
const editingCategory = ref<Category | null>(null)
const deletingCategory = ref<Category | null>(null)
const isLoadingCategories = ref(false)
const isSavingCategory = ref(false)
const isDeletingCategory = ref(false)
const categoryListError = ref('')
const categoryFormError = ref('')
const deleteError = ref('')
const showFeedbackModal = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const formName = ref('')
const formType = ref<'expense' | 'income'>('expense')
const formIcon = ref('other')
const formColor = ref('#334155')
const formRemark = ref('')
const formParentId = ref(ROOT_PARENT_VALUE)

const categoryTypeOptions: CommonSelectOption[] = [
  { label: '支出', value: 'expense' },
  { label: '收入', value: 'income' },
]

const categoryIconOptions: CommonSelectOption[] = [
  { label: '餐饮', value: 'food' },
  { label: '日用', value: 'daily' },
  { label: '交通', value: 'transport' },
  { label: '娱乐', value: 'entertainment' },
  { label: '购物', value: 'shopping' },
  { label: '工资', value: 'salary' },
  { label: '理财', value: 'investment-income' },
  { label: '其他', value: 'other' },
]

const categoryModalTitle = computed(() => (editingCategory.value ? '修改分类' : '新增分类'))
const expenseCategoryGroups = computed(() => buildCategoryGroups('expense'))
const incomeCategoryGroups = computed(() => buildCategoryGroups('income'))
const editingCategoryHasChildren = computed(() => (
  editingCategory.value !== null &&
  categories.value.some((item) => item.parentId === editingCategory.value?.id)
))
const parentCategoryOptions = computed<CommonSelectOption[]>(() => [
  { label: '无（一级分类）', value: ROOT_PARENT_VALUE },
  ...categories.value
    .filter((item) => item.type === formType.value && !item.parentId && item.id !== editingCategory.value?.id)
    .map((item) => ({
      label: item.name,
      value: String(item.id),
    })),
])

onMounted(() => {
  loadCategories()
})

watch(formType, () => {
  if (formParentId.value === ROOT_PARENT_VALUE) {
    return
  }

  const exists = categories.value.some((item) => (
    item.type === formType.value &&
    !item.parentId &&
    item.id !== editingCategory.value?.id &&
    String(item.id) === formParentId.value
  ))

  if (!exists) {
    formParentId.value = ROOT_PARENT_VALUE
  }
})

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function openCreateModal(parent?: Category) {
  editingCategory.value = null
  resetForm()
  if (parent) {
    formType.value = parent.type
    formParentId.value = String(parent.id)
  }
  showCategoryModal.value = true
}

function openEditModal(category: Category) {
  if (!isManageMode.value) {
    return
  }

  editingCategory.value = category
  formName.value = category.name
  formType.value = category.type
  formIcon.value = category.icon
  formColor.value = category.color || '#334155'
  formRemark.value = category.remark || ''
  formParentId.value = category.parentId ? String(category.parentId) : ROOT_PARENT_VALUE
  categoryFormError.value = ''
  showCategoryModal.value = true
}

function closeCategoryModal() {
  showCategoryModal.value = false
  editingCategory.value = null
  resetForm()
}

function resetForm() {
  formName.value = ''
  formType.value = 'expense'
  formIcon.value = 'other'
  formColor.value = '#334155'
  formRemark.value = ''
  formParentId.value = ROOT_PARENT_VALUE
  categoryFormError.value = ''
}

async function loadCategories() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    categoryListError.value = '请先登录后查看分类'
    return
  }

  isLoadingCategories.value = true
  categoryListError.value = ''

  try {
    categories.value = await getCategories({
      userId: currentUser.id,
      status: 'active',
    })
  } catch (error) {
    categoryListError.value = error instanceof Error ? error.message : '分类列表加载失败'
  } finally {
    isLoadingCategories.value = false
  }
}

async function saveCategory() {
  if (isSavingCategory.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const trimmedName = formName.value.trim()
  const trimmedRemark = formRemark.value.trim()

  if (!currentUser) {
    categoryFormError.value = '请先登录后再保存分类'
    return
  }

  if (!trimmedName) {
    categoryFormError.value = '请输入分类名称'
    return
  }

  isSavingCategory.value = true
  categoryFormError.value = ''

  try {
    const parentId = formParentId.value === ROOT_PARENT_VALUE ? null : Number(formParentId.value)
    const payload: SaveCategoryParams = {
      userId: editingCategory.value?.userId ?? currentUser.id,
      name: trimmedName,
      type: formType.value,
      icon: formIcon.value,
      color: formColor.value,
      parentId,
      system: editingCategory.value?.system ?? false,
      sortOrder: editingCategory.value?.sortOrder ?? getNextCategorySortOrder(formType.value, parentId),
      status: 'active',
      remark: trimmedRemark || null,
    }

    if (editingCategory.value) {
      await updateCategory(editingCategory.value.id, payload)
      showFeedback('修改成功', 'success')
    } else {
      await createCategory(payload)
      showFeedback('新增成功', 'success')
    }

    closeCategoryModal()
    await loadCategories()
  } catch (error) {
    const message = error instanceof Error ? error.message : '分类保存失败'
    categoryFormError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingCategory.value = false
  }
}

function openDeleteConfirmModal(category: Category) {
  deletingCategory.value = category
  deleteError.value = ''
  showDeleteConfirmModal.value = true
}

function closeDeleteConfirmModal() {
  showDeleteConfirmModal.value = false
  deletingCategory.value = null
  deleteError.value = ''
}

async function confirmDeleteCategory() {
  if (!deletingCategory.value || isDeletingCategory.value) {
    return
  }

  isDeletingCategory.value = true
  deleteError.value = ''

  try {
    await deleteCategory(deletingCategory.value.id)
    closeDeleteConfirmModal()
    showFeedback('删除成功', 'success')
    await loadCategories()
  } catch (error) {
    const message = error instanceof Error ? error.message : '分类删除失败'
    deleteError.value = message
    showFeedback(message, 'error')
  } finally {
    isDeletingCategory.value = false
  }
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedbackModal.value = true
}

function buildCategoryGroups(type: 'expense' | 'income') {
  const roots = categories.value.filter((item) => item.type === type && !item.parentId)
  return roots.map<CategoryGroup>((parent) => ({
    parent,
    children: categories.value.filter((item) => item.parentId === parent.id),
  }))
}

function getNextCategorySortOrder(type: 'expense' | 'income', parentId: number | null) {
  const siblingSortOrders = categories.value
    .filter((item) => item.type === type && (item.parentId ?? null) === parentId)
    .map((item) => item.sortOrder ?? 0)
  return (siblingSortOrders.length ? Math.max(...siblingSortOrders) : 0) + 10
}

function getCategoryIcon(icon: string) {
  const iconMap: Record<string, string> = {
    food: '餐',
    daily: '日',
    transport: '行',
    entertainment: '娱',
    shopping: '购',
    salary: '薪',
    'investment-income': '利',
    other: '其',
  }

  return iconMap[icon] ?? icon.slice(0, 1)
}

function getCategoryIconStyle(category: Category) {
  return {
    backgroundColor: category.color || '#334155',
  }
}

function canDeleteCategory(category: Category) {
  return !categories.value.some((item) => item.parentId === category.id)
}
</script>

<template>
  <section class="category-management-page" aria-label="分类管理">
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />

    <header class="category-management-header">
      <PageHeader title="分类管理" back-to="/finance/entry/expense" back-label="返回记一笔">
        <template #right>
          <button
            class="category-management-manage"
            type="button"
            :class="{ active: isManageMode }"
            @click="toggleManageMode"
          >
            {{ isManageMode ? '完成' : '管理' }}
          </button>
        </template>
      </PageHeader>
    </header>

    <SegmentedControl v-model="activeTab" :options="tabOptions" label="分类筛选" />

    <p v-if="categoryListError" class="category-list-message category-list-message-error">
      {{ categoryListError }}
    </p>
    <CommonLoading v-else-if="isLoadingCategories" />
    <p v-else-if="categories.length === 0" class="category-list-message">
      暂无分类
    </p>

    <section v-else class="category-card" aria-label="分类内容">
      <template v-if="activeTab !== '收入'">
        <h2>支出分类</h2>
        <p v-if="expenseCategoryGroups.length === 0" class="category-section-empty">暂无支出分类</p>
        <div v-else class="category-group-list">
          <section v-for="group in expenseCategoryGroups" :key="group.parent.id" class="category-group">
            <div class="category-group-header">
              <article class="category-item category-item-parent">
                <button
                  type="button"
                  :class="['category-tile', 'category-tile-parent', { editable: isManageMode }]"
                  :disabled="!isManageMode"
                  :aria-disabled="!isManageMode"
                  @click="openEditModal(group.parent)"
                >
                  <span class="category-icon" :style="getCategoryIconStyle(group.parent)">
                    {{ getCategoryIcon(group.parent.icon) }}
                  </span>
                  <strong>{{ group.parent.name }}</strong>
                </button>
                <button
                  v-if="isManageMode"
                  type="button"
                  class="category-delete-trigger"
                  :aria-label="`删除${group.parent.name}`"
                  :disabled="!canDeleteCategory(group.parent)"
                  @click="openDeleteConfirmModal(group.parent)"
                >
                  ×
                </button>
              </article>
              <button
                v-if="isManageMode"
                type="button"
                class="category-child-create"
                @click="openCreateModal(group.parent)"
              >
                新增二级
              </button>
            </div>

            <div v-if="group.children.length > 0" class="category-grid category-grid-children">
              <article v-for="item in group.children" :key="item.id" class="category-item">
                <button
                  type="button"
                  :class="['category-tile', { editable: isManageMode }]"
                  :disabled="!isManageMode"
                  :aria-disabled="!isManageMode"
                  @click="openEditModal(item)"
                >
                  <span class="category-icon" :style="getCategoryIconStyle(item)">
                    {{ getCategoryIcon(item.icon) }}
                  </span>
                  <strong>{{ item.name }}</strong>
                </button>
                <button
                  v-if="isManageMode"
                  type="button"
                  class="category-delete-trigger"
                  :aria-label="`删除${item.name}`"
                  @click="openDeleteConfirmModal(item)"
                >
                  ×
                </button>
              </article>
            </div>
          </section>
        </div>
      </template>

      <div v-if="activeTab === '全部'" class="category-divider"></div>

      <template v-if="activeTab !== '支出'">
        <h2>收入分类</h2>
        <p v-if="incomeCategoryGroups.length === 0" class="category-section-empty">暂无收入分类</p>
        <div v-else class="category-group-list">
          <section v-for="group in incomeCategoryGroups" :key="group.parent.id" class="category-group">
            <div class="category-group-header">
              <article class="category-item category-item-parent">
                <button
                  type="button"
                  :class="['category-tile', 'category-tile-parent', { editable: isManageMode }]"
                  :disabled="!isManageMode"
                  :aria-disabled="!isManageMode"
                  @click="openEditModal(group.parent)"
                >
                  <span class="category-icon" :style="getCategoryIconStyle(group.parent)">
                    {{ getCategoryIcon(group.parent.icon) }}
                  </span>
                  <strong>{{ group.parent.name }}</strong>
                </button>
                <button
                  v-if="isManageMode"
                  type="button"
                  class="category-delete-trigger"
                  :aria-label="`删除${group.parent.name}`"
                  :disabled="!canDeleteCategory(group.parent)"
                  @click="openDeleteConfirmModal(group.parent)"
                >
                  ×
                </button>
              </article>
              <button
                v-if="isManageMode"
                type="button"
                class="category-child-create"
                @click="openCreateModal(group.parent)"
              >
                新增二级
              </button>
            </div>

            <div v-if="group.children.length > 0" class="category-grid category-grid-children">
              <article v-for="item in group.children" :key="item.id" class="category-item">
                <button
                  type="button"
                  :class="['category-tile', { editable: isManageMode }]"
                  :disabled="!isManageMode"
                  :aria-disabled="!isManageMode"
                  @click="openEditModal(item)"
                >
                  <span class="category-icon" :style="getCategoryIconStyle(item)">
                    {{ getCategoryIcon(item.icon) }}
                  </span>
                  <strong>{{ item.name }}</strong>
                </button>
                <button
                  v-if="isManageMode"
                  type="button"
                  class="category-delete-trigger"
                  :aria-label="`删除${item.name}`"
                  @click="openDeleteConfirmModal(item)"
                >
                  ×
                </button>
              </article>
            </div>
          </section>
        </div>
      </template>
    </section>

    <FloatingAddButton aria-label="新增分类" @click="openCreateModal" />

    <CommonModal v-model="showCategoryModal" :title="categoryModalTitle">
      <form class="category-create-form" @submit.prevent="saveCategory">
        <CommonInput v-model="formName" label="分类名称" placeholder="请输入分类名称" />
        <CommonSelect v-model="formType" label="分类类型" :options="categoryTypeOptions" />
        <CommonSelect
          v-model="formParentId"
          label="上级分类"
          :options="parentCategoryOptions"
          :disabled="editingCategoryHasChildren"
        />
        <CommonSelect v-model="formIcon" label="分类图标" :options="categoryIconOptions" />
        <div class="category-color-field">
          <span class="category-color-label">分类颜色</span>
          <div class="category-color-picker-row">
            <input
              id="category-color-picker"
              v-model="formColor"
              class="category-color-picker"
              type="color"
              aria-label="选择分类颜色"
            />
            <span class="category-color-value">{{ formColor.toUpperCase() }}</span>
          </div>
        </div>
        <CommonInput v-model="formRemark" label="备注" placeholder="可选，添加分类说明" />
        <p v-if="editingCategoryHasChildren" class="category-form-hint">
          当前一级分类下已有二级分类，不能再调整为二级分类。
        </p>
        <p v-if="categoryFormError" class="category-form-error">{{ categoryFormError }}</p>
      </form>

      <template #footer>
        <div class="category-create-actions">
          <CommonButton variant="secondary" :disabled="isSavingCategory" @click="closeCategoryModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isSavingCategory" @click="saveCategory">
            {{ isSavingCategory ? '保存中...' : '保存' }}
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
      <p class="category-delete-message">
        确认删除“{{ deletingCategory?.name }}”吗？
      </p>
      <p v-if="deleteError" class="category-form-error">{{ deleteError }}</p>

      <template #footer>
        <div class="category-create-actions">
          <CommonButton variant="secondary" :disabled="isDeletingCategory" @click="closeDeleteConfirmModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" :disabled="isDeletingCategory" @click="confirmDeleteCategory">
            {{ isDeletingCategory ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
