import { EMPRESA, TARJETA_PRUEBA, whatsappLink } from '../config/empresa'
import { clp } from '../utils/format'
import { WhatsappIcon } from './icons'

/** Fictitious company bank details plus a WhatsApp button to send the receipt. */
export function DatosTransferencia({ codigo, total }: { codigo?: string; total?: number }) {
  const mensaje = codigo
    ? `Hola Pedidos360, envío el comprobante de transferencia del pedido ${codigo} por ${clp(total)}.`
    : 'Hola Pedidos360, quiero enviar el comprobante de mi transferencia.'
  return (
    <div className="pay-details">
      <dl className="bank">
        <dt>Titular</dt>
        <dd>{EMPRESA.nombre}</dd>
        <dt>RUT</dt>
        <dd>{EMPRESA.rut}</dd>
        <dt>Banco</dt>
        <dd>{EMPRESA.banco}</dd>
        <dt>{EMPRESA.tipoCuenta}</dt>
        <dd className="m">{EMPRESA.numeroCuenta}</dd>
        <dt>Correo</dt>
        <dd>{EMPRESA.email}</dd>
        {total != null && (
          <>
            <dt>Monto</dt>
            <dd>
              <strong>{clp(total)}</strong>
            </dd>
          </>
        )}
      </dl>
      <span className="faint" style={{ fontSize: 12 }}>
        Datos ficticios de demostración.
      </span>
      {codigo && (
        <a className="btn btn-whatsapp" href={whatsappLink(mensaje)} target="_blank" rel="noreferrer">
          <WhatsappIcon size={18} />
          Enviar comprobante por WhatsApp
        </a>
      )}
    </div>
  )
}

export function DatosWebpay() {
  return (
    <div className="pay-details">
      <p style={{ margin: 0 }}>
        Al confirmar te llevamos a <strong>Webpay</strong> de Transbank para pagar con débito o crédito. Tu tarjeta nunca pasa
        por Pedidos360.
      </p>
      <div className="test-card">
        <span className="m accent" style={{ fontSize: 11, letterSpacing: '.08em' }}>
          AMBIENTE DE PRUEBAS · TARJETA DE PRUEBA
        </span>
        <span className="m">{TARJETA_PRUEBA.numero}</span>
        <span className="muted" style={{ fontSize: 13 }}>
          CVV {TARJETA_PRUEBA.cvv} · vencimiento {TARJETA_PRUEBA.vencimiento} · RUT {TARJETA_PRUEBA.rut} · clave{' '}
          {TARJETA_PRUEBA.clave}
        </span>
      </div>
    </div>
  )
}
