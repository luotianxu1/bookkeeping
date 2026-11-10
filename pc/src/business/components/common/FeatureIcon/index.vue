<script setup lang="ts">
import accountsIcon from '@/business/assets/system-icons/accounts.png'
import analysisIcon from '@/business/assets/system-icons/analysis.png'
import breakEvenIcon from '@/business/assets/system-icons/break-even.png'
import budgetIcon from '@/business/assets/system-icons/budget.png'
import categoryManagementIcon from '@/business/assets/system-icons/category-management.png'
import dividendForecastIcon from '@/business/assets/system-icons/dividend-forecast.png'
import dividendIncomeIcon from '@/business/assets/system-icons/dividend-income.png'
import dividendStrategyIcon from '@/business/assets/system-icons/dividend-strategy.png'
import exchangeRateIcon from '@/business/assets/system-icons/exchange-rate.png'
import expenseRecoveriesIcon from '@/business/assets/system-icons/expense-recoveries.png'
import freeEstimateIcon from '@/business/assets/system-icons/free-estimate.png'
import fundProfitIcon from '@/business/assets/system-icons/fund-profit.png'
import globalMarketIcon from '@/business/assets/system-icons/global-market.png'
import goldPriceIcon from '@/business/assets/system-icons/gold-price.png'
import marketNewsIcon from '@/business/assets/system-icons/market-news.png'
import profitForecastIcon from '@/business/assets/system-icons/profit-forecast.png'
import renewalsIcon from '@/business/assets/system-icons/renewals.png'
import salaryIcon from '@/business/assets/system-icons/salary.png'
import savingsGoalsIcon from '@/business/assets/system-icons/savings-goals.png'
import stockScreenerIcon from '@/business/assets/system-icons/stock-screener.png'
import termDepositsIcon from '@/business/assets/system-icons/term-deposits.png'
import transactionsIcon from '@/business/assets/system-icons/transactions.png'
import trendIcon from '@/business/assets/system-icons/trend.png'
import usMarketIcon from '@/business/assets/system-icons/us-market.png'

export type FeatureIconKey =
  | 'expense-recoveries'
  | 'term-deposits'
  | 'savings-goals'
  | 'global-market'
  | 'dividend-strategy'
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

const props = withDefaults(
  defineProps<{
    icon: FeatureIconKey
    size?: number
    label?: string
  }>(),
  {
    size: 32,
    label: '',
  },
)

const iconSources: Record<FeatureIconKey, string> = {
  'dividend-strategy': dividendStrategyIcon,
  'global-market': globalMarketIcon,
  'savings-goals': savingsGoalsIcon,
  'term-deposits': termDepositsIcon,
  'expense-recoveries': expenseRecoveriesIcon,
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

<style scoped>
.feature-icon-image {
  display: block;
  flex: 0 0 auto;
  object-fit: contain;
}
</style>
