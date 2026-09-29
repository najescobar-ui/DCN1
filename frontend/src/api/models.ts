export interface Producto {
  id: number
  nombre: string
  descripcion?: string
  categoria?: string
  precio: number
  precioAnterior?: number | null
  stock: number
  imagenUrl?: string | null
  creadoEn?: string
}

export type ProductoRequest = Omit<Producto, 'id' | 'creadoEn'>

export const ESTADOS = ['PENDIENTE', 'CONFIRMADO', 'DESPACHADO', 'ENTREGADO', 'CANCELADO'] as const
export type EstadoPedido = (typeof ESTADOS)[number]

/** Customer-facing names for the backend order states. */
export const ESTADO_LABEL: Record<EstadoPedido, string> = {
  PENDIENTE: 'Recibido',
  CONFIRMADO: 'En preparación',
  DESPACHADO: 'En camino',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
}

export const codigoPedido = (id: number) => `#P360-${String(id).padStart(5, '0')}`

export interface ItemPedido {
  productoId: number
  nombreProducto: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

export interface Pedido {
  id: number
  clienteId: string
  clienteUsername?: string
  direccionEntrega?: string
  estado: EstadoPedido
  total: number
  items: ItemPedido[]
  creadoEn: string
  actualizadoEn: string
}

export interface CrearPedidoRequest {
  items: { productoId: number; cantidad: number }[]
  direccionEntrega?: string
}

export interface Usuario {
  sub: string
  username?: string
  clientId?: string
  issuer?: string
  roles: string[]
  scopes: string[]
  emitidoEn?: string
  expiraEn?: string
}

export interface Resumen {
  usuario: Usuario
  productosDisponibles: number
  totalPedidos: number
  montoTotal: number
  pedidosPorEstado: Record<string, number>
  ultimosPedidos: Pick<Pedido, 'id' | 'clienteUsername' | 'estado' | 'total' | 'creadoEn'>[]
  productosDestacados: Producto[]
}
