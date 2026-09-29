/** Images bundled with the frontend (public/img). */
export const PRODUCT_IMAGES = [
  '/img/productos/hamburguesa-queso.jpg',
  '/img/productos/hamburguesa-smash.jpg',
  '/img/productos/pizza-familiar.jpg',
  '/img/productos/pizza-napolitana.jpg',
  '/img/productos/sushi-platter.jpg',
  '/img/productos/gyozas.jpg',
  '/img/productos/lata.jpg',
  '/img/productos/brownie.jpg',
]

export const FALLBACK_IMAGE = '/img/pudu/pudu-tostado.jpg'

export const imageOf = (url?: string | null) => url || FALLBACK_IMAGE

export const descuento = (precio: number, anterior?: number | null) =>
  anterior && anterior > precio ? `-${Math.round((1 - precio / anterior) * 100)}%` : null
