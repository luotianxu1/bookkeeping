import echarts from './echarts'
import { ScatterChart } from 'echarts/charts'
import { DataZoomComponent } from 'echarts/components'

echarts.use([ScatterChart, DataZoomComponent])
export { init } from './echarts'
export default echarts
