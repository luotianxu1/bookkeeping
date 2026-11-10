export type AmountTone = 'positive' | 'negative' | 'neutral'

/** 资金流入 / 盈利为红色，流出 / 亏损为绿色；净支出需传入相反数。 */
export function amountTone(value: number): AmountTone {
  if (value > 0) return 'positive'
  if (value < 0) return 'negative'
  return 'neutral'
}
