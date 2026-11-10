<script setup lang="ts">
import DesktopTable from '@/business/components/DesktopTable.vue'

import DesktopInput from '@/business/components/DesktopInput.vue'

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/business/components/common/CommonButton/index.vue'

import CommonFeedback from '@/business/components/common/CommonFeedback/index.vue'
import CommonInput from '@/business/components/common/CommonInput/index.vue'

import CommonModal from '@/business/components/common/CommonModal/index.vue'
import CommonSelect, {
  type CommonSelectOption,
} from '@/business/components/common/CommonSelect/index.vue'

import MonthPicker from '@/business/components/common/MonthPicker/index.vue'
import PageHeader from '@/business/components/common/PageHeader/index.vue'
import SegmentedControl from '@/business/components/common/SegmentedControl/index.vue'
import { getAccounts, type Account } from '@/business/api/modules/finance'
import {
  cancelPhotographyOrder,
  collectPhotographyOrderFinal,
  createPhotographyOrder,
  deletePhotographyOrder,
  getPhotographyOrders,
  updatePhotographyOrder,
  type PhotographyOrder,
  type PhotographyOrderType,
} from '@/business/api/modules/tool'
import { getStoredCurrentUser } from '@/business/utils/current-user'
import { getErrorMessage } from '@/business/api/request'
import { useFeedback } from '@/business/composables/useFeedback'
import {
  buildCurrentMonth,
  buildDefaultDateTimeLocal,
  finalStatusLabel,
  formatCurrency,
  formatDateTime,
  isCancelled,
  isCashAccount,
  isFinalPaid,
  isShotByTime,
  normalizeDateTimeInput,
  normalizeDateTimeLocal,
  nullableText,
  orderTypeLabel,
  parseAmount,
  roundAmount,
  typeOptions,
} from './photographyOrderFormat'

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

const summaryAccents = ['brand', 'blue', 'green'] as const

const activeTab = ref<TabKey>('pending')
const selectedMonth = ref(buildCurrentMonth())
const router = useRouter()
const orders = ref<PhotographyOrder[]>([])
const cashAccounts = ref<Account[]>([])
const expandedOrderId = ref<number | null>(null)

const isLoading = ref(true)
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
const {
  showFeedbackModal: feedbackVisible,
  feedbackMessage,
  feedbackType,
  showFeedback,
} = useFeedback()

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

const selectedMonthOrders = computed(() => {
  const [yearText = '', monthText = ''] = selectedMonth.value.split('-')
  const targetYear = Number(yearText)
  const targetMonth = Number(monthText) - 1

  return orders.value.filter((order) => {
    const shootAt = new Date(order.shootAt)
    return (
      shootAt.getFullYear() === targetYear && shootAt.getMonth() === targetMonth
    )
  })
})

const activeMonthOrders = computed(() =>
  selectedMonthOrders.value.filter((order) => !isCancelled(order)),
)

const filteredOrders = computed(() => {
  const targetStatus = activeTab.value
  const now = Date.now()
  const source =
    targetStatus === 'all'
      ? selectedMonthOrders.value
      : activeMonthOrders.value.filter(
          (order) => isShotByTime(order, now) === (targetStatus === 'shot'),
        )

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
    return (
      sum + Number(order.depositAmount ?? 0) + Number(order.finalAmount ?? 0)
    )
  }, 0)
  const typeCounts = typeOptions
    .map((item) => {
      const count = selectedMonthOrders.value.filter(
        (order) => order.orderType === item.value,
      ).length

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
      accent:
        summaryAccents[index] ?? summaryAccents[summaryAccents.length - 1],
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
    isLoading.value = false
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
    pageError.value = getErrorMessage(error, '摄影订单加载失败')
  } finally {
    isLoading.value = false
  }
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
  formDepositAccountId.value = String(
    order.depositAccountId ?? cashAccounts.value[0]?.id ?? '',
  )
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
  formDepositAccountId.value = cashAccounts.value[0]
    ? String(cashAccounts.value[0].id)
    : ''
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
      depositAccountId:
        depositAmount > 0 && formDepositAccountId.value
          ? Number(formDepositAccountId.value)
          : null,
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
    orderFormError.value = getErrorMessage(
      error,
      `${isEditing ? '修改' : '新增'}订单失败`,
    )
    showFeedback(orderFormError.value, 'error')
  } finally {
    isSaving.value = false
    isUpdating.value = false
  }
}

