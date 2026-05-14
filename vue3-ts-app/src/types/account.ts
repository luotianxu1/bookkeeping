// 账户管理类型：描述账户分组、账户条目和账户管理页总览数据。
export type AccountItem = {
  /** 账户ID。 */
  id?: number
  /** 账户图标文本，后续可替换为图标组件。 */
  icon: string
  /** 账户名称。 */
  name: string
  /** 账户副标题。 */
  subtitle?: string
  /** 账户余额展示文本。 */
  amount: string
  /** 可选跳转路径。 */
  path?: string
}

export type AccountGroup = {
  /** 账户类型ID。 */
  accountTypeId?: number
  /** 账户分组名称，如现金账户、投资账户。 */
  title: string
  /** 分组级可选跳转路径。 */
  path?: string
  /** 分组下账户列表。 */
  items: AccountItem[]
}

export type AccountOverview = {
  /** 总资产标签。 */
  label: string
  /** 总资产金额。 */
  amount: string
}

/** 投资详情总览：描述详情页头部资产卡展示内容。 */
export type InvestmentDetailOverview = {
  /** 投资标的名称。 */
  name: string
  /** 标的代码或份额描述。 */
  subtitle: string
  /** 当前净值或市值。 */
  amount: string
  /** 底部更新时间文本。 */
  updatedAt: string
  /** 右上角今日盈亏标签。 */
  todayLabel: string
  /** 右上角今日盈亏值。 */
  todayValue: string
}

/** 投资详情键值项：用于基础信息、分析数据等成对信息。 */
export type InvestmentDetailStat = {
  /** 指标名称。 */
  label: string
  /** 指标值。 */
  value: string
  /** 可选强调色标记。 */
  tone?: 'primary' | 'positive' | 'negative' | 'default'
}

/** 投资详情交易记录：用于列表展示每一条买卖/分红变动。 */
export type InvestmentDetailTransaction = {
  /** 交易类型标题。 */
  title: string
  /** 交易发生时间。 */
  time: string
  /** 展示金额。 */
  amount: string
  /** 份额变化。 */
  units: string
  /** 可选金额补充说明。 */
  note?: string
  /** 交易方向色彩。 */
  trend: 'up' | 'down'
}
