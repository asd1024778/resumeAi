import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from '@/App.vue'
import router from '@/router'
import { pinia } from '@/stores'
import { useUserStore } from '@/stores/user'
import '@/style.css'

const app = createApp(App)

app.use(ElementPlus)
app.use(pinia)
app.use(router)

const userStore = useUserStore()
void userStore.fetchUser().finally(() => {
  app.mount('#app')
})
