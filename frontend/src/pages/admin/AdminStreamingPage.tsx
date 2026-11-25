import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { api } from '../../api/api'
import type {
  CleanupPolicy,
  ConsumidorInfo,
  DestinoOffsets,
  EscenarioDemo,
  GrupoDetalle,
  GrupoResumen,
  KafkaCluster,
  TableroVentas,
  TopicoDetalle,
  TopicoResumen,
} from '../../api/models'
import { clp } from '../../utils/format'
import { notify } from '../../utils/notify'
import { alturaBarra, duracion, horaVentana, salud, serieDeVentanas, tamano } from '../../utils/streaming'

/** Cada cuánto se refrescan el tablero, los grupos y el lag. */
const REFRESCO_MS = 3000
const MINUTOS_GRAFICO = 15

/** Eventos de prueba (los publica ms-streaming-admin en pedidos.actividad). */
const PRUEBAS: { escenario: EscenarioDemo; titulo: string; detalle: string; cantidad?: number }[] = [
  {
    escenario: 'VALIDO',
    titulo: 'Pedido completo',
    detalle: 'Creado, pagado y entregado con la misma key: los tres eventos caen en la misma partición, en orden.',
  },
  {
    escenario: 'LOTE',
    titulo: 'Lote de 50 pedidos',
    detalle: '150 eventos repartidos en las 3 particiones según su key. Con un grupo detenido, el lag sube.',
    cantidad: 50,
  },
  {
    escenario: 'INVALIDO',
    titulo: 'Evento ilegible',
    detalle: 'Texto que no es JSON: no se reintenta, va a pedidos.actividad.DLT y la partición sigue avanzando.',
  },
  {
    escenario: 'FALLO_TRANSITORIO',
    titulo: 'Fallo transitorio',
    detalle: 'La analítica falla 3 veces (1 s entre intentos) y lo manda a la DLT. El seguimiento lo procesa igual.',
  },
]

function erroresDe(e: unknown): string | null {
  if (!isAxiosError(e)) return null
  const data = e.response?.data as { detail?: string; errores?: Record<string, string> } | undefined
  if (data?.errores) {
    return Object.entries(data.errores)
      .map(([campo, motivo]) => `${campo}: ${motivo}`)
      .join(' · ')
  }
  return data?.detail ?? null
}

const SALUD_TEXTO = { ok: 'al día', atrasado: 'con lag', detenido: 'sin consumidores' }
const SALUD_CLASE = { ok: 'status-ok', atrasado: 'accent', detenido: 'status-err' }

