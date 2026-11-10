<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import SavingsGoalCard from '@/components/finance/SavingsGoalCard/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import CommonHeaderActionButton from '@/components/common/CommonHeaderActionButton/index.vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonEmpty from '@/components/common/CommonEmpty/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import { getAccounts, getSavingsGoals, createSavingsGoal, updateSavingsGoal, deleteSavingsGoal,
  addSavingsGoalEntry,
  type Account, type SavingsGoal, type SavingsGoalEntry } from '@/api/modules/finance'
import { getErrorMessage } from '@/api/request'
import { getStoredCurrentUser } from '@/utils/current-user'
import { useAsyncSection } from '@/composables/useAsyncSection'
import { useIdempotentSubmit } from '@/composables/useIdempotentSubmit'
import { useVisibilityPolling } from '@/composables/useVisibilityPolling'

const router = useRouter()
const goals = ref<SavingsGoal[]>([])
const accounts = ref<Account[]>([])
const selectedFilter = ref('all')
const filters = [{ value: 'all', label: '全部' }, { value: 'saving', label: '储蓄中' }, { value: 'completed', label: '已达成' }]
const visibleGoals = computed(() => goals.value.filter(goal => selectedFilter.value === 'all'
  || (selectedFilter.value === 'completed' ? goal.status === 'completed' : goal.status !== 'completed')))
const list = useAsyncSection(getSavingsGoals, value => { goals.value = value }, '储蓄目标加载失败')
const accountSection = useAsyncSection(signal => {
  const user = getStoredCurrentUser()
  if (!user) throw new Error('请先登录')
  return getAccounts({ userId: user.id, status: 'active' }, { signal })
}, value => { accounts.value = value.filter(account => account.accountTypeCode === 'cash') }, '现金账户加载失败')
onMounted(() => { void list.run() })
useVisibilityPolling(() => list.run(), 60_000)

const showForm = ref(false), showDelete = ref(false), saving = ref(false), deleting = ref(false)
const editing = ref<SavingsGoal | null>(null), deletingGoal = ref<SavingsGoal | null>(null)
const name = ref(''), accountId = ref(''), amount = ref(''), targetDate = ref(''), remark = ref('')
const formError = ref(''), deleteError = ref('')
const submission = useIdempotentSubmit()
const accountOptions = computed(() => accounts.value.map(account => ({ value: String(account.id),
  label: `${account.name} · ${money(account.currentBalance, account.currencyCode)}` })))
const selectedAccount = computed(() => accounts.value.find(account => account.id === Number(accountId.value)))
const canSave = computed(() => !!selectedAccount.value && !saving.value && !accountSection.loading.value && !accountSection.error.value)
const formOpen = computed({ get: () => showForm.value, set: value => { if (!saving.value) showForm.value = value } })
const deleteOpen = computed({ get: () => showDelete.value, set: value => { if (!deleting.value) showDelete.value = value } })
const showEntry = ref(false), entrySaving = ref(false)
const entryGoal = ref<SavingsGoal | null>(null), entryType = ref<SavingsGoalEntry['type']>('deposit')
const entryAmount = ref(''), entryDate = ref(''), entryRemark = ref(''), entryError = ref('')
const entrySubmission = useIdempotentSubmit()
const entryOpen = computed({ get: () => showEntry.value, set: value => { if (!entrySaving.value) showEntry.value = value } })
const entryTitle = computed(() => entryType.value === 'deposit' ? '存入' : '取出')
function openEntry(goal: SavingsGoal, type: SavingsGoalEntry['type']) {
  entryGoal.value = goal; entryType.value = type; entryAmount.value = ''; entryDate.value = goal.asOfDate
  entryRemark.value = ''; entryError.value = ''; entrySubmission.reset(); showEntry.value = true
}
async function saveEntry() {
  if (!entryGoal.value || entrySaving.value) return
  entryError.value = ''
  if (!/^\d{1,16}(\.\d{1,2})?$/.test(entryAmount.value.trim()) || Number(entryAmount.value) <= 0) {
    entryError.value = '请输入大于0且最多两位小数的金额'; return
  }
  if (entryType.value === 'withdrawal' && Number(entryAmount.value) > entryGoal.value.currentAmount) {
    entryError.value = '取出金额不能超过该目标已存金额'; return
  }
  if (entryType.value === 'deposit' && Number(entryAmount.value) > (entryGoal.value.accountAvailableAmount ?? 0)) {
    entryError.value = '存入金额不能超过账户可分配余额'; return
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(entryDate.value)) { entryError.value = '请选择有效日期'; return }
  if (entryRemark.value.length > 500) { entryError.value = '备注最多500字'; return }
  entrySaving.value = true
  try {
    await addSavingsGoalEntry(entryGoal.value.id, entrySubmission.withRequestId({ type: entryType.value,
      amount: Number(entryAmount.value), occurredOn: entryDate.value, remark: entryRemark.value.trim() || null }))
    entrySubmission.reset(); showEntry.value = false
    await list.run()
  } catch (cause) { entryError.value = getErrorMessage(cause, '记录保存失败') }
  finally { entrySaving.value = false }
}
function openHistory(goal: SavingsGoal) {
  void router.push({ name: 'finance-savings-goal-entries', params: { id: goal.id } })
}

