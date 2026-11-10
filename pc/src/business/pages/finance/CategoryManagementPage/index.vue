<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

// 分类管理页：使用财务模块分类接口完成列表、新增、修改和删除。
import { computed, onMounted, ref, watch } from 'vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'

import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'

import {
  createCategory,
  deleteCategory,
  getCategories,
  updateCategory,
  type Category,
  type SaveCategoryParams,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'
import { useColorPicker } from './useColorPicker'
import { canCategoryBeDeleted, getNextCategorySortOrder } from './categoryTree'

const tabOptions = ['全部', '支出', '收入']
const activeTab = ref(tabOptions[0])
const ROOT_PARENT_VALUE = 'root'

const isManageMode = ref(true)
const desktopCategories = computed(() =>
  categories.value.filter(
    (c) =>
      activeTab.value === '全部' ||
      c.type === (activeTab.value === '收入' ? 'income' : 'expense'),
  ),
)
const categories = ref<Category[]>([])
const showCategoryModal = ref(false)
const showDeleteConfirmModal = ref(false)
const editingCategory = ref<Category | null>(null)
const deletingCategory = ref<Category | null>(null)
const isLoadingCategories = ref(true)
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

const {
  showColorPickerModal,
  colorPickerColor,
  colorPickerDraft,
  colorPickerHue,
  colorWheelRef,
  colorFieldRef,
  colorWheelHandleStyle,
  colorFieldHandleStyle,
  colorWheelHueStyle,
  colorFieldHueStyle,
  openColorPickerModal,
  handleColorDraftInput,
  resetColorDraft,
  selectCategoryColor,
  applyCategoryColor,
  handleColorWheelPointer,
  handleColorWheelKeydown,
  handleColorFieldPointer,
} = useColorPicker({
  getSourceColor: () => formColor.value,
  onApply: (color) => {
    formColor.value = color
  },
})

const categoryColorOptions = [
  '#ef4444',
  '#f97316',
  '#f59e0b',
  '#eab308',
  '#84cc16',
  '#22c55e',
  '#10b981',
  '#14b8a6',
  '#06b6d4',
  '#0ea5e9',
  '#3b82f6',
  '#6366f1',
  '#8b5cf6',
  '#a855f7',
  '#ec4899',
  '#f43f5e',
  '#64748b',
  '#334155',
]

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

const categoryModalTitle = computed(() =>
  editingCategory.value ? '修改分类' : '新增分类',
)

const editingCategoryHasChildren = computed(
  () =>
    editingCategory.value !== null &&
    categories.value.some(
      (item) => item.parentId === editingCategory.value?.id,
    ),
)
const parentCategoryOptions = computed<CommonSelectOption[]>(() => [
  { label: '无（一级分类）', value: ROOT_PARENT_VALUE },
  ...categories.value
    .filter(
      (item) =>
        item.type === formType.value &&
        !item.parentId &&
        item.id !== editingCategory.value?.id,
    )
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

  const exists = categories.value.some(
    (item) =>
      item.type === formType.value &&
      !item.parentId &&
      item.id !== editingCategory.value?.id &&
      String(item.id) === formParentId.value,
  )

  if (!exists) {
    formParentId.value = ROOT_PARENT_VALUE
  }
})

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
  if (!isManageMode.value || category.system) {
    return
  }

  editingCategory.value = category
  formName.value = category.name
  formType.value = category.type
  formIcon.value = category.icon
  formColor.value = category.color || '#334155'
  formRemark.value = category.remark || ''
  formParentId.value = category.parentId
    ? String(category.parentId)
    : ROOT_PARENT_VALUE
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
    isLoadingCategories.value = false
    return
  }

  isLoadingCategories.value = true
  categoryListError.value = ''

  try {
    const nextCategories = await getCategories({
      userId: currentUser.id,
      status: 'active',
    })
    categories.value = nextCategories
  } catch (error) {
    categoryListError.value = getErrorMessage(error, '分类列表加载失败')
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
    const parentId =
      formParentId.value === ROOT_PARENT_VALUE
        ? null
        : Number(formParentId.value)
    const payload: SaveCategoryParams = {
      userId: editingCategory.value?.userId ?? currentUser.id,
      name: trimmedName,
      type: formType.value,
      icon: formIcon.value,
      color: formColor.value,
      parentId,
      system: editingCategory.value?.system ?? false,
      sortOrder:
        editingCategory.value?.sortOrder ??
        getNextCategorySortOrder(formType.value, parentId, categories.value),
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
    const message = getErrorMessage(error, '分类保存失败')
    categoryFormError.value = message
    showFeedback(message, 'error')
  } finally {
    isSavingCategory.value = false
  }
}

