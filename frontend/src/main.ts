import './styles/main.css'

import Aura from '@primeuix/themes/aura'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'

const queryClient = new QueryClient()

createApp(App)
  .use(createPinia())
  .use(router)
  .use(VueQueryPlugin, { queryClient })
  .use(PrimeVue, {
    theme: {
      preset: Aura,
    },
  })
  .mount('#app')
