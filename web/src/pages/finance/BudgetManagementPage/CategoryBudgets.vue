<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import { getCategoryBudgets, saveCategoryBudget, deleteCategoryBudget, getCategories, type Category, type CategoryBudget } from '@/api/modules/finance'
import { getStoredCurrentUser } from '@/utils/current-user'
import { getErrorMessage } from '@/api/request'
const props = defineProps<{ month: string }>()
const items = ref<CategoryBudget[]>([])
const categories = ref<Category[]>([])
const loading = ref(true), busy = ref(false), show = ref(false), editing = ref(false)
const error = ref(''), formError = ref(''), categoryId = ref(''), amount = ref('')
const deleting = ref<CategoryBudget | null>(null)
const options = computed(() => categories.value.filter(c => !categories.value.some(child => child.parentId === c.id))
  .map(c => ({ value: String(c.id), label: [categories.value.find(p => p.id === c.parentId)?.name, c.name].filter(Boolean).join(' / ') })))
let serial = 0
watch(() => props.month, () => { show.value = false; deleting.value = null; void load() }, { immediate: true })
async function load() {
  const ticket = ++serial
  loading.value = true; error.value = ''
  try {
    const user = getStoredCurrentUser()
    if (!user) throw new Error('请先登录')
    const [budgets, available] = await Promise.all([getCategoryBudgets(props.month), getCategories({ userId: user.id, type: 'expense', status: 'active' })])
    if (ticket !== serial) return
    items.value = budgets; categories.value = available
  } catch (cause) { if (ticket === serial) error.value = getErrorMessage(cause, '分类预算加载失败') }
  finally { if (ticket === serial) loading.value = false }
}
function open(item?: CategoryBudget) {
  editing.value = !!item
  categoryId.value = item ? String(item.categoryId) : options.value[0]?.value ?? ''
  amount.value = item ? String(item.amount) : ''; formError.value = ''; show.value = true
}
function money(value: number) { return `¥${Number(value).toFixed(2)}` }
async function save() {
  if (busy.value) return
  if (!categoryId.value || !Number.isFinite(Number(amount.value)) || Number(amount.value) <= 0) { formError.value = '请选择分类并输入大于0的金额'; return }
  busy.value = true; formError.value = ''
  try { await saveCategoryBudget({ budgetMonth: props.month, categoryId: Number(categoryId.value), amount: Number(amount.value) }); show.value = false; await load() }
  catch (cause) { formError.value = getErrorMessage(cause, '保存失败') }
  finally { busy.value = false }
}
async function remove() {
  if (!deleting.value || busy.value) return
  busy.value = true; formError.value = ''
  try { await deleteCategoryBudget(deleting.value.id); deleting.value = null; await load() }
  catch (cause) { formError.value = getErrorMessage(cause, '删除失败') }
  finally { busy.value = false }
}
</script>
<template>
  <section class="category-budgets" aria-label="分类预算">
    <div class="section-heading"><h2>分类预算</h2><button :disabled="loading || !!error" @click="open()">新增分类预算</button></div>
    <p class="hint">按具体支出分类设置；已用达 80% 和 100% 时在统一提醒中提示。</p>
    <CommonLoading v-if="loading" />
    <p v-else-if="error" class="error" role="alert">{{ error }} <button @click="load">重试</button></p>
    <template v-else>
      <article v-for="item in items" :key="item.id" class="category-card" :class="{ warning: item.usagePercent >= 80, exceeded: item.usagePercent >= 100 }">
        <div class="section-heading"><strong>{{ item.categoryName }}</strong><span>{{ money(item.amount) }}</span></div>
        <p>已用 {{ money(item.usedAmount) }} · {{ item.remainingAmount >= 0 ? '剩余' : '超出' }} {{ money(Math.abs(item.remainingAmount)) }}</p>
        <div class="progress"><span :style="{ width: `${Math.min(100, Math.max(0, item.usagePercent))}%` }" /></div>
        <div class="section-heading"><span>{{ item.usagePercent >= 100 ? '预算已用完' : item.usagePercent >= 80 ? '预算即将用完' : `已用 ${item.usagePercent}%` }}</span>
          <div class="actions"><button @click="open(item)">修改</button><button @click="deleting = item; formError = ''">删除</button></div></div>
      </article>
      <p v-if="!items.length" class="hint">该月还没有分类预算</p>
    </template>
    <CommonModal v-model="show" title="分类预算" :show-close="!busy" :close-on-overlay="!busy">
      <div class="form"><CommonSelect v-model="categoryId" label="支出分类" :options="options" :disabled="busy || editing" />
        <CommonInput v-model="amount" label="预算金额" input-type="number" input-mode="decimal" />
        <p v-if="formError" class="error" role="alert">{{ formError }}</p></div>
      <template #footer><div class="modal-actions"><CommonButton variant="secondary" :disabled="busy" @click="show = false">取消</CommonButton><CommonButton :disabled="busy" @click="save">{{ busy ? '保存中...' : '保存' }}</CommonButton></div></template>
    </CommonModal>
    <CommonModal :model-value="!!deleting" title="删除分类预算" :show-close="!busy" :close-on-overlay="!busy" @update:model-value="v => { if (!v) deleting = null }">
      <p class="hint">确认删除“{{ deleting?.categoryName }}”的预算？</p><p v-if="formError" class="error" role="alert">{{ formError }}</p>
      <template #footer><div class="modal-actions"><CommonButton variant="secondary" :disabled="busy" @click="deleting = null">取消</CommonButton><CommonButton :disabled="busy" @click="remove">确认删除</CommonButton></div></template>
    </CommonModal>
  </section>
</template>
<style scoped lang="scss">
.category-budgets { display: grid; gap: 8px; font-size: 12px; line-height: 1.5; }
h2, p { margin: 0; } h2 { font-size: 14px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
button { border: 0; background: none; color: var(--color-brand); font-size: 12px; padding: 4px 0; cursor: pointer; }
.category-card { display: grid; gap: 6px; padding: 10px; border: 1px solid var(--color-bg-soft-2); border-radius: 12px; background: var(--color-bg-card); }
.hint, .category-card p { color: var(--color-text-muted); } .error { color: #dc2626; font-size: 12px; }
.progress { height: 5px; border-radius: 5px; overflow: hidden; background: var(--color-bg-soft-2); }
.progress span { display: block; height: 100%; background: var(--color-brand); }
.warning .progress span { background: #d97706; } .exceeded .progress span { background: #dc2626; }
.actions { display: flex; gap: 16px; }.form { display: grid; gap: 8px; }
.modal-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); width: 100%; gap: 10px; }
</style>
