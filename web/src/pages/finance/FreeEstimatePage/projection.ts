export type ScenarioId = 'conservative' | 'base' | 'optimistic'

export interface ProjectionPoint { year: number; assets: number }
export interface EstimateScenario {
  id: ScenarioId
  label: string
  annualReturn: number
  monthsToFreedom: number | null
  points: ProjectionPoint[]
}

/** 沿用原测算口径：按月复利，月末投入非负结余，最长测算 100 年。 */
export function projectAssets(currentAssets: number, targetAssets: number, monthlySurplus: number, annualReturn: number, years: number) {
  let balance = currentAssets
  let monthsToFreedom: number | null = balance >= targetAssets ? 0 : null
  const points: ProjectionPoint[] = [{ year: 0, assets: balance }]
  const contribution = Math.max(monthlySurplus, 0)
  for (let month = 1; month <= 1200; month += 1) {
    balance = balance * (1 + annualReturn / 100 / 12) + contribution
    if (monthsToFreedom === null && balance >= targetAssets) monthsToFreedom = month
    if (month <= years * 12 && month % 12 === 0) points.push({ year: month / 12, assets: balance })
    if (month >= years * 12 && monthsToFreedom !== null) break
  }
  return { monthsToFreedom, points }
}

export function buildScenarios(currentAssets: number, targetAssets: number, monthlySurplus: number, baseReturn: number, years: number): EstimateScenario[] {
  const base = Math.min(20, Math.max(0, baseReturn))
  return [
    { id: 'conservative' as const, label: '保守', annualReturn: Math.max(0, base - 2) },
    { id: 'base' as const, label: '基准', annualReturn: base },
    { id: 'optimistic' as const, label: '乐观', annualReturn: Math.min(20, base + 2) },
  ].map(scenario => ({ ...scenario, ...projectAssets(currentAssets, targetAssets, monthlySurplus, scenario.annualReturn, years) }))
}
