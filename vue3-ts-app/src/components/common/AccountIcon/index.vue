<script setup lang="ts">
import { computed } from 'vue'
import alipayIcon from '@/assets/account-icons/alipay.png'
import bankAbcIcon from '@/assets/account-icons/bank-abc.png'
import bankBocIcon from '@/assets/account-icons/bank-boc.png'
import bankBocomIcon from '@/assets/account-icons/bank-bocom.png'
import bankCardIcon from '@/assets/account-icons/bank-card.png'
import bankCcbIcon from '@/assets/account-icons/bank-ccb.png'
import bankCebIcon from '@/assets/account-icons/bank-ceb.png'
import bankCgbIcon from '@/assets/account-icons/bank-cgb.png'
import bankCibIcon from '@/assets/account-icons/bank-cib.png'
import bankCiticIcon from '@/assets/account-icons/bank-citic.png'
import bankCmbIcon from '@/assets/account-icons/bank-cmb.png'
import bankCmbcIcon from '@/assets/account-icons/bank-cmbc.png'
import bankIcbcIcon from '@/assets/account-icons/bank-icbc.png'
import bankMybankIcon from '@/assets/account-icons/bank-mybank.png'
import bankPinganIcon from '@/assets/account-icons/bank-pingan.png'
import bankPsbcIcon from '@/assets/account-icons/bank-psbc.png'
import bankSpdbIcon from '@/assets/account-icons/bank-spdb.png'
import creditCardIcon from '@/assets/account-icons/credit-card.png'
import debtIcon from '@/assets/account-icons/debt.png'
import fundIcon from '@/assets/account-icons/fund.png'
import goldIcon from '@/assets/account-icons/gold.png'
import humanRelationIcon from '@/assets/account-icons/human-relation.png'
import liabilityIcon from '@/assets/account-icons/liability.png'
import otherAssetIcon from '@/assets/account-icons/other-asset.png'
import otherLiabilityIcon from '@/assets/account-icons/other-liability.png'
import otherIcon from '@/assets/account-icons/other.png'
import reserveFundIcon from '@/assets/account-icons/reserve-fund.png'
import stockIcon from '@/assets/account-icons/stock.png'
import walletIcon from '@/assets/account-icons/wallet.png'

type AccountIconKey =
  | 'wallet'
  | 'bank-card'
  | 'bank-icbc'
  | 'bank-ccb'
  | 'bank-abc'
  | 'bank-boc'
  | 'bank-cmb'
  | 'bank-bocom'
  | 'bank-psbc'
  | 'bank-citic'
  | 'bank-cmbc'
  | 'bank-cib'
  | 'bank-spdb'
  | 'bank-pingan'
  | 'bank-ceb'
  | 'bank-cgb'
  | 'bank-mybank'
  | 'alipay'
  | 'reserve-fund'
  | 'fund'
  | 'gold'
  | 'stock'
  | 'credit-card'
  | 'debt'
  | 'liability'
  | 'human-relation'
  | 'other-asset'
  | 'other-liability'
  | 'other'

const props = withDefaults(defineProps<{
  icon?: string | null
  accountTypeCode?: string | null
  color?: string | null
  name?: string | null
  size?: number
}>(), {
  icon: '',
  accountTypeCode: '',
  color: '',
  name: '',
  size: 36,
})

const iconSources: Record<AccountIconKey, string> = {
  wallet: walletIcon,
  'bank-card': bankCardIcon,
  'bank-icbc': bankIcbcIcon,
  'bank-ccb': bankCcbIcon,
  'bank-abc': bankAbcIcon,
  'bank-boc': bankBocIcon,
  'bank-cmb': bankCmbIcon,
  'bank-bocom': bankBocomIcon,
  'bank-psbc': bankPsbcIcon,
  'bank-citic': bankCiticIcon,
  'bank-cmbc': bankCmbcIcon,
  'bank-cib': bankCibIcon,
  'bank-spdb': bankSpdbIcon,
  'bank-pingan': bankPinganIcon,
  'bank-ceb': bankCebIcon,
  'bank-cgb': bankCgbIcon,
  'bank-mybank': bankMybankIcon,
  alipay: alipayIcon,
  'reserve-fund': reserveFundIcon,
  fund: fundIcon,
  gold: goldIcon,
  stock: stockIcon,
  'credit-card': creditCardIcon,
  debt: debtIcon,
  liability: liabilityIcon,
  'human-relation': humanRelationIcon,
  'other-asset': otherAssetIcon,
  'other-liability': otherLiabilityIcon,
  other: otherIcon,
}

