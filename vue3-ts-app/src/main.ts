import { createApp } from 'vue'
import App from './App.vue'
import './styles/base.css'
import { router } from './router'
import { startGoldPriceAutoRefresh } from './utils/gold-price-cache'

const app = createApp(App)
app.use(router)
startGoldPriceAutoRefresh()

router.isReady().then(() => {
  app.mount('#app')
})
