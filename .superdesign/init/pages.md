# SuperDesign Page Dependency Context

These trees prioritize the ten product-critical routes. They trace local imports that affect rendering or page state; framework and node_modules imports are omitted. API/query files are retained where they determine visible states and copy.

## / (Landing)

Entry: `frontend/src/pages/LandingPage.vue`

Dependencies:
- `frontend/src/pages/LandingProductDemo.vue`
- `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/BrandMark.vue`
- `frontend/src/shared/ui/productJourney.ts`

Layout: standalone landing composition.

## /dashboard (Application Home)

Entry: `frontend/src/pages/DashboardPage.vue`

Dependencies:
- `frontend/src/shared/api/dashboardApi.ts`
  - `frontend/src/shared/api/contracts.ts`
  - `frontend/src/shared/api/http.ts`
  - `frontend/src/shared/api/errors.ts`
- `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/PageHeader.vue`
- `frontend/src/shared/ui/StatePanel.vue`
  - `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/StatusBadge.vue`
- `frontend/src/stores/auth.ts`
- `frontend/src/layouts/AppLayout.vue`
  - `frontend/src/shared/ui/AppIcon.vue`
  - `frontend/src/shared/ui/BrandMark.vue`
  - `frontend/src/features/auth/formValidation.ts`
  - `frontend/src/shared/api/errors.ts`
  - `frontend/src/stores/auth.ts`

## /profile/basic (Basic Profile)

Entry: `frontend/src/pages/ProfileBasicPage.vue`

Dependencies:
- `frontend/src/features/profile/ProfileTabs.vue`
- `frontend/src/features/profile/ProfileSectionActions.vue`
- `frontend/src/features/profile/VersionConflictPanel.vue`
- `frontend/src/features/profile/schemas.ts`
- `frontend/src/features/profile/queryKeys.ts`
- `frontend/src/shared/api/profileApi.ts`
- `frontend/src/shared/ui/PageHeader.vue`
- `frontend/src/shared/ui/StatePanel.vue`
  - `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/layouts/AppLayout.vue`

## /documents (Documents)

Entry: `frontend/src/pages/DocumentListPage.vue`

Dependencies:
- `frontend/src/features/documents/filters.ts`
- `frontend/src/features/documents/presentation.ts`
- `frontend/src/features/documents/queries.ts`
- `frontend/src/features/documents/DocumentRunMonitor.vue`
- `frontend/src/shared/api/documentContracts.ts`
- `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/PageHeader.vue`
- `frontend/src/shared/ui/PaginationNav.vue`
- `frontend/src/shared/ui/StatePanel.vue`
- `frontend/src/shared/ui/StatusBadge.vue`
- `frontend/src/layouts/AppLayout.vue`

## /documents/:documentId (Document Detail)

Entry: `frontend/src/pages/DocumentDetailPage.vue`

Dependencies:
- `frontend/src/features/documents/DocumentEvidencePanel.vue`
- `frontend/src/features/documents/DocumentRunMonitor.vue`
- `frontend/src/features/documents/presentation.ts`
- `frontend/src/features/documents/queries.ts`
- `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/PageHeader.vue`
- `frontend/src/shared/ui/StatePanel.vue`
- `frontend/src/shared/ui/StatusBadge.vue`
- `frontend/src/layouts/AppLayout.vue`

## /jobs (Saved Job Postings)

Entry: `frontend/src/pages/JobListPage.vue`

Dependencies:
- `frontend/src/features/jobs/filters.ts`
- `frontend/src/features/jobs/presentation.ts`
- `frontend/src/features/jobs/queries.ts`
  - `frontend/src/features/agent-runs/queries.ts`
  - `frontend/src/shared/api/agentRunApi.ts`
  - `frontend/src/shared/api/jobApi.ts`
- `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/PageHeader.vue`
- `frontend/src/shared/ui/PaginationNav.vue`
- `frontend/src/shared/ui/StatePanel.vue`
- `frontend/src/shared/ui/StatusBadge.vue`
- `frontend/src/layouts/AppLayout.vue`

## /jobs/new (Register Job)

Entry: `frontend/src/pages/JobNewPage.vue`

Dependencies:
- `frontend/src/features/jobs/validation.ts`
- `frontend/src/features/jobs/queries.ts`
- `frontend/src/shared/api/errors.ts`
- `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/PageHeader.vue`
- `frontend/src/layouts/AppLayout.vue`

