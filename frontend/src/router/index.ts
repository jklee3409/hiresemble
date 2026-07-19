import type { Pinia } from 'pinia'
import {
  createRouter,
  createWebHistory,
  type Router,
  type RouterHistory,
  type RouteRecordRaw,
} from 'vue-router'

import { appPinia } from '@/app/pinia'
import AppLayout from '@/layouts/AppLayout.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import DashboardPage from '@/pages/DashboardPage.vue'
import LoginPage from '@/pages/LoginPage.vue'
import NotFoundPage from '@/pages/NotFoundPage.vue'
import OnboardingPage from '@/pages/OnboardingPage.vue'
import RootRedirectPage from '@/pages/RootRedirectPage.vue'
import SignupPage from '@/pages/SignupPage.vue'
import { useAuthStore } from '@/stores/auth'

import { safeReturnTo } from './returnTo'

declare module 'vue-router' {
  interface RouteMeta {
    publicOnly?: boolean
    requiresAuth?: boolean
    title?: string
  }
}

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: RootRedirectPage,
  },
  {
    path: '/',
    component: PublicLayout,
    children: [
      {
        path: 'signup',
        name: 'signup',
        component: SignupPage,
        meta: { publicOnly: true, title: '회원가입' },
      },
      {
        path: 'login',
        name: 'login',
        component: LoginPage,
        meta: { publicOnly: true, title: '로그인' },
      },
    ],
  },
  {
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: 'onboarding',
        name: 'onboarding',
        component: OnboardingPage,
        meta: { title: '온보딩' },
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: DashboardPage,
        meta: { title: '대시보드' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundPage,
    meta: { title: '페이지를 찾을 수 없음' },
  },
]

export function createAppRouter(options?: { history?: RouterHistory; pinia?: Pinia }): Router {
  const pinia = options?.pinia ?? appPinia
  const router = createRouter({
    history: options?.history ?? createWebHistory(import.meta.env.BASE_URL),
    routes,
  })
  const authStore = useAuthStore(pinia)

  authStore.$subscribe(() => {
    const currentRoute = router.currentRoute.value
    if (authStore.status !== 'anonymous' || currentRoute.meta.requiresAuth !== true) {
      return
    }

    const returnTo = safeReturnTo(currentRoute.fullPath)
    void router.replace({
      name: 'login',
      query: returnTo === null ? undefined : { returnTo },
    })
  })

  router.beforeEach(async (to) => {
    await authStore.bootstrap()

    if (to.name === 'home') {
      return { name: authStore.isAuthenticated ? 'dashboard' : 'login', replace: true }
    }

    if (to.meta.requiresAuth === true && !authStore.isAuthenticated) {
      const returnTo = safeReturnTo(to.fullPath)
      return {
        name: 'login',
        query: returnTo === null ? undefined : { returnTo },
        replace: true,
      }
    }

    if (to.meta.publicOnly === true && authStore.isAuthenticated) {
      const returnTo = safeReturnTo(to.query.returnTo)
      return returnTo ?? { name: 'dashboard', replace: true }
    }

    return true
  })

  return router
}

const router = createAppRouter()

export default router
