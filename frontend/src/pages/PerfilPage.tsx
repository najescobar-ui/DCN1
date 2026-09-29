import { useEffect, useState } from 'react'
import { api } from '../api/api'
import type { Usuario } from '../api/models'
import { useSession } from '../auth/useSession'
import { config } from '../config'
import { fecha } from '../utils/format'
import { alterarFirma } from '../utils/jwt'

interface Prueba {
  titulo: string
  status: number
  body: unknown
}

const ME_URL = `${config.apiUrl}/api/bff/me`

/** Shows the tokens and compares calls with a valid token, without token and with a tampered one. */
export function PerfilPage() {
  const session = useSession()
  const [me, setMe] = useState<Usuario | null>(null)
  const [prueba, setPrueba] = useState<Prueba | null>(null)

  useEffect(() => {
    api.me().then(setMe, () => undefined)
  }, [])

  // Plain fetch (no axios interceptor), so each request carries exactly the headers set here.
  const llamar = async (titulo: string, headers: Record<string, string>) => {
    try {
      const res = await fetch(ME_URL, { headers })
      const text = await res.text()
      let body: unknown = text
      try {
        body = JSON.parse(text)
      } catch {
        // Keep the raw text.
      }
      setPrueba({ titulo, status: res.status, body })
    } catch (error) {
      setPrueba({ titulo, status: 0, body: String(error) })
    }
  }

  const token = session.accessToken ?? ''
  const alterado = alterarFirma(token)

  return (
    <div className="container page">
      <h1 className="d page-title">Mi sesión y tokens</h1>
      <section className="two-cols">
        <div className="card card-pad" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h2 className="d" style={{ fontSize: 24 }}>
            Lo que valida el backend
          </h2>
          {me ? (
            <>
              <p style={{ margin: 0 }}>
                <strong>{me.username}</strong> <span className="m faint" style={{ fontSize: 12 }}>({me.sub})</span>
              </p>
              <p className="muted" style={{ margin: 0, fontSize: 14 }}>
                Emisor: <span className="m">{me.issuer}</span>
                <br />
                Client ID: <span className="m">{me.clientId}</span>
              </p>
              <div>
                {me.roles.map((role) => (
                  <span className="role-tag" key={role} style={{ marginRight: 6 }}>
                    ROLE {role}
                  </span>
                ))}
              </div>
              <div>
                {me.scopes.map((scope) => (
                  <span className="chip" key={scope}>
                    {scope}
                  </span>
                ))}
              </div>
              <p className="muted" style={{ margin: 0, fontSize: 14 }}>
                Emitido {fecha(me.emitidoEn)} · expira {fecha(me.expiraEn)}
              </p>
            </>
          ) : (
            <p className="muted">Cargando...</p>
          )}
        </div>

        <div className="card card-pad" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h2 className="d" style={{ fontSize: 24 }}>
            Probar la validación del JWT
          </h2>
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>
            Llamadas directas a <code className="m">/api/bff/me</code> por API Gateway, sin pasar por el interceptor.
          </p>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button className="btn btn-sm" type="button" onClick={() => llamar('Token válido', { Authorization: `Bearer ${token}` })}>
              Con token válido
            </button>
            <button className="btn btn-sm btn-ghost" type="button" onClick={() => llamar('Sin header Authorization', {})}>
              Sin token
            </button>
            <button className="btn btn-sm btn-danger" type="button" onClick={() => llamar('Token con firma alterada', { Authorization: `Bearer ${alterado}` })}>
              Token alterado
            </button>
          </div>
          {prueba && (
            <>
              <p style={{ margin: 0 }}>
                <strong>{prueba.titulo}</strong> → HTTP{' '}
                <span className={`m ${prueba.status >= 400 || prueba.status === 0 ? 'status-err' : 'status-ok'}`}>{prueba.status}</span>
              </p>
              <pre className="code">{JSON.stringify(prueba.body, null, 2)}</pre>
            </>
          )}
        </div>
      </section>

      <section className="two-cols">
        <div className="card card-pad" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h2 className="d" style={{ fontSize: 24 }}>
            Access token
          </h2>
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>
            Se envía como Bearer a API Gateway. Expira: {session.claims?.exp ? fecha(session.claims.exp * 1000) : ''}
          </p>
          <pre className="code">{JSON.stringify(session.claims, null, 2)}</pre>
        </div>
        <div className="card card-pad" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h2 className="d" style={{ fontSize: 24 }}>
            ID token
          </h2>
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>
            Identifica al usuario ante el frontend (OpenID Connect).
          </p>
          <pre className="code">{JSON.stringify(session.idClaims, null, 2)}</pre>
        </div>
      </section>
    </div>
  )
}
