import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api/api'
import type { Producto } from '../api/models'
import { clp } from '../utils/format'
import { notify } from '../utils/notify'

export function NuevoPedidoPage() {
  const navigate = useNavigate()
  const [productos, setProductos] = useState<Producto[]>([])
  const [cantidades, setCantidades] = useState<Record<number, number>>({})
  const [direccion, setDireccion] = useState('')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    api.productos().then(setProductos, () => undefined)
  }, [])

  const items = useMemo(
    () =>
      Object.entries(cantidades)
        .filter(([, cantidad]) => cantidad > 0)
        .map(([productoId, cantidad]) => ({ productoId: Number(productoId), cantidad })),
    [cantidades],
  )
  // Preview only: the backend recalculates prices from ms-productos.
  const total = productos.reduce((sum, p) => sum + p.precio * (cantidades[p.id] ?? 0), 0)

  const cambiarCantidad = (id: number, value: number) =>
    setCantidades((c) => ({ ...c, [id]: Math.max(0, Math.min(99, Number.isNaN(value) ? 0 : value)) }))

  const confirmar = async () => {
    setEnviando(true)
    try {
      const pedido = await api.crearPedido({ items, direccionEntrega: direccion || undefined })
      notify.ok(`Pedido #${pedido.id} creado`)
      navigate('/pedidos')
    } catch {
      setEnviando(false)
    }
  }

  return (
    <>
      <h1>Nuevo pedido</h1>
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>Producto</th>
              <th>Precio</th>
              <th>Disponibles</th>
              <th style={{ width: 120 }}>Cantidad</th>
            </tr>
          </thead>
          <tbody>
            {productos.map((p) => (
              <tr key={p.id}>
                <td>
                  <strong>{p.nombre}</strong>
                  <div className="muted">{p.descripcion}</div>
                </td>
                <td>{clp(p.precio)}</td>
                <td>{p.stock}</td>
                <td>
                  <input
                    type="number"
                    min={0}
                    max={p.stock}
                    value={cantidades[p.id] ?? 0}
                    onChange={(e) => cambiarCantidad(p.id, e.target.valueAsNumber)}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <label>
          Direccion de entrega
          <input
            value={direccion}
            maxLength={250}
            placeholder="Calle, numero, comuna"
            onChange={(e) => setDireccion(e.target.value)}
          />
        </label>
        <div className="actions">
          <span className="stat">{clp(total)}</span>
          <button className="btn" type="button" disabled={items.length === 0 || enviando} onClick={confirmar}>
            Confirmar pedido
          </button>
        </div>
      </div>
    </>
  )
}
