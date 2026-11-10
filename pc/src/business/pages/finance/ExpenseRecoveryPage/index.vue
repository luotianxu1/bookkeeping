<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import CommonButton from '@/business/components/common/CommonButton/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'
import CommonSelect from '@/business/components/common/CommonSelect/index.vue'
import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonLoading from '@/business/components/common/CommonLoading/index.vue'
import CommonEmpty from '@/business/components/common/CommonEmpty/index.vue'
import CommonDateTimePicker from '@/business/components/common/CommonDateTimePicker/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import {
  getExpenseRecoveries,
  getExpenseRecovery,
  saveReimbursementPlan,
  receiveExpenseRecovery,
  deleteTransaction,
  getAccounts,
  getAccountTypes,
  type ExpenseRecovery,
  type ExpenseReceipt,
  type ExpenseRecoveryPage,
  type Account,
} from '@/business/api/modules/finance'
import { getErrorMessage } from '@/business/api/request'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { useIdempotentSubmit } from '@/business/composables/useIdempotentSubmit'

const route = useRoute(),
  router = useRouter()
const submission = useIdempotentSubmit()
const state = ref('all'),
  page = ref(1),
  pages = ref(1),
  total = ref(0)
const items = ref<ExpenseRecovery[]>([]),
  accounts = ref<Account[]>([])
const pendingSummary = ref<ExpenseRecoveryPage['pendingSummary']>([])
const loading = ref(true),
  detailLoading = ref(false),
  busy = ref(false)
const error = ref(''),
  detailError = ref(''),
  formError = ref(''),
  feedback = ref('')
const detail = ref<ExpenseRecovery | null>(null),
  showDetail = ref(false)
const mode = ref<'plan' | 'refund' | 'reimbursement' | null>(null)
const amount = ref(''),
  accountId = ref(''),
  occurredAt = ref(''),
  remark = ref('')
const recipient = ref(''),
  expectedDate = ref('')
const undoTarget = ref<ExpenseReceipt | null>(null)
const showCancelPlan = ref(false)
const cancelPlanLabel = computed(() =>
  (detail.value?.reimbursedAmount ?? 0) > 0 ? '撤销剩余报销' : '撤销报销',
)
const statusLabels = {
  none: '未申请报销',
  pending: '待报销',
  partial: '部分到账',
  received: '全部到账',
}
const title = computed(() =>
  mode.value === 'plan'
    ? '设置报销计划'
    : mode.value === 'refund'
      ? '登记退款'
      : '登记报销到账',
)
const accountOptions = computed(() =>
  accounts.value
    .filter((a) => a.currencyCode === detail.value?.currencyCode)
    .map((a) => ({ label: a.name, value: String(a.id) })),
)
const targetId = computed(() => {
  const value = Number(route.query.transactionId)
  return Number.isSafeInteger(value) && value > 0 ? value : undefined
})
let listSerial = 0,
  detailSerial = 0
