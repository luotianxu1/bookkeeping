import { describe, expect, test } from 'vitest'
import { buildScenarios, projectAssets } from '../src/pages/finance/FreeEstimatePage/projection'

describe('财务自由方案和趋势使用相同的月度预测', () => {
  test('零回报时按月末投入累计，图表与首次达标月份一致', () => {
    const result = projectAssets(12000, 24000, 1000, 0, 2)
    expect(result.monthsToFreedom).toBe(12)
    expect(result.points).toEqual([{ year: 0, assets: 12000 }, { year: 1, assets: 24000 }, { year: 2, assets: 36000 }])
  })

  test('三种方案共用本金和结余，回报率越高越早达标', () => {
    const [low, base, high] = buildScenarios(100000, 1500000, 10000, 4, 30)
    expect([low.annualReturn, base.annualReturn, high.annualReturn]).toEqual([2, 4, 6])
    expect(high.monthsToFreedom!).toBeLessThan(base.monthsToFreedom!)
    expect(base.monthsToFreedom!).toBeLessThan(low.monthsToFreedom!)
    for (const scenario of [low, base, high]) {
      expect(scenario.points[0].assets).toBe(100000)
      expect(scenario.points).toHaveLength(31)
    }
    expect(high.points[30].assets).toBeGreaterThan(base.points[30].assets)
    expect(base.points[30].assets).toBeGreaterThan(low.points[30].assets)
  })

  test('缩短图表年限不会改变达标时间，年化边界允许方案重合', () => {
    const short = buildScenarios(0, 500000, 1000, 0, 10)
    const long = buildScenarios(0, 500000, 1000, 0, 30)
    expect(short[1].monthsToFreedom).toBe(500)
    expect(short[1].monthsToFreedom).toBe(long[1].monthsToFreedom)
    expect(short[0].annualReturn).toBe(0)
    expect(short[0].points).toEqual(short[1].points)
    expect(short[1].points).toHaveLength(11)
    expect(buildScenarios(0, 500000, 1000, 20, 10).map(s => s.annualReturn)).toEqual([18, 20, 20])
  })

  test('区分当前达标和预测期内无法达到目标', () => {
    expect(projectAssets(500000, 500000, 0, 0, 10).monthsToFreedom).toBe(0)
    const unavailable = projectAssets(0, 500000, 0, 4, 10)
    expect(unavailable.monthsToFreedom).toBeNull()
    expect(unavailable.points.every(p => p.assets === 0)).toBe(true)
  })
})
