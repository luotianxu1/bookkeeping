<script setup lang="ts">
// 账户管理页：还原 Pencil「账户管理」画板中的总览、账户分组和新增按钮。
import { ref } from 'vue'
import CommonButton from '@/components/common/CommonButton/index.vue'
import CommonInput from '@/components/common/CommonInput/index.vue'
import CommonModal from '@/components/common/CommonModal/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import CommonSelect from '@/components/common/CommonSelect/index.vue'
import CommonSwitch from '@/components/common/CommonSwitch/index.vue'
import { accountGroups, accountOverview } from '@/data/account'
import AccountGroupCard from '../components/AccountGroupCard/index.vue'
import AccountOverviewCard from '../components/AccountOverviewCard/index.vue'

const showCreateAccountModal = ref(false)
const accountName = ref('')
const accountType = ref('现金账户')
const initialAmount = ref('')
const setAsDefault = ref(false)

function closeCreateAccountModal() {
  showCreateAccountModal.value = false
}

function saveAccount() {
  showCreateAccountModal.value = false
}
</script>

<template>
  <section class="account-management-page" aria-label="账户管理">
    <PageHeader title="账户管理" back-to="/finance" back-label="返回财务首页" />

    <AccountOverviewCard :overview="accountOverview" />

    <div class="account-groups">
      <AccountGroupCard v-for="group in accountGroups" :key="group.title" :group="group" />
    </div>

    <button
      class="account-fab"
      type="button"
      aria-label="新增账户"
      @click="showCreateAccountModal = true"
    >
      +
    </button>

    <CommonModal v-model="showCreateAccountModal" title="新增账户">
      <form class="create-account-form">
        <CommonInput v-model="accountName" label="账户名称" placeholder="例如：日常钱包" />
        <CommonSelect
          v-model="accountType"
          label="账户类型"
          :options="['现金账户', '投资账户', '储蓄账户']"
        />
        <CommonInput
          v-model="initialAmount"
          label="初始金额"
          placeholder="0.00"
          input-type="number"
          input-mode="decimal"
        />
        <CommonSwitch v-model="setAsDefault" label="设为默认账户" />
      </form>

      <template #footer>
        <div class="create-account-actions">
          <CommonButton variant="secondary" @click="closeCreateAccountModal">
            取消
          </CommonButton>
          <CommonButton variant="primary" @click="saveAccount">
            保存
          </CommonButton>
        </div>
      </template>
    </CommonModal>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
