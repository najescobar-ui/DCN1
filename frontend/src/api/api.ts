import { http } from './http'
import type { CrearPedidoRequest, EstadoPedido, Pedido, Producto, ProductoRequest, Resumen, Usuario } from './models'

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
}
