import { Link } from 'react-router'

export function NoAutorizadoPage() {
  return (
    <div className="card">
      <h1>Acceso denegado</h1>
      <p className="muted">Tu rol no tiene permiso para ver esta seccion.</p>
      <Link className="btn" to="/dashboard">
        Volver al inicio
      </Link>
    </div>
  )
}
