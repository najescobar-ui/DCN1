/**
 * Company contact and payment data shown in the store.
 * Company, bank and social data are FICTITIOUS demo values;the WhatsApp link opens the share screen (no number).
 */
export const EMPRESA = {
  nombre: 'Pedidos360 SpA',
  rut: '76.360.360-0',
  email: 'pagos@pedidos360.cl',
  banco: 'Banco de Chile',
  tipoCuenta: 'Cuenta Corriente',
  numeroCuenta: '00-360-36036-00',
  /** WhatsApp in international format without "+", used for wa.me links. */
  whatsapp: '',
  redes: {
    instagram: 'https://www.instagram.com/',
    facebook: 'https://www.facebook.com/',
  },
}

export const whatsappLink = (mensaje: string) =>
  `https://wa.me/${EMPRESA.whatsapp}?text=${encodeURIComponent(mensaje)}`

/** Transbank integration (sandbox) test card, shown to the user so the demo can be paid. */
export const TARJETA_PRUEBA = {
  numero: '4051 8856 0044 6623',
  cvv: '123',
  vencimiento: 'cualquier fecha futura',
  rut: '11.111.111-1',
  clave: '123',
}
