import { defineConfig, devices } from '@playwright/test'

const requestedFrontendPort = Number(process.env.P4_FRONTEND_PORT ?? '5173')
if (
  !Number.isInteger(requestedFrontendPort) ||
  requestedFrontendPort < 1 ||
  requestedFrontendPort > 65_535
) {
  throw new Error('P4_FRONTEND_PORT must be an integer between 1 and 65535.')
}
const frontendBaseUrl =
  process.env.P4_FRONTEND_BASE_URL ?? `http://127.0.0.1:${requestedFrontendPort}`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: frontendBaseUrl,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer:
    process.env.P4_SKIP_WEB_SERVER === 'true'
      ? undefined
      : {
          command: `corepack pnpm dev --host 127.0.0.1 --port ${requestedFrontendPort} --strictPort`,
          url: frontendBaseUrl,
          reuseExistingServer: process.env.P4_FRONTEND_PORT === undefined && !process.env.CI,
        },
})
