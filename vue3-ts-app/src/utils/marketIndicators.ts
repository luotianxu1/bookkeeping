/** 行情技术指标计算（MACD/KDJ/RSI/BIAS），输入与输出序列等长，窗口不足处为 null */

/** EMA 递推（首值作种子） */
export function ema(values: number[], n: number): number[] {
  const k = 2 / (n + 1)
  const out: number[] = [values[0]]
  let prev = values[0]
  for (let i = 1; i < values.length; i++) {
    prev = values[i] * k + prev * (1 - k)
    out.push(prev)
  }
  return out
}

/** 中国式 SMA(X,N,M)：Y = (M*X + (N-M)*Y') / N，X 中 null 跳过并延续 */
export function smaCN(values: (number | null)[], n: number, m: number): (number | null)[] {
  const out: (number | null)[] = []
  let prev: number | null = null
  for (const x of values) {
    if (x === null) {
      out.push(null)
      continue
    }
    prev = prev === null ? x : (m * x + (n - m) * prev) / n
    out.push(prev)
  }
  return out
}

/** 简单移动平均，前 n-1 根为 null */
export function maSeries(values: number[], n: number): (number | null)[] {
  const out: (number | null)[] = []
  let sum = 0
  for (let i = 0; i < values.length; i++) {
    sum += values[i]
    if (i >= n) sum -= values[i - n]
    out.push(i >= n - 1 ? sum / n : null)
  }
  return out
}

/** MACD(12,26,9)：DIF/DEA/柱（柱 = 2*(DIF-DEA)，国内软件惯例） */
export function macd(closes: number[]): { dif: number[]; dea: number[]; hist: number[] } {
  const e12 = ema(closes, 12)
  const e26 = ema(closes, 26)
  const dif = e12.map((v, i) => v - e26[i])
  const dea = ema(dif, 9)
  const hist = dif.map((v, i) => 2 * (v - dea[i]))
  return { dif, dea, hist }
}

/** KDJ(9,3,3)：RSV 未成熟随机值 + 中国式 SMA 平滑 */
export function kdj(
  highs: number[],
  lows: number[],
  closes: number[],
  n = 9,
): { k: (number | null)[]; d: (number | null)[]; j: (number | null)[] } {
  const len = closes.length
  const rsv: (number | null)[] = []
  for (let i = 0; i < len; i++) {
    if (i < n - 1) {
      rsv.push(null)
      continue
    }
    let hh = -Infinity
    let ll = Infinity
    for (let t = i - n + 1; t <= i; t++) {
      if (highs[t] > hh) hh = highs[t]
      if (lows[t] < ll) ll = lows[t]
    }
    rsv.push(hh === ll ? 50 : ((closes[i] - ll) / (hh - ll)) * 100)
  }
  const k = smaCN(rsv, 3, 1)
  const d = smaCN(k, 3, 1)
  const j = k.map((kv, i) => (kv === null || d[i] === null ? null : 3 * kv - 2 * (d[i] as number)))
  return { k, d, j }
}

/** RSI(N)：SMA(涨幅,N,1)/SMA(振幅,N,1)*100，首个值从第 2 根起算 */
export function rsi(closes: number[], n: number): (number | null)[] {
  const up: (number | null)[] = [null]
  const dn: (number | null)[] = [null]
  for (let i = 1; i < closes.length; i++) {
    const ch = closes[i] - closes[i - 1]
    up.push(Math.max(ch, 0))
    dn.push(Math.abs(ch))
  }
  const su = smaCN(up, n, 1)
  const sd = smaCN(dn, n, 1)
  return su.map((u, i) => {
    const s = sd[i]
    if (u === null || s === null) return null
    return s === 0 ? 100 : (u / s) * 100
  })
}

/** BIAS(N) 乖离率：(C-MA)/MA*100 */
export function bias(closes: number[], n: number): (number | null)[] {
  const ma = maSeries(closes, n)
  return closes.map((c, i) => {
    const m = ma[i]
    return m === null || m === 0 ? null : ((c - m) / m) * 100
  })
}
