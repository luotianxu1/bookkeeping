// 账户分组构建：从账户列表与账户类型推导分组卡片数据（纯函数）。
import { formatAmount } from '@/business/utils/format'
import type { Account, AccountType } from '@/business/api/modules/finance'
import type { AccountGroup } from '@/business/types/account'

const DEBT_ACCOUNT_CODES = new Set(['debt'])
const LIABILITY_ACCOUNT_CODES = new Set(['liability'])
const CONTACT_LINKED_ACCOUNT_CODES = new Set(['debt', 'human_relation'])

export type BuildAccountGroupsOptions = {
  isSelfView: boolean
  isReadOnly: boolean
  viewerNameByUserId: Map<number, string>
}

export function buildAccountGroups(
  accounts: Account[],
  accountTypes: AccountType[],
  collapsedGroupState: Record<string, boolean>,
  options: BuildAccountGroupsOptions,
): AccountGroup[] {
  const groupedAccounts = new Map<number, Account[]>()

  accounts.forEach((account) => {
    if (account.status !== 'active') {
      return
    }
    if (isZeroAmount(getSignedBalance(account, accountTypes))) {
      return
    }

    const groupAccounts = groupedAccounts.get(account.accountTypeId) ?? []
    groupAccounts.push(account)
    groupedAccounts.set(account.accountTypeId, groupAccounts)
  })

  const typeById = new Map(accountTypes.map((type) => [type.id, type]))
  const groupTypeIds = Array.from(groupedAccounts.keys()).sort(
    (leftId, rightId) => {
      const leftType = typeById.get(leftId)
      const rightType = typeById.get(rightId)
      const leftSort = leftType?.sortOrder ?? 0
      const rightSort = rightType?.sortOrder ?? 0
      return leftSort - rightSort || leftId - rightId
    },
  )

  return groupTypeIds.map((accountTypeId) => {
    const groupAccounts = groupedAccounts.get(accountTypeId) ?? []
    const sortedGroupAccounts = [...groupAccounts].sort((left, right) => {
      const amountDifference =
        getSignedBalance(right, accountTypes) -
        getSignedBalance(left, accountTypes)
      if (amountDifference !== 0) {
        return amountDifference
      }

      return left.name.localeCompare(right.name, 'zh-CN') || left.id - right.id
    })
    const firstAccount = sortedGroupAccounts[0]
    const accountType = typeById.get(accountTypeId)
    const groupCode = accountType?.code ?? firstAccount?.accountTypeCode
    const groupStorageKey = buildGroupStorageKey(
      accountTypeId,
      groupCode,
      accountType?.name ?? firstAccount?.accountTypeName,
    )
    const title = DEBT_ACCOUNT_CODES.has(groupCode ?? '')
      ? '债务账户'
      : LIABILITY_ACCOUNT_CODES.has(groupCode ?? '')
        ? '负债账户'
        : groupCode === 'human_relation'
          ? '人情账户'
          : accountType?.name
            ? `${accountType.name}账户`
            : firstAccount?.accountTypeName
              ? `${firstAccount.accountTypeName}账户`
              : '其他账户'

    return {
      accountTypeId,
      storageKey: groupStorageKey,
      title,
      amount: formatAmount(
        groupAccounts.reduce(
          (total, account) => total + getSignedBalance(account, accountTypes),
          0,
        ),
      ),
      path:
        options.isSelfView && groupCode === 'cash'
          ? '/finance/accounts/cash'
          : options.isSelfView && DEBT_ACCOUNT_CODES.has(groupCode ?? '')
            ? '/finance/accounts/debt'
            : options.isSelfView && LIABILITY_ACCOUNT_CODES.has(groupCode ?? '')
              ? '/finance/accounts/liability'
              : options.isSelfView && groupCode === 'human_relation'
                ? '/finance/accounts/human-relation'
                : options.isSelfView && groupCode === 'gold'
                  ? '/finance/accounts/gold'
                  : options.isSelfView && groupCode === 'term_deposit'
                    ? '/finance/term-deposits'
                    : options.isSelfView && groupCode === 'investment'
                      ? '/finance/accounts/investment'
                      : undefined,
      collapsed: collapsedGroupState[groupStorageKey] ?? false,
      items: sortedGroupAccounts.map((account) => ({
        id: account.id,
        icon: account.icon?.trim() ?? '',
        accountTypeCode:
          account.accountTypeCode ?? accountType?.code ?? 'other',
        color: account.color?.trim() || undefined,
        name: options.isReadOnly
          ? `${account.name} · ${options.viewerNameByUserId.get(account.userId) ?? `成员${account.userId}`}`
          : account.name,
        subtitle: account.remark ?? account.accountTypeName ?? '',
        amount: formatAmount(getSignedBalance(account, accountTypes)),
        path:
          options.isSelfView && account.accountTypeCode === 'cash'
            ? `/finance/accounts/cash/${account.id}`
            : options.isSelfView &&
                DEBT_ACCOUNT_CODES.has(account.accountTypeCode ?? '')
              ? `/finance/accounts/debt/${account.id}`
              : options.isSelfView &&
                  LIABILITY_ACCOUNT_CODES.has(account.accountTypeCode ?? '')
                ? `/finance/accounts/liability/${account.id}`
                : options.isSelfView &&
                    account.accountTypeCode === 'human_relation'
                  ? `/finance/accounts/human-relation/${account.id}`
                  : options.isSelfView && account.accountTypeCode === 'gold'
                    ? `/finance/accounts/gold/position?accountId=${account.id}`
                    : options.isSelfView &&
                        account.accountTypeCode === 'term_deposit'
                      ? `/finance/term-deposits?accountId=${account.id}`
                      : options.isSelfView &&
                          account.accountTypeCode === 'investment'
                        ? `/finance/accounts/investment/${account.id}`
                        : undefined,
      })),
    }
  })
}

export function getSignedBalance(
  account: Account,
  accountTypes: AccountType[],
) {
  const rawBalance = Number(account.currentBalance ?? 0)
  if (CONTACT_LINKED_ACCOUNT_CODES.has(account.accountTypeCode ?? '')) {
    return rawBalance
  }
  const accountType = accountTypes.find(
    (type) => type.id === account.accountTypeId,
  )
  return accountType?.balanceDirection === 'credit'
    ? rawBalance * -1
    : rawBalance
}

export function isZeroAmount(value: number) {
  return Math.abs(value) < 0.005
}

export function buildGroupStorageKey(
  accountTypeId?: number,
  groupCode?: string | null,
  fallbackName?: string | null,
) {
  if (typeof accountTypeId === 'number') {
    return `type-${accountTypeId}`
  }
  if (groupCode?.trim()) {
    return `code-${groupCode.trim()}`
  }
  if (fallbackName?.trim()) {
    return `name-${fallbackName.trim()}`
  }
  return 'group-unknown'
}
