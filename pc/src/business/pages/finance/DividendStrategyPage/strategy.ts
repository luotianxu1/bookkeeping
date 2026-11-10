// 红利低波年线策略纯计算模块：不依赖 DOM，便于复用与测试。
// 口径：收盘价 vs 250 日年线（MA250），带缓冲带与确认天数，避免低波动资产在年线附近反复缠绕导致的假信号。

/** 策略参数 */
export interface StrategyParams {
  /** 缓冲带比例，如 0.02 表示年线 ±2% */
  band: number
  /** 连续确认天数：信号需连续 N 个交易日满足条件才触发 */
  confirmDays: number
}

/** 年线序列（前 249 个交易日不足 250 日，为 null） */
export function calcMa250(closes: number[]): (number | null)[] {
  const MA_LEN = 250
  const ma: (number | null)[] = new Array(closes.length).fill(null)
  for (let i = MA_LEN - 1; i < closes.length; i += 1) {
    let sum = 0
    for (let j = i - MA_LEN + 1; j <= i; j += 1) {
      sum += closes[j]
    }
    ma[i] = sum / MA_LEN
  }
  return ma
}

/** 当前相对年线的位置状态 */
export type PositionZone = 'buy' | 'sell-watch' | 'neutral' | 'unknown'

export interface PositionStatus {
  zone: PositionZone
  /** 最新收盘价 */
  price: number
  /** 当前年线值 */
  ma: number
  /** 收盘价相对年线的偏离比例（-0.05 表示低于年线 5%） */
  deviation: number
  /** 买入触发线：年线 × (1 - band) */
  buyLine: number
  /** 卖出触发线：年线 × (1 + band) */
  sellLine: number
}

/** 判断最新收盘价落在哪个区间（均值回归口径：跌破年线-缓冲带=买入区，升破年线+缓冲带=止盈观察区） */
export function currentPosition(
  closes: number[],
  ma: (number | null)[],
  params: StrategyParams,
): PositionStatus {
  const last = closes.length - 1
  const maValue = ma[last]
  if (maValue === null || maValue === undefined) {
    return {
      zone: 'unknown',
      price: closes[last] ?? 0,
      ma: 0,
      deviation: 0,
      buyLine: 0,
      sellLine: 0,
    }
  }
  const price = closes[last]
  const deviation = price / maValue - 1
  const zone: PositionZone =
    deviation < -params.band
      ? 'buy'
      : deviation > params.band
        ? 'sell-watch'
        : 'neutral'
  return {
    zone,
    price,
    ma: maValue,
    deviation,
    buyLine: maValue * (1 - params.band),
    sellLine: maValue * (1 + params.band),
  }
}
