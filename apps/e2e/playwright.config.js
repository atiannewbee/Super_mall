import { defineConfig } from '@playwright/test'
import { readRuntimeConfig } from './support/runtime.js'

const runtime = readRuntimeConfig()
const browserChannel = process.env.E2E_BROWSER_CHANNEL
  || (process.env.CI ? undefined : 'chrome')

export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    baseURL: runtime.storefrontUrl,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...(browserChannel ? { channel: browserChannel } : {}),
  },
})
