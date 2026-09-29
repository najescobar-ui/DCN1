import { useEffect, useState } from 'react'
import { api } from '../api/api'
import type { Usuario } from '../api/models'
import { useSession } from '../auth/useSession'
import { config } from '../config'
import { fecha } from '../utils/format'

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
  // Flip the last signature character: same claims, invalid signature.
  const alterado = token.slice(0, -1) + (token.endsWith('A') ? 'B' : 'A')

  return (
    <>
      <h1>Mi sesion y tokens</h1>
      <section className="grid">
        <div className="card">
          <h2>Lo que valida el backend</h2>
          {me ? (
            <>
              <p>
                <strong>{me.username}</strong> <span className="muted">({me.sub})</span>
              </p>
              <p className="muted">
                Emisor: {me.issuer}
                <br />
                Client ID: {me.clientId}
              </p>
              <div>
                {me.roles.map((role) => (
                  <span className="chip" key={role}>
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
              <p className="muted">
                Emitido {fecha(me.emitidoEn)} · expira {fecha(me.expiraEn)}
              </p>
            </>
          ) : (
            <p className="muted">Cargando...</p>
          )}
        </div>

        <div className="card">
          <h2>Probar la validacion del JWT</h2>
          <p className="muted">
            Llamadas directas a <code>/api/bff/me</code> sin pasar por el interceptor.
          </p>
          <div className="actions">
            <button className="btn small" type="button" onClick={() => llamar('Token valido', { Authorization: `Bearer ${token}` })}>
              Con token valido
            </button>
            <button className="btn small secondary" type="button" onClick={() => llamar('Sin Authorization header', {})}>
              Sin token
            </button>
            <button className="btn small danger" type="button" onClick={() => llamar('Token con firma alterada', { Authorization: `Bearer ${alterado}` })}>
              Token alterado
            </button>
          </div>
          {prueba && (
            <>
              <p>
                <strong>{prueba.titulo}</strong> → HTTP{' '}
                <span className={prueba.status >= 400 ? 'error' : ''}>{prueba.status}</span>
              </p>
              <pre>{JSON.stringify(prueba.body, null, 2)}</pre>
            </>
          )}
        </div>
      </section>

      <section className="grid">
        <div className="card">
          <h2>Access token (claims)</h2>
          <p className="muted">Expira: {session.claims?.exp ? fecha(session.claims.exp * 1000) : ''}</p>
          <pre>{JSON.stringify(session.claims, null, 2)}</pre>
        </div>
        <div className="card">
          <h2>ID token (claims)</h2>
          <pre>{JSON.stringify(session.idClaims, null, 2)}</pre>
        </div>
      </section>
    </>
  )
}
