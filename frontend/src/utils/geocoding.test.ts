import { describe, expect, it } from 'vitest'
import { combinarDireccion, numeroEscrito, ordenarPorNumero, type Lugar } from './geocoding'

const providencia: Lugar = {
  etiqueta: 'Avenida Providencia 1208, Providencia, Región Metropolitana',
  lat: -33.42,
  lon: -70.61,
  numero: '1208',
  comuna: 'Providencia',
}

describe('geocoding', () => {
  it('reads the street number from the first part of the address', () => {
    expect(numeroEscrito('Av. Providencia 1234, Providencia')).toBe('1234')
    expect(numeroEscrito('Los Aromos 45B')).toBe('45B')
    expect(numeroEscrito('Avenida Apoquindo')).toBeUndefined()
  })

  it('keeps the typed number when the map result has another one', () => {
    expect(combinarDireccion('Av. Providencia 1234', providencia)).toEqual({
      direccion: 'Av. Providencia 1234, Providencia',
      exacta: false,
    })
  })

  it('does not repeat the comuna if the user already typed it', () => {
    expect(combinarDireccion('Av. Providencia 1234 Providencia', providencia).direccion).toBe('Av. Providencia 1234 Providencia')
  })

  it('uses the map result when the number matches or none was typed', () => {
    expect(combinarDireccion('Providencia 1208', providencia)).toEqual({ direccion: providencia.etiqueta, exacta: true })
    expect(combinarDireccion('Providencia', providencia).exacta).toBe(true)
  })

  it('puts results with the typed number first', () => {
    const otro: Lugar = { ...providencia, etiqueta: 'otro', numero: '1234' }
    expect(ordenarPorNumero([providencia, otro], 'Providencia 1234')[0].etiqueta).toBe('otro')
  })
})
