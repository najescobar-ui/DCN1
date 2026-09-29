import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef, useState } from 'react'
import { config } from '../config'
import { buscarDirecciones, combinarDireccion, direccionEn, type Lugar } from '../utils/geocoding'
import { PinIcon, SearchIcon } from './icons'

interface Props {
  direccion: string
  lat?: number | null
  lon?: number | null
  onChange: (value: { direccion: string; lat: number | null; lon: number | null }) => void
}

const SANTIAGO: L.LatLngExpression = [-33.4489, -70.6693]
const pinIcon = L.divIcon({ className: 'map-pin', html: '<span></span>', iconSize: [28, 28], iconAnchor: [14, 28] })

/** Address input with suggestions (Photon / OpenStreetMap) and a map with a draggable pin. */
export function AddressPicker({ direccion, lat, lon, onChange }: Props) {
  const [texto, setTexto] = useState(direccion)
  const [sugerencias, setSugerencias] = useState<Lugar[]>([])
  const [abierto, setAbierto] = useState(false)
  const [buscando, setBuscando] = useState(false)
  // Last address chosen from the list or the map; typing that exact text again does not search.
  const [seleccion, setSeleccion] = useState(direccion)
  const [aviso, setAviso] = useState<string | null>(null)
  const textoRef = useRef(texto)
  textoRef.current = texto
  const mapEl = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const marker = useRef<L.Marker | null>(null)
  const onChangeRef = useRef(onChange)
  onChangeRef.current = onChange

  // Map setup (once).
  useEffect(() => {
    if (!mapEl.current || map.current) return
    const m = L.map(mapEl.current, { zoomControl: true, attributionControl: true }).setView(
      lat != null && lon != null ? [lat, lon] : SANTIAGO,
      lat != null ? 16 : 11,
    )
    if (config.mapboxToken) {
      // Mapbox dark style as raster tiles (free tier: 200k tile requests/month).
      L.tileLayer(`https://api.mapbox.com/styles/v1/mapbox/dark-v11/tiles/512/{z}/{x}/{y}@2x?access_token=${config.mapboxToken}`, {
        tileSize: 512,
        zoomOffset: -1,
        maxZoom: 20,
        attribution:
          '&copy; <a href="https://www.mapbox.com/about/maps/">Mapbox</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> <a href="https://www.mapbox.com/map-feedback/">Improve this map</a>',
      }).addTo(m)
    } else {
      // OpenStreetMap standard tiles (free, attribution required); darkened with CSS to match the theme.
      mapEl.current.classList.add('map-osm')
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      }).addTo(m)
    }
    const mk = L.marker(lat != null && lon != null ? [lat, lon] : SANTIAGO, { draggable: true, icon: pinIcon })
    if (lat != null) mk.addTo(m)
    // Dragging only adjusts the point on the map; the address the user typed is kept.
    mk.on('dragend', async () => {
      const { lat: la, lng: lo } = mk.getLatLng()
      let actual = textoRef.current.trim()
      if (!actual) {
        const lugar = await direccionEn(la, lo).catch(() => null)
        actual = lugar?.etiqueta ?? `${la.toFixed(5)}, ${lo.toFixed(5)}`
        setTexto(actual)
        setSeleccion(actual)
      }
      setAviso(null)
      onChangeRef.current({ direccion: actual, lat: la, lon: lo })
    })
    map.current = m
    marker.current = mk
    return () => {
      m.remove()
      map.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Suggestions while typing (debounced, cancels the previous request).
  useEffect(() => {
    const q = texto.trim()
    if (q.length < 4 || q === seleccion) {
      setSugerencias([])
      return
    }
    const ctrl = new AbortController()
    const timer = setTimeout(async () => {
      setBuscando(true)
      try {
        setSugerencias(await buscarDirecciones(q, ctrl.signal))
        setAbierto(true)
      } catch {
        // Aborted or offline: keep the typed text.
      } finally {
        setBuscando(false)
      }
    }, 350)
    return () => {
      clearTimeout(timer)
      ctrl.abort()
    }
  }, [texto, seleccion])

  const ubicar = (lugar: Lugar, escrito = texto) => {
    const { direccion: final, exacta } = combinarDireccion(escrito, lugar)
    setTexto(final)
    setSeleccion(final)
    setSugerencias([])
    setAbierto(false)
    setAviso(
      exacta
        ? null
        : 'El mapa no tiene ese número exacto: dejamos tu dirección tal como la escribiste y ubicamos la calle. Arrastra el pin a tu puerta si hace falta.',
    )
    onChange({ direccion: final, lat: lugar.lat, lon: lugar.lon })
    if (map.current && marker.current) {
      marker.current.setLatLng([lugar.lat, lugar.lon]).addTo(map.current)
      map.current.setView([lugar.lat, lugar.lon], 16)
    }
  }

  const miUbicacion = () => {
    navigator.geolocation?.getCurrentPosition(async ({ coords }) => {
      const lugar = await direccionEn(coords.latitude, coords.longitude).catch(() => null)
      ubicar(lugar ?? { etiqueta: `${coords.latitude.toFixed(5)}, ${coords.longitude.toFixed(5)}`, lat: coords.latitude, lon: coords.longitude }, '')
    })
  }

  return (
    <div className="address-picker">
      <div className="address-input">
        <label className="field" style={{ flex: 1 }}>
          Dirección de entrega
          <div className="search" style={{ background: 'var(--bg)' }}>
            <SearchIcon size={18} className="muted" />
            <input
              value={texto}
              placeholder="Busca tu calle y número, ej: Av. Providencia 1234"
              autoComplete="street-address"
              role="combobox"
              aria-expanded={abierto && sugerencias.length > 0}
              aria-controls="sugerencias-direccion"
              onChange={(e) => {
                setTexto(e.target.value)
                onChange({ direccion: e.target.value, lat: null, lon: null })
              }}
              onFocus={() => setAbierto(true)}
              onBlur={() => setTimeout(() => setAbierto(false), 150)}
            />
            {buscando && <span className="muted" style={{ fontSize: 12 }}>buscando…</span>}
          </div>
        </label>
        {abierto && sugerencias.length > 0 && (
          <ul className="suggestions" id="sugerencias-direccion" role="listbox">
            {sugerencias.map((s) => (
              <li key={`${s.lat},${s.lon}`} role="option" aria-selected={false}>
                <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={() => ubicar(s)}>
                  <PinIcon size={16} className="accent" />
                  {s.etiqueta}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="map-wrap">
        <div ref={mapEl} className="map" aria-label="Mapa de la dirección de entrega" />
        <button type="button" className="btn btn-sm btn-ghost map-locate" onClick={miUbicacion}>
          Usar mi ubicación
        </button>
      </div>
      {aviso ? (
        <span className="address-note">{aviso}</span>
      ) : (
        <span className="faint" style={{ fontSize: 12 }}>
          {lat != null
            ? 'Puedes arrastrar el pin para ajustar el punto exacto; tu dirección escrita no cambia.'
            : 'Escribe tu calle y número, y elige una sugerencia para ubicarla en el mapa.'}
        </span>
      )}
    </div>
  )
}
