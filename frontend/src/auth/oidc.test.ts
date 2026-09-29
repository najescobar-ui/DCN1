import { describe, expect, it } from 'vitest'
import { nuevoNonce, signinArgs } from './oidc'

describe('nonce', () => {
  it('is 64 hex characters and different every time', () => {
    const a = nuevoNonce()
    expect(a).toMatch(/^[0-9a-f]{64}$/)
    expect(nuevoNonce()).not.toBe(a)
  })

  it('is sent with every authorize request', () => {
    expect(signinArgs('/pedidos')).toMatchObject({ state: { returnTo: '/pedidos' }, nonce: expect.stringMatching(/^[0-9a-f]{64}$/) })
  })
})
