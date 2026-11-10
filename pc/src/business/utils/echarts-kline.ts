import echarts from './echarts-investment'
import { BarChart, CandlestickChart } from 'echarts/charts'

// 只在打开 K 线页面时下载并注册；其余组件复用普通图表入口。
echarts.use([BarChart, CandlestickChart])
export { init } from './echarts-investment'
export default echarts
