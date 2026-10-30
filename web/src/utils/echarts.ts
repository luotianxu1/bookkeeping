/**
 * echarts 按需引入：全项目统一从这里动态加载，避免打入 1MB+ 的全量包。
 *
 * 用法（页面内）：
 *   echartsLib = await import('@/utils/echarts')
 *   const chart = echartsLib.init(el)
 *
 * 类型仍从 'echarts' 导入（import type 不会打入产物）：
 *   import type { ECharts, EChartsCoreOption } from 'echarts'
 *
 * 新增图表类型/组件时在此注册，并同步更新注释清单。
 */
import * as echarts from 'echarts/core';
import type { ECharts } from 'echarts';
import {
  BarChart,
  CandlestickChart,
  LineChart,
  PieChart,
  ScatterChart,
} from 'echarts/charts';
import {
  AxisPointerComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  MarkPointComponent,
  TooltipComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

// 已注册：折线/柱状/饼图/散点/K线 + 网格/提示框/图例/坐标轴指示器/区域缩放/标线/标点
echarts.use([
  LineChart,
  BarChart,
  PieChart,
  ScatterChart,
  CandlestickChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  AxisPointerComponent,
  DataZoomComponent,
  MarkLineComponent,
  MarkPointComponent,
  CanvasRenderer,
]);

// init 返回类型收口为主包 ECharts（core 的 EChartsType 与主包类型有私有字段差异，
// 页面统一 import type { ECharts } from 'echarts'，运行时是同一实例）
export const init = (...args: Parameters<typeof echarts.init>): ECharts =>
  echarts.init(...args) as unknown as ECharts;
export default echarts;