watch(state, () => {
  page.value = 1
  void load()
})
watch(targetId, async (id) => {
  page.value = 1
  await load()
  if (id && id === targetId.value) await openDetail(id)
})
onMounted(async () => {
  await load()
  if (targetId.value) await openDetail(targetId.value)
})
function money(value: number, currency = detail.value?.currencyCode || 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(
    Number(value),
  )
}
function date(value: string) {
  return value.replace('T', ' ').slice(0, 16)
}
function isOverdue(item: ExpenseRecovery) {
  const today = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai',
  }).format(new Date())
  return (
    item.pendingAmount > 0 &&
    !!item.expectedReimbursementDate &&
    item.expectedReimbursementDate < today
  )
}
function now() {
  const d = new Date(),
    pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
async function load() {
  const ticket = ++listSerial
  loading.value = true
  error.value = ''
  try {
    const result = await getExpenseRecoveries({
      state: state.value,
      page: page.value,
      transactionId: targetId.value,
    })
    if (ticket !== listSerial) return
    items.value = result.items
    page.value = result.page
    pages.value = result.totalPages
    total.value = result.total
    pendingSummary.value = result.pendingSummary ?? []
  } catch (cause) {
    if (ticket === listSerial)
      error.value = getErrorMessage(cause, '支出加载失败')
  } finally {
    if (ticket === listSerial) loading.value = false
  }
}
async function openDetail(id: number) {
  const ticket = ++detailSerial
  showDetail.value = true
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const user = getStoredCurrentUser()
    if (!user) throw new Error('请先登录')
    const [result, types] = await Promise.all([
      getExpenseRecovery(id),
      getAccountTypes(),
    ])
    const cashType = types.find((t) => t.code === 'cash')
    const cash = cashType
      ? await getAccounts({
          userId: user.id,
          accountTypeId: cashType.id,
          status: 'active',
        })
      : []
    if (ticket !== detailSerial) return
    detail.value = result
    accounts.value = cash
  } catch (cause) {
    if (ticket === detailSerial)
      detailError.value = getErrorMessage(cause, '详情加载失败')
  } finally {
    if (ticket === detailSerial) detailLoading.value = false
  }
}
function closeDetail() {
  if (busy.value) return
  ++detailSerial
  showDetail.value = false
}
function start(next: 'plan' | 'refund' | 'reimbursement') {
  if (!detail.value) return
  mode.value = next
  formError.value = ''
  submission.reset()
  amount.value = String(
    next === 'plan'
      ? detail.value.reimbursementAmount ||
          Number(detail.value.amount) - Number(detail.value.refundedAmount)
      : next === 'refund'
        ? detail.value.recoverableAmount
        : detail.value.pendingAmount,
  )
  accountId.value = accountOptions.value[0]?.value || ''
  occurredAt.value = now()
  remark.value = ''
  recipient.value = detail.value.reimbursementRecipient || ''
  expectedDate.value = detail.value.expectedReimbursementDate || ''
}
async function save() {
  if (!detail.value || !mode.value || busy.value) return
  const value = Number(amount.value),
    id = detail.value.id,
    action = mode.value
  if (
    !amount.value.trim() ||
    !Number.isFinite(value) ||
    value < 0 ||
    (action !== 'plan' && value === 0) ||
    !/^\d+(\.\d{1,2})?$/.test(amount.value)
  ) {
    formError.value = '请输入有效金额，最多两位小数'
    return
  }
  if (action !== 'plan' && (!accountId.value || !occurredAt.value)) {
    formError.value = '请选择到账账户和时间'
    return
  }
  busy.value = true
  formError.value = ''
  feedback.value = ''
  try {
    if (action === 'plan')
      await saveReimbursementPlan(id, value, {
        recipient: recipient.value.trim() || null,
        expectedDate: expectedDate.value || null,
      })
    else
      await receiveExpenseRecovery(
        submission.withRequestId({
          transactionId: id,
          accountId: Number(accountId.value),
          amount: value,
          kind: action,
          occurredAt: occurredAt.value,
          remark: remark.value.trim() || undefined,
        }),
      )
    // 提交成功后立即退出表单，刷新失败也不能让用户重复提交已到账的记录。
    submission.reset()
    mode.value = null
    feedback.value = action === 'plan' ? '报销计划已保存' : '到账已登记'
    await refreshAfterMutation(id)
  } catch (cause) {
    formError.value = getErrorMessage(cause, '保存失败')
  } finally {
    busy.value = false
  }
}
async function refreshAfterMutation(id: number) {
  try {
    detail.value = await getExpenseRecovery(id)
    detailError.value = ''
  } catch (cause) {
    detail.value = null
    detailError.value = getErrorMessage(
      cause,
      '保存成功，详情刷新失败，请重新打开',
    )
  }
  await load()
}
async function undo() {
  const user = getStoredCurrentUser()
  if (!undoTarget.value || !detail.value || !user || busy.value) return
  const id = detail.value.id
  busy.value = true
  formError.value = ''
  feedback.value = ''
  try {
    await deleteTransaction(undoTarget.value.id, user.id)
    undoTarget.value = null
    await refreshAfterMutation(id)
  } catch (cause) {
    formError.value = getErrorMessage(cause, '撤销失败')
  } finally {
    busy.value = false
  }
}
async function cancelPlan() {
  if (!detail.value || detail.value.pendingAmount <= 0 || busy.value) return
  const id = detail.value.id,
    received = detail.value.reimbursedAmount
  busy.value = true
  formError.value = ''
  feedback.value = ''
  try {
    // 计划总额降至已到账金额，只取消未到账部分；后端锁内校验避免覆盖新增到账。
    await saveReimbursementPlan(id, received)
    showCancelPlan.value = false
    await refreshAfterMutation(id)
  } catch (cause) {
    formError.value = getErrorMessage(cause, '撤销失败')
  } finally {
    busy.value = false
  }
}
function changePage(delta: number) {
  page.value += delta
  void load()
}

