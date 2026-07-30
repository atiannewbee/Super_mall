import { expect } from '@playwright/test'

export function collectBrowserErrors(page) {
  const errors = []

  page.on('pageerror', (error) => {
    errors.push(`pageerror: ${error.message}`)
  })
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(`console: ${message.text()}`)
  })

  return {
    assertNone() {
      expect(errors, errors.join('\n')).toEqual([])
    },
  }
}

export async function expectHealthy(request, baseUrl) {
  const response = await request.get(`${baseUrl}/actuator/health`)
  expect(response.ok()).toBeTruthy()
  await expect(response.json()).resolves.toMatchObject({ status: 'UP' })
}

export function waitForApi(page, method, path) {
  return page.waitForResponse((response) => {
    const url = new URL(response.url())
    const pathMatches = path instanceof RegExp ? path.test(url.pathname) : url.pathname === path
    return response.request().method() === method && pathMatches
  })
}

export async function expectApiOk(responsePromise) {
  const response = await responsePromise
  expect(response.ok(), `${response.request().method()} ${new URL(response.url()).pathname} -> ${response.status()}`)
    .toBeTruthy()
  return response
}