function openDeleteConfirmModal(category: Category) {
  if (category.system) {
    return
  }
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

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    deleteError.value = '请先登录后再删除分类'
    return
  }

  isDeletingCategory.value = true
  deleteError.value = ''

  try {
    await deleteCategory(deletingCategory.value.id, currentUser.id)
    closeDeleteConfirmModal()
    showFeedback('删除成功', 'success')
    await loadCategories()
  } catch (error) {
    const message = getErrorMessage(error, '分类删除失败')
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

function canDeleteCategory(category: Category) {
  return canCategoryBeDeleted(category, categories.value)
}

type DesktopRow = (typeof desktopCategories.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace">
    <PageHeader title="收支分类">
      <template #right>
        <el-button type="primary" @click="openCreateModal()">
          新增分类
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="categoryListError"
      :title="categoryListError"
      type="error"
      :closable="false"
      show-icon
    />
    <DesktopTable
      title="分类目录"
      searchable
      search-placeholder="分类名称 / 上级分类"
      :rows="desktopCategories"
      :loading="isLoadingCategories"
    >
      <template #toolbar>
        <SegmentedControl
          v-model="activeTab"
          :options="tabOptions"
          label="收支类型"
        />
      </template>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="名称"
        prop="name"
        sortable="custom"
        min-width="180"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="类型" min-width="100">
        <template #default="{ row }: { row: DesktopRow }">
          {{ row.type === 'income' ? '收入' : '支出' }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="上级分类" min-width="180">
        <template #default="{ row }: { row: DesktopRow }">
          {{
            categories.find((c) => c.id === row.parentId)?.name || '一级分类'
          }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="来源" min-width="100">
        <template #default="{ row }: { row: DesktopRow }">
          <el-tag :type="row.system ? 'info' : 'primary'">
            {{ row.system ? '系统' : '个人' }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="备注"
        prop="remark"
        sortable="custom"
        min-width="220"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button
              link
              type="primary"
              v-if="!row.parentId"
              @click="openCreateModal(row)"
            >
              新增二级
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="row.system"
              @click="openEditModal(row)"
            >
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
    <CommonFeedback
      v-model="showFeedbackModal"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal v-model="showCategoryModal" :title="categoryModalTitle">
      <el-form
        class="category-create-form"
        @submit.prevent="saveCategory"
        label-position="top"
      >
        <CommonInput
          v-model="formName"
          label="分类名称"
          placeholder="请输入分类名称"
        />
        <CommonSelect
          v-model="formType"
          label="分类类型"
          :options="categoryTypeOptions"
        />
        <CommonSelect
          v-model="formParentId"
          label="上级分类"
          :options="parentCategoryOptions"
          :disabled="editingCategoryHasChildren"
        />
        <CommonSelect
          v-model="formIcon"
          label="分类图标"
          :options="categoryIconOptions"
        />
        <el-form-item label="分类颜色">
          <el-color-picker
            v-model="formColor"
            :predefine="categoryColorOptions"
          />
        </el-form-item>
        <CommonInput
          v-model="formRemark"
          label="备注"
          placeholder="可选，添加分类说明"
        />
        <p v-if="editingCategoryHasChildren" class="category-form-hint">
          当前一级分类下已有二级分类，不能再调整为二级分类。
        </p>
        <p v-if="categoryFormError" class="category-form-error">
          {{ categoryFormError }}
        </p>
      </el-form>
      <template #footer>
        <div class="pc-toolbar category-create-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSavingCategory"
            @click="closeCategoryModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isSavingCategory"
            @click="saveCategory"
          >
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
        关联的历史流水和固定支出将归入“其他”。
      </p>
      <p v-if="deleteError" class="category-form-error">{{ deleteError }}</p>
      <template #footer>
        <div class="pc-toolbar category-create-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeletingCategory"
            @click="closeDeleteConfirmModal"
          >
            取消
          </CommonButton>
          <CommonButton
            variant="primary"
            :disabled="isDeletingCategory"
            @click="confirmDeleteCategory"
          >
            {{ isDeletingCategory ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
