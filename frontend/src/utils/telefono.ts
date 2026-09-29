/** Chilean mobile: 9 digits starting with 9, optionally with +56. */
export function telefonoValido(value: string): boolean {
  return /^(\+?56)?9\d{8}$/.test(value.replace(/[\s-]/g, ''))
}

export function formatearTelefono(value: string): string {
  const d = value.replace(/\D/g, '').replace(/^56/, '')
  return d.length === 9 ? `+56 ${d[0]} ${d.slice(1, 5)} ${d.slice(5)}` : value
}
