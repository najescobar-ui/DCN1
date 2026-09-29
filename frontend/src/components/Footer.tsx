import { EMPRESA, whatsappLink } from '../config/empresa'
import { Brand } from './Brand'
import { FacebookIcon, InstagramIcon, WhatsappIcon } from './icons'

const YEAR = new Date().getFullYear()

export function SocialLinks() {
  return (
    <div className="social" aria-label="Redes sociales">
      <a href={EMPRESA.redes.instagram} target="_blank" rel="noreferrer" aria-label="Instagram de Pedidos360">
        <InstagramIcon />
      </a>
      <a href={EMPRESA.redes.facebook} target="_blank" rel="noreferrer" aria-label="Facebook de Pedidos360">
        <FacebookIcon />
      </a>
      <a href={whatsappLink('Hola Pedidos360, tengo una consulta.')} target="_blank" rel="noreferrer" aria-label="WhatsApp de Pedidos360">
        <WhatsappIcon />
      </a>
    </div>
  )
}

export function Footer() {
  return (
    <footer className="site-footer">
      <div className="container site-footer-inner">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <Brand />
          <span className="muted" style={{ fontSize: 14 }}>
            Pide. Rastrea. Recibe.
          </span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'flex-end' }}>
          <span className="muted" style={{ fontSize: 13 }}>
            Síguenos
          </span>
          <SocialLinks />
        </div>
      </div>
      <div className="container faint site-footer-legal">
        © {YEAR} {EMPRESA.nombre} · Proyecto académico, datos de contacto ficticios.
      </div>
    </footer>
  )
}
