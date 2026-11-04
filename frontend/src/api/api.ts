import { http } from './http'
import type {
  BindingInfo,
  BindingRequest,
  ClusterInfo,
  ColaInfo,
  Comprobante,
  CrearColaRequest,
  CrearPedidoRequest,
  EstadoPago,
  EstadoPedido,
  ExchangeInfo,
  MensajeFallido,
  Notificacion,
  Pedido,
  Producto,
  ProductoRequest,
  PublicarMensajeRequest,
  RegistroAuditoria,
  Resumen,
  TipoExchange,
  Usuario,
} from './models'

/** All calls go to API Gateway; the request interceptor adds the access token. */
export const api = {
  resumen: () => http.get<Resumen>('/bff/resumen').then((r) => r.data),
  me: () => http.get<Usuario>('/bff/me').then((r) => r.data),

  productos: () => http.get<Producto[]>('/productos').then((r) => r.data),
  crearProducto: (body: ProductoRequest) => http.post<Producto>('/productos', body).then((r) => r.data),
  actualizarProducto: (id: number, body: ProductoRequest) =>
    http.put<Producto>(`/productos/${id}`, body).then((r) => r.data),
  eliminarProducto: (id: number) => http.delete<void>(`/productos/${id}`),

  pedidos: () => http.get<Pedido[]>('/pedidos').then((r) => r.data),
  crearPedido: (body: CrearPedidoRequest) => http.post<Pedido>('/pedidos', body).then((r) => r.data),
  cambiarEstado: (id: number, estado: EstadoPedido) =>
    http.patch<Pedido>(`/pedidos/${id}/estado`, { estado }).then((r) => r.data),
  cancelarPedido: (id: number) => http.post<Pedido>(`/pedidos/${id}/cancelar`).then((r) => r.data),
  cambiarEstadoPago: (id: number, estadoPago: EstadoPago) =>
    http.patch<Pedido>(`/pedidos/${id}/pago`, { estadoPago }).then((r) => r.data),
  iniciarWebpay: (id: number) =>
    http
      .post<{ token: string; url: string }>(`/pedidos/${id}/pago/webpay`, { origen: window.location.origin })
      .then((r) => r.data),

  // Resultados de los consumidores RabbitMQ (ms-notificaciones).
  notificaciones: () => http.get<Notificacion[]>('/notificaciones').then((r) => r.data),
  comprobantes: () => http.get<Comprobante[]>('/comprobantes').then((r) => r.data),
  auditoria: () => http.get<RegistroAuditoria[]>('/auditoria').then((r) => r.data),
  // Mensajes no entregados registrados por los listeners de las DLQ (solo ADMIN, scope mensajeria.admin).
  fallidos: () => http.get<MensajeFallido[]>('/mensajes-fallidos').then((r) => r.data),
  reprocesarFallido: (id: number) =>
    http.post<MensajeFallido>(`/mensajes-fallidos/${id}/reprocesar`).then((r) => r.data),

  // Administración de RabbitMQ (ms-mensajeria-admin). Requiere scope mensajeria.admin y rol ADMIN.
  mq: {
    cluster: () => http.get<ClusterInfo>('/mensajeria/cluster').then((r) => r.data),
    colas: () => http.get<ColaInfo[]>('/mensajeria/queues').then((r) => r.data),
    crearCola: (body: CrearColaRequest) => http.post<ColaInfo>('/mensajeria/queues', body).then((r) => r.data),
    eliminarCola: (nombre: string, soloSiVacia = true) =>
      http.delete<void>(`/mensajeria/queues/${encodeURIComponent(nombre)}`, { params: { soloSiVacia } }),
    purgarCola: (nombre: string) =>
      http.delete<{ descartados: number }>(`/mensajeria/queues/${encodeURIComponent(nombre)}/messages`).then((r) => r.data),
    reprocesarDlq: (nombre: string) =>
      http
        .post<{ reprocesados: number; sinDestino: number }>(`/mensajeria/queues/${encodeURIComponent(nombre)}/reprocess`)
        .then((r) => r.data),
    exchanges: () => http.get<ExchangeInfo[]>('/mensajeria/exchanges').then((r) => r.data),
    crearExchange: (nombre: string, tipo: TipoExchange) =>
      http.post<ExchangeInfo>('/mensajeria/exchanges', { nombre, tipo }).then((r) => r.data),
    eliminarExchange: (nombre: string) => http.delete<void>(`/mensajeria/exchanges/${encodeURIComponent(nombre)}`),
    bindings: () => http.get<BindingInfo[]>('/mensajeria/bindings').then((r) => r.data),
    crearBinding: (body: BindingRequest) => http.post<BindingInfo>('/mensajeria/bindings', body).then((r) => r.data),
    eliminarBinding: (body: BindingRequest) => http.delete<void>('/mensajeria/bindings', { data: body }),
    publicar: (body: PublicarMensajeRequest) =>
      http.post<{ messageId: string }>('/mensajeria/messages', body).then((r) => r.data),
  },
}
