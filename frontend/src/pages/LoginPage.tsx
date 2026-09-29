import { Navigate } from 'react-router'
import { Brand } from '../components/Brand'
import { LoginIcon, ShieldIcon } from '../components/icons'
import { useSession } from '../auth/useSession'

export function LoginPage() {
  const session = useSession()

  if (session.isAuthenticated) {
    return <Navigate to={session.isAdmin ? '/admin' : '/catalogo'} replace />
  }

  return (
    <div className="login">
      <section className="login-art">
        <div className="saw" style={{ top: 0, zIndex: 3 }} />
        <img
          src="/img/pudu/pudu-tostado.jpg"
          alt="Mascota de Pedidos360: un pudú con gorro y bufanda esperando pan tostado con palta"
        />
        <div className="login-brand">
          <Brand to="/" large />
        </div>
        <div className="login-slogan">
          <h1 className="d">
            Pide. <span className="accent">Rastrea.</span>
            <br />
            Recibe.
          </h1>
          <p>Tus pedidos, de la cocina a tu puerta, con seguimiento en tiempo real.</p>
        </div>
      </section>

      <section className="login-panel">
        <div className="login-form">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <h2 className="d">Inicia sesión</h2>
            <p className="muted" style={{ margin: 0 }}>
              Entra con tu cuenta para hacer y seguir tus pedidos.
            </p>
          </div>
          <button className="btn btn-lg" type="button" onClick={() => session.login()} disabled={session.isLoading}>
            <LoginIcon />
            Iniciar sesión
          </button>
          <div className="divider">¿Primera vez?</div>
          <button className="btn btn-lg btn-outline" type="button" onClick={session.register} disabled={session.isLoading}>
            Crear cuenta
          </button>
          <div className="secure-note">
            <ShieldIcon className="accent" style={{ flexShrink: 0 }} />
            <span>
              <strong>Inicio de sesión seguro</strong>
              <small>Te autenticas en Amazon Cognito con OpenID Connect (Authorization Code + PKCE).</small>
            </span>
          </div>
        </div>
      </section>
    </div>
  )
}
