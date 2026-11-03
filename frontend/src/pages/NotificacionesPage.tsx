import { useEffect, useState } from 'react'
import { api } from '../api/api'
import type { Comprobante, Notificacion } from '../api/models'
import { clp, fecha } from '../utils/format'

/** Cada cuánto se consultan avisos nuevos (los genera ms-notificaciones al consumir de RabbitMQ). */
const REFRESCO_MS = 15000

/** Avisos y comprobantes que los consumidores de RabbitMQ generaron para el cliente. */
export function NotificacionesPage() {
  const [notificaciones, setNotificaciones] = useState<Notificacion[]>([])
  const [comprobantes, setComprobantes] = useState<Comprobante[]>([])

  useEffect(() => {
    const cargar = () => {
      api.notificaciones().then(setNotificaciones, () => undefined)
      api.comprobantes().then(setComprobantes, () => undefined)
    }
    cargar()
    const timer = window.setInterval(cargar, REFRESCO_MS)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <div className="container" style={{ padding: '32px 16px', display: 'flex', flexDirection: 'column', gap: 24, maxWidth: 860 }}>
      <h1 className="d">Notificaciones</h1>
      <section className="card card-pad" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {notificaciones.length === 0 && <p className="muted">Aún no tienes avisos. Llegan aquí con cada avance de tu pedido.</p>}
        {notificaciones.map((n) => (
          <div key={n.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 16, borderBottom: '1px solid var(--line, #eee)', paddingBottom: 10 }}>
            <span>{n.mensaje}</span>
            <span className="faint" style={{ fontSize: 13, whiteSpace: 'nowrap' }}>{fecha(n.creadaEn)}</span>
          </div>
        ))}
      </section>
      <h2 className="d" style={{ fontSize: 22 }}>Comprobantes</h2>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr><th>Folio</th><th>Pedido</th><th>Medio de pago</th><th>Total</th><th>Emitido</th></tr>
          </thead>
          <tbody>
            {comprobantes.map((c) => (
              <tr key={c.id}>
                <td className="m">{c.folio}</td>
                <td className="m">#P360-{String(c.pedidoId).padStart(5, '0')}</td>
                <td>{c.metodoPago}</td>
                <td>{clp(c.total)}</td>
                <td className="muted">{fecha(c.emitidoEn)}</td>
              </tr>
            ))}
            {comprobantes.length === 0 && (
              <tr><td colSpan={5} className="muted">Los comprobantes se emiten cuando un pedido queda pagado.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
