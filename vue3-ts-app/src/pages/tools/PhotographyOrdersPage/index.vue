<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonFeedback from '@/components/common/CommonFeedback/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect, { type CommonSelectOption } from '@/components/common/CommonSelect/index.vue'
import FloatingAddButton from '@/components/common/FloatingAddButton/index.vue'
import MonthPicker from '@/components/common/MonthPicker/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import SegmentedControl from '@/components/common/SegmentedControl/index.vue'
import { getAccounts, type Account } from '@/api/modules/finance'
import {
  cancelPhotographyOrder,
  collectPhotographyOrderFinal,
  createPhotographyOrder,
  deletePhotographyOrder,
  getPhotographyOrders,
  updatePhotographyOrder,
  type PhotographyOrder,
  type PhotographyOrderType,
} from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

type TabKey = 'pending' | 'shot' | 'all'

type TypeSummaryCard = {
  key: PhotographyOrderType
  label: string
  accent: string
  count: number
}

const tabOptions = [
  { label: '未拍摄', value: 'pending' },
  { label: '已拍摄', value: 'shot' },
  { label: '全部', value: 'all' },
]

const typeOptions: Array<{ label: string; value: PhotographyOrderType }> = [
  { label: '周岁', value: 'first_birthday' },
  { label: '百天', value: 'hundred_days' },
  { label: '订婚', value: 'engagement' },
  { label: '答谢宴', value: 'thanks_banquet' },
  { label: '婚礼', value: 'wedding' },
  { label: '毕业照', value: 'graduation' },
]

const summaryAccents = ['brand', 'blue', 'green'] as const

const activeTab = ref<TabKey>('pending')
const selectedMonth = ref(buildCurrentMonth())
const router = useRouter()
const orders = ref<PhotographyOrder[]>([])
const cashAccounts = ref<Account[]>([])
const expandedOrderId = ref<number | null>(null)

const isLoading = ref(false)
const isSaving = ref(false)
const isUpdating = ref(false)
const isCollecting = ref(false)
const isDeleting = ref(false)
const isCancelling = ref(false)
const pageError = ref('')
const orderFormError = ref('')
const collectFormError = ref('')

const showOrderModal = ref(false)
const showCollectModal = ref(false)
const showDeleteModal = ref(false)
const showCancelModal = ref(false)
const feedbackVisible = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')

const selectedOrder = ref<PhotographyOrder | null>(null)
const editingOrder = ref<PhotographyOrder | null>(null)

const formOrderType = ref<PhotographyOrderType>('first_birthday')
const formShootAt = ref('')
const formContactInfo = ref('')
const formDepositAmount = ref('100')
const formFinalAmount = ref('299')
const formDepositAccountId = ref('')
const formAddress = ref('')
const formRemark = ref('')

const collectFinalAccountId = ref('')

const isBusy = computed(() =>
  isLoading.value
  || isSaving.value
  || isUpdating.value
  || isCollecting.value
  || isDeleting.value
  || isCancelling.value,
)
const selectedMonthOrders = computed(() => {
  const [yearText = '', monthText = ''] = selectedMonth.value.split('-')
  const targetYear = Number(yearText)
  const targetMonth = Number(monthText) - 1

  return orders.value.filter((order) => {
    const shootAt = new Date(order.shootAt)
    return shootAt.getFullYear() === targetYear && shootAt.getMonth() === targetMonth
  })
})

const activeMonthOrders = computed(() => selectedMonthOrders.value.filter((order) => !isCancelled(order)))

const filteredOrders = computed(() => {
  const targetStatus = activeTab.value
  const now = Date.now()
  const source = targetStatus === 'all'
    ? selectedMonthOrders.value
    : activeMonthOrders.value.filter((order) => isShotByTime(order, now) === (targetStatus === 'shot'))

  return [...source].sort((left, right) => {
    const leftTime = new Date(left.shootAt).getTime()
    const rightTime = new Date(right.shootAt).getTime()
    if (targetStatus === 'shot') {
      return rightTime - leftTime
    }
    return leftTime - rightTime
  })
})

