<script setup lang="ts">
import { formatPercentage } from '@/business/utils/percentage'

import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, ref, watch } from 'vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'

import {
  getCategoryBudgets,
  saveCategoryBudget,
  deleteCategoryBudget,
  getCategories,
  type Category,
  type CategoryBudget,
} from '@/business/api/modules/finance'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'
const props = defineProps<{ month: string }>()
const items = ref<CategoryBudget[]>([])
const categories = ref<Category[]>([])
const loading = ref(true),
  busy = ref(false),
  show = ref(false),
  editing = ref(false)
const error = ref(''),
  formError = ref(''),
  categoryId = ref(''),
  amount = ref('')
const deleting = ref<CategoryBudget | null>(null)
const options = computed(() =>
  categories.value
    .filter((c) => !categories.value.some((child) => child.parentId === c.id))
    .map((c) => ({
      value: String(c.id),
      label: [categories.value.find((p) => p.id === c.parentId)?.name, c.name]
        .filter(Boolean)
        .join(' / '),
    })),
)
let serial = 0
watch(
  () => props.month,
  () => {
    show.value = false
    deleting.value = null
    void load()
  },
  { immediate: true },
)
async function load() {
  const ticket = ++serial
  loading.value = true
  error.value = ''
  try {
    const user = getStoredCurrentUser()
    if (!user) throw new Error('请先登录')
    const [budgets, available] = await Promise.all([
      getCategoryBudgets(props.month),
      getCategories({ userId: user.id, type: 'expense', status: 'active' }),
    ])
    if (ticket !== serial) return
    items.value = budgets
    categories.value = available
  } catch (cause) {
    if (ticket === serial)
      error.value = getErrorMessage(cause, '分类预算加载失败')
  } finally {
    if (ticket === serial) loading.value = false
  }
}
function open(item?: CategoryBudget) {
  editing.value = !!item
  categoryId.value = item
    ? String(item.categoryId)
    : (options.value[0]?.value ?? '')
  amount.value = item ? String(item.amount) : ''
  formError.value = ''
  show.value = true
}
function money(value: number) {
  return `¥${Number(value).toFixed(2)}`
}
async function save() {
  if (busy.value) return
  if (
    !categoryId.value ||
    !Number.isFinite(Number(amount.value)) ||
    Number(amount.value) <= 0
  ) {
    formError.value = '请选择分类并输入大于0的金额'
    return
  }
  busy.value = true
  formError.value = ''
  try {
    await saveCategoryBudget({
      budgetMonth: props.month,
      categoryId: Number(categoryId.value),
      amount: Number(amount.value),
    })
    show.value = false
    await load()
  } catch (cause) {
    formError.value = getErrorMessage(cause, '保存失败')
  } finally {
    busy.value = false
  }
}
async function remove() {
  if (!deleting.value || busy.value) return
  busy.value = true
  formError.value = ''
  try {
    await deleteCategoryBudget(deleting.value.id)
    deleting.value = null
    await load()
  } catch (cause) {
    formError.value = getErrorMessage(cause, '删除失败')
  } finally {
    busy.value = false
  }
}

type DesktopRow = (typeof items.value)[number]
</script>
<template>
  <section class="desktop-workspace">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <DesktopTable :rows="items" :loading="loading" title="分类预算">
      <template #toolbar>
        <el-button
          type="primary"
          :disabled="loading || !!error"
          @click="open()"
        >
          新增分类预算
        </el-button>
      </template>
      <el-table-column
        prop="categoryName"
        label="支出分类"
        min-width="180"
        sortable="custom"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="预算" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ money(row.amount) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="已使用" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ money(row.usedAmount) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="剩余预算" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ money(row.remainingAmount) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="执行进度" min-width="200">
        <template #default="{ row }: { row: DesktopRow }">
          <el-progress
            :format="formatPercentage"
            :percentage="Math.min(100, Math.max(0, row.usagePercent))"
            :status="
              row.usagePercent >= 100
                ? 'exception'
                : row.usagePercent >= 80
                  ? 'warning'
                  : undefined
            "
          />
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column fixed="right" label="操作" width="140">
        <template #default="{ row }: { row: DesktopRow }">
          <el-button link type="primary" @click="open(row)">修改</el-button>
          <el-button
            link
            type="danger"
            @click="
              () => {
                deleting = row
                formError = ''
              }
            "
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonModal
      v-model="show"
      title="分类预算"
      :show-close="!busy"
      :close-on-overlay="!busy"
    >
      <div class="pc-form-grid form">
        <CommonSelect
          v-model="categoryId"
          label="支出分类"
          :options="options"
          :disabled="busy || editing"
        />
        <CommonInput
          v-model="amount"
          label="预算金额"
          input-type="number"
          input-mode="decimal"
        />
        <p v-if="formError" class="error" role="alert">{{ formError }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="busy"
            @click="show = false"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="busy" @click="save">
            {{ busy ? '保存中...' : '保存' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      :model-value="!!deleting"
      title="删除分类预算"
      :show-close="!busy"
      :close-on-overlay="!busy"
      @update:model-value="
        (v) => {
          if (!v) deleting = null
        }
      "
    >
      <p class="hint">确认删除“{{ deleting?.categoryName }}”的预算？</p>
      <p v-if="formError" class="error" role="alert">{{ formError }}</p>
      <template #footer>
        <div class="pc-toolbar modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="busy"
            @click="deleting = null"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="busy" @click="remove">确认删除</CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