function money(value: number | null, currency: string) {
  if (value === null) return '--'
  try { return new Intl.NumberFormat('zh-CN', { style: 'currency', currency, minimumFractionDigits: 2 }).format(value) }
  catch { return `${currency} ${Number(value).toFixed(2)}` }
}
function openForm(goal: SavingsGoal | null = null) {
  editing.value = goal
  name.value = goal?.name ?? ''; accountId.value = goal ? String(goal.accountId) : ''
  amount.value = goal ? String(goal.targetAmount) : ''; targetDate.value = goal?.targetDate ?? ''; remark.value = goal?.remark ?? ''
  formError.value = ''; submission.reset(); showForm.value = true
  void accountSection.run()
}
async function save() {
  if (saving.value) return
  formError.value = ''
  if (!name.value.trim() || name.value.trim().length > 80) { formError.value = '请输入不超过80字的目标名称'; return }
  if (!selectedAccount.value) {
    formError.value = '请选择现金账户'; return
  }
  if (!/^\d{1,16}(\.\d{1,2})?$/.test(amount.value.trim()) || Number(amount.value) <= 0) {
    formError.value = '请输入大于0且最多两位小数的目标金额'; return
  }
  if (targetDate.value && !/^\d{4}-\d{2}-\d{2}$/.test(targetDate.value)) { formError.value = '请选择有效截止日期'; return }
  if (remark.value.length > 500) { formError.value = '备注最多500字'; return }
  const payload = { name: name.value.trim(), accountId: Number(accountId.value), targetAmount: Number(amount.value),
    targetDate: targetDate.value || null, remark: remark.value.trim() || null }
  saving.value = true
  try {
    if (editing.value) await updateSavingsGoal(editing.value.id, payload)
    else await createSavingsGoal(submission.withRequestId(payload))
    submission.reset(); showForm.value = false
    await list.run()
  } catch (cause) { formError.value = getErrorMessage(cause, '目标保存失败') }
  finally { saving.value = false }
}
function openDelete(goal: SavingsGoal) {
  deletingGoal.value = goal; deleteError.value = ''; showDelete.value = true
}
async function remove() {
  if (!deletingGoal.value || deleting.value) return
  deleting.value = true; deleteError.value = ''
  try {
    await deleteSavingsGoal(deletingGoal.value.id)
    showDelete.value = false
    await list.run()
  } catch (cause) { deleteError.value = getErrorMessage(cause, '目标删除失败') }
  finally { deleting.value = false }
}
</script>

