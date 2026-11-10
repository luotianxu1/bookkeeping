import { ref, type Ref } from 'vue'
import {
  getAccountSummary,
  getAccountTypes,
  type Account,
  type AccountType,
} from '@/business/api/modules/finance'
import { useAsyncSection } from '@/business/composables/useAsyncSection'

export function useAccountSummary(userIds: Readonly<Ref<number[]>>) {
  const accounts = ref<Account[]>([])
  const accountTypes = ref<AccountType[]>([])
  const overviewTotalAssets = ref<number | null>(null)
  const section = useAsyncSection(
    async (signal) => {
      const ids = [...userIds.value]
      if (!ids.length) throw new Error('请先登录后查看账户')
      return Promise.all([
        Promise.all(ids.map((id) => getAccountSummary(id, { signal }))),
        getAccountTypes({ status: 'active' }, { signal }),
      ])
    },
    ([summaries, types]) => {
      accounts.value = summaries.flatMap((summary) => summary.accounts)
      accountTypes.value = types
      overviewTotalAssets.value = summaries.reduce(
        (total, summary) => total + Number(summary.totalAssets ?? 0),
        0,
      )
    },
    '账户列表加载失败',
  )
  section.loading.value = true

  function loadAccounts() {
    section.reset()
    accounts.value = []
    overviewTotalAssets.value = null
    return section.run()
  }

  return {
    accounts,
    accountTypes,
    overviewTotalAssets,
    loadAccounts,
    isLoadingAccounts: section.loading,
    accountListError: section.error,
  }
}