const iconAliases: Record<string, AccountIconKey> = {
  cash: 'wallet',
  investment: 'fund',
  icbc: 'bank-icbc',
  ccb: 'bank-ccb',
  abc: 'bank-abc',
  boc: 'bank-boc',
  cmb: 'bank-cmb',
  bocom: 'bank-bocom',
  psbc: 'bank-psbc',
  citic: 'bank-citic',
  cmbc: 'bank-cmbc',
  cib: 'bank-cib',
  spdb: 'bank-spdb',
  pingan: 'bank-pingan',
  ceb: 'bank-ceb',
  cgb: 'bank-cgb',
  mybank: 'bank-mybank',
}

const colorAliases: Record<string, string> = {
  blue: '#3B82F6',
  purple: '#8B5CF6',
  orange: '#F97316',
  sky: '#38BDF8',
  green: '#10B981',
}

function normalizeIcon(value?: string | null) {
  return value?.trim().toLowerCase().replace(/_/g, '-') ?? ''
}

function resolveIconKey(value?: string | null): AccountIconKey | null {
  const normalized = normalizeIcon(value)
  if (!normalized) return null
  if (Object.prototype.hasOwnProperty.call(iconSources, normalized)) {
    return normalized as AccountIconKey
  }
  return iconAliases[normalized] ?? null
}

const iconKey = computed<AccountIconKey>(() =>
  resolveIconKey(props.icon) ?? resolveIconKey(props.accountTypeCode) ?? 'other',
)
const initialAccountType = computed<'debt' | 'human-relation' | null>(() => {
  const accountTypeCode = normalizeIcon(props.accountTypeCode)
  const icon = normalizeIcon(props.icon)
  if (accountTypeCode === 'debt' && (!icon || icon === 'debt')) return 'debt'
  if (accountTypeCode === 'human-relation' && (!icon || icon === 'human-relation')) {
    return 'human-relation'
  }
  return null
})
const showNameInitial = computed(() => initialAccountType.value !== null)
const nameInitial = computed(() => {
  const fallback = initialAccountType.value === 'human-relation' ? '人' : '债'
  return Array.from(props.name?.trim() || fallback)[0] ?? fallback
})
const iconUrl = computed(() => iconSources[iconKey.value])
const backgroundColor = computed(() => {
  const value = props.color?.trim() ?? ''
  const resolvedColor = colorAliases[value.toLowerCase()] ?? value
  if (resolvedColor) return `color-mix(in srgb, ${resolvedColor} 18%, transparent)`
  if (initialAccountType.value === 'human-relation') return 'var(--color-bg-purple-soft)'
  return showNameInitial.value ? 'var(--color-warning-soft)' : 'transparent'
})
</script>

<template>
  <span
    class="account-icon-shell"
    :style="{ width: `${size}px`, height: `${size}px`, backgroundColor }"
  >
    <span
      v-if="showNameInitial"
      :class="['account-icon-initial', { 'is-human-relation': initialAccountType === 'human-relation' }]"
      aria-hidden="true"
    >
      {{ nameInitial }}
    </span>
    <img
      v-else
      class="account-icon-image"
      :src="iconUrl"
      :alt="name ? `${name}账户图标` : ''"
      :aria-hidden="!name"
    />
  </span>
</template>

<style scoped lang="scss">
.account-icon-shell {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  overflow: hidden;
  border-radius: 28%;
}

.account-icon-image {
  width: 88%;
  height: 88%;
  display: block;
  object-fit: contain;
}

.account-icon-initial {
  color: var(--color-orange);
  font-size: var(--font-size-16);
  font-weight: 700;
  line-height: 1;
}

.account-icon-initial.is-human-relation {
  color: var(--color-purple);
}
</style>