export function AdminStreamingPage() {
  const [cluster, setCluster] = useState<KafkaCluster | null>(null)
  const [topicos, setTopicos] = useState<TopicoResumen[]>([])
  const [grupos, setGrupos] = useState<GrupoResumen[]>([])
  const [consumidores, setConsumidores] = useState<ConsumidorInfo[]>([])
  const [tablero, setTablero] = useState<TableroVentas | null>(null)
  const [detalleTopico, setDetalleTopico] = useState<TopicoDetalle | null>(null)
  const [detalleGrupo, setDetalleGrupo] = useState<GrupoDetalle | null>(null)
  const [ahora, setAhora] = useState(() => Date.now())

  const refrescar = useCallback(() => {
    api.kafka.topicos().then(setTopicos, () => undefined)
    api.kafka.grupos().then(setGrupos, () => undefined)
    api.analitica.consumidores().then(setConsumidores, () => undefined)
    api.analitica.metricas().then(
      (t) => {
        setTablero(t)
        setAhora(Date.now())
      },
      () => undefined,
    )
  }, [])

  const cargar = useCallback(() => {
    api.kafka.cluster().then(setCluster, () => undefined)
    refrescar()
  }, [refrescar])

  useEffect(() => {
    cargar()
    const timer = window.setInterval(() => {
      if (!document.hidden) refrescar()
    }, REFRESCO_MS)
    return () => window.clearInterval(timer)
  }, [cargar, refrescar])

  // El detalle abierto también se mantiene al día (offsets, lag y miembros cambian solos).
  const topicoAbierto = detalleTopico?.nombre
  const grupoAbierto = detalleGrupo?.id
  useEffect(() => {
    if (!topicoAbierto && !grupoAbierto) return
    const timer = window.setInterval(() => {
      if (document.hidden) return
      if (topicoAbierto) api.kafka.topico(topicoAbierto).then(setDetalleTopico, () => undefined)
      if (grupoAbierto) api.kafka.grupo(grupoAbierto).then(setDetalleGrupo, () => undefined)
    }, REFRESCO_MS)
    return () => window.clearInterval(timer)
  }, [topicoAbierto, grupoAbierto])

  const propios = topicos.filter((t) => !t.interno)
  const lagTotal = grupos.reduce((s, g) => s + g.lagTotal, 0)
  const resumen = tablero?.resumen
  const serie = serieDeVentanas(resumen?.ventanas ?? [], MINUTOS_GRAFICO, ahora)
  const maxPedidos = Math.max(1, ...serie.map((s) => s.pedidos))

  // ---------- Acciones ----------
  const probar = async (p: (typeof PRUEBAS)[number]) => {
    try {
      const publicados = await api.kafka.publicar(p.escenario, p.cantidad)
      const particiones = [...new Set(publicados.map((x) => x.particion))].sort().join(', ')
      notify.ok(`${p.titulo}: ${publicados.length} eventos en pedidos.actividad (partición ${particiones})`)
      window.setTimeout(refrescar, 1200)
    } catch {
      // El interceptor ya mostró el error.
    }
  }

  const cambiarConsumidores = async (c: ConsumidorInfo, n: number) => {
    try {
      await api.analitica.cambiarConsumidores(c.id, n)
      notify.ok(n === 0 ? `Grupo ${c.grupo} detenido: el lag empezará a subir` : `Grupo ${c.grupo} con ${n} consumidores`)
      window.setTimeout(refrescar, 1500)
    } catch {
      // El interceptor ya mostró el error.
    }
  }

  const reset = async (g: GrupoResumen, destino: DestinoOffsets) => {
    const topico = g.topicos[0]
    if (!topico) return
    const texto =
      destino === 'INICIO'
        ? `¿Rebobinar ${g.id} al inicio de ${topico}? Reprocesará todo lo retenido.`
        : `¿Mover ${g.id} al final de ${topico}? Se saltará lo pendiente.`
    if (!window.confirm(texto)) return
    try {
      const r = await api.kafka.resetOffsets(g.id, topico, destino)
      notify.ok(`Offsets de ${r.grupo}: ${r.particiones.map((p) => `p${p.particion} ${p.antes}→${p.despues}`).join(' · ')}`)
      refrescar()
    } catch {
      // El interceptor ya mostró el error (409 si el grupo tiene consumidores activos).
    }
  }

  const verTopico = (nombre: string) => api.kafka.topico(nombre).then(setDetalleTopico, () => undefined)
  const verGrupo = (id: string) => api.kafka.grupo(id).then(setDetalleGrupo, () => undefined)

  const eliminarTopico = async (t: TopicoResumen) => {
    if (!window.confirm(`¿Eliminar el tópico ${t.nombre} y sus ${t.mensajes} eventos?`)) return
    try {
      await api.kafka.eliminarTopico(t.nombre)
      notify.ok(`Tópico ${t.nombre} eliminado`)
      if (detalleTopico?.nombre === t.nombre) setDetalleTopico(null)
      refrescar()
    } catch {
      // El interceptor ya mostró el error (409 para los del sistema).
    }
  }

  // ---------- Formularios ----------
  const [nuevo, setNuevo] = useState({
    nombre: '',
    particiones: '3',
    replicacion: '3',
    retencionHoras: '24',
    cleanupPolicy: 'DELETE' as CleanupPolicy,
  })
  const [errorNuevo, setErrorNuevo] = useState<string | null>(null)
  const crearTopico = async (event: FormEvent) => {
    event.preventDefault()
    setErrorNuevo(null)
    try {
      await api.kafka.crearTopico({
        nombre: nuevo.nombre.trim(),
        particiones: Number(nuevo.particiones),
        replicacion: Number(nuevo.replicacion),
        retencionMs: nuevo.retencionHoras ? Number(nuevo.retencionHoras) * 3_600_000 : null,
        cleanupPolicy: nuevo.cleanupPolicy,
      })
      notify.ok(`Tópico ${nuevo.nombre} creado`)
      setNuevo((n) => ({ ...n, nombre: '' }))
      refrescar()
    } catch (e) {
      setErrorNuevo(erroresDe(e))
    }
  }

  const [cambio, setCambio] = useState({ retencionHoras: '', cleanupPolicy: '' as CleanupPolicy | '', particiones: '' })
  const [errorCambio, setErrorCambio] = useState<string | null>(null)
  const modificar = async (event: FormEvent) => {
    event.preventDefault()
    if (!detalleTopico) return
    setErrorCambio(null)
    try {
      let d = detalleTopico
      if (cambio.retencionHoras || cambio.cleanupPolicy) {
        d = await api.kafka.modificarTopico(d.nombre, {
          retencionMs: cambio.retencionHoras ? Number(cambio.retencionHoras) * 3_600_000 : null,
          cleanupPolicy: cambio.cleanupPolicy || null,
        })
      }
      if (cambio.particiones) {
        d = await api.kafka.aumentarParticiones(d.nombre, Number(cambio.particiones))
      }
      setDetalleTopico(d)
      setCambio({ retencionHoras: '', cleanupPolicy: '', particiones: '' })
      notify.ok(`Tópico ${d.nombre} actualizado`)
      refrescar()
    } catch (e) {
      setErrorCambio(erroresDe(e))
    }
  }

  return (
    <>
      <div className="admin-head">
        <h1 className="d">Streaming</h1>
        <span className="muted">Kafka · se actualiza cada {REFRESCO_MS / 1000} s</span>
      </div>

      <section className="kpis">
        <div className="card kpi">
          <span className="muted">Brokers</span>
          <strong className="accent">{cluster ? `${cluster.brokers.length} / 3` : '—'}</strong>
          <span className="faint">controlador: broker {cluster?.controlador ?? '—'}</span>
        </div>
        <div className="card kpi">
          <span className="muted">Tópicos</span>
          <strong>{propios.length}</strong>
          <span className="faint">
            {propios.every((t) => t.isrCompleto) ? 'todas las réplicas en sincronía' : 'hay réplicas fuera del ISR'}
          </span>
        </div>
        <div className="card kpi">
          <span className="muted">Consumer groups</span>
          <strong>{grupos.length}</strong>
          <span className="faint">{grupos.filter((g) => g.miembros > 0).length} con consumidores activos</span>
        </div>
        <div className="card kpi">
          <span className="muted">Lag total</span>
          <strong className={lagTotal > 0 ? 'accent' : undefined}>{lagTotal}</strong>
          <span className="faint">eventos por procesar</span>
        </div>
      </section>

      <section className="card card-pad stream-panel">
        <div className="stream-panel-head">
          <h2 className="d" style={{ fontSize: 22 }}>
            Ventas en vivo
          </h2>
          <span className="faint" style={{ fontSize: 13 }}>
            ventas.metricas · grupo tablero-ventas ·{' '}
            {resumen?.actualizadoEn ? `última métrica ${new Date(resumen.actualizadoEn).toLocaleTimeString('es-CL')}` : 'sin datos aún'}
          </span>
        </div>
        <div className="stream-cifras">
          <div>
            <span className="muted">Pedidos</span>
            <strong>{resumen?.pedidosCreados ?? 0}</strong>
          </div>
          <div>
            <span className="muted">Pagados</span>
            <strong>{resumen?.pedidosPagados ?? 0}</strong>
          </div>
          <div>
            <span className="muted">Vendido</span>
            <strong className="accent">{clp(resumen?.montoPagado)}</strong>
          </div>
          <div>
            <span className="muted">Ticket promedio</span>
            <strong>{clp(resumen?.ticketPromedio)}</strong>
          </div>
          <div>
            <span className="muted">Cancelados</span>
            <strong>{resumen?.pedidosCancelados ?? 0}</strong>
          </div>
        </div>
        <div className="stream-cols">
          <div>
            <span className="muted" style={{ fontSize: 13 }}>
              Pedidos por minuto (últimos {MINUTOS_GRAFICO})
            </span>
            <div className="barras" role="img" aria-label="Pedidos por minuto">
              {serie.map((s) => (
                <div key={s.inicio} className="barra" title={`${new Date(s.inicio).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' })}: ${s.pedidos} pedidos`}>
                  <span style={{ height: `${alturaBarra(s.pedidos, maxPedidos)}%` }} />
                </div>
              ))}
            </div>
            <div className="barras-eje faint">
              <span>{new Date(serie[0]?.inicio ?? ahora).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' })}</span>
              <span>ahora</span>
            </div>
          </div>
          <div>
            <span className="muted" style={{ fontSize: 13 }}>
              Más pedidos
            </span>
            <ol className="stream-top">
              {(resumen?.topProductos ?? []).map((p) => (
                <li key={p.productoId}>
                  <span>{p.nombre}</span>
                  <strong>{p.unidades} u.</strong>
                </li>
              ))}
              {!resumen?.topProductos.length && <li className="faint">Aún no hay pedidos en la última hora</li>}
            </ol>
          </div>
        </div>
        {resumen && resumen.ventanas.length > 0 && (
          <span className="faint" style={{ fontSize: 12 }}>
            Ventana más reciente: {horaVentana(resumen.ventanas[resumen.ventanas.length - 1])} ·{' '}
            {tablero?.eventosContados ?? 0} eventos contados por analitica-ventas
          </span>
        )}
      </section>

      <section className="card card-pad" style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 24 }}>
        <h2 className="d" style={{ fontSize: 22 }}>
          Probar el streaming
        </h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
          {PRUEBAS.map((p) => (
            <div key={p.escenario} className="card card-pad" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <strong>{p.titulo}</strong>
              <span className="muted" style={{ fontSize: 13, flex: 1 }}>
                {p.detalle}
              </span>
              <button type="button" className="btn btn-sm" onClick={() => probar(p)}>
                Publicar
              </button>
            </div>
          ))}
        </div>
      </section>

      <h2 className="d" style={{ fontSize: 22, margin: '8px 0 12px' }}>
        Consumer groups
      </h2>
      <div className="table-wrap" style={{ marginBottom: 16 }}>
        <table className="table">
          <thead>
            <tr>
              <th>Grupo</th>
              <th>Tópico</th>
              <th>Estado</th>
              <th>Consumidores</th>
              <th>Lag</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {grupos.map((g) => {
              const c = consumidores.find((x) => x.grupo === g.id)
              const s = salud(g.miembros, g.lagTotal)
              return (
                <tr key={g.id}>
                  <td className="m" style={{ fontSize: 13 }}>
                    <button type="button" className="link-btn" onClick={() => verGrupo(g.id)}>
                      {g.id}
                    </button>
                  </td>
                  <td className="m faint" style={{ fontSize: 12 }}>
                    {g.topicos.join(', ')}
                  </td>
                  <td>
                    <span className={SALUD_CLASE[s]}>{SALUD_TEXTO[s]}</span>{' '}
                    <span className="faint" style={{ fontSize: 12 }}>
                      {g.estado}
                    </span>
                  </td>
                  <td>
                    {c ? (
                      <select
                        className="select select-sm"
                        aria-label={`Consumidores de ${g.id}`}
                        value={c.consumidores}
                        onChange={(e) => cambiarConsumidores(c, Number(e.target.value))}
                      >
                        {Array.from({ length: c.maximo + 1 }, (_, n) => (
                          <option key={n} value={n}>
                            {n === 0 ? '0 (detenido)' : n}
                          </option>
                        ))}
                      </select>
                    ) : (
                      g.miembros
                    )}
                  </td>
                  <td>
                    <strong className={g.lagTotal > 0 ? 'accent' : undefined}>{g.lagTotal}</strong>
                  </td>
                  <td>
                    {g.miembros === 0 && g.topicos.length > 0 && (
                      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                        <button type="button" className="btn btn-sm btn-ghost" onClick={() => reset(g, 'INICIO')}>
                          Reprocesar todo
                        </button>
                        <button type="button" className="btn btn-sm btn-ghost" onClick={() => reset(g, 'FIN')}>
                          Saltar al final
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {detalleGrupo && (
        <section className="card card-pad" style={{ marginBottom: 24, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div className="stream-panel-head">
            <h3 className="d" style={{ fontSize: 18 }}>
              {detalleGrupo.id}
            </h3>
            <span className="faint" style={{ fontSize: 13 }}>
              {detalleGrupo.estado} · asignador {detalleGrupo.asignador || '—'} · coordinador broker {detalleGrupo.coordinador ?? '—'}
            </span>
            <button type="button" className="btn btn-sm btn-ghost" onClick={() => setDetalleGrupo(null)}>
              Cerrar
            </button>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Partición</th>
                  <th>Offset confirmado</th>
                  <th>Offset final</th>
                  <th>Lag</th>
                  <th>Consumidor</th>
                </tr>
              </thead>
              <tbody>
                {detalleGrupo.particiones.map((p) => (
                  <tr key={`${p.topico}-${p.particion}`}>
                    <td className="m">
                      {p.topico}-{p.particion}
                    </td>
                    <td>{p.confirmado < 0 ? '—' : p.confirmado}</td>
                    <td>{p.fin}</td>
                    <td>
                      <strong className={p.lag > 0 ? 'accent' : undefined}>{p.lag}</strong>
                    </td>
                    <td className="m faint" style={{ fontSize: 12 }}>
                      {p.miembro || 'sin asignar'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <h2 className="d" style={{ fontSize: 22, margin: '8px 0 12px' }}>
        Tópicos
      </h2>
      <div className="table-wrap" style={{ marginBottom: 16 }}>
        <table className="table">
          <thead>
            <tr>
              <th>Tópico</th>
              <th>Particiones</th>
              <th>Réplicas</th>
              <th>ISR</th>
              <th>Eventos retenidos</th>
              <th>Retención</th>
              <th>Cleanup</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {topicos.map((t) => (
              <tr key={t.nombre} className={t.interno ? 'faint' : undefined}>
                <td className="m" style={{ fontSize: 13 }}>
                  <button type="button" className="link-btn" onClick={() => verTopico(t.nombre)}>
                    {t.nombre}
                  </button>{' '}
                  {t.protegido && !t.interno && <span className="badge">sistema</span>}
                  {t.interno && <span className="badge">interno</span>}
                </td>
                <td>{t.particiones}</td>
                <td>{t.replicacion}</td>
                <td>
                  <span className={t.isrCompleto ? 'status-ok' : 'status-err'}>{t.isrCompleto ? 'completo' : 'incompleto'}</span>
                </td>
                <td>{t.mensajes}</td>
                <td>{duracion(t.retencionMs)}</td>
                <td className="m" style={{ fontSize: 12 }}>
                  {t.cleanupPolicy}
                </td>
                <td>
                  {!t.protegido && (
                    <button type="button" className="btn btn-sm btn-danger" onClick={() => eliminarTopico(t)}>
                      Eliminar
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {detalleTopico && (
        <section className="card card-pad" style={{ marginBottom: 24, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div className="stream-panel-head">
            <h3 className="d" style={{ fontSize: 18 }}>
              {detalleTopico.nombre}
            </h3>
            <span className="faint" style={{ fontSize: 13 }}>
              {detalleTopico.particiones.length} particiones · factor de replicación {detalleTopico.replicacion} · retención{' '}
              {duracion(detalleTopico.configuracion['retention.ms'])} o {tamano(detalleTopico.configuracion['retention.bytes'])} por
              partición · min.insync.replicas {detalleTopico.configuracion['min.insync.replicas']}
            </span>
            <button type="button" className="btn btn-sm btn-ghost" onClick={() => setDetalleTopico(null)}>
              Cerrar
            </button>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Partición</th>
                  <th>Líder</th>
                  <th>Réplicas</th>
                  <th>ISR</th>
                  <th>Offset inicial</th>
                  <th>Offset final</th>
                  <th>Eventos</th>
                </tr>
              </thead>
              <tbody>
                {detalleTopico.particiones.map((p) => (
                  <tr key={p.particion}>
                    <td className="m">{p.particion}</td>
                    <td>
                      <strong className={p.lider < 0 ? 'status-err' : 'accent'}>{p.lider < 0 ? 'sin líder' : `broker ${p.lider}`}</strong>
                    </td>
                    <td className="m">{p.replicas.join(', ')}</td>
                    <td className="m">
                      <span className={p.isr.length === p.replicas.length ? 'status-ok' : 'status-err'}>{p.isr.join(', ')}</span>
                    </td>
                    <td>{p.offsetInicio}</td>
                    <td>{p.offsetFin}</td>
                    <td>{p.mensajes}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!detalleTopico.interno && (
            <form onSubmit={modificar} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div className="form-grid">
                <label className="field">
                  Retención (horas)
                  <input
                    className="input"
                    inputMode="numeric"
                    value={cambio.retencionHoras}
                    placeholder={String(Number(detalleTopico.configuracion['retention.ms'] ?? 0) / 3_600_000)}
                    onChange={(e) => setCambio({ ...cambio, retencionHoras: e.target.value })}
                  />
                </label>
                <label className="field">
                  Cleanup policy
                  <select
                    className="select"
                    value={cambio.cleanupPolicy}
                    onChange={(e) => setCambio({ ...cambio, cleanupPolicy: e.target.value as CleanupPolicy | '' })}
                  >
                    <option value="">sin cambio ({detalleTopico.configuracion['cleanup.policy']})</option>
                    <option value="DELETE">delete</option>
                    <option value="COMPACT">compact</option>
                    <option value="COMPACT_DELETE">compact,delete</option>
                  </select>
                </label>
                <label className="field">
                  Particiones (solo aumentar)
                  <input
                    className="input"
                    inputMode="numeric"
                    value={cambio.particiones}
                    placeholder={String(detalleTopico.particiones.length)}
                    onChange={(e) => setCambio({ ...cambio, particiones: e.target.value })}
                  />
                </label>
              </div>
              {errorCambio && <span className="error-text">{errorCambio}</span>}
              <button type="submit" className="btn btn-sm" style={{ alignSelf: 'flex-start' }}>
                Aplicar cambios
              </button>
            </form>
          )}
        </section>
      )}

      <form className="card card-pad" onSubmit={crearTopico} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 24 }}>
        <h3 className="d" style={{ fontSize: 18 }}>
          Nuevo tópico
        </h3>
        <div className="form-grid">
          <label className="field">
            Nombre
            <input className="input" value={nuevo.nombre} placeholder="pruebas.demo" onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })} />
          </label>
          <label className="field">
            Particiones
            <input className="input" inputMode="numeric" value={nuevo.particiones} onChange={(e) => setNuevo({ ...nuevo, particiones: e.target.value })} />
          </label>
          <label className="field">
            Réplicas
            <select className="select" value={nuevo.replicacion} onChange={(e) => setNuevo({ ...nuevo, replicacion: e.target.value })}>
              <option value="1">1</option>
              <option value="2">2</option>
              <option value="3">3</option>
            </select>
          </label>
          <label className="field">
            Retención (horas)
            <input className="input" inputMode="numeric" value={nuevo.retencionHoras} onChange={(e) => setNuevo({ ...nuevo, retencionHoras: e.target.value })} />
          </label>
          <label className="field">
            Cleanup policy
            <select className="select" value={nuevo.cleanupPolicy} onChange={(e) => setNuevo({ ...nuevo, cleanupPolicy: e.target.value as CleanupPolicy })}>
              <option value="DELETE">delete</option>
              <option value="COMPACT">compact</option>
              <option value="COMPACT_DELETE">compact,delete</option>
            </select>
          </label>
        </div>
        {errorNuevo && <span className="error-text">{errorNuevo}</span>}
        <button type="submit" className="btn btn-sm" style={{ alignSelf: 'flex-start' }}>
          Crear tópico
        </button>
      </form>

      {cluster && (
        <p className="faint" style={{ fontSize: 12 }}>
          Clúster {cluster.clusterId} · brokers {cluster.brokers.map((b) => `${b.id} (${b.host}:${b.puerto})`).join(' · ')}
        </p>
      )}
    </>
  )
}
