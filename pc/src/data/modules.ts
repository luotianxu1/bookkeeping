export interface MenuEntry {
  title: string
  path: string
  permission?: string
  adminOnly?: boolean
}
export interface MenuModule {
  id: string
  title: string
  icon: string
  entries: MenuEntry[]
}
const entries = (items: [string, string][]): MenuEntry[] =>
  items.map(([title, path]) => ({ title, path }))
export const modules: MenuModule[] = [
  {
    id: 'access',
    title: '权限管理',
    icon: 'shield',
    entries: [
      { title: '用户管理', path: '/users', permission: 'user.read' },
      { title: '角色管理', path: '/roles', permission: 'role.read' },
      {
        title: '权限配置',
        path: '/permissions',
        permission: 'permission.read',
      },
    ],
  },
  {
    id: 'finance',
    title: '财务管理',
    icon: 'wallet',
    entries: entries([
      ['财务总览', '/finance'],
      ['收支流水', '/finance/transactions'],
      ['记一笔', '/finance/entry/expense'],
      ['收支分析', '/finance/analysis'],
      ['资产趋势', '/finance/trend'],
      ['预算管理', '/finance/budgets'],
      ['退款与报销', '/finance/expense-recoveries'],
      ['固定支出', '/finance/renewals'],
      ['收支分类', '/finance/categories'],
      ['工资总览', '/finance/salary'],
      ['工资设置', '/finance/salary/settings'],
      ['工资明细', '/finance/salary/records'],
      ['社保账户', '/finance/salary/accounts/social-security'],
      ['公积金账户', '/finance/salary/accounts/housing-fund'],
      ['医保账户', '/finance/salary/accounts/medical'],
      ['个税计算', '/finance/salary/tax'],
      ['汇率换算', '/finance/exchange-rate'],
    ]),
  },
  {
    id: 'assets',
    title: '资产管理',
    icon: 'landmark',
    entries: entries([
      ['账户总览', '/finance/accounts'],
      ['现金账户', '/finance/accounts/cash'],
      ['债权账户', '/finance/accounts/debt'],
      ['负债账户', '/finance/accounts/liability'],
      ['人情往来', '/finance/accounts/human-relation'],
      ['黄金账户', '/finance/accounts/gold'],
      ['黄金持仓', '/finance/accounts/gold/position'],
      ['黄金清仓记录', '/finance/accounts/gold/liquidation'],
      ['投资账户', '/finance/accounts/investment'],
      ['定期存款', '/finance/term-deposits'],
      ['储蓄目标', '/finance/savings-goals'],
    ]),
  },
  {
    id: 'market',
    title: '投资行情',
    icon: 'chart',
    entries: entries([
      ['全球行情', '/finance/global-market/global'],
      ['A 股行情', '/finance/global-market/a'],
      ['港股行情', '/finance/global-market/hk'],
      ['美股行情', '/finance/global-market/us'],
      ['基金行情', '/finance/global-market/funds'],
      ['ETF 行情', '/finance/global-market/etf'],
      ['黄金行情', '/finance/global-market/gold'],
      ['行业板块', '/finance/global-market/sectors'],
      ['美股盘前', '/finance/us-market'],
      ['金价', '/finance/gold-price'],
      ['财经快讯', '/finance/market-news'],
      ['A 股选股', '/finance/stock-screener'],
      ['红利策略', '/finance/dividend-strategy'],
      ['攒股收息', '/finance/dividend-income'],
      ['收息预测', '/finance/dividend-forecast'],
      ['收益预测', '/finance/profit-forecast'],
      ['基金收益', '/finance/fund-profit'],
      ['回本预测', '/finance/break-even-forecast'],
      ['财务自由测算', '/finance/free-estimate'],
    ]),
  },
  {
    id: 'tools',
    title: '生活工具',
    icon: 'calendar',
    entries: entries([
      ['日历', '/tools/calendar'],
      ['纪念日', '/tools/anniversaries'],
      ['待办事项', '/tools/todo-items'],
      ['联系人', '/tools/contacts'],
      ['摄影订单', '/tools/photography-orders'],
      ['订单总览', '/tools/photography-orders/overview'],
      ['旅行计划', '/tools/travel-plans'],
      ['健康档案', '/tools/health'],
      ['体重记录', '/tools/health/records'],
    ]),
  },
  {
    id: 'food',
    title: '家庭餐饮',
    icon: 'food',
    entries: entries([
      ['点菜', '/food'],
      ['菜单订单', '/food/menu'],
      ['菜品管理', '/food/dishes'],
      ['菜品分类', '/food/categories'],
      ['食材管理', '/food/ingredients'],
      ['食材分类', '/food/ingredient-categories'],
    ]),
  },
  {
    id: 'profile',
    title: '账号与家庭',
    icon: 'users',
    entries: [
      ...entries([
        ['账号设置', '/profile'],
        ['个人资料', '/profile/edit'],
        ['账号安全', '/profile/security'],
        ['家庭成员', '/profile/family-members'],
        ['统一提醒', '/profile/reminders'],
        ['图标资源', '/profile/icon-management'],
      ]),
      { title: '定时任务', path: '/profile/task-management', adminOnly: true },
    ],
  },
]
export function findMenu(path: string) {
  const candidates = modules
    .flatMap((module) => module.entries.map((entry) => ({ module, entry })))
    .filter(
      ({ entry }) => path === entry.path || path.startsWith(entry.path + '/'),
    )
    .sort((a, b) => b.entry.path.length - a.entry.path.length)
  if (path.startsWith('/finance/global-market/detail/'))
    return (
      candidates.find((item) => item.module.id === 'market') || {
        module: modules[3]!,
        entry: modules[3]!.entries[0]!,
      }
    )
  if (path === '/tools')
    return { module: modules[4]!, entry: modules[4]!.entries[0]! }
  return candidates[0]
}
