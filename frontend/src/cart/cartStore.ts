import { createContext } from 'react'

export interface CartItem {
  productoId: number
  nombre: string
  precio: number
  imagenUrl?: string | null
  categoria?: string
  cantidad: number
}

export interface Entrega {
  direccion: string
  lat: number | null
  lon: number | null
}

export interface CartState {
  items: CartItem[]
  direccion: string
  lat: number | null
  lon: number | null
  telefono: string
}

export interface CartApi extends CartState {
  count: number
  subtotal: number
  add: (item: Omit<CartItem, 'cantidad'>, cantidad?: number) => void
  setCantidad: (productoId: number, cantidad: number) => void
  remove: (productoId: number) => void
  clear: () => void
  setEntrega: (entrega: Entrega) => void
  setTelefono: (telefono: string) => void
}

export const CartContext = createContext<CartApi | null>(null)
