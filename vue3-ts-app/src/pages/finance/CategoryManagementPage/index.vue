<script setup lang="ts">
// 分类管理页：还原 Pencil「分类管理」页面，展示分类筛选与分类网格。
import { ref } from 'vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'

const tabOptions = ['全部', '支出', '收入']
const activeTab = ref(tabOptions[0])

const expenseCategories = ref([
  { icon: '🍽', label: '餐饮', active: true },
  { icon: '🧴', label: '日用' },
  { icon: '🚗', label: '交通' },
  { icon: '🎬', label: '娱乐' },
  { icon: '🛍', label: '购物' },
  { icon: '🧩', label: '其他' },
])

const incomeCategories = ref([
  { icon: '💰', label: '工资' },
  { icon: '🎁', label: '奖金' },
])

const showCreateModal = ref(false)
const newCategoryName = ref('')
const newCategoryType = ref('支出')
const newCategoryIcon = ref('🧩')

const categoryTypeOptions = ['支出', '收入']
const categoryIconOptions = ['🍽', '🧴', '🚗', '🎬', '🛍', '🧩', '💰', '🎁', '📦', '📚']

function openCreateModal() {
  showCreateModal.value = true
}

function closeCreateModal() {
  showCreateModal.value = false
}

function createCategory() {
  const label = newCategoryName.value.trim()
  if (!label) return

  const nextItem = { icon: newCategoryIcon.value, label }
  if (newCategoryType.value === '收入') {
    incomeCategories.value.push(nextItem)
  } else {
    expenseCategories.value.push(nextItem)
  }

  newCategoryName.value = ''
  newCategoryType.value = '支出'
  newCategoryIcon.value = '🧩'
  closeCreateModal()
}
</script>

<template>
  <section class="category-management-page" aria-label="分类管理">
    <PageHeader title="分类管理" back-to="/finance/entry/expense" back-label="返回记一笔" />

    <SegmentedControl v-model="activeTab" :options="tabOptions" label="分类筛选" />

    <section class="category-card" aria-label="分类内容">
      <template v-if="activeTab !== '收入'">
        <h2>支出分类</h2>
        <div class="category-grid">
          <button
            v-for="item in expenseCategories"
            :key="item.label"
            type="button"
            :class="['category-item', { active: item.active }]"
          >
            <span>{{ item.icon }}</span>
            <strong>{{ item.label }}</strong>
          </button>
        </div>
      </template>

      <div class="category-divider"></div>

      <template v-if="activeTab !== '支出'">
        <h2>收入分类</h2>
        <div class="category-grid income">
          <button v-for="item in incomeCategories" :key="item.label" type="button" class="category-item">
            <span>{{ item.icon }}</span>
            <strong>{{ item.label }}</strong>
          </button>
        </div>
      </template>
    </section>

    <FloatingAddButton aria-label="新增分类" @click="openCreateModal" />

    <CommonModal v-model="showCreateModal" title="新增分类">
      <div class="category-create-form">
        <CommonInput v-model="newCategoryName" label="分类名称" placeholder="请输入分类名称" />
        <CommonSelect v-model="newCategoryType" label="分类类型" :options="categoryTypeOptions" />
        <CommonSelect v-model="newCategoryIcon" label="分类图标" :options="categoryIconOptions" />
      </div>

      <template #footer>
        <div class="category-create-actions">
          <CommonButton variant="secondary" @click="closeCreateModal">取消</CommonButton>
          <CommonButton @click="createCategory">保存</CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
