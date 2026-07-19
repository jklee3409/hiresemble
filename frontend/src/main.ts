import './styles/main.css'

import Aura from '@primeuix/themes/aura'
import { VueQueryPlugin } from '@tanstack/vue-query'
import PrimeVue from 'primevue/config'
import { createApp } from 'vue'

import { appPinia } from './app/pinia'
import { queryClient } from './app/queryClient'
import App from './App.vue'
import router from './router'
import { installUnauthorizedReset } from './stores/auth'

installUnauthorizedReset(appPinia)

createApp(App)
  .use(appPinia)
  .use(router)
  .use(VueQueryPlugin, { queryClient })
  .use(PrimeVue, {
    theme: {
      preset: Aura,
    },
  })
  .mount('#app')