type DesktopRow = (typeof items.value)[number]
</script>
<template>
  <section class="pc-page">
    <PageHeader title="退款与报销">
      <template #right>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    />
    <div v-if="targetId" class="pc-toolbar">
      <el-tag>当前原支出</el-tag>
      <el-button
        link
        type="primary"
        @click="router.replace('/finance/expense-recoveries')"
      >
        查看全部
      </el-button>
    </div>
    <div class="pc-toolbar">
      <el-tag
        v-for="summary in pendingSummary"
        :key="summary.currencyCode"
        type="warning"
      >
        待报销 {{ money(summary.amount, summary.currencyCode) }}
      </el-tag>
      <el-segmented
        v-model="state"
        :options="[
          { label: '全部', value: 'all' },
          { label: '待报销', value: 'pending' },
          { label: '部分到账', value: 'partial' },
          { label: '全部到账', value: 'received' },
        ]"
      />
    </div>
    <DesktopTable :rows="items" :loading="loading">
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="支出"
        prop="title"
        min-width="180"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="支出日期" min-width="180">
        <template #default="{ row }: { row: DesktopRow }">
          {{ date(row.occurredAt) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="金额" min-width="150">
        <template #default="{ row }: { row: DesktopRow }">
          {{ money(row.amount, row.currencyCode) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="已退款" min-width="140">
        <template #default="{ row }: { row: DesktopRow }">
          {{ money(row.refundedAmount, row.currencyCode) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="已报销" min-width="140">
        <template #default="{ row }: { row: DesktopRow }">
          {{ money(row.reimbursedAmount, row.currencyCode) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="待报销" min-width="140">
        <template #default="{ row }: { row: DesktopRow }">
          {{ money(row.pendingAmount, row.currencyCode) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="预计报销日期"
        prop="expectedReimbursementDate"
        min-width="160"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button link type="primary" @click="openDetail(row.id)">
              详情 / 登记到账
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <div class="pc-pagination">
      <el-pagination
        v-model:current-page="page"
        :page-count="pages"
        layout="prev, pager, next"
        @current-change="load"
      />
      <span>共 {{ total }} 条</span>
    </div>
    <CommonModal
      :model-value="showDetail && !mode && !undoTarget && !showCancelPlan"
      title="支出详情"
      :show-close="!busy"
      :close-on-overlay="!busy"
      @update:model-value="
        (v) => {
          if (!v) closeDetail()
        }
      "
    >
      <CommonLoading v-if="detailLoading" />
      <p v-if="detailError" class="error" role="alert">{{ detailError }}</p>
      <div v-if="detail" class="detail-content">
        <div class="pc-toolbar card-heading">
          <strong>{{ detail.title }}</strong>
          <strong>{{ money(detail.amount) }}</strong>
        </div>
        <p class="hint">
          {{ detail.categoryName }} · {{ detail.accountName }} ·
          {{ date(detail.occurredAt) }}
        </p>
        <p v-if="detail.reimbursementRecipient" class="hint">
          报销对象：{{ detail.reimbursementRecipient }}
        </p>
        <p
          v-if="detail.expectedReimbursementDate"
          class="hint"
          :class="{ overdue: isOverdue(detail) }"
        >
          预计到账：{{ detail.expectedReimbursementDate
          }}{{ isOverdue(detail) ? ' · 已逾期' : '' }}
        </p>
        <dl>
          <div>
            <dt>已退款</dt>
            <dd>{{ money(detail.refundedAmount) }}</dd>
          </div>
          <div>
            <dt>计划报销</dt>
            <dd>{{ money(detail.reimbursementAmount) }}</dd>
          </div>
          <div>
            <dt>报销到账</dt>
            <dd>{{ money(detail.reimbursedAmount) }}</dd>
          </div>
          <div>
            <dt>待到账</dt>
            <dd>{{ money(detail.pendingAmount) }}</dd>
          </div>
        </dl>
        <div
          class="pc-toolbar detail-actions"
          :class="{ 'detail-actions-with-cancel': detail.pendingAmount > 0 }"
        >
          <CommonButton
            variant="secondary"
            :disabled="busy"
            @click="start('plan')"
          >
            报销计划
          </CommonButton>
          <CommonButton
            v-if="detail.pendingAmount > 0"
            variant="secondary"
            :disabled="busy"
            @click="
() => {
showCancelPlan = true
              formError = ''
}"
          >
            {{ cancelPlanLabel }}
          </CommonButton>
          <CommonButton
            variant="secondary"
            :disabled="busy || detail.recoverableAmount <= 0"
            @click="start('refund')"
          >
            登记退款
          </CommonButton>
          <CommonButton
            :disabled="busy || detail.pendingAmount <= 0"
            @click="start('reimbursement')"
          >
            报销到账
          </CommonButton>
        </div>
        <h3>到账记录</h3>
        <p v-if="!detail.receipts?.length" class="hint">暂无到账记录</p>
        <el-card
          v-for="receipt in detail.receipts"
          :key="receipt.id"
          class="pc-record-card receipt"
          shadow="never"
        >
          <div class="pc-toolbar card-heading">
            <strong>
              {{ receipt.kind === 'refund' ? '退款' : '报销到账' }}
              {{ money(receipt.amount) }}
            </strong>
            <el-button
              :disabled="busy"
              @click="
() => {
undoTarget = receipt
                formError = ''
}"
            >
              撤销
            </el-button>
          </div>
          <p class="hint">
            {{ receipt.accountName }} · {{ date(receipt.occurredAt) }}
          </p>
          <p v-if="receipt.remark" class="hint">{{ receipt.remark }}</p>
        </el-card>
      </div>
    </CommonModal>
    <CommonModal
      :model-value="!!mode"
      :title="title"
      :show-close="!busy"
      :close-on-overlay="!busy"
      @update:model-value="
        (v) => {
          if (!v && !busy) mode = null
        }
      "
    >
      <div class="pc-form-grid recovery-form">
        <p class="hint">{{ detail?.title }}</p>
        <CommonInput
          v-model="amount"
          :label="mode === 'plan' ? '计划报销总额' : '本次到账金额'"
          input-type="number"
          input-mode="decimal"
          :disabled="busy"
        />
        <template v-if="mode === 'plan'">
          <CommonInput
            v-model="recipient"
            label="报销对象"
            placeholder="公司、部门或个人（选填）"
            :disabled="busy"
          />
          <CommonInput
            v-model="expectedDate"
            label="预计到账日期"
            input-type="date"
            :disabled="busy"
          />
          <p class="hint">
            设置日期后按统一提醒的提前天数提醒，逾期未到账会继续保留提醒。计划总额不能低于已到账金额。
          </p>
        </template>
        <template v-else>
          <CommonSelect
            v-model="accountId"
            label="到账账户"
            :options="accountOptions"
            :disabled="busy"
          />
          <CommonDateTimePicker
            v-model="occurredAt"
            label="到账时间"
            :disabled="busy"
          />
          <CommonInput
            v-model="remark"
            label="备注"
            placeholder="选填"
            :disabled="busy"
          />
          <p v-if="!accountOptions.length" class="hint">
            请先创建与原支出币种一致的现金账户。
          </p>
        </template>
        <p v-if="formError" class="error" role="alert">{{ formError }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="busy"
            @click="mode = null"
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
      :model-value="showCancelPlan"
      :title="cancelPlanLabel"
      :show-close="!busy"
      :close-on-overlay="!busy"
      @update:model-value="
        (v) => {
          if (!v && !busy) showCancelPlan = false
        }
      "
    >
      <div class="pc-form-grid recovery-form">
        <p>
          确认撤销“{{ detail?.title }}”的待报销金额
          {{ money(detail?.pendingAmount || 0) }}？
        </p>
        <p class="hint">
          原支出和账户余额不变。
          <template v-if="(detail?.reimbursedAmount || 0) > 0">
            已到账的
            {{ money(detail?.reimbursedAmount || 0) }} 及到账记录会保留。
          </template>
          <template v-else>撤销后可重新设置报销计划。</template>
        </p>
        <p v-if="formError" class="error" role="alert">{{ formError }}</p>
      </div>
      <template #footer>
        <div class="pc-toolbar modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="busy"
            @click="showCancelPlan = false"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="busy" @click="cancelPlan">
            {{ busy ? '撤销中...' : '确认撤销' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      :model-value="!!undoTarget"
      title="撤销到账"
      :show-close="!busy"
      :close-on-overlay="!busy"
      @update:model-value="
        (v) => {
          if (!v && !busy) undoTarget = null
        }
      "
    >
      <p class="hint">
        将从“{{ undoTarget?.accountName }}”扣回
        {{ money(undoTarget?.amount || 0) }}，并恢复对应支出和待到账金额。
      </p>
      <p v-if="formError" class="error" role="alert">{{ formError }}</p>
      <template #footer>
        <div class="pc-toolbar modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="busy"
            @click="undoTarget = null"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="busy" @click="undo">确认撤销</CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