const summary = computed(() => {
  const totalOrders = selectedMonthOrders.value.length
  const receivedAmount = selectedMonthOrders.value.reduce((sum, order) => {
    let total = sum
    if (order.depositReceivedAt) {
      total += Number(order.depositAmount ?? 0)
    }
    if (order.finalReceivedAt) {
      total += Number(order.finalAmount ?? 0)
    }
    return total
  }, 0)
  const finalPending = activeMonthOrders.value.reduce((sum, order) => {
    if (order.finalReceivedAt) {
      return sum
    }
    return sum + Number(order.finalAmount ?? 0)
  }, 0)
  const totalAmount = activeMonthOrders.value.reduce((sum, order) => {
    return sum + Number(order.depositAmount ?? 0) + Number(order.finalAmount ?? 0)
  }, 0)
  const typeCounts = typeOptions
    .map((item) => {
      const count = selectedMonthOrders.value.filter((order) => order.orderType === item.value).length

      return count > 0
        ? {
            key: item.value,
            label: item.label,
            count,
          }
        : null
    })
    .filter((item): item is Omit<TypeSummaryCard, 'accent'> => item !== null)
    .sort((left, right) => right.count - left.count)
    .slice(0, 3)
    .map((item, index) => ({
      ...item,
      accent: summaryAccents[index] ?? summaryAccents[summaryAccents.length - 1],
    }))

  return {
    totalOrders,
    receivedAmount,
    finalPending,
    totalAmount,
    typeCounts,
  }
})

const cashAccountOptions = computed<CommonSelectOption[]>(() => {
  if (cashAccounts.value.length === 0) {
    return [{ label: '暂无现金账户', value: '', disabled: true }]
  }

  return cashAccounts.value.map((account) => ({
    label: account.name,
    value: String(account.id),
  }))
})

onMounted(() => {
  void loadPage()
})

async function loadPage() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看摄影订单'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [orderList, accountList] = await Promise.all([
      getPhotographyOrders({ userId: currentUser.id, status: 'all' }),
      getAccounts({ userId: currentUser.id, status: 'active' }),
    ])

    orders.value = orderList
    cashAccounts.value = accountList.filter(isCashAccount)
    hydrateDefaultAccounts()
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '摄影订单加载失败'
  } finally {
    isLoading.value = false
  }
}

function isCashAccount(account: Account) {
  return account.accountTypeCode === 'cash' || account.accountTypeName?.includes('现金')
}

function hydrateDefaultAccounts() {
  const firstCashAccount = cashAccounts.value[0]
  if (!firstCashAccount) {
    return
  }

  if (!formDepositAccountId.value) {
    formDepositAccountId.value = String(firstCashAccount.id)
  }
  if (!collectFinalAccountId.value) {
    collectFinalAccountId.value = String(firstCashAccount.id)
  }
}

function openCreateModal() {
  editingOrder.value = null
  resetCreateForm()
  orderFormError.value = ''
  showOrderModal.value = true
}

function openEditModal(order: PhotographyOrder) {
  editingOrder.value = order
  formOrderType.value = order.orderType as PhotographyOrderType
  formShootAt.value = normalizeDateTimeInput(order.shootAt)
  formContactInfo.value = order.contactInfo ?? ''
  formDepositAmount.value = String(order.depositAmount ?? 0)
  formFinalAmount.value = String(order.finalAmount ?? 0)
  formDepositAccountId.value = String(order.depositAccountId ?? cashAccounts.value[0]?.id ?? '')
  formAddress.value = order.address ?? ''
  formRemark.value = order.remark ?? ''
  orderFormError.value = ''
  expandedOrderId.value = order.id
  showOrderModal.value = true
}

function closeOrderModal() {
  if (isSaving.value || isUpdating.value) {
    return
  }
  showOrderModal.value = false
  editingOrder.value = null
}

function resetCreateForm() {
  formOrderType.value = 'first_birthday'
  formShootAt.value = buildDefaultDateTimeLocal()
  formContactInfo.value = ''
  formDepositAmount.value = '100'
  formFinalAmount.value = '299'
  formDepositAccountId.value = cashAccounts.value[0] ? String(cashAccounts.value[0].id) : ''
  formAddress.value = ''
  formRemark.value = ''
}

