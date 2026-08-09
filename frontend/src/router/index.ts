import type { Pinia } from 'pinia'
import {
  createRouter,
  createWebHistory,
  type Router,
  type RouterHistory,
  type RouteRecordRaw,
} from 'vue-router'

import { appPinia } from '@/app/pinia'
import { featureFlags } from '@/app/featureFlags'
import AppLayout from '@/layouts/AppLayout.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import DashboardPage from '@/pages/DashboardPage.vue'
import LandingPage from '@/pages/LandingPage.vue'
import LoginPage from '@/pages/LoginPage.vue'
import NotFoundPage from '@/pages/NotFoundPage.vue'
import OnboardingPage from '@/pages/OnboardingPage.vue'
import ProfileBasicPage from '@/pages/ProfileBasicPage.vue'
import ProfileActivitiesPage from '@/pages/ProfileActivitiesPage.vue'
import SignupPage from '@/pages/SignupPage.vue'
import StructuredProfilePage from '@/pages/StructuredProfilePage.vue'
import { useAuthStore } from '@/stores/auth'

import { safeReturnTo } from './returnTo'

declare module 'vue-router' {
  interface RouteMeta {
    publicOnly?: boolean
    requiresAuth?: boolean
    profileRecommended?: boolean
    title?: string
  }
}

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: LandingPage,
    meta: { title: '내 경험을, 다음 기회로' },
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
        meta: { title: '지원 홈' },
      },
      {
        path: 'guide',
        name: 'guide',
        component: () => import('@/pages/GuidePage.vue'),
        meta: { title: '이용 가이드' },
      },
      {
        path: 'profile',
        redirect: { name: 'profile-basic' },
      },
      {
        path: 'profile/basic',
        name: 'profile-basic',
        component: ProfileBasicPage,
        meta: { title: '내 지원 정보', profileRecommended: true },
      },
      {
        path: 'profile/education',
        name: 'profile-education',
        component: StructuredProfilePage,
        props: { kind: 'education' },
        meta: { title: '학력', profileRecommended: true },
      },
      {
        path: 'profile/certifications',
        name: 'profile-certifications',
        component: StructuredProfilePage,
        props: { kind: 'certification' },
        meta: { title: '자격증', profileRecommended: true },
      },
      {
        path: 'profile/languages',
        name: 'profile-languages',
        component: StructuredProfilePage,
        props: { kind: 'language' },
        meta: { title: '어학 성적', profileRecommended: true },
      },
      {
        path: 'profile/awards',
        name: 'profile-awards',
        component: StructuredProfilePage,
        props: { kind: 'award' },
        meta: { title: '수상', profileRecommended: true },
      },
      {
        path: 'profile/careers',
        name: 'profile-careers',
        component: StructuredProfilePage,
        props: { kind: 'career' },
        meta: { title: '경력', profileRecommended: true },
      },
      {
        path: 'profile/activities',
        name: 'profile-activities',
        component: ProfileActivitiesPage,
        meta: { title: '대외활동', profileRecommended: true },
      },
      {
        path: 'profile/experiences',
        name: 'profile-experiences',
        component: () => import('@/pages/ExperienceLibraryPage.vue'),
        meta: { title: '경험 보관함', profileRecommended: true },
      },
      {
        path: 'profile/evidence',
        redirect: { name: 'profile-activities' },
      },
      {
        path: 'documents',
        name: 'documents',
        component: () => import('@/pages/DocumentListPage.vue'),
        meta: { title: '이력서·자료' },
      },
      {
        path: 'documents/:documentId',
        name: 'document-detail',
        component: () => import('@/pages/DocumentDetailPage.vue'),
        meta: { title: '자료 확인' },
      },
      ...gitHubIntegrationRoutes(featureFlags.githubSourceEnabled),
      ...careerArtifactRoutes(featureFlags.careerArtifactEnabled),
      {
        path: 'settings',
        redirect: { name: 'settings-account' },
      },
      {
        path: 'settings/account',
        name: 'settings-account',
        component: () => import('@/pages/AccountSettingsPage.vue'),
        meta: { title: '계정 관리' },
      },
      {
        path: 'jobs',
        name: 'jobs',
        component: () => import('@/pages/JobListPage.vue'),
        meta: { title: '관심 공고' },
      },
      {
        path: 'jobs/new',
        name: 'job-new',
        component: () => import('@/pages/JobNewPage.vue'),
        meta: { title: '공고 등록' },
      },
      {
        path: 'jobs/:jobId',
        component: () => import('@/layouts/JobDetailLayout.vue'),
        children: [
          {
            path: '',
            name: 'job-detail',
            redirect: { name: 'job-overview' },
          },
          {
            path: 'overview',
            name: 'job-overview',
            component: () => import('@/pages/JobOverviewPage.vue'),
            meta: { title: '공고 정보' },
          },
          {
            path: 'analysis',
            name: 'job-analysis',
            component: () => import('@/pages/JobAnalysisPage.vue'),
            meta: { title: '공고 분석', profileRecommended: true },
          },
          {
            path: 'cover-letter',
            name: 'job-cover-letter',
            component: () => import('@/pages/JobCoverLetterPage.vue'),
            meta: { title: '자기소개서', profileRecommended: true },
          },
          {
            path: 'interview',
            name: 'job-interview',
            component: () => import('@/pages/JobInterviewPage.vue'),
            meta: { title: '면접 준비', profileRecommended: true },
          },
        ],
      },
      {
        path: 'cover-letters',
        name: 'cover-letters',
        component: () => import('@/pages/CoverLetterListPage.vue'),
        meta: { title: '자기소개서', profileRecommended: true },
      },
      {
        path: 'cover-letters/:coverLetterId/edit',
        name: 'cover-letter-edit',
        component: () => import('@/pages/CoverLetterEditPage.vue'),
        meta: { title: '자기소개서 편집', profileRecommended: true },
      },
      {
        path: 'interviews',
        name: 'interviews',
        component: () => import('@/pages/InterviewListPage.vue'),
        meta: { title: '면접 준비', profileRecommended: true },
      },
      {
        path: 'interview-question-sets/:questionSetId',
        name: 'interview-question-set',
        component: () => import('@/pages/InterviewQuestionSetPage.vue'),
        meta: { title: '예상 질문 세트', profileRecommended: true },
      },
      {
        path: 'agent-runs',
        name: 'agent-runs',
        component: () => import('@/pages/AgentRunListPage.vue'),
        meta: { title: 'AI 작업' },
      },
      {
        path: 'agent-runs/:agentRunId',
        name: 'agent-run-detail',
        component: () => import('@/pages/AgentRunDetailPage.vue'),
        meta: { title: 'AI 작업 상세' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundPage,
    meta: { title: '페이지를 찾을 수 없어요' },
  },
]

