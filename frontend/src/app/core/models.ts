export interface Producto {
  id: number;
  nombre: string;
  descripcion?: string;
  precio: number;
  stock: number;
  creadoEn?: string;
}

export type ProductoRequest = Omit<Producto, 'id' | 'creadoEn'>;

export type EstadoPedido = 'PENDIENTE' | 'CONFIRMADO' | 'DESPACHADO' | 'ENTREGADO' | 'CANCELADO';

export const ESTADOS: EstadoPedido[] = ['PENDIENTE', 'CONFIRMADO', 'DESPACHADO', 'ENTREGADO', 'CANCELADO'];

export interface ItemPedido {
  productoId: number;
  nombreProducto: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

export interface Pedido {
  id: number;
  clienteId: string;
  clienteUsername?: string;
  direccionEntrega?: string;
  estado: EstadoPedido;
  total: number;
  items: ItemPedido[];
  creadoEn: string;
  actualizadoEn: string;
}

export interface CrearPedidoRequest {
  items: { productoId: number; cantidad: number }[];
  direccionEntrega?: string;
}

export interface Usuario {
  sub: string;
  username?: string;
  clientId?: string;
  issuer?: string;
  roles: string[];
  scopes: string[];
  emitidoEn?: string;
  expiraEn?: string;
}

export interface Resumen {
  usuario: Usuario;
  productosDisponibles: number;
  totalPedidos: number;
  montoTotal: number;
  pedidosPorEstado: Record<string, number>;
  ultimosPedidos: Pick<Pedido, 'id' | 'clienteUsername' | 'estado' | 'total' | 'creadoEn'>[];
  productosDestacados: Producto[];
}
