import { Link } from 'react-router'
import { useSession } from '../auth/useSession'

export function HomePage() {
  const session = useSession()
  return (
    <>
      <section className="card hero">
        <h1>Pedidos360</h1>
        <p className="muted">
          Haz tus pedidos en linea y sigue su estado en tiempo real. Inicia sesion con tu cuenta o registrate.
        </p>
        <div className="actions">
          {session.isAuthenticated ? (
            <Link className="btn" to="/dashboard">
              Ir a mi panel
            </Link>
          ) : (
            <>
              <button className="btn" type="button" onClick={() => session.login()}>
                Iniciar sesion
              </button>
              <button className="btn secondary" type="button" onClick={session.register}>
                Crear cuenta
              </button>
            </>
          )}
        </div>
      </section>
      <section className="grid">
        <div className="card">
          <h2>Autenticacion</h2>
          <p className="muted">Amazon Cognito con OpenID Connect, flujo Authorization Code + PKCE.</p>
        </div>
        <div className="card">
          <h2>API protegida</h2>
          <p className="muted">AWS API Gateway valida el JWT antes de llegar a los microservicios.</p>
        </div>
        <div className="card">
          <h2>Microservicios</h2>
          <p className="muted">Spring Boot en EC2: productos, pedidos y un BFF, sobre PostgreSQL en RDS.</p>
        </div>
      </section>
    </>
  )
}