function isDepositLocked() {
  return Boolean(
    editingOrder.value?.depositTransactionId ||
      editingOrder.value?.depositReceivedAt,
  )
}

function isFinalAmountLocked() {
  return Boolean(
    editingOrder.value?.finalTransactionId ||
      editingOrder.value?.finalReceivedAt,
  )
}

function openCollectModal(order: PhotographyOrder) {
  selectedOrder.value = order
  collectFormError.value = ''
  collectFinalAccountId.value = String(
    order.finalAccountId ??
      order.depositAccountId ??
      cashAccounts.value[0]?.id ??
      '',
  )
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
      finalAccountId:
        Number(order.finalAmount) > 0 && collectFinalAccountId.value
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
    collectFormError.value = getErrorMessage(error, '尾款入账失败')
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
    showFeedback(getErrorMessage(error, '取消拍摄失败'), 'error')
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
    showFeedback(getErrorMessage(error, '删除订单失败'), 'error')
  } finally {
    isDeleting.value = false
  }
}

function getNextSortOrder() {
  return (
    orders.value.reduce(
      (max, order) => Math.max(max, Number(order.sortOrder ?? 0)),
      0,
    ) + 10
  )
}

function openOverview() {
  const [
    year = String(new Date().getFullYear()),
    month = String(new Date().getMonth() + 1).padStart(2, '0'),
  ] = selectedMonth.value.split('-')
  void router.push(
    `/tools/photography-orders/overview?view=calendar&anchor=${year}-${month}`,
  )
}

