import { Link } from 'react-router'

export function Brand({ to = '/catalogo', large = false }: { to?: string; large?: boolean }) {
  return (
    <Link to={to} className={`brand ${large ? 'brand-lg' : ''}`} aria-label="Pedidos360, inicio">
      <span className="brand-mark d">P</span>
      <span className="brand-name d">
        Pedidos<span className="accent">360</span>
      </span>
    </Link>
  )
}
