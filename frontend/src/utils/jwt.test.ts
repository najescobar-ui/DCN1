import { describe, expect, it } from 'vitest'
import { alterarFirma } from './jwt'

describe('alterarFirma', () => {
  it('keeps header and payload and changes the signature', () => {
    const token = 'aaa.bbb.' + 'Q'.repeat(342) + 'A'
    const [h, p, s] = alterarFirma(token).split('.')
    expect(h).toBe('aaa')
    expect(p).toBe('bbb')
    expect(s).not.toBe(token.split('.')[2])
    expect(s.at(-1)).toBe('A') // the padded last char is left untouched
  })
})