type DesktopRow = (typeof filteredOrders.value)[number]
</script>
<template>
  <section class="desktop-workspace desktop-list-workspace">
    <PageHeader title="摄影订单">
      <template #right>
        <el-button @click="openOverview">订单总览</el-button>
        <el-button type="primary" @click="openCreateModal">新增订单</el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
    />
    <div class="pc-statistics">
      <el-card shadow="never">
        <small>订单数</small>
        <strong>{{ summary.totalOrders }}</strong>
      </el-card>
      <el-card shadow="never">
        <small>已收款</small>
        <strong>{{ formatCurrency(summary.receivedAmount) }}</strong>
      </el-card>
      <el-card shadow="never">
        <small>待收尾款</small>
        <strong>{{ formatCurrency(summary.finalPending) }}</strong>
      </el-card>
    </div>
    <DesktopTable
      title="订单明细"
      searchable
      search-placeholder="联系方式 / 拍摄地址"
      :rows="filteredOrders"
      :loading="isLoading"
    >
      <template #toolbar>
        <MonthPicker v-model="selectedMonth" />
        <SegmentedControl
          v-model="activeTab"
          :options="tabOptions"
          label="订单状态"
        />
      </template>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="类型" min-width="130">
        <template #default="{ row }: { row: DesktopRow }">
          {{ orderTypeLabel(row.orderType) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="拍摄时间" min-width="180">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatDateTime(row.shootAt) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="联系方式"
        prop="contactInfo"
        sortable="custom"
        min-width="180"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column
        label="地址"
        prop="address"
        sortable="custom"
        min-width="220"
        show-overflow-tooltip
      />
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="订金" min-width="120">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatCurrency(row.depositAmount) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="尾款" min-width="120">
        <template #default="{ row }: { row: DesktopRow }">
          {{ formatCurrency(row.finalAmount) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }: { row: DesktopRow }">
          {{ finalStatusLabel(row) }}
        </template>
      </el-table-column>
      <!-- @vue-generic {DesktopRow} -->
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }: { row: DesktopRow }">
          <div class="pc-row-actions">
            <el-button link type="primary" @click="openEditModal(row)">
              编辑
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="
                isCancelled(row) ||
                isFinalPaid(row) ||
                Number(row.finalAmount) <= 0
              "
              @click="openCollectModal(row)"
            >
              收尾款
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="isCancelled(row)"
              @click="openCancelModal(row)"
            >
              取消
            </el-button>
            <el-button link type="danger" @click="openDeleteModal(row)">
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </DesktopTable>
    <CommonFeedback
      v-model="feedbackVisible"
      :message="feedbackMessage"
      :type="feedbackType"
    />
    <CommonModal
      v-model="showOrderModal"
      :title="editingOrder ? '修改摄影订单' : '新增摄影订单'"
      size="compact"
      :show-close="!isSaving && !isUpdating"
      @close="orderFormError = ''"
    >
      <div class="pc-form-grid order-form">
        <CommonSelect
          v-model="formOrderType"
          label="订单类型"
          :options="typeOptions"
        />
        <CommonInput
          v-model="formShootAt"
          label="拍摄时间"
          input-type="datetime-local"
        />
        <CommonInput
          v-model="formContactInfo"
          label="联系方式"
          placeholder="138****2001 / 微信同号"
        />
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
        <CommonInput
          v-model="formAddress"
          label="地址"
          placeholder="请输入拍摄地址"
        />
        <label class="pc-field order-form-field">
          <span>备注</span>
          <DesktopInput
            v-model="formRemark"
            placeholder="服装两套，外景在公园，需提前沟通天气"
            type="textarea"
          ></DesktopInput>
        </label>
        <p v-if="orderFormError" class="order-form-error">
          {{ orderFormError }}
        </p>
        <p
          v-if="editingOrder && (isDepositLocked() || isFinalAmountLocked())"
          class="order-form-tip"
        >
          已入账的金额和账户不可修改，其他订单信息仍可调整。
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar orders-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isSaving || isUpdating"
            @click="closeOrderModal"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="isSaving || isUpdating" @click="saveOrder">
            {{ isSaving || isUpdating ? '保存中...' : '保存订单' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal
      v-model="showCollectModal"
      title="确认收尾款"
      size="compact"
      @close="collectFormError = ''"
    >
      <div v-if="selectedOrder" class="collect-modal">
        <p class="collect-modal-subtitle">
          {{ orderTypeLabel(selectedOrder.orderType) }} ·
          {{ formatDateTime(selectedOrder.shootAt) }}
        </p>
        <el-card class="pc-record-card collect-amount-card" shadow="never">
          <span>待收尾款</span>
          <strong>{{ formatCurrency(selectedOrder.finalAmount) }}</strong>
        </el-card>
        <CommonSelect
          v-if="Number(selectedOrder.finalAmount) > 0"
          v-model="collectFinalAccountId"
          label="入账账户"
          :options="cashAccountOptions"
        />
        <p class="collect-modal-tip">
          确认前可切换尾款入账账户，确认后会记录本次尾款收款，并将该订单归入已拍摄。
        </p>
        <p v-if="collectFormError" class="order-form-error">
          {{ collectFormError }}
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar orders-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isCollecting"
            @click="closeCollectModal"
          >
            取消
          </CommonButton>
          <CommonButton :disabled="isCollecting" @click="confirmCollectFinal">
            {{ isCollecting ? '提交中...' : '确认收款' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal v-model="showDeleteModal" title="删除订单" size="compact">
      <div class="delete-modal">
        <p class="delete-modal-title">是否确认删除？</p>
        <p class="delete-modal-tip">
          订单、订金和尾款记录会一并删除，请谨慎操作。
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar orders-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isDeleting"
            @click="closeDeleteModal"
          >
            取消
          </CommonButton>
          <CommonButton
            class="orders-modal-danger"
            :disabled="isDeleting"
            @click="confirmDeleteOrder"
          >
            {{ isDeleting ? '删除中...' : '确认删除' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
    <CommonModal v-model="showCancelModal" title="取消拍摄" size="compact">
      <div class="delete-modal">
        <p class="delete-modal-title">是否确认取消这次拍摄？</p>
        <p class="delete-modal-tip">
          订单会标记为已取消，订金收款记录会保留，不会再计入待收尾款。
        </p>
      </div>
      <template #footer>
        <div class="pc-toolbar orders-modal-actions">
          <CommonButton
            variant="secondary"
            :disabled="isCancelling"
            @click="closeCancelModal"
          >
            取消
          </CommonButton>
          <CommonButton
            class="orders-modal-danger"
            :disabled="isCancelling"
            @click="confirmCancelOrder"
          >
            {{ isCancelling ? '取消中...' : '确认取消' }}
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>
