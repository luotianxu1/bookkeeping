import echarts from './echarts'
import { BarChart, PieChart } from 'echarts/charts'

echarts.use([BarChart, PieChart])
export { init } from './echarts'
export default echarts
