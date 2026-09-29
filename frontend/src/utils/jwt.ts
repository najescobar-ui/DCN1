/** Change one character in the middle of the signature: same claims, invalid signature.
 * (Not the last one: its low bits are base64url padding and may decode to the same bytes.) */
export function alterarFirma(token: string): string {
  const [header, payload, firma = ''] = token.split('.')
  const i = Math.floor(firma.length / 2)
  const nuevo = firma[i] === 'A' ? 'B' : 'A'
  return [header, payload, firma.slice(0, i) + nuevo + firma.slice(i + 1)].join('.')
}
