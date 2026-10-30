<script setup lang="ts">
import accountsIcon from '@/assets/system-icons/accounts.png'
import analysisIcon from '@/assets/system-icons/analysis.png'
import breakEvenIcon from '@/assets/system-icons/break-even.png'
import budgetIcon from '@/assets/system-icons/budget.png'
import categoryManagementIcon from '@/assets/system-icons/category-management.png'
import dividendForecastIcon from '@/assets/system-icons/dividend-forecast.png'
import dividendIncomeIcon from '@/assets/system-icons/dividend-income.png'
import exchangeRateIcon from '@/assets/system-icons/exchange-rate.png'
import freeEstimateIcon from '@/assets/system-icons/free-estimate.png'
import fundProfitIcon from '@/assets/system-icons/fund-profit.png'
import goldPriceIcon from '@/assets/system-icons/gold-price.png'
import marketNewsIcon from '@/assets/system-icons/market-news.png'
import profitForecastIcon from '@/assets/system-icons/profit-forecast.png'
import renewalsIcon from '@/assets/system-icons/renewals.png'
import salaryIcon from '@/assets/system-icons/salary.png'
import stockScreenerIcon from '@/assets/system-icons/stock-screener.png'
import transactionsIcon from '@/assets/system-icons/transactions.png'
import trendIcon from '@/assets/system-icons/trend.png'
import usMarketIcon from '@/assets/system-icons/us-market.png'

export type FeatureIconKey =
  | 'category-management'
  | 'transactions'
  | 'analysis'
  | 'trend'
  | 'accounts'
  | 'salary'
  | 'fund-profit'
  | 'dividend-income'
  | 'budget'
  | 'renewals'
  | 'stock-screener'
  | 'gold-price'
  | 'us-market'
  | 'market-news'
  | 'exchange-rate'
  | 'profit-forecast'
  | 'break-even'
  | 'dividend-forecast'
  | 'free-estimate'

const props = withDefaults(defineProps<{
  icon: FeatureIconKey
  size?: number
  label?: string
}>(), {
  size: 32,
  label: '',
})

const iconSources: Record<FeatureIconKey, string> = {
  'category-management': categoryManagementIcon,
  transactions: transactionsIcon,
  analysis: analysisIcon,
  trend: trendIcon,
  accounts: accountsIcon,
  salary: salaryIcon,
  'fund-profit': fundProfitIcon,
  'dividend-income': dividendIncomeIcon,
  budget: budgetIcon,
  renewals: renewalsIcon,
  'stock-screener': stockScreenerIcon,
  'gold-price': goldPriceIcon,
  'us-market': usMarketIcon,
  'market-news': marketNewsIcon,
  'exchange-rate': exchangeRateIcon,
  'profit-forecast': profitForecastIcon,
  'break-even': breakEvenIcon,
  'dividend-forecast': dividendForecastIcon,
  'free-estimate': freeEstimateIcon,
}
</script>

<template>
  <img
    class="feature-icon-image"
    :src="iconSources[props.icon]"
    :width="size"
    :height="size"
    :alt="label"
    :aria-hidden="!label"
  />
</template>

<style scoped lang="scss">
.feature-icon-image {
  display: block;
  flex: 0 0 auto;
  object-fit: contain;
}
</style>
