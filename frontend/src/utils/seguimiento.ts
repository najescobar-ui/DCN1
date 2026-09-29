import type { Pedido, Producto } from '../api/models'

const PUDU = '/img/pudu'

/** Categorías con su propia imagen de preparación. */
const PREPARACION: Record<string, string> = {
  Sushi: 'sushi',
  Pizzas: 'pizza',
  Hamburguesas: 'hamburguesa',
}

/**
 * Imagen del seguimiento según el estado. En preparación depende de lo pedido: si hay una sola
 * de las categorías principales se usa la suya; si hay varias (o ninguna) se usa "preparando todo".
 */
export function imagenSeguimiento(pedido: Pedido, productos: Producto[]): string {
  switch (pedido.estado) {
    case 'PENDIENTE':
      return `${PUDU}/pudu-ansioso.jpg`
    case 'DESPACHADO':
      return `${PUDU}/pudu-entrega.jpg`
    case 'ENTREGADO':
      return `${PUDU}/pudu-enjoy.jpg`
    case 'CONFIRMADO': {
      const categoria = new Map(productos.map((p) => [p.id, p.categoria]))
      const tipos = new Set(
        pedido.items.map((i) => PREPARACION[categoria.get(i.productoId) ?? '']).filter(Boolean),
      )
      return tipos.size === 1 ? `${PUDU}/pudu-preparando-${[...tipos][0]}.jpg` : `${PUDU}/pudu-preparando-todo.jpg`
    }
    default:
      return `${PUDU}/pudu-home.jpg`
  }
}

export const TEXTO_SEGUIMIENTO: Record<Pedido['estado'], string> = {
  PENDIENTE: 'Esperando que la cocina lo tome...',
  CONFIRMADO: 'Preparando tu pedido...',
  DESPACHADO: '¡Va en camino!',
  ENTREGADO: '¡Que lo disfrutes!',
  CANCELADO: 'Pedido cancelado',
}

export const unidades = (n: number) => `${n} ${n === 1 ? 'producto' : 'productos'}`

/** Encuadre de la imagen en el recuadro: la de "Enjoy" tiene el texto a la derecha. */
export const encuadre = (src: string) => (src.includes('enjoy') ? '82% 45%' : '50% 40%')
