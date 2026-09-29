import { describe, expect, it } from 'vitest'
import { EMPRESA, whatsappLink } from './empresa'

describe('whatsappLink', () => {
  it('opens the share screen with the message when no business number is set', () => {
    expect(EMPRESA.whatsapp).toBe('')
    expect(whatsappLink('Hola Pedidos360')).toBe('https://wa.me/?text=Hola%20Pedidos360')
  })
})