<template>
  <section class="savings-page" aria-label="储蓄目标">
    <PageHeader title="储蓄目标" back-to="/finance/more-features" back-label="返回更多功能" prefer-back-to>
      <template #right>
        <div class="header-actions">
          <CommonHeaderActionButton label="刷新储蓄目标" :disabled="list.loading.value" @click="list.run">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20 7v5h-5M4 17v-5h5M5.5 7a7 7 0 0 1 12-2L20 8M4 16l2.5 3a7 7 0 0 0 12-2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </CommonHeaderActionButton>
          <CommonHeaderActionButton label="新增储蓄目标" :disabled="!list.ready.value || !!list.error.value" @click="openForm()">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
          </CommonHeaderActionButton>
        </div>
      </template>
    </PageHeader>
    <SegmentedControl v-model="selectedFilter" :options="filters" label="储蓄目标状态" />
    <p v-if="list.error.value" class="error" role="alert">{{ list.error.value }} <button @click="list.run">重试</button></p>
    <CommonLoading v-if="list.loading.value && !list.ready.value" />
    <div v-else-if="list.ready.value" class="savings-list" :aria-busy="list.loading.value">
      <CommonEmpty v-if="!goals.length" title="暂无储蓄目标" description="关联一个现金账户，开始为应急金、旅行或买车存钱。" action-text="新建目标" @action="openForm()" />
      <p v-else-if="!visibleGoals.length" class="empty">暂无{{ selectedFilter === 'completed' ? '已达成' : '储蓄中' }}的目标</p>
      <SavingsGoalCard v-for="goal in visibleGoals" :key="goal.id" :goal="goal">
        <template #actions>
          <div class="card-actions">
            <button :aria-label="`存入${goal.name}`" title="存入" @click="openEntry(goal, 'deposit')"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7" /><path d="M12 7v10M7 12h10" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg></button>
            <button :aria-label="`取出${goal.name}`" title="取出" :disabled="goal.currentAmount <= 0" @click="openEntry(goal, 'withdrawal')"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7" /><path d="M7 12h10" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg></button>
            <button :aria-label="`${goal.name}存取记录`" title="存取记录" @click="openHistory(goal)"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7" /><path d="M12 7v5l3 2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg></button>
            <button :aria-label="`修改${goal.name}`" title="修改" @click="openForm(goal)"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m14 5 5 5M4 20l5-1L20 8a2 2 0 0 0-5-5L4 14v6Z" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg></button>
            <button class="danger" :aria-label="`删除${goal.name}`" title="删除" @click="openDelete(goal)"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5M14 11v5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg></button>
          </div>
        </template>
      </SavingsGoalCard>
    </div>

    <CommonModal v-model="formOpen" :title="editing ? '修改储蓄目标' : '新增储蓄目标'" :close-on-overlay="!saving" :show-close="!saving">
      <form id="savings-form" class="savings-form" @submit.prevent="save">
        <CommonInput v-model="name" label="目标名称" placeholder="例如：应急金、买车" :disabled="saving" />
        <CommonSelect v-model="accountId" label="关联现金账户" :options="accountOptions" :disabled="saving || accountSection.loading.value" />
        <p v-if="accountSection.error.value" class="error" role="alert">{{ accountSection.error.value }} <button type="button" @click="accountSection.run">重试</button></p>
        <p v-else-if="!accountSection.loading.value && !accounts.length" class="hint">暂无启用的现金账户，<RouterLink to="/finance/accounts/cash" @click="showForm = false">先去新增账户</RouterLink>。</p>
        <p class="hint">一个账户可关联多个目标，已存总额不得超过账户余额。收入可计入目标，支出可选择扣减目标。</p>
        <CommonInput v-model="amount" :label="`目标金额${selectedAccount ? `（${selectedAccount.currencyCode}）` : ''}`" input-mode="decimal" placeholder="请输入目标金额" :disabled="saving" />
        <CommonInput v-model="targetDate" label="截止日期（可选）" input-type="date" :disabled="saving" />
        <p class="hint">每月建议金额按本月至截止月份（含首尾）均摊剩余金额。</p>
        <CommonInput v-model="remark" label="备注（可选）" placeholder="记录你的储蓄计划" :disabled="saving" />
        <p v-if="formError" class="error" role="alert">{{ formError }}</p>
      </form>
      <template #footer><div class="modal-actions"><CommonButton variant="secondary" :disabled="saving" @click="showForm = false">取消</CommonButton><CommonButton type="submit" form="savings-form" :disabled="!canSave">{{ saving ? '保存中…' : '保存' }}</CommonButton></div></template>
    </CommonModal>
    <CommonModal v-model="deleteOpen" title="删除储蓄目标" :show-close="!deleting" :close-on-overlay="!deleting" size="compact">
      <div class="savings-form"><p>确认删除“{{ deletingGoal?.name }}”及其全部存取记录？账户及余额会保留。</p><p v-if="deleteError" class="error" role="alert">{{ deleteError }}</p></div>
      <template #footer><div class="modal-actions"><CommonButton variant="secondary" :disabled="deleting" @click="showDelete = false">取消</CommonButton><CommonButton :disabled="deleting" @click="remove">{{ deleting ? '删除中…' : '确认删除' }}</CommonButton></div></template>
    </CommonModal>
    <CommonModal v-model="entryOpen" :title="`${entryTitle} · ${entryGoal?.name ?? ''}`" :close-on-overlay="!entrySaving" :show-close="!entrySaving" size="compact">
      <form id="savings-entry-form" class="savings-form" @submit.prevent="saveEntry">
        <p v-if="entryGoal" class="hint">当前已存 {{ money(entryGoal.currentAmount, entryGoal.currencyCode) }}，账户可分配 {{ money(entryGoal.accountAvailableAmount, entryGoal.currencyCode) }}。存入预留资金，取出释放预留，账户余额不变。</p>
        <CommonInput v-model="entryAmount" :label="`${entryTitle}金额（${entryGoal?.currencyCode ?? ''}）`" input-mode="decimal" placeholder="请输入金额" :disabled="entrySaving" />
        <CommonInput v-model="entryDate" label="记录日期" input-type="date" :disabled="entrySaving" />
        <CommonInput v-model="entryRemark" label="备注（可选）" placeholder="例如：本月储蓄" :disabled="entrySaving" />
        <p v-if="entryError" class="error" role="alert">{{ entryError }}</p>
      </form>
      <template #footer><div class="modal-actions"><CommonButton variant="secondary" :disabled="entrySaving" @click="showEntry = false">取消</CommonButton><CommonButton type="submit" form="savings-entry-form" :disabled="entrySaving">{{ entrySaving ? '保存中…' : '确认记录' }}</CommonButton></div></template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
