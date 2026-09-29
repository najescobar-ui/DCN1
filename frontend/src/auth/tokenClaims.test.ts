import { describe, expect, it } from 'vitest'
import { DEFAULT_ROLE, decodeJwt, rolesFrom, scopesFrom } from './tokenClaims'

const b64url = (value: object) => btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')

describe('tokenClaims', () => {
  it('maps cognito groups to upper-case roles', () => {
    expect(rolesFrom({ 'cognito:groups': ['admin', 'Cliente'] })).toEqual(['ADMIN', 'CLIENTE'])
  })

  it('falls back to the default role when the user has no group', () => {
    expect(rolesFrom({ sub: 'abc' })).toEqual([DEFAULT_ROLE])
  })

  it('returns no roles without a token', () => {
    expect(rolesFrom(null)).toEqual([])
  })

  it('splits the scope claim', () => {
    expect(scopesFrom({ scope: 'openid pedidos360/pedidos.read' })).toEqual(['openid', 'pedidos360/pedidos.read'])
  })

  it('decodes the payload of a JWT', () => {
    const token = `${b64url({ alg: 'RS256' })}.${b64url({ username: 'ana', scope: 'openid' })}.firma`
    expect(decodeJwt(token)).toMatchObject({ username: 'ana', scope: 'openid' })
  })

  it('returns null for malformed tokens', () => {
    expect(decodeJwt('no-es-un-jwt')).toBeNull()
    expect(decodeJwt(undefined)).toBeNull()
  })
})