## /jobs/:jobId/analysis (Job Analysis — Target Page)

Entry: `frontend/src/pages/JobAnalysisPage.vue`

Dependencies:
- `frontend/src/features/agent-runs/presentation.ts`
  - `frontend/src/shared/api/agentRunContracts.ts`
- `frontend/src/features/agent-runs/queries.ts`
  - `frontend/src/shared/api/agentRunApi.ts`
  - `frontend/src/shared/api/agentRunContracts.ts`
- `frontend/src/features/agent-runs/stream.ts`
- `frontend/src/features/jobs/analysisPresentation.ts`
  - `frontend/src/shared/api/jobContracts.ts`
- `frontend/src/features/jobs/JobPreparationJourney.vue`
  - `frontend/src/features/jobs/analysisPresentation.ts`
  - `frontend/src/shared/api/jobContracts.ts`
- `frontend/src/features/jobs/queries.ts`
  - `frontend/src/features/agent-runs/queries.ts`
  - `frontend/src/shared/api/agentRunApi.ts`
  - `frontend/src/shared/api/jobApi.ts`
  - `frontend/src/shared/api/jobContracts.ts`
- `frontend/src/features/profile/queryKeys.ts`
  - `frontend/src/shared/api/profileApi.ts`
- `frontend/src/shared/api/errors.ts`
- `frontend/src/shared/api/jobContracts.ts`
- `frontend/src/shared/api/jobApi.ts`
- `frontend/src/shared/api/profileApi.ts`
- `frontend/src/shared/ui/PaginationNav.vue`
- `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/StatePanel.vue`
  - `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/StatusBadge.vue`
- `frontend/src/stores/auth.ts`
- `frontend/src/layouts/JobDetailLayout.vue`
  - `frontend/src/features/jobs/presentation.ts`
  - `frontend/src/features/jobs/queries.ts`
  - `frontend/src/shared/ui/AppIcon.vue`
  - `frontend/src/shared/ui/StatusBadge.vue`
  - `frontend/src/stores/auth.ts`
- `frontend/src/layouts/AppLayout.vue`
  - `frontend/src/shared/ui/AppIcon.vue`
  - `frontend/src/shared/ui/BrandMark.vue`
  - `frontend/src/features/auth/formValidation.ts`
  - `frontend/src/shared/api/errors.ts`
  - `frontend/src/stores/auth.ts`

Actual render branch:
- `JobAnalysisPage.vue:410:1244` is the complete template.
- Scoped visual rules start at `JobAnalysisPage.vue:1246`; because the file exceeds 900 lines, pass only the selectors needed for the selected report sections.
- `AppLayout.vue:282:538` is its rendered shell template.
- `JobDetailLayout.vue:71:154` is its nested workspace template.

## /jobs/:jobId/cover-letter (Cover Letter Workspace)

Entry: `frontend/src/pages/JobCoverLetterPage.vue`

Dependencies:
- `frontend/src/features/cover-letters/CoverLetterConflictPanel.vue`
- `frontend/src/features/cover-letters/CoverLetterRunMonitor.vue`
- `frontend/src/features/cover-letters/presentation.ts`
- `frontend/src/features/cover-letters/queries.ts`
- `frontend/src/features/jobs/queries.ts`
- `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/StatePanel.vue`
- `frontend/src/shared/ui/StatusBadge.vue`
- `frontend/src/layouts/JobDetailLayout.vue`
- `frontend/src/layouts/AppLayout.vue`

## /jobs/:jobId/interview (Interview Preparation)

Entry: `frontend/src/pages/JobInterviewPage.vue`

Dependencies:
- `frontend/src/features/interviews/InterviewQuestionCard.vue`
- `frontend/src/features/interviews/InterviewRunMonitor.vue`
- `frontend/src/features/interviews/presentation.ts`
- `frontend/src/features/interviews/queries.ts`
- `frontend/src/features/jobs/queries.ts`
- `frontend/src/shared/ui/AppIcon.vue`
- `frontend/src/shared/ui/StatePanel.vue`
- `frontend/src/shared/ui/StatusBadge.vue`
- `frontend/src/layouts/JobDetailLayout.vue`
- `frontend/src/layouts/AppLayout.vue`

