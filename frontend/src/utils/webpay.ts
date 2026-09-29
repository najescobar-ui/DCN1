/** Webpay Plus expects the browser to POST token_ws to the transaction URL. */
export function irAWebpay(url: string, token: string): void {
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = url
  const input = document.createElement('input')
  input.type = 'hidden'
  input.name = 'token_ws'
  input.value = token
  form.appendChild(input)
  document.body.appendChild(form)
  form.submit()
}
