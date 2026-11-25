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

/** Estados a los que puede pasar un pedido (misma regla que EstadoPedido.siguientes() en ms-pedidos). */
export const SIGUIENTES: Record<EstadoPedido, EstadoPedido[]> = {
  PENDIENTE: ['CONFIRMADO', 'CANCELADO'],
  CONFIRMADO: ['DESPACHADO', 'CANCELADO'],
  DESPACHADO: ['ENTREGADO'],
  ENTREGADO: [],
  CANCELADO: [],
}

export const codigoPedido = (id: number) => `#P360-${String(id).padStart(5, '0')}`

export interface ItemPedido {
  productoId: number
  nombreProducto: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

export type MetodoPago = 'TARJETA' | 'TRANSFERENCIA' | 'EFECTIVO'
export type EstadoPago = 'PENDIENTE' | 'PAGADO' | 'RECHAZADO'

export const METODO_LABEL: Record<MetodoPago, string> = {
  TARJETA: 'Tarjeta (Webpay)',
  TRANSFERENCIA: 'Transferencia',
  EFECTIVO: 'Efectivo al recibir',
}

export const ESTADO_PAGO_LABEL: Record<EstadoPago, string> = {
  PENDIENTE: 'Pago pendiente',
  PAGADO: 'Pagado',
  RECHAZADO: 'Pago rechazado',
}

export interface Pedido {
  id: number
  clienteId: string
  clienteUsername?: string
  direccionEntrega?: string
  telefonoContacto?: string
  latitud?: number | null
  longitud?: number | null
  estado: EstadoPedido
  metodoPago?: MetodoPago | null
  estadoPago?: EstadoPago | null
  pagoAutorizacion?: string | null
  pagoTarjeta?: string | null
  total: number
  items: ItemPedido[]
  creadoEn: string
  actualizadoEn: string
}

export interface CrearPedidoRequest {
  items: { productoId: number; cantidad: number }[]
  direccionEntrega: string
  telefono: string
  latitud?: number | null
  longitud?: number | null
  metodoPago: MetodoPago
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

// ---------- Mensajería (RabbitMQ) ----------

export type TipoCola = 'QUORUM' | 'CLASSIC'
export type TipoExchange = 'DIRECT' | 'TOPIC' | 'FANOUT' | 'HEADERS'

export interface ColaInfo {
  nombre: string
  tipo: string
  durable: boolean
  nodo: string
  mensajes: number
  listos: number
  sinConfirmar: number
  consumidores: number
  argumentos: Record<string, unknown> | null
}

export interface ExchangeInfo {
  nombre: string
  tipo: string
  durable: boolean
  interno: boolean
}

export interface BindingInfo {
  exchange: string
  destino: string
  tipoDestino: string
  routingKey: string
}

export interface ClusterInfo {
  nombre: string
  version: string
  nodos: { nombre: string; activo: boolean; memoriaUsadaBytes: number; uptimeMs: number }[]
}

export interface CrearColaRequest {
  nombre: string
  tipo: TipoCola
  conDlq: boolean
  ttlMs?: number | null
  limiteEntregas?: number | null
}

export interface BindingRequest {
  exchange: string
  cola: string
  routingKey: string
}

export interface PublicarMensajeRequest {
  exchange: string
  routingKey: string
  cuerpo: string
  headers?: Record<string, string>
}

export interface Notificacion {
  id: number
  pedidoId: number
  clienteUsername?: string | null
  tipo: string
  mensaje: string
  creadaEn: string
}

export interface Comprobante {
  id: number
  folio: string
  pedidoId: number
  clienteUsername?: string | null
  metodoPago?: string | null
  total: number
  emitidoEn: string
}

export interface RegistroAuditoria {
  id: number
  eventoId: string
  routingKey: string
  tipo: string
  pedidoId: number
  estado?: string | null
  estadoPago?: string | null
  clienteUsername?: string | null
  recibidoEn: string
}

/** Mensaje no entregado que registró el listener de una DLQ (ms-notificaciones). */
export interface MensajeFallido {
  id: number
  dlq: string
  colaOrigen?: string | null
  /** rejected (NACK), expired (TTL), maxlen (largo máximo) o delivery_limit */
  motivo?: string | null
  veces: number
  exchangeOrigen?: string | null
  routingKeyOrigen?: string | null
  messageId?: string | null
  cuerpo?: string | null
  recibidoEn: string
  reprocesadoEn?: string | null
}

// ---------- Streaming (Kafka): ms-streaming-admin y ms-analitica ----------

export interface KafkaBroker {
  id: number
  host: string
  puerto: number
  rack: string | null
  controlador: boolean
}

export interface KafkaCluster {
  clusterId: string
  controlador: number | null
  brokers: KafkaBroker[]
  topicos: number
  grupos: number
}

export type CleanupPolicy = 'DELETE' | 'COMPACT' | 'COMPACT_DELETE'

export interface TopicoResumen {
  nombre: string
  interno: boolean
  protegido: boolean
  particiones: number
  replicacion: number
  isrCompleto: boolean
  mensajes: number
  cleanupPolicy: string | null
  retencionMs: string | null
}

export interface ParticionInfo {
  particion: number
  lider: number
  replicas: number[]
  isr: number[]
  offsetInicio: number
  offsetFin: number
  mensajes: number
}

export interface TopicoDetalle {
  nombre: string
  interno: boolean
  protegido: boolean
  replicacion: number
  particiones: ParticionInfo[]
  configuracion: Record<string, string>
}

export interface CrearTopicoRequest {
  nombre: string
  particiones: number
  replicacion: number
  minInsync?: number | null
  retencionMs?: number | null
  retencionBytes?: number | null
  cleanupPolicy?: CleanupPolicy | null
}

export interface ModificarTopicoRequest {
  retencionMs?: number | null
  retencionBytes?: number | null
  cleanupPolicy?: CleanupPolicy | null
  minInsync?: number | null
}

export interface GrupoResumen {
  id: string
  estado: string
  miembros: number
  coordinador: number | null
  topicos: string[]
  lagTotal: number
}

export interface LagInfo {
  topico: string
  particion: number
  confirmado: number
  fin: number
  lag: number
  miembro: string
}

export interface GrupoDetalle {
  id: string
  estado: string
  asignador: string
  coordinador: number | null
  miembros: { clientId: string; consumerId: string; host: string; particiones: string[] }[]
  particiones: LagInfo[]
  lagTotal: number
}

export type DestinoOffsets = 'INICIO' | 'FIN' | 'FECHA'

export interface ResetResultado {
  grupo: string
  topico: string
  destino: string
  particiones: { particion: number; antes: number; despues: number }[]
}

export type EscenarioDemo = 'VALIDO' | 'INVALIDO' | 'INCOMPLETO' | 'FALLO_TRANSITORIO' | 'LOTE'

export interface EventoPublicado {
  key: string
  particion: number
  offset: number
  descripcion: string
}

export interface ConsumidorInfo {
  id: string
  grupo: string
  topicos: string[]
  activo: boolean
  consumidores: number
  maximo: number
  asignaciones: Record<string, string[]>
}

export interface ProductoVendido {
  productoId: number
  nombre: string
  unidades: number
  monto: number
}

export interface MetricaVentas {
  ventana: string
  inicio: string
  fin: string
  pedidosCreados: number
  pedidosPagados: number
  pedidosCancelados: number
  montoPagado: number
  ticketPromedio: number
  unidadesVendidas: number
  topProductos: ProductoVendido[]
  porMetodoPago: Record<string, number>
  eventosProcesados: number
  generadoEn: string
}

export interface TableroVentas {
  resumen: {
    pedidosCreados: number
    pedidosPagados: number
    pedidosCancelados: number
    unidadesVendidas: number
    montoPagado: number
    ticketPromedio: number
    topProductos: ProductoVendido[]
    porMetodoPago: Record<string, number>
    ventanas: MetricaVentas[]
    actualizadoEn: string | null
  }
  eventosContados: number
  pedidosEnSeguimiento: number
}

export interface HitoSeguimiento {
  tipo: 'PEDIDO_CREADO' | 'ESTADO_CAMBIADO' | 'PAGO_APROBADO' | 'PAGO_RECHAZADO'
  estado: string
  estadoPago: string
  ocurridoEn: string
  procesadoEn: string
  particion: number
  offset: number
}

export interface SeguimientoPedido {
  pedidoId: number
  clienteId: string
  clienteUsername: string
  estado: string
  estadoPago: string
  total: number | null
  particion: number
  hitos: HitoSeguimiento[]
}
