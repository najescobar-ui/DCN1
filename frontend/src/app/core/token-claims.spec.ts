import { describe, expect, it } from 'vitest';
import { DEFAULT_ROLE, rolesFrom, scopesFrom } from './token-claims';

describe('token-claims', () => {
  it('maps cognito groups to upper-case roles', () => {
    expect(rolesFrom({ 'cognito:groups': ['admin', 'Cliente'] })).toEqual(['ADMIN', 'CLIENTE']);
  });

  it('falls back to the default role when the user has no group', () => {
    expect(rolesFrom({ sub: 'abc' })).toEqual([DEFAULT_ROLE]);
  });

  it('returns no roles without a token', () => {
    expect(rolesFrom(null)).toEqual([]);
  });

  it('splits the scope claim', () => {
    expect(scopesFrom({ scope: 'openid pedidos360/pedidos.read' })).toEqual(['openid', 'pedidos360/pedidos.read']);
  });
});
