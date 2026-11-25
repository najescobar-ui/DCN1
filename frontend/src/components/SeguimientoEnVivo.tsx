import { useEffect, useState } from 'react'
import { api } from '../api/api'
import type { HitoSeguimiento, SeguimientoPedido } from '../api/models'

const CADA_MS = 3000

const TEXTO: Record<HitoSeguimiento['tipo'], (h: HitoSeguimiento) => string> = {
  PEDIDO_CREADO: () => 'Pedido recibido',
  ESTADO_CAMBIADO: (h) => `Estado: ${h.estado.toLowerCase()}`,
  PAGO_APROBADO: () => 'Pago aprobado',
  PAGO_RECHAZADO: () => 'Pago rechazado',
}

/**
 * Línea de tiempo del pedido armada por el consumer group seguimiento-pedidos a partir del tópico
 * pedidos.actividad. Se consulta cada pocos segundos: cuando el admin cambia el estado, el evento viaja por Kafka
 * y aparece aquí sin recargar. Cada hito muestra la partición y el offset por donde llegó.
 */
export function SeguimientoEnVivo({ pedidoId }: { pedidoId: number }) {
  const [seguimiento, setSeguimiento] = useState<SeguimientoPedido | null>(null)

  useEffect(() => {
    let vigente = true
    const cargar = () => {
      if (document.hidden) return
      // 404 mientras el evento aún no llega: se reintenta en la próxima vuelta sin avisar al usuario.
      api.analitica.seguimientoDe(pedidoId).then(
        (s) => vigente && setSeguimiento(s),
        () => undefined,
      )
    }
    cargar()
    const timer = window.setInterval(cargar, CADA_MS)
    return () => {
      vigente = false
      window.clearInterval(timer)
    }
  }, [pedidoId])

  if (!seguimiento) return null
  return (
    <section className="card card-pad" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div className="stream-panel-head">
        <h2 className="d" style={{ fontSize: 20 }}>
          En vivo
        </h2>
        <span className="faint" style={{ fontSize: 12 }}>
          eventos de Kafka · partición {seguimiento.particion} de pedidos.actividad
        </span>
      </div>
      <ol className="timeline">
        {[...seguimiento.hitos].reverse().map((h) => (
          <li key={`${h.particion}-${h.offset}`}>
            <span className="m faint">{new Date(h.ocurridoEn).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })}</span>
            <span>{TEXTO[h.tipo](h)}</span>
            <span className="m faint" style={{ fontSize: 11 }}>
              offset {h.offset}
            </span>
          </li>
        ))}
      </ol>
    </section>
  )
}