async function saveOrder() {
  if (isSaving.value || isUpdating.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    orderFormError.value = `请先登录后再${editingOrder.value ? '修改' : '新增'}订单`
    return
  }

  const depositAmount = parseAmount(formDepositAmount.value)
  const finalAmount = parseAmount(formFinalAmount.value)
  const totalAmount = roundAmount(depositAmount + finalAmount)

  if (!formShootAt.value) {
    orderFormError.value = '请选择拍摄时间'
    return
  }
  if (totalAmount <= 0) {
    orderFormError.value = '总金额必须大于0'
    return
  }
  if (depositAmount < 0 || finalAmount < 0) {
    orderFormError.value = '金额不能小于0'
    return
  }
  if (depositAmount > 0 && !formDepositAccountId.value) {
    orderFormError.value = '请选择订金收款账户'
    return
  }

  const isEditing = Boolean(editingOrder.value)
  if (isEditing) {
    isUpdating.value = true
  } else {
    isSaving.value = true
  }
  orderFormError.value = ''

  try {
    const params = {
      userId: currentUser.id,
      contactInfo: nullableText(formContactInfo.value),
      orderType: formOrderType.value,
      shootAt: normalizeDateTimeLocal(formShootAt.value),
      totalAmount,
      depositAmount,
      finalAmount,
      depositAccountId: depositAmount > 0 && formDepositAccountId.value ? Number(formDepositAccountId.value) : null,
      address: nullableText(formAddress.value),
      remark: nullableText(formRemark.value),
      sortOrder: editingOrder.value?.sortOrder ?? getNextSortOrder(),
    }

    if (editingOrder.value) {
      await updatePhotographyOrder(editingOrder.value.id, params)
    } else {
      await createPhotographyOrder(params)
    }

    showOrderModal.value = false
    editingOrder.value = null
    expandedOrderId.value = null
    showFeedback(isEditing ? '摄影订单修改成功' : '新增摄影订单成功', 'success')
    await loadPage()
  } catch (error) {
    orderFormError.value = error instanceof Error
      ? error.message
      : `${isEditing ? '修改' : '新增'}订单失败`
    showFeedback(orderFormError.value, 'error')
  } finally {
    isSaving.value = false
    isUpdating.value = false
  }
}

function isDepositLocked() {
  return Boolean(editingOrder.value?.depositTransactionId || editingOrder.value?.depositReceivedAt)
}

function isFinalAmountLocked() {
  return Boolean(editingOrder.value?.finalTransactionId || editingOrder.value?.finalReceivedAt)
}

function toggleOrder(orderId: number) {
  expandedOrderId.value = expandedOrderId.value === orderId ? null : orderId
}

function openCollectModal(order: PhotographyOrder) {
  selectedOrder.value = order
  collectFormError.value = ''
  collectFinalAccountId.value = String(order.finalAccountId ?? order.depositAccountId ?? cashAccounts.value[0]?.id ?? '')
  showCollectModal.value = true
}

function closeCollectModal() {
  if (isCollecting.value) {
    return
  }
  showCollectModal.value = false
  selectedOrder.value = null
  collectFormError.value = ''
}

async function confirmCollectFinal() {
  if (isCollecting.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    collectFormError.value = '请先登录后再收尾款'
    return
  }

  const order = selectedOrder.value
  if (!order) {
    collectFormError.value = '未找到订单信息'
    return
  }

  if (Number(order.finalAmount) > 0 && !collectFinalAccountId.value) {
    collectFormError.value = '请选择尾款入账账户'
    return
  }

  isCollecting.value = true
  collectFormError.value = ''

  try {
    await collectPhotographyOrderFinal(order.id, {
      userId: currentUser.id,
      finalAccountId: Number(order.finalAmount) > 0 && collectFinalAccountId.value
        ? Number(collectFinalAccountId.value)
        : null,
      occurredAt: normalizeDateTimeLocal(buildDefaultDateTimeLocal()),
    })

    showCollectModal.value = false
    selectedOrder.value = null
    expandedOrderId.value = null
    showFeedback('尾款已入账', 'success')
    await loadPage()
  } catch (error) {
    collectFormError.value = error instanceof Error ? error.message : '尾款入账失败'
    showFeedback(collectFormError.value, 'error')
  } finally {
    isCollecting.value = false
  }
}

function openDeleteModal(order: PhotographyOrder) {
  selectedOrder.value = order
  showDeleteModal.value = true
}

function closeDeleteModal() {
  if (isDeleting.value) {
    return
  }
  showDeleteModal.value = false
  selectedOrder.value = null
}

function openCancelModal(order: PhotographyOrder) {
  selectedOrder.value = order
  showCancelModal.value = true
}

function closeCancelModal() {
  if (isCancelling.value) {
    return
  }
  showCancelModal.value = false
  selectedOrder.value = null
}

