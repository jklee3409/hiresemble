import { defineConfig, devices } from '@playwright/test'

const configuredFrontendPort = process.env.P5_FRONTEND_PORT ?? process.env.P4_FRONTEND_PORT
const requestedFrontendPort = Number(configuredFrontendPort ?? '5173')
if (
  !Number.isInteger(requestedFrontendPort) ||
  requestedFrontendPort < 1 ||
  requestedFrontendPort > 65_535
) {
  throw new Error('The configured frontend port must be an integer between 1 and 65535.')
}
const frontendBaseUrl =
  process.env.P5_FRONTEND_BASE_URL ??
  process.env.P4_FRONTEND_BASE_URL ??
  `http://127.0.0.1:${requestedFrontendPort}`
const skipWebServer =
  process.env.P5_SKIP_WEB_SERVER === 'true' || process.env.P4_SKIP_WEB_SERVER === 'true'

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
  webServer: skipWebServer
    ? undefined
    : {
        command: `corepack pnpm dev --host 127.0.0.1 --port ${requestedFrontendPort} --strictPort`,
        url: frontendBaseUrl,
        reuseExistingServer: configuredFrontendPort === undefined && !process.env.CI,
      },
})
