import { createApp } from 'vue'
import App from './App.vue'
import './styles/base.css'
import { router } from './router'
import { startGoldPriceAutoRefresh } from './utils/gold-price-cache'
import { setupRem } from './utils/rem'
import { initTheme } from './utils/theme'

initTheme()

const app = createApp(App)
app.use(router)
setupRem()
startGoldPriceAutoRefresh()

router.isReady().then(() => {
  app.mount('#app')
})
