import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

window.addEventListener('unhandledrejection', (event) => {
  if (event.reason?.message?.includes('dynamically imported module') || event.reason?.message?.includes('Failed to fetch')) {
    event.preventDefault()
    window.location.reload()
  }
})

const app = createApp(App)

app.use(router)
app.use(store)
app.use(ElementPlus)

app.mount('#app')
