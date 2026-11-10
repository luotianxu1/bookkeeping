/**
 * echarts 按需引入：普通图表从这里动态加载，K 线通过 echarts-kline 额外注册，避免打入 1MB+ 的全量包。
 *
 * 用法（页面内）：
 *   echartsLib = await import('@/business/utils/echarts')
 *   const chart = echartsLib.init(el)
 *
 * 类型仍从 'echarts' 导入（import type 不会打入产物）：
 *   import type { ECharts, EChartsCoreOption } from 'echarts'
 *
 * 统计图表使用 echarts-statistics，投资图表使用 echarts-investment，K 线使用 echarts-kline。
 */
import * as echarts from 'echarts/core'
import type { ECharts } from 'echarts'
import { LineChart } from 'echarts/charts'
import {
  AxisPointerComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  MarkPointComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// 已注册：折线 + 网格/提示框/图例/坐标轴指示器/标线/标点
echarts.use([
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  AxisPointerComponent,
  MarkLineComponent,
  MarkPointComponent,
  CanvasRenderer,
])

// init 返回类型收口为主包 ECharts（core 的 EChartsType 与主包类型有私有字段差异，
// 页面统一 import type { ECharts } from 'echarts'，运行时是同一实例）
export const init = (...args: Parameters<typeof echarts.init>): ECharts =>
  echarts.init(...args) as unknown as ECharts
export default echarts
