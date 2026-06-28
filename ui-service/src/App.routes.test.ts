import { describe, expect, it } from 'vitest'
import App from './App'

/**
 * Importing the router tree ensures the new borrower/sales route modules type-check in CI.
 */
describe('App', () => {
  it('is a function component that can be imported for build', () => {
    expect(typeof App).toBe('function')
  })
})
