import { existsSync } from 'node:fs';
import { defineConfig, devices } from '@playwright/test';

// Reuse an installed browser on Windows. Else use Playwright's managed Chromium.
const windowsChannel = process.platform === 'win32'
  ? existsSync('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe') ? 'chrome'
    : existsSync('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe') ? 'msedge' : undefined
  : undefined;
const channel = process.env.PLAYWRIGHT_CHANNEL || windowsChannel;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: 'list',
  use: {
    ...devices['Desktop Chrome'],
    channel,
    baseURL: 'http://127.0.0.1:5173',
    viewport: { width: 1440, height: 1000 },
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm run dev:demo',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
