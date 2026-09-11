import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'
import './price-watch-overrides.css'

const app = createApp(App)
app.use(router)
app.mount('#app')
