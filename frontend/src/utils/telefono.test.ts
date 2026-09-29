import { describe, expect, it } from 'vitest'
import { formatearTelefono, telefonoValido } from './telefono'

describe('telefono', () => {
  it('accepts Chilean mobiles with or without +56', () => {
    expect(telefonoValido('+56 9 1234 5678')).toBe(true)
    expect(telefonoValido('912345678')).toBe(true)
    expect(telefonoValido('56912345678')).toBe(true)
  })

  it('rejects landlines and short numbers', () => {
    expect(telefonoValido('221234567')).toBe(false)
    expect(telefonoValido('12345')).toBe(false)
  })

  it('formats to +56 9 XXXX XXXX', () => {
    expect(formatearTelefono('+56912345678')).toBe('+56 9 1234 5678')
  })
})
