import { Link } from 'react-router'
import { ShieldIcon } from '../components/icons'

export function NoAutorizadoPage() {
  return (
    <div className="container page">
      <div className="card empty">
        <ShieldIcon size={40} className="accent" />
        <h1 className="d page-title">Acceso denegado</h1>
        <p className="muted" style={{ margin: 0 }}>
          Tu rol no tiene permiso para ver esta sección (403).
        </p>
        <Link className="btn" to="/catalogo">
          Volver al catálogo
        </Link>
      </div>
    </div>
  )
}