async function confirmCancelOrder() {
  if (isCancelling.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const order = selectedOrder.value
  if (!currentUser || !order) {
    return
  }

  isCancelling.value = true

  try {
    await cancelPhotographyOrder(order.id, currentUser.id)
    showCancelModal.value = false
    selectedOrder.value = null
    expandedOrderId.value = null
    showFeedback('拍摄已取消，订金已保留', 'success')
    await loadPage()
  } catch (error) {
    showFeedback(error instanceof Error ? error.message : '取消拍摄失败', 'error')
  } finally {
    isCancelling.value = false
  }
}

async function confirmDeleteOrder() {
  if (isDeleting.value) {
    return
  }

  const currentUser = getStoredCurrentUser()
  const order = selectedOrder.value
  if (!currentUser || !order) {
    return
  }

  isDeleting.value = true

  try {
    await deletePhotographyOrder(order.id, currentUser.id)
    showDeleteModal.value = false
    selectedOrder.value = null
    expandedOrderId.value = null
    showFeedback('订单删除成功', 'success')
    await loadPage()
  } catch (error) {
    showFeedback(error instanceof Error ? error.message : '删除订单失败', 'error')
  } finally {
    isDeleting.value = false
  }
}

function getNextSortOrder() {
  return orders.value.reduce((max, order) => Math.max(max, Number(order.sortOrder ?? 0)), 0) + 10
}

function isShotByTime(order: PhotographyOrder, now = Date.now()) {
  if (isCancelled(order)) {
    return false
  }
  const shootAt = new Date(order.shootAt).getTime()
  return Number.isFinite(shootAt) && shootAt <= now
}

function showFeedback(message: string, type: 'success' | 'error') {
  feedbackMessage.value = message
  feedbackType.value = type
  feedbackVisible.value = true
}

function parseAmount(value: string) {
  const normalized = Number(value.trim() || '0')
  if (!Number.isFinite(normalized)) {
    return 0
  }
  return roundAmount(normalized)
}

function roundAmount(value: number) {
  return Number(value.toFixed(2))
}

function nullableText(value: string) {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function normalizeDateTimeLocal(value: string) {
  return value.length === 16 ? `${value}:00` : value
}

function normalizeDateTimeInput(value: string) {
  return value.replace(' ', 'T').slice(0, 16)
}

function buildDefaultDateTimeLocal() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hour = String(now.getHours()).padStart(2, '0')
  const minute = String(now.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

function formatDateTime(value: string) {
  if (!value) {
    return '--'
  }

  const [datePart = '', timePart = ''] = value.replace('T', ' ').split(' ')
  const displayDate = datePart.replace(/-/g, '.')
  return `${displayDate} ${timePart.slice(0, 5)}`
}

function formatCurrency(value: number | string | null | undefined) {
  const amount = Number(value ?? 0)
  return `¥${amount.toLocaleString('zh-CN', {
    minimumFractionDigits: amount % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  })}`
}

function orderTypeLabel(type: string) {
  return typeOptions.find((option) => option.value === type)?.label ?? type
}

function depositStatusLabel(order: PhotographyOrder) {
  if (isShotByTime(order)) {
    return '已拍摄'
  }
  return Number(order.depositAmount) > 0 ? '已付订金' : '未付订金'
}

function depositStatusClass(order: PhotographyOrder) {
  return isShotByTime(order) ? 'order-card-deposit--shot' : 'order-card-deposit--pending'
}

function finalStatusLabel(order: PhotographyOrder) {
  if (isCancelled(order)) {
    return '已取消'
  }
  if (Number(order.finalAmount ?? 0) <= 0) {
    return '无尾款'
  }

  return isFinalPaid(order) ? '尾款已收' : '待收尾款'
}

function finalStatusClass(order: PhotographyOrder) {
  if (isCancelled(order)) {
    return 'order-card-final--cancelled'
  }
  if (Number(order.finalAmount ?? 0) <= 0) {
    return 'order-card-final--empty'
  }

  return isFinalPaid(order) ? 'order-card-final--paid' : 'order-card-final--pending'
}

function amountTextClass(value: number | string | null | undefined) {
  return Number(value ?? 0) === 0 ? 'is-zero' : ''
}

function isFinalPaid(order: PhotographyOrder) {
  return Boolean(order.finalReceivedAt)
}

function isCancelled(order: PhotographyOrder) {
  return order.status === 'cancelled'
}

function openOverview() {
  const [year = String(new Date().getFullYear()), month = String(new Date().getMonth() + 1).padStart(2, '0')] = selectedMonth.value.split('-')
  void router.push(`/tools/photography-orders/overview?view=calendar&anchor=${year}-${month}`)
}

function buildCurrentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}
</script>

<template>
  <section class="photography-orders-page" aria-label="摄影订单管理">
    <PageHeader title="摄影订单" back-to="/tools" back-label="返回工具页" />

    <MonthPicker :model-value="selectedMonth" @update:model-value="selectedMonth = $event" />

    <section class="orders-summary-card" aria-label="摄影订单汇总">
      <div class="orders-summary-head">
        <strong>{{ summary.totalOrders }} 单</strong>
        <button type="button" class="orders-summary-pill" @click="openOverview">
          {{ Number(selectedMonth.split('-')[1] || '0') }}月订单
        </button>
      </div>
      <p>已收款 {{ formatCurrency(summary.receivedAmount) }} · 待收尾款 {{ formatCurrency(summary.finalPending) }} · 总金额 {{ formatCurrency(summary.totalAmount) }}</p>
      <div class="orders-summary-types">
        <article
          v-for="item in summary.typeCounts"
          :key="item.key"
          :class="['orders-summary-type', `orders-summary-type--${item.accent}`]"
        >
          <strong>{{ item.label }} {{ item.count }}</strong>
        </article>
      </div>
    </section>

    <SegmentedControl
      v-model="activeTab"
      :options="tabOptions"
      label="摄影订单状态切换"
      variant="brand"
    />

    <CommonLoading v-if="isBusy" text="处理中..." />
    <CommonFeedback v-model="feedbackVisible" :message="feedbackMessage" :type="feedbackType" />

    <p v-if="pageError" class="orders-page-error">{{ pageError }}</p>
    <p v-else-if="filteredOrders.length === 0" class="orders-empty">暂无摄影订单</p>

    <section v-else class="orders-list" aria-label="摄影订单列表">
      <article
        v-for="order in filteredOrders"
        :key="order.id"
        :class="['order-card', { 'order-card-expanded': expandedOrderId === order.id }]"
        @click="toggleOrder(order.id)"
      >
        <div class="order-card-top">
          <div class="order-card-customer">
            <div>
              <strong>{{ orderTypeLabel(order.orderType) }}</strong>
              <p>{{ formatDateTime(order.shootAt) }}</p>
            </div>
          </div>

          <div class="order-card-meta">
            <span :class="['order-card-deposit', depositStatusClass(order)]">{{ depositStatusLabel(order) }}</span>
            <span :class="['order-card-final', finalStatusClass(order)]">{{ finalStatusLabel(order) }}</span>
          </div>
        </div>

        <div class="order-card-amounts">
          <div class="order-card-amount">
            <span>订金</span>
            <strong :class="amountTextClass(order.depositAmount)">{{ formatCurrency(order.depositAmount) }}</strong>
          </div>
          <div class="order-card-amount">
            <span>尾款</span>
            <strong :class="amountTextClass(order.finalAmount)">{{ formatCurrency(order.finalAmount) }}</strong>
          </div>
        </div>

        <div class="order-card-bottom">
          <p v-if="order.address">地址：{{ order.address }}</p>
          <p v-if="order.contactInfo">联系方式：{{ order.contactInfo }}</p>
          <p v-if="order.remark">{{ order.remark }}</p>
        </div>

        <div v-if="expandedOrderId === order.id" class="order-card-actions" @click.stop>
          <button
            class="order-card-action order-card-action-cancel"
            type="button"
            :disabled="isCancelled(order)"
            @click="openCancelModal(order)"
          >
            {{ isCancelled(order) ? '已取消' : '取消' }}
          </button>
          <button class="order-card-action order-card-action-edit" type="button" @click="openEditModal(order)">
            修改
          </button>
          <button class="order-card-action order-card-action-delete" type="button" @click="openDeleteModal(order)">
            删除
          </button>
          <button
            class="order-card-action order-card-action-primary"
            type="button"
            :disabled="isFinalPaid(order) || isCancelled(order)"
            @click="openCollectModal(order)"
          >
            {{ isFinalPaid(order) ? '已收尾款' : '收尾款' }}
          </button>
        </div>
      </article>
    </section>

    <FloatingAddButton aria-label="新增摄影订单" storage-key="photography-order" @click="openCreateModal" />

    <CommonModal
      v-model="showOrderModal"
      :title="editingOrder ? '修改摄影订单' : '新增摄影订单'"
      size="compact"
      :show-close="!isSaving && !isUpdating"
      @close="orderFormError = ''"
    >
      <div class="order-form">
        <CommonSelect v-model="formOrderType" label="订单类型" :options="typeOptions" />
        <CommonInput v-model="formShootAt" label="拍摄时间" input-type="datetime-local" />
        <CommonInput v-model="formContactInfo" label="联系方式" placeholder="138****2001 / 微信同号" />

        <div class="order-form-split">
          <CommonInput
            v-model="formDepositAmount"
            label="订金"
            placeholder="¥ 100"
            input-mode="decimal"
            :disabled="isDepositLocked()"
          />
          <CommonInput
            v-model="formFinalAmount"
            label="尾款"
            placeholder="¥ 299"
            input-mode="decimal"
            :disabled="isFinalAmountLocked()"
          />
        </div>

        <CommonSelect
          v-model="formDepositAccountId"
          label="收款账户"
          :options="cashAccountOptions"
          :disabled="isDepositLocked()"
        />
        <CommonInput v-model="formAddress" label="地址" placeholder="请输入拍摄地址" />

        <label class="order-form-field">
          <span>备注</span>
          <textarea v-model="formRemark" placeholder="服装两套，外景在公园，需提前沟通天气"></textarea>
        </label>

        <p v-if="orderFormError" class="order-form-error">{{ orderFormError }}</p>
        <p v-if="editingOrder && (isDepositLocked() || isFinalAmountLocked())" class="order-form-tip">
          已入账的金额和账户不可修改，其他订单信息仍可调整。
        </p>
      </div>

      <template #footer>
        <div class="orders-modal-actions">
          <CommonButton variant="secondary" :disabled="isSaving || isUpdating" @click="closeOrderModal">取消</CommonButton>
          <CommonButton :disabled="isSaving || isUpdating" @click="saveOrder">
            {{ isSaving || isUpdating ? '保存中...' : '保存订单' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal v-model="showCollectModal" title="确认收尾款" size="compact" @close="collectFormError = ''">
      <div v-if="selectedOrder" class="collect-modal">
        <p class="collect-modal-subtitle">
          {{ orderTypeLabel(selectedOrder.orderType) }} · {{ formatDateTime(selectedOrder.shootAt) }}
        </p>
        <section class="collect-amount-card">
          <span>待收尾款</span>
          <strong>{{ formatCurrency(selectedOrder.finalAmount) }}</strong>
        </section>
        <CommonSelect
          v-if="Number(selectedOrder.finalAmount) > 0"
          v-model="collectFinalAccountId"
          label="入账账户"
          :options="cashAccountOptions"
        />
        <p class="collect-modal-tip">确认前可切换尾款入账账户，确认后会记录本次尾款收款，并将该订单归入已拍摄。</p>
        <p v-if="collectFormError" class="order-form-error">{{ collectFormError }}</p>
      </div>

      <template #footer>
        <div class="orders-modal-actions">
          <CommonButton variant="secondary" :disabled="isCollecting" @click="closeCollectModal">取消</CommonButton>
          <CommonButton :disabled="isCollecting" @click="confirmCollectFinal">
            {{ isCollecting ? '提交中...' : '确认收款' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal v-model="showDeleteModal" title="删除订单" size="compact">
      <div class="delete-modal">
        <p class="delete-modal-title">是否确认删除？</p>
        <p class="delete-modal-tip">订单、订金和尾款记录会一并删除，请谨慎操作。</p>
      </div>

      <template #footer>
        <div class="orders-modal-actions">
          <CommonButton variant="secondary" :disabled="isDeleting" @click="closeDeleteModal">取消</CommonButton>
          <CommonButton class="orders-modal-danger" :disabled="isDeleting" @click="confirmDeleteOrder">
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>

    <CommonModal v-model="showCancelModal" title="取消拍摄" size="compact">
      <div class="delete-modal">
        <p class="delete-modal-title">是否确认取消这次拍摄？</p>
        <p class="delete-modal-tip">订单会标记为已取消，订金收款记录会保留，不会再计入待收尾款。</p>
      </div>

      <template #footer>
        <div class="orders-modal-actions">
          <CommonButton variant="secondary" :disabled="isCancelling" @click="closeCancelModal">取消</CommonButton>
          <CommonButton class="orders-modal-danger" :disabled="isCancelling" @click="confirmCancelOrder">
            {{ isCancelling ? '取消中...' : '确认取消' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
