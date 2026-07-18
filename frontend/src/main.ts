import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => undefined)
  })
}

createApp(App).use(router).mount('#app')
