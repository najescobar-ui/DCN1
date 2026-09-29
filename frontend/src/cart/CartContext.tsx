import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useSession } from '../auth/useSession'
import { CartContext, type CartApi, type CartState } from './cartStore'

const EMPTY: CartState = { items: [], direccion: '', lat: null, lon: null, telefono: '', detalle: '' }
const MAX_QTY = 99

function load(key: string): CartState {
  try {
    const raw = localStorage.getItem(key)
    return raw ? { ...EMPTY, ...(JSON.parse(raw) as CartState) } : EMPTY
  } catch {
    return EMPTY
  }
}

/** Shopping cart kept in the browser, one per signed-in user. Prices are recalculated by the backend. */
export function CartProvider({ children }: { children: ReactNode }) {
  const { claims } = useSession()
  const key = `p360.cart.${claims?.sub ?? 'anon'}`
  const [state, setState] = useState<{ key: string; cart: CartState }>(() => ({ key, cart: load(key) }))

  // Switch carts when the signed-in user changes.
  const cart = state.key === key ? state.cart : load(key)
  if (state.key !== key) {
    setState({ key, cart })
  }

  useEffect(() => {
    try {
      localStorage.setItem(state.key, JSON.stringify(state.cart))
    } catch {
      // Storage unavailable (private mode): the cart just won't persist.
    }
  }, [state])

  const update = useCallback(
    (fn: (c: CartState) => CartState) => setState((s) => ({ key: s.key, cart: fn(s.cart) })),
    [],
  )

  const api = useMemo<CartApi>(
    () => ({
      ...cart,
      count: cart.items.reduce((n, i) => n + i.cantidad, 0),
      subtotal: cart.items.reduce((n, i) => n + i.precio * i.cantidad, 0),
      add: (item, cantidad = 1) =>
        update((c) => {
          const existing = c.items.find((i) => i.productoId === item.productoId)
          const items = existing
            ? c.items.map((i) =>
                i.productoId === item.productoId ? { ...i, ...item, cantidad: Math.min(MAX_QTY, i.cantidad + cantidad) } : i,
              )
            : [...c.items, { ...item, cantidad: Math.min(MAX_QTY, cantidad) }]
          return { ...c, items }
        }),
      setCantidad: (productoId, cantidad) =>
        update((c) => ({
          ...c,
          items:
            cantidad <= 0
              ? c.items.filter((i) => i.productoId !== productoId)
              : c.items.map((i) => (i.productoId === productoId ? { ...i, cantidad: Math.min(MAX_QTY, cantidad) } : i)),
        })),
      remove: (productoId) => update((c) => ({ ...c, items: c.items.filter((i) => i.productoId !== productoId) })),
      clear: () => update((c) => ({ ...c, items: [] })),
      setEntrega: ({ direccion, lat, lon }) => update((c) => ({ ...c, direccion, lat, lon })),
      setTelefono: (telefono) => update((c) => ({ ...c, telefono })),
      setDetalle: (detalle) => update((c) => ({ ...c, detalle })),
    }),
    [cart, update],
  )

  return <CartContext.Provider value={api}>{children}</CartContext.Provider>
}
