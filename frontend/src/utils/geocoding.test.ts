import { describe, expect, it } from 'vitest'
import { combinarDireccion, numeroEscrito, ordenar, sinNumero, type Lugar } from './geocoding'

const providencia: Lugar = {
  etiqueta: 'Avenida Providencia 1208, Providencia, Región Metropolitana',
  lat: -33.42,
  lon: -70.61,
  numero: '1208',
  comuna: 'Providencia',
  calle: 'Avenida Providencia',
  tipo: 'house',
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
    expect(ordenar([providencia, otro], 'Providencia 1234')[0].etiqueta).toBe('otro')
    expect(ordenar([providencia, otro], 'Providencia 1234 Providencia')[0].etiqueta).toBe('otro')
  })

  it('removes the street number to search the street', () => {
    expect(sinNumero('San Jorge 60, Ñuñoa')).toBe('San Jorge, Ñuñoa')
  })

  // Real case: OSM has "San Jorge 60" only in La Florida; in Ñuñoa it has the street without numbers.
  it('prefers the street in the typed comuna over an exact number elsewhere', () => {
    const laFlorida: Lugar = { etiqueta: 'San Jorge 60, La Florida', lat: -33.559, lon: -70.583, numero: '60', comuna: 'La Florida', calle: 'San Jorge', tipo: 'house' }
    const monckeberg: Lugar = { etiqueta: 'Avenida Alcalde Jorge Monckeberg 60, Ñuñoa', lat: -33.455, lon: -70.582, numero: '60', comuna: 'Ñuñoa', calle: 'Avenida Alcalde Jorge Monckeberg', tipo: 'house' }
    const calleNunoa: Lugar = { etiqueta: 'San Jorge, Ñuñoa', lat: -33.455, lon: -70.577, comuna: 'Ñuñoa', calle: 'San Jorge', tipo: 'street' }
    const ferreteria: Lugar = { etiqueta: 'Ferretería San Jorge, San Jorge 1089, Ñuñoa', lat: -33.45, lon: -70.6, numero: '1089', comuna: 'Ñuñoa', calle: 'San Jorge', tipo: 'place' }
    const orden = ordenar([monckeberg, laFlorida, ferreteria, calleNunoa], 'San Jorge 60, Ñuñoa')
    expect(orden[0]).toBe(calleNunoa)
    expect(orden.indexOf(laFlorida)).toBeGreaterThan(orden.indexOf(ferreteria))
    expect(combinarDireccion('San Jorge 60, Ñuñoa', orden[0])).toEqual({ direccion: 'San Jorge 60, Ñuñoa', exacta: false })
  })
})
