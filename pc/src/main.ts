import { createApp } from 'vue'
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/main.css'
import '@/styles/business.css'
import App from './App.vue'
import router from './router'

import { initTheme } from '@/business/utils/theme'
initTheme()
const app = createApp(App)
app.use(router).mount('#app')
