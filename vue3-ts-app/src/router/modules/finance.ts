// 财务业务路由模块：承载财务首页、分析、记账和账户相关页面。
import type { RouteRecordRaw } from 'vue-router'

export const financeRoutes: RouteRecordRaw[] = [
  {
    path: '/finance',
    name: 'finance',
    component: () => import('@/pages/finance/FinancePage/index.vue'),
    meta: {
      section: 'finance',
      title: '财务首页',
    },
  },
  {
    path: '/finance/transactions',
    name: 'finance-transactions',
    component: () => import('@/pages/finance/TransactionListPage/index.vue'),
    meta: {
      section: 'finance',
      title: '收支列表',
    },
  },
  {
    path: '/finance/analysis',
    name: 'finance-analysis',
    component: () => import('@/pages/finance/AnalysisPage/index.vue'),
    meta: {
      section: 'finance',
      title: '收支分析',
    },
  },
  {
    path: '/finance/more-features',
    name: 'finance-more-features',
    component: () => import('@/pages/finance/MoreFeaturesPage/index.vue'),
    meta: {
      section: 'finance',
      title: '更多功能',
    },
  },
  {
    path: '/finance/trend',
    name: 'finance-trend',
    component: () => import('@/pages/finance/InvestmentTrendPage/index.vue'),
    meta: {
      section: 'finance',
      title: '资产趋势',
    },
  },
  {
    path: '/finance/gold-price',
    name: 'finance-gold-price',
    component: () => import('@/pages/finance/GoldPricePage/index.vue'),
    meta: {
      section: 'finance',
      title: '金价',
    },
  },
  {
    path: '/finance/market-news',
    name: 'finance-market-news',
    component: () => import('@/pages/finance/MarketNewsPage/index.vue'),
    meta: {
      section: 'finance',
      title: '市场快讯',
    },
  },
  {
    path: '/finance/stock-screener',
    name: 'finance-stock-screener',
    component: () => import('@/pages/finance/StockScreenerPage/index.vue'),
    meta: {
      section: 'finance',
      title: 'A股选股',
    },
  },
  {
    path: '/finance/us-market',
    name: 'finance-us-market',
    component: () => import('@/pages/finance/UsMarketPage/index.vue'),
    meta: {
      section: 'finance',
      title: '美股行情',
    },
  },
  {
    path: '/finance/global-market',
    name: 'finance-global-market',
    component: () => import('@/pages/finance/market/index.vue'),
    meta: {
      section: 'finance',
      title: '行情中心',
    },
    children: [
      { path: '', redirect: '/finance/global-market/global' },
      {
        path: 'global',
        name: 'finance-market-global',
        component: () => import('@/pages/finance/market/pages/GlobalPage.vue'),
        meta: { section: 'finance', title: '行情中心 · 全球' },
      },
      {
        path: 'a',
        name: 'finance-market-a',
        component: () => import('@/pages/finance/market/pages/APage.vue'),
        meta: { section: 'finance', title: '行情中心 · A股' },
      },
      {
        path: 'hk',
        name: 'finance-market-hk',
        component: () => import('@/pages/finance/market/pages/GroupPage.vue'),
        props: { group: 'hk' },
        meta: { section: 'finance', title: '行情中心 · 港股' },
      },
      {
        path: 'us',
        name: 'finance-market-us',
        component: () => import('@/pages/finance/market/pages/GroupPage.vue'),
        props: { group: 'us' },
        meta: { section: 'finance', title: '行情中心 · 美股' },
      },
      {
        path: 'etf',
        name: 'finance-market-etf',
        component: () => import('@/pages/finance/market/pages/GroupPage.vue'),
        props: { group: 'etf' },
        meta: { section: 'finance', title: '行情中心 · ETF' },
      },
      {
        path: 'gold',
        name: 'finance-market-gold',
        component: () => import('@/pages/finance/market/pages/GroupPage.vue'),
        props: { group: 'gold' },
        meta: { section: 'finance', title: '行情中心 · 黄金' },
      },
      {
        path: 'sectors',
        name: 'finance-market-sectors',
        component: () => import('@/pages/finance/market/pages/SectorsPage.vue'),
        meta: { section: 'finance', title: '行情中心 · 板块' },
      },
    ],
  },
  {
    path: '/finance/global-market/detail/:kind/:code',
    name: 'finance-market-detail',
    component: () => import('@/pages/finance/market/pages/DetailPage.vue'),
    meta: {
      section: 'finance',
      title: '行情详情',
    },
  },
  {
    path: '/finance/profit-forecast',
    name: 'finance-profit-forecast',
    component: () => import('@/pages/finance/ProfitForecastPage/index.vue'),
    meta: {
      section: 'finance',
      title: '收益预测',
    },
  },
  {
    path: '/finance/fund-profit',
    name: 'finance-fund-profit',
    component: () => import('@/pages/finance/FundProfitPage/index.vue'),
    meta: {
      section: 'finance',
      title: '基金收益',
    },
  },
  {
    path: '/finance/break-even-forecast',
    name: 'finance-break-even-forecast',
    component: () => import('@/pages/finance/BreakEvenForecastPage/index.vue'),
    meta: {
      section: 'finance',
      title: '回本预测',
    },
  },
  {
    path: '/finance/dividend-income',
    name: 'finance-dividend-income',
    component: () => import('@/pages/finance/DividendIncomePage/index.vue'),
    meta: {
      section: 'finance',
      title: '攒股收息',
    },
  },
  {
    path: '/finance/dividend-forecast',
    name: 'finance-dividend-forecast',
    component: () => import('@/pages/finance/DividendForecastPage/index.vue'),
    meta: {
      section: 'finance',
      title: '收息预测',
    },
  },
  {
    path: '/finance/free-estimate',
    name: 'finance-free-estimate',
    component: () => import('@/pages/finance/FreeEstimatePage/index.vue'),
    meta: {
      section: 'finance',
      title: 'Free测算',
    },
  },
  {
    path: '/finance/budgets',
    name: 'finance-budgets',
    component: () => import('@/pages/finance/BudgetManagementPage/index.vue'),
    meta: {
      section: 'finance',
      title: '预算管理',
    },
  },
  {
    path: '/finance/renewals',
    name: 'finance-renewals',
    component: () => import('@/pages/finance/RenewalManagementPage/index.vue'),
    meta: {
      section: 'finance',
      title: '固定支出',
    },
  },
  {
    path: '/finance/salary',
    name: 'finance-salary',
    component: () => import('@/pages/finance/SalaryHomePage/index.vue'),
    meta: {
      section: 'finance',
      title: '工资管理',
    },
  },
  {
    path: '/finance/salary/settings',
    name: 'finance-salary-settings',
    component: () => import('@/pages/finance/SalarySettingsPage/index.vue'),
    meta: {
      section: 'finance',
      title: '工资设置',
    },
  },
  {
    path: '/finance/salary/records',
    name: 'finance-salary-records',
    component: () => import('@/pages/finance/SalaryRecordPage/index.vue'),
    meta: {
      section: 'finance',
      title: '工资明细',
    },
  },
  {
    path: '/finance/salary/cash',
    name: 'finance-salary-cash',
    component: () => import('@/pages/finance/SalaryCashPage/index.vue'),
    meta: {
      section: 'finance',
      title: '现金账户',
    },
  },
  {
    path: '/finance/salary/accounts/:accountType',
    name: 'finance-salary-account',
    component: () => import('@/pages/finance/SalaryAccountPage/index.vue'),
    meta: {
      section: 'finance',
      title: '工资账户',
    },
  },
  {
    path: '/finance/salary/tax',
    name: 'finance-salary-tax',
    component: () => import('@/pages/finance/SalaryTaxPage/index.vue'),
    meta: {
      section: 'finance',
      title: '工资税务',
    },
  },
  {
    path: '/finance/exchange-rate',
    name: 'finance-exchange-rate',
    component: () => import('@/pages/finance/ExchangeRatePage/index.vue'),
    meta: {
      section: 'finance',
      title: '汇率换算',
    },
  },
  {
    path: '/finance/entry/expense',
    name: 'finance-entry-expense',
    component: () => import('@/pages/finance/ExpenseEntryPage/index.vue'),
    meta: {
      section: 'finance',
      title: '记一笔-支出',
    },
  },
  {
    path: '/finance/categories',
    name: 'finance-categories',
    component: () => import('@/pages/finance/CategoryManagementPage/index.vue'),
    meta: {
      section: 'finance',
      title: '分类管理',
    },
  },
  {
    path: '/finance/accounts',
    name: 'finance-accounts',
    component: () => import('@/pages/finance/AccountManagementPage/index.vue'),
    meta: {
      section: 'finance',
      title: '账户管理',
    },
  },
  {
    path: '/finance/accounts/cash',
    name: 'finance-accounts-cash',
    component: () => import('@/pages/finance/CashAccountPage/index.vue'),
    meta: {
      section: 'finance',
      title: '现金账户',
    },
  },
  {
    path: '/finance/accounts/cash/:accountId',
    name: 'finance-accounts-cash-detail',
    component: () => import('@/pages/finance/CashAssetDetailPage/index.vue'),
    meta: {
      section: 'finance',
      title: '现金资产详情',
    },
  },
  {
    path: '/finance/accounts/debt',
    name: 'finance-accounts-debt',
    component: () => import('@/pages/finance/DebtAccountPage/index.vue'),
    meta: {
      section: 'finance',
      title: '债务账户',
    },
  },
  {
    path: '/finance/accounts/debt/history',
    name: 'finance-accounts-debt-history',
    component: () => import('@/pages/finance/DebtAccountHistoryPage/index.vue'),
    meta: {
      section: 'finance',
      title: '已结清明细',
    },
  },
  {
    path: '/finance/accounts/debt/:accountId/record/:recordId',
    name: 'finance-accounts-debt-record-detail',
    component: () => import('@/pages/finance/DebtRecordDetailPage/index.vue'),
    meta: {
      section: 'finance',
      title: '债务项详情',
    },
  },
  {
    path: '/finance/accounts/debt/:accountId',
    name: 'finance-accounts-debt-detail',
    component: () => import('@/pages/finance/DebtAccountDetailPage/index.vue'),
    meta: {
      section: 'finance',
      title: '债务详情',
    },
  },
  {
    path: '/finance/accounts/liability',
    name: 'finance-accounts-liability',
    component: () => import('@/pages/finance/LiabilityAccountPage/index.vue'),
    meta: {
      section: 'finance',
      title: '负债账户',
    },
  },
  {
    path: '/finance/accounts/liability/:accountId',
    name: 'finance-accounts-liability-detail',
    component: () => import('@/pages/finance/LiabilityAccountDetailPage/index.vue'),
    meta: {
      section: 'finance',
      title: '负债详情',
    },
  },
  {
    path: '/finance/accounts/human-relation',
    name: 'finance-accounts-human-relation',
    component: () => import('@/pages/finance/HumanRelationAccountPage/index.vue'),
    meta: {
      section: 'finance',
      title: '人情账户',
    },
  },
  {
    path: '/finance/accounts/human-relation/:accountId',
    name: 'finance-accounts-human-relation-detail',
    component: () => import('@/pages/finance/HumanRelationAccountDetailPage/index.vue'),
    meta: {
      section: 'finance',
      title: '人情详情',
    },
  },
  {
    path: '/finance/accounts/gold',
    name: 'finance-accounts-gold',
    component: () => import('@/pages/finance/GoldAccountPage/index.vue'),
    meta: {
      section: 'finance',
      title: '黄金账户',
    },
  },
  {
    path: '/finance/accounts/gold/position',
    name: 'finance-accounts-gold-position',
    component: () => import('@/pages/finance/GoldAccountPositionPage/index.vue'),
    meta: {
      section: 'finance',
      title: '黄金账户持仓',
    },
  },
  {
    path: '/finance/accounts/gold/liquidation',
    name: 'finance-accounts-gold-liquidation',
    component: () => import('@/pages/finance/GoldLiquidationPage/index.vue'),
    meta: {
      section: 'finance',
      title: '清仓记录',
    },
  },
  {
    path: '/finance/accounts/investment',
    name: 'finance-accounts-investment',
    component: () => import('@/pages/finance/InvestmentAccountListPage/index.vue'),
    meta: {
      section: 'finance',
      title: '投资账户',
    },
  },
  {
    path: '/finance/accounts/investment/:accountId/liquidations',
    name: 'finance-accounts-investment-liquidations',
    component: () => import('@/pages/finance/InvestmentLiquidationPage/index.vue'),
    meta: {
      section: 'finance',
      title: '清仓明细',
    },
  },
  {
    path: '/finance/accounts/investment/:accountId',
    name: 'finance-accounts-investment-account-detail',
    component: () => import('@/pages/finance/InvestmentAccountPage/index.vue'),
    meta: {
      section: 'finance',
      title: '投资详情',
    },
  },
  {
    path: '/finance/accounts/investment/detail',
    name: 'finance-accounts-investment-detail',
    component: () => import('@/pages/finance/InvestmentDetailPage/index.vue'),
    meta: {
      section: 'finance',
      title: '投资资产详情',
    },
  },
]
