// 财务业务路由模块：承载财务首页、分析、记账和账户相关页面。
import type { RouteRecordRaw } from 'vue-router'
import FinancePage from '@/pages/finance/FinancePage/index.vue'
import TransactionListPage from '@/pages/finance/TransactionListPage/index.vue'
import AnalysisPage from '@/pages/finance/AnalysisPage/index.vue'
import InvestmentTrendPage from '@/pages/finance/InvestmentTrendPage/index.vue'
import MoreFeaturesPage from '@/pages/finance/MoreFeaturesPage/index.vue'
import ExchangeRatePage from '@/pages/finance/ExchangeRatePage/index.vue'
import GoldPricePage from '@/pages/finance/GoldPricePage/index.vue'
import MarketNewsPage from '@/pages/finance/MarketNewsPage/index.vue'
import GoldAccountPage from '@/pages/finance/GoldAccountPage/index.vue'
import GoldAccountPositionPage from '@/pages/finance/GoldAccountPositionPage/index.vue'
import GoldLiquidationPage from '@/pages/finance/GoldLiquidationPage/index.vue'
import ProfitForecastPage from '@/pages/finance/ProfitForecastPage/index.vue'
import FundProfitPage from '@/pages/finance/FundProfitPage/index.vue'
import BreakEvenForecastPage from '@/pages/finance/BreakEvenForecastPage/index.vue'
import DividendIncomePage from '@/pages/finance/DividendIncomePage/index.vue'
import DividendForecastPage from '@/pages/finance/DividendForecastPage/index.vue'
import BudgetManagementPage from '@/pages/finance/BudgetManagementPage/index.vue'
import RenewalManagementPage from '@/pages/finance/RenewalManagementPage/index.vue'
import ExpenseEntryPage from '@/pages/finance/ExpenseEntryPage/index.vue'
import CategoryManagementPage from '@/pages/finance/CategoryManagementPage/index.vue'
import AccountManagementPage from '@/pages/finance/AccountManagementPage/index.vue'
import CashAccountPage from '@/pages/finance/CashAccountPage/index.vue'
import CashAssetDetailPage from '@/pages/finance/CashAssetDetailPage/index.vue'
import DebtAccountPage from '@/pages/finance/DebtAccountPage/index.vue'
import DebtAccountDetailPage from '@/pages/finance/DebtAccountDetailPage/index.vue'
import LiabilityAccountPage from '@/pages/finance/LiabilityAccountPage/index.vue'
import LiabilityAccountDetailPage from '@/pages/finance/LiabilityAccountDetailPage/index.vue'
import HumanRelationAccountPage from '@/pages/finance/HumanRelationAccountPage/index.vue'
import HumanRelationAccountDetailPage from '@/pages/finance/HumanRelationAccountDetailPage/index.vue'
import InvestmentAccountListPage from '@/pages/finance/InvestmentAccountListPage/index.vue'
import InvestmentAccountPage from '@/pages/finance/InvestmentAccountPage/index.vue'
import InvestmentDetailPage from '@/pages/finance/InvestmentDetailPage/index.vue'
import SalaryHomePage from '@/pages/finance/SalaryHomePage/index.vue'
import SalarySettingsPage from '@/pages/finance/SalarySettingsPage/index.vue'
import SalaryRecordPage from '@/pages/finance/SalaryRecordPage/index.vue'
import SalaryAccountPage from '@/pages/finance/SalaryAccountPage/index.vue'
import SalaryTaxPage from '@/pages/finance/SalaryTaxPage/index.vue'

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
    component: InvestmentTrendPage,
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
    path: '/finance/market-news',
    name: 'finance-market-news',
    component: MarketNewsPage,
    meta: {
      section: 'finance',
      title: '市场快讯',
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
    path: '/finance/fund-profit',
    name: 'finance-fund-profit',
    component: FundProfitPage,
    meta: {
      section: 'finance',
      title: '基金收益',
    },
  },
  {
    path: '/finance/break-even-forecast',
    name: 'finance-break-even-forecast',
    component: BreakEvenForecastPage,
    meta: {
      section: 'finance',
      title: '回本预测',
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
    path: '/finance/dividend-forecast',
    name: 'finance-dividend-forecast',
    component: DividendForecastPage,
    meta: {
      section: 'finance',
      title: '收息预测',
    },
  },
  {
    path: '/finance/budgets',
    name: 'finance-budgets',
    component: BudgetManagementPage,
    meta: {
      section: 'finance',
      title: '预算管理',
    },
  },
  {
    path: '/finance/renewals',
    name: 'finance-renewals',
    component: RenewalManagementPage,
    meta: {
      section: 'finance',
      title: '续费管理',
    },
  },
  {
    path: '/finance/salary',
    name: 'finance-salary',
    component: SalaryHomePage,
    meta: {
      section: 'finance',
      title: '工资管理',
    },
  },
  {
    path: '/finance/salary/settings',
    name: 'finance-salary-settings',
    component: SalarySettingsPage,
    meta: {
      section: 'finance',
      title: '工资设置',
    },
  },
  {
    path: '/finance/salary/records',
    name: 'finance-salary-records',
    component: SalaryRecordPage,
    meta: {
      section: 'finance',
      title: '工资明细',
    },
  },
  {
    path: '/finance/salary/accounts/:accountType',
    name: 'finance-salary-account',
    component: SalaryAccountPage,
    meta: {
      section: 'finance',
      title: '工资账户',
    },
  },
  {
    path: '/finance/salary/tax',
    name: 'finance-salary-tax',
    component: SalaryTaxPage,
    meta: {
      section: 'finance',
      title: '工资税务',
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
    path: '/finance/accounts/debt',
    name: 'finance-accounts-debt',
    component: DebtAccountPage,
    meta: {
      section: 'finance',
      title: '债务账户',
    },
  },
  {
    path: '/finance/accounts/debt/:accountId',
    name: 'finance-accounts-debt-detail',
    component: DebtAccountDetailPage,
    meta: {
      section: 'finance',
      title: '债务详情',
    },
  },
  {
    path: '/finance/accounts/liability',
    name: 'finance-accounts-liability',
    component: LiabilityAccountPage,
    meta: {
      section: 'finance',
      title: '负债账户',
    },
  },
  {
    path: '/finance/accounts/liability/:accountId',
    name: 'finance-accounts-liability-detail',
    component: LiabilityAccountDetailPage,
    meta: {
      section: 'finance',
      title: '负债详情',
    },
  },
  {
    path: '/finance/accounts/human-relation',
    name: 'finance-accounts-human-relation',
    component: HumanRelationAccountPage,
    meta: {
      section: 'finance',
      title: '人情账户',
    },
  },
  {
    path: '/finance/accounts/human-relation/:accountId',
    name: 'finance-accounts-human-relation-detail',
    component: HumanRelationAccountDetailPage,
    meta: {
      section: 'finance',
      title: '人情详情',
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
    component: InvestmentAccountListPage,
    meta: {
      section: 'finance',
      title: '投资账户',
    },
  },
  {
    path: '/finance/accounts/investment/:accountId',
    name: 'finance-accounts-investment-account-detail',
    component: InvestmentAccountPage,
    meta: {
      section: 'finance',
      title: '投资详情',
    },
  },
  {
    path: '/finance/accounts/investment/detail',
    name: 'finance-accounts-investment-detail',
    component: InvestmentDetailPage,
    meta: {
      section: 'finance',
      title: '投资资产详情',
    },
  },
]
