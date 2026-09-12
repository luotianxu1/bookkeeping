<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommonLoading from '@/components/common/CommonLoading/index.vue'
import PageHeader from '@/components/common/PageHeader/index.vue'
import {
  getAccounts,
  getDebtRecords,
  type Account,
  type DebtRecord,
} from '@/api/modules/finance'
import { getContacts, type Contact } from '@/api/modules/tool'
import { getStoredCurrentUser } from '@/utils/current-user'

type DebtHistoryGroup = {
  accountId: number
  name: string
  avatarText: string
  avatarClass: string
  records: DebtRecord[]
  balance: number
  settled: boolean
}

const DEBT_ACCOUNT_CODES = new Set(['debt'])

const router = useRouter()
const accounts = ref<Account[]>([])
const contacts = ref<Contact[]>([])
const debtRecords = ref<DebtRecord[]>([])
const isLoading = ref(false)
const pageError = ref('')

const contactMap = computed(() => new Map(contacts.value.map((contact) => [contact.id, contact])))
const debtGroups = computed<DebtHistoryGroup[]>(() => {
  const recordsByAccountId = new Map<number, DebtRecord[]>()
  for (const record of debtRecords.value) {
    const records = recordsByAccountId.get(record.accountId) ?? []
    records.push(record)
    recordsByAccountId.set(record.accountId, records)
  }

  return accounts.value
    .filter((account) => DEBT_ACCOUNT_CODES.has(account.accountTypeCode ?? ''))
    .map((account, index) => {
      const records = (recordsByAccountId.get(account.id) ?? []).slice().sort((left, right) => (
        new Date(right.occurredAt).getTime() - new Date(left.occurredAt).getTime()
      ))
      const contact = account.contactId ? contactMap.value.get(account.contactId) ?? null : null
      const name = contact?.name?.trim() || account.name
      const balance = sumDebtBalance(records)

      return {
        accountId: account.id,
        name,
        avatarText: (name || '债').slice(0, 1),
        avatarClass: `debt-history-avatar-${index % 4}`,
        records,
        balance,
        settled: Math.round(balance * 100) === 0,
      }
    })
    .filter((group) => group.records.length > 0 && group.settled)
})

const settledAccountCount = computed(() => debtGroups.value.length)

function openDebtDetail(accountId: number) {
  router.push(`/finance/accounts/debt/${accountId}`)
}

function handleGroupKeydown(event: KeyboardEvent, accountId: number) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    openDebtDetail(accountId)
  }
}

onMounted(() => {
  void loadHistory()
})

async function loadHistory() {
  const currentUser = getStoredCurrentUser()
  if (!currentUser) {
    pageError.value = '请先登录后查看债务明细'
    return
  }

  isLoading.value = true
  pageError.value = ''

  try {
    const [accountList, contactList, recordList] = await Promise.all([
      getAccounts({ userId: currentUser.id, status: 'active' }),
      getContacts({ userId: currentUser.id, status: 'active' }),
      getDebtRecords({ userId: currentUser.id }),
    ])
    accounts.value = accountList
    contacts.value = contactList
    debtRecords.value = recordList
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : '债务明细加载失败'
  } finally {
    isLoading.value = false
  }
}

function sumDebtBalance(records: Array<Pick<DebtRecord, 'direction' | 'recordType' | 'amount'>>) {
  return records.reduce((total, record) => total + getDebtRecordBalanceDelta(record), 0)
}

function getDebtRecordBalanceDelta(record: Pick<DebtRecord, 'direction' | 'recordType' | 'amount'>) {
  const amount = Number(record.amount ?? 0)
  if (!Number.isFinite(amount) || amount <= 0) {
    return 0
  }
  if (record.direction === 'receivable') {
    return record.recordType === 'repayment' ? -amount : amount
  }
  return record.recordType === 'repayment' ? amount : -amount
}

</script>

<template>
  <section class="debt-history-page" aria-label="已结清债务账户">
    <header class="debt-history-header">
      <PageHeader title="已结清明细" back-to="/finance/accounts/debt" back-label="返回债务账户" />
    </header>

    <p v-if="pageError" class="debt-history-message debt-history-message-error">
      {{ pageError }}
    </p>
    <CommonLoading v-else-if="isLoading" text="已结清明细加载中..." />

    <template v-else>
      <div class="debt-history-summary">
        <strong>{{ settledAccountCount }} 个已结清账户</strong>
        <span>点击账户查看详情</span>
      </div>

      <p v-if="debtGroups.length === 0" class="debt-history-empty">
        暂无已结清债务账户
      </p>

      <section v-else class="debt-history-list" aria-label="已结清债务账户">
        <article
          v-for="group in debtGroups"
          :key="group.accountId"
          class="debt-history-card"
          role="button"
          tabindex="0"
          @click="openDebtDetail(group.accountId)"
          @keydown="handleGroupKeydown($event, group.accountId)"
        >
          <div class="debt-history-card-head">
            <div class="debt-history-person">
              <span class="debt-history-avatar" :class="group.avatarClass">{{ group.avatarText }}</span>
              <strong>{{ group.name }}</strong>
            </div>
            <span class="debt-history-settled-badge">已结清</span>
          </div>
        </article>
      </section>
    </template>
  </section>
</template>

<style scoped lang="scss" src="./style.scss"></style>
