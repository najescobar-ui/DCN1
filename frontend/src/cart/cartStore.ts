import { createContext } from 'react'

export interface CartItem {
  productoId: number
  nombre: string
  precio: number
  imagenUrl?: string | null
  categoria?: string
  cantidad: number
}

export interface CartState {
  items: CartItem[]
  direccion: string
}

export interface CartApi extends CartState {
  count: number
  subtotal: number
  add: (item: Omit<CartItem, 'cantidad'>, cantidad?: number) => void
  setCantidad: (productoId: number, cantidad: number) => void
  remove: (productoId: number) => void
  clear: () => void
  setDireccion: (direccion: string) => void
}

export const CartContext = createContext<CartApi | null>(null)
