import './styles/main.css'

import { VueQueryPlugin } from '@tanstack/vue-query'
import { createApp } from 'vue'

import { appPinia } from './app/pinia'
import { queryClient } from './app/queryClient'
import App from './App.vue'
import router from './router'
import { installUnauthorizedReset } from './stores/auth'

installUnauthorizedReset(appPinia)

createApp(App).use(appPinia).use(router).use(VueQueryPlugin, { queryClient }).mount('#app')
