import { createApp } from 'vue'
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/main.css'
import '@/styles/business.css'
import '@/styles/desktop.css'
import App from './App.vue'
import router from './router'

import { initTheme } from '@/business/utils/theme'
initTheme()
const app = createApp(App)
// 全局兜底：布局层（AdminLayout）内有页面级错误边界负责展示重试 UI，
// 这里兜住布局之外的错误（登录页等），保证有日志可查、不静默失败
app.config.errorHandler = (err, _instance, info) => {
  console.error(`[Life Hub] 未处理错误（${info}）`, err)
}
app.use(router).mount('#app')
