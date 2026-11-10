export type ScreeningRule = {
  key: string
  label: string
  title: string
  description: string
  points: string[]
}

// 选股规则目录：后续新增规则时，在此追加一项即可自动出现在下拉框中。
export const screeningRules: ScreeningRule[] = [
  {
    key: 'sunrise-rise',
    label: '旭日东升',
    title: '旭日东升',
    description: '连续下跌末端出现缩量阳线实体反包，捕捉超跌反转的启动信号。',
    points: [
      '近6日至少 4 根阴线，处于连续下跌通道',
      '最后3日累计跌幅不低于 9.0%，第3根阴线单日跌幅不低于 3.0%',
      '最后3日连续收阴，次日阳线实体反包且缩量',
    ],
  },
  {
    key: 'yin-yang-double-bear',
    label: '阴阳双熊',
    title: '阴阳双熊',
    description:
      '上涨趋势中先出现放量阴线，次日低开后以缩量阳线收回阴线实体但不完全反包。',
    points: [
      '5日均线连续向上，且排除短期连续4个涨停后的形态',
      '阴线与阳线实体至少一根达到 5.0%，实体最大不超过 15.0%',
      '阳线相对阴线收盘低开至少 1.5%，收盘进入阴线实体但不完全反包',
      '阴线实体不超过阳线实体 2 倍，阳线上影线不长于自身实体',
      '阴线相对前5日均量放量 1.5～4 倍，阳线较阴线缩量',
    ],
  },
  {
    key: 'first-board-high-bear',
    label: '首版高阴',
    title: '首版高阴',
    description:
      '低位首个实体涨停后出现高开低走放量阴线，再等待反包、突破或缩量回踩的确认买点。',
    points: [
      '近一个月窗口内优先寻找低位震荡后的首个实体涨停，涨停穿越3日均线且不过度爆量',
      '涨停次日高开低走收阴，阴线实体和回落幅度受控，成交量高于首板',
      '后续满足反包高阴、放量突破高阴最高位、或地量回踩首板低点附近任一买点',
    ],
  },
]
