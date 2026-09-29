import { describe, expect, it } from 'vitest'
import type { EstadoPedido, Pedido, Producto } from '../api/models'
import { imagenSeguimiento, unidades } from './seguimiento'

const productos = [
  { id: 1, nombre: 'Smash', categoria: 'Hamburguesas', precio: 1, stock: 1 },
  { id: 2, nombre: 'Pizza', categoria: 'Pizzas', precio: 1, stock: 1 },
  { id: 3, nombre: 'Promo', categoria: 'Sushi', precio: 1, stock: 1 },
  { id: 4, nombre: 'Bebida', categoria: 'Bebidas', precio: 1, stock: 1 },
] as Producto[]

const pedido = (estado: EstadoPedido, ids: number[]) =>
  ({ estado, items: ids.map((productoId) => ({ productoId, cantidad: 1 })) }) as unknown as Pedido

describe('imagenSeguimiento', () => {
  it('usa una imagen por estado', () => {
    expect(imagenSeguimiento(pedido('PENDIENTE', [1]), productos)).toContain('ansioso')
    expect(imagenSeguimiento(pedido('DESPACHADO', [1]), productos)).toContain('entrega')
    expect(imagenSeguimiento(pedido('ENTREGADO', [1]), productos)).toContain('enjoy')
  })

  it('en preparación muestra la categoría pedida, sin contar bebidas ni postres', () => {
    expect(imagenSeguimiento(pedido('CONFIRMADO', [3, 4]), productos)).toContain('preparando-sushi')
    expect(imagenSeguimiento(pedido('CONFIRMADO', [1]), productos)).toContain('preparando-hamburguesa')
    expect(imagenSeguimiento(pedido('CONFIRMADO', [2, 2]), productos)).toContain('preparando-pizza')
  })

  it('si mezcla categorías principales usa "preparando todo"', () => {
    expect(imagenSeguimiento(pedido('CONFIRMADO', [1, 3]), productos)).toContain('preparando-todo')
    expect(imagenSeguimiento(pedido('CONFIRMADO', [4]), productos)).toContain('preparando-todo')
  })

  it('escribe producto en singular o plural', () => {
    expect(unidades(1)).toBe('1 producto')
    expect(unidades(3)).toBe('3 productos')
  })
})