/*
 * GitHub 연동 화면은 `이력서·자료` 영역으로 옮겼다.
 * 다만 `/profile/github`는 backend가 required user action route로 그대로 돌려주는 값이므로
 * 계약을 바꾸지 않고 새 경로로 보내는 redirect만 남긴다.
 */
export function gitHubIntegrationRoutes(enabled: boolean): RouteRecordRaw[] {
  return enabled
    ? [
        {
          path: 'integrations',
          name: 'integrations',
          component: () => import('@/pages/GitHubSourcePage.vue'),
          meta: { title: '외부 연동', profileRecommended: true },
        },
        {
          path: 'profile/github',
          redirect: (to) => ({ name: 'integrations', query: to.query }),
        },
      ]
    : []
}

export function careerArtifactRoutes(enabled: boolean): RouteRecordRaw[] {
  return enabled
    ? [
        {
          path: 'career-artifacts',
          name: 'career-artifacts',
          component: () => import('@/pages/CareerArtifactListPage.vue'),
          meta: { title: 'AI로 만든 초안' },
        },
        {
          path: 'career-artifacts/new',
          name: 'career-artifact-new',
          component: () => import('@/pages/CareerArtifactNewPage.vue'),
          meta: { title: '새 초안 만들기' },
        },
        {
          path: 'career-artifacts/:careerArtifactId',
          name: 'career-artifact-detail',
          component: () => import('@/pages/CareerArtifactDetailPage.vue'),
          meta: { title: '생성 자료 확인' },
        },
      ]
    : []
}

export function createAppRouter(options?: { history?: RouterHistory; pinia?: Pinia }): Router {
  const pinia = options?.pinia ?? appPinia
  const router = createRouter({
    history: options?.history ?? createWebHistory(import.meta.env.BASE_URL),
    routes,
    scrollBehavior: (_to, _from, savedPosition) => savedPosition ?? { top: 0 },
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
      return authStore.isAuthenticated ? { name: 'dashboard', replace: true } : true
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

  router.afterEach((to) => {
    document.title =
      typeof to.meta.title === 'string' ? `${to.meta.title} | Hiresemble` : 'Hiresemble'
  })

  return router
}

const router = createAppRouter()

export default router
