// 财务业务路由模块：承载财务首页、分析、记账和账户相关页面。
import type { RouteRecordRaw } from 'vue-router'
import FinancePage from '@/pages/finance/FinancePage/index.vue'
import TransactionListPage from '@/pages/finance/TransactionListPage/index.vue'
import AnalysisPage from '@/pages/finance/AnalysisPage/index.vue'
import MoreFeaturesPage from '@/pages/finance/MoreFeaturesPage/index.vue'
import ExchangeRatePage from '@/pages/finance/ExchangeRatePage/index.vue'
import GoldPricePage from '@/pages/finance/GoldPricePage/index.vue'
import GoldAccountPage from '@/pages/finance/GoldAccountPage/index.vue'
import GoldAccountPositionPage from '@/pages/finance/GoldAccountPositionPage/index.vue'
import GoldLiquidationPage from '@/pages/finance/GoldLiquidationPage/index.vue'
import ProfitForecastPage from '@/pages/finance/ProfitForecastPage/index.vue'
import DividendIncomePage from '@/pages/finance/DividendIncomePage/index.vue'
import ExpenseEntryPage from '@/pages/finance/ExpenseEntryPage/index.vue'
import CategoryManagementPage from '@/pages/finance/CategoryManagementPage/index.vue'
import AccountManagementPage from '@/pages/finance/AccountManagementPage/index.vue'
import CashAccountPage from '@/pages/finance/CashAccountPage/index.vue'
import CashAssetDetailPage from '@/pages/finance/CashAssetDetailPage/index.vue'
import InvestmentAccountPage from '@/pages/finance/InvestmentAccountPage/index.vue'
import InvestmentDetailPage from '@/pages/finance/InvestmentDetailPage/index.vue'
import PlaceholderPage from '@/pages/placeholder/PlaceholderPage/index.vue'

export const financeRoutes: RouteRecordRaw[] = [
  {
    path: '/finance',
    name: 'finance',
    component: FinancePage,
    meta: {
      section: 'finance',
      title: '财务首页',
    },
  },
  {
    path: '/finance/transactions',
    name: 'finance-transactions',
    component: TransactionListPage,
    meta: {
      section: 'finance',
      title: '收支列表',
    },
  },
  {
    path: '/finance/analysis',
    name: 'finance-analysis',
    component: AnalysisPage,
    meta: {
      section: 'finance',
      title: '收支分析',
    },
  },
  {
    path: '/finance/more-features',
    name: 'finance-more-features',
    component: MoreFeaturesPage,
    meta: {
      section: 'finance',
      title: '更多功能',
    },
  },
  {
    path: '/finance/trend',
    name: 'finance-trend',
    component: PlaceholderPage,
    meta: {
      section: 'finance',
      title: '资产趋势',
    },
  },
  {
    path: '/finance/gold-price',
    name: 'finance-gold-price',
    component: GoldPricePage,
    meta: {
      section: 'finance',
      title: '金价',
    },
  },
  {
    path: '/finance/profit-forecast',
    name: 'finance-profit-forecast',
    component: ProfitForecastPage,
    meta: {
      section: 'finance',
      title: '收益预测',
    },
  },
  {
    path: '/finance/dividend-income',
    name: 'finance-dividend-income',
    component: DividendIncomePage,
    meta: {
      section: 'finance',
      title: '攒股收息',
    },
  },
  {
    path: '/finance/exchange-rate',
    name: 'finance-exchange-rate',
    component: ExchangeRatePage,
    meta: {
      section: 'finance',
      title: '汇率换算',
    },
  },
  {
    path: '/finance/entry/expense',
    name: 'finance-entry-expense',
    component: ExpenseEntryPage,
    meta: {
      section: 'finance',
      title: '记一笔-支出',
    },
  },
  {
    path: '/finance/categories',
    name: 'finance-categories',
    component: CategoryManagementPage,
    meta: {
      section: 'finance',
      title: '分类管理',
    },
  },
  {
    path: '/finance/accounts',
    name: 'finance-accounts',
    component: AccountManagementPage,
    meta: {
      section: 'finance',
      title: '账户管理',
    },
  },
  {
    path: '/finance/accounts/cash',
    name: 'finance-accounts-cash',
    component: CashAccountPage,
    meta: {
      section: 'finance',
      title: '现金账户',
    },
  },
  {
    path: '/finance/accounts/cash/:accountId',
    name: 'finance-accounts-cash-detail',
    component: CashAssetDetailPage,
    meta: {
      section: 'finance',
      title: '现金资产详情',
    },
  },
  {
    path: '/finance/accounts/gold',
    name: 'finance-accounts-gold',
    component: GoldAccountPage,
    meta: {
      section: 'finance',
      title: '黄金账户',
    },
  },
  {
    path: '/finance/accounts/gold/position',
    name: 'finance-accounts-gold-position',
    component: GoldAccountPositionPage,
    meta: {
      section: 'finance',
      title: '黄金账户持仓',
    },
  },
  {
    path: '/finance/accounts/gold/liquidation',
    name: 'finance-accounts-gold-liquidation',
    component: GoldLiquidationPage,
    meta: {
      section: 'finance',
      title: '清仓记录',
    },
  },
  {
    path: '/finance/accounts/investment',
    name: 'finance-accounts-investment',
    component: InvestmentAccountPage,
    meta: {
      section: 'finance',
      title: '投资账户',
    },
  },
  {
    path: '/finance/accounts/investment/detail',
    name: 'finance-accounts-investment-detail',
    component: InvestmentDetailPage,
    meta: {
      section: 'finance',
      title: '投资详情',
    },
  },
]
