import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/api'
import type { Resumen } from '../api/models'
import { useSession } from '../auth/useSession'
import { clp, fecha } from '../utils/format'

export function DashboardPage() {
  const session = useSession()
  const [resumen, setResumen] = useState<Resumen | null | undefined>(undefined)

  useEffect(() => {
    api.resumen().then(setResumen, () => setResumen(null))
  }, [])

  if (resumen === undefined) {
    return <p className="muted">Cargando...</p>
  }
  if (resumen === null) {
    return <p className="error">No se pudo cargar el resumen.</p>
  }
  return (
    <>
      <h1>Hola, {session.username}</h1>
      <section className="grid">
        <div className="card">
          <div className="muted">Productos disponibles</div>
          <div className="stat">{resumen.productosDisponibles}</div>
        </div>
        <div className="card">
          <div className="muted">{session.isAdmin ? 'Pedidos totales' : 'Mis pedidos'}</div>
          <div className="stat">{resumen.totalPedidos}</div>
        </div>
        <div className="card">
          <div className="muted">Monto (sin cancelados)</div>
          <div className="stat">{clp(resumen.montoTotal)}</div>
        </div>
      </section>

      <section className="grid">
        <div className="card">
          <h2>Pedidos por estado</h2>
          {Object.entries(resumen.pedidosPorEstado).map(([estado, cantidad]) => (
            <div className="actions" key={estado}>
              <span className={`badge ${estado}`}>{estado}</span> {cantidad}
            </div>
          ))}
          {Object.keys(resumen.pedidosPorEstado).length === 0 && <p className="muted">Aun no hay pedidos.</p>}
        </div>
        <div className="card">
          <h2>Ultimos pedidos</h2>
          {resumen.ultimosPedidos.map((p) => (
            <div className="actions" key={p.id}>
              #{p.id} <span className={`badge ${p.estado}`}>{p.estado}</span> {clp(p.total)}
              <span className="muted">{fecha(p.creadoEn)}</span>
            </div>
          ))}
          {resumen.ultimosPedidos.length === 0 && <p className="muted">Sin pedidos recientes.</p>}
          <p>
            <Link to="/pedidos/nuevo">Hacer un pedido</Link>
          </p>
        </div>
      </section>

      <section className="card">
        <h2>Sesion (segun el BFF)</h2>
        <p className="muted">Roles y scopes que el backend leyo desde el access token validado.</p>
        <div>
          {resumen.usuario.roles.map((role) => (
            <span className="chip" key={role}>
              ROLE {role}
            </span>
          ))}
          {resumen.usuario.scopes.map((scope) => (
            <span className="chip" key={scope}>
              {scope}
            </span>
          ))}
        </div>
        <p className="muted">El token expira: {fecha(resumen.usuario.expiraEn)}</p>
      </section>
    </>
  )
}
