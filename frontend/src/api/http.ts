import axios, { AxiosError } from 'axios'
import { userManager } from '../auth/oidc'
import { config } from '../config'
import { notify } from '../utils/notify'

const MESSAGES: Record<number, string> = {
  0: 'No se pudo contactar la API (red o CORS)',
  401: 'Sesion expirada o token invalido (401)',
  403: 'No tienes permisos para esta accion (403)',
  404: 'Recurso no encontrado (404)',
  502: 'Un microservicio no respondio (502)',
}

export const http = axios.create({ baseURL: `${config.apiUrl}/api` })

/** Request interceptor: attaches the Bearer access token to every call to our API. */
http.interceptors.request.use(async (request) => {
  const user = await userManager.getUser()
  if (user?.access_token && !user.expired) {
    request.headers.Authorization = `Bearer ${user.access_token}`
  }
  return request
})

/** Response interceptor: shows the backend error (problem+json "detail" when available). */
http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ detail?: string }>) => {
    const status = error.response?.status ?? 0
    notify.error(error.response?.data?.detail ?? MESSAGES[status] ?? `Error ${status}`)
    return Promise.reject(error)
  },
)
