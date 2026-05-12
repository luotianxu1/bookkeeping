<script setup lang="ts">
// 现金账户页：支持管理模式切换与新增账户弹窗。
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import AmountText from '@/components/common/AmountText/index.vue'
import { cashAccountItems, cashAccountOverview } from '@/data/account'

const isManageMode = ref(false)
const showCreateAccountModal = ref(false)
const accountItems = ref([...cashAccountItems])
const router = useRouter()

const formName = ref('')
const formType = ref('现金账户')
const formAmount = ref('')
const formRemark = ref('')
const setAsCommon = ref(false)

const accountTypeOptions = ['现金账户', '银行卡', '第三方钱包', '备用金']

const computedOverviewAmount = computed(() => {
  const total = accountItems.value.reduce((sum, item) => {
    const numericAmount = Number(item.amount.replace(/[^\d.-]/g, '')) || 0
    return sum + numericAmount
  }, 0)

  return `${total.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
})

function toggleManageMode() {
  isManageMode.value = !isManageMode.value
}

function openCreateModal() {
  showCreateAccountModal.value = true
}

function closeCreateModal() {
  showCreateAccountModal.value = false
}

function resetForm() {
  formName.value = ''
  formType.value = '现金账户'
  formAmount.value = ''
  formRemark.value = ''
  setAsCommon.value = false
}

function createAccount() {
  const trimmedName = formName.value.trim()
  if (!trimmedName) return

  const numericAmount = Number(formAmount.value || '0')
  const normalizedAmount = Number.isFinite(numericAmount) ? numericAmount : 0

  accountItems.value.push({
    icon: formType.value === '银行卡' ? '🏦' : formType.value === '第三方钱包' ? '💳' : formType.value === '备用金' ? '🧧' : '💵',
    name: trimmedName,
    subtitle: formRemark.value.trim() || (setAsCommon.value ? '常用账户' : '新建账户'),
    amount: `${normalizedAmount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
  })

  closeCreateModal()
  resetForm()
}

function removeAccount(name: string) {
  accountItems.value = accountItems.value.filter((item) => item.name !== name)
}

function openDetail(path?: string) {
  if (!path || isManageMode.value) return
  router.push(path)
}
</script>

<template>
  <section class="cash-account-page" aria-label="现金账户">
    <header class="cash-account-header">
      <PageHeader title="现金账户" back-to="/finance/accounts" back-label="返回账户管理" />
      <button class="cash-account-manage" type="button" @click="toggleManageMode">
        管理
      </button>
    </header>

    <section class="cash-overview-card" aria-label="现金账户总额">
      <p>{{ cashAccountOverview.label }}</p>
      <AmountText tag="strong" :value="computedOverviewAmount || cashAccountOverview.amount" />
    </section>

    <section class="cash-list" aria-label="现金账户列表">
      <article v-for="item in accountItems" :key="item.name" class="cash-list-row">
        <button
          v-if="isManageMode"
          type="button"
          class="cash-remove-trigger"
          :aria-label="`删除${item.name}`"
          @click="removeAccount(item.name)"
        >
          <span class="cash-remove-dash"></span>
        </button>

        <button
          :class="['cash-list-item', { 'manage-shifted': isManageMode }]"
          type="button"
          @click="openDetail(item.path)"
        >
        <span class="cash-item-left">
          <span class="cash-item-icon">{{ item.icon }}</span>
          <span class="cash-item-text">
            <span class="cash-item-name">{{ item.name }}</span>
            <span class="cash-item-subtitle">{{ item.subtitle }}</span>
          </span>
        </span>

        <AmountText tag="strong" class="cash-item-amount" :value="item.amount" />
        </button>
      </article>
    </section>

    <button class="cash-account-fab" type="button" aria-label="新增现金账户" @click="openCreateModal">
      +
    </button>

    <CommonModal v-model="showCreateAccountModal" title="新增现金账户">
      <form class="cash-create-form" @submit.prevent="createAccount">
        <CommonInput v-model="formName" label="账户名称" placeholder="例如：日常钱包" />
        <CommonSelect v-model="formType" label="账户类型" :options="accountTypeOptions" />
        <CommonInput
          v-model="formAmount"
          label="初始金额"
          placeholder="0.00"
          input-type="number"
          input-mode="decimal"
        />
        <CommonInput v-model="formRemark" label="备注" placeholder="例如：日常零用" />
        <CommonSwitch v-model="setAsCommon" label="设为常用账户" />
      </form>

      <template #footer>
        <div class="cash-create-actions">
          <CommonButton variant="secondary" @click="closeCreateModal">取消</CommonButton>
          <CommonButton variant="primary" @click="createAccount">保存</CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
