import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../api/api'
import type { Producto, ProductoRequest } from '../api/models'
import { useSession } from '../auth/useSession'
import { clp } from '../utils/format'
import { notify } from '../utils/notify'

const VACIO: ProductoRequest = { nombre: '', descripcion: '', precio: 0, stock: 0 }

function validar(form: ProductoRequest): string | null {
  if (!form.nombre.trim() || form.nombre.length > 120) return 'El nombre es obligatorio (max. 120 caracteres).'
  if ((form.descripcion ?? '').length > 500) return 'La descripcion admite hasta 500 caracteres.'
  if (!(form.precio >= 1)) return 'El precio debe ser mayor a 0.'
  if (!(form.stock >= 0) || !Number.isInteger(form.stock)) return 'El stock debe ser un entero no negativo.'
  return null
}

export function ProductosPage() {
  const session = useSession()
  const [productos, setProductos] = useState<Producto[]>([])
  const [editandoId, setEditandoId] = useState<number | null>(null)
  const [form, setForm] = useState<ProductoRequest>(VACIO)
  const [error, setError] = useState<string | null>(null)

  const cargar = useCallback(() => {
    api.productos().then(setProductos, () => undefined)
  }, [])

  useEffect(cargar, [cargar])

  const campo = <K extends keyof ProductoRequest>(key: K, value: ProductoRequest[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  const editar = (p: Producto) => {
    setEditandoId(p.id)
    setForm({ nombre: p.nombre, descripcion: p.descripcion ?? '', precio: p.precio, stock: p.stock })
    setError(null)
  }

  const cancelarEdicion = () => {
    setEditandoId(null)
    setForm(VACIO)
    setError(null)
  }

  const guardar = async (event: FormEvent) => {
    event.preventDefault()
    const problema = validar(form)
    setError(problema)
    if (problema) return
    try {
      if (editandoId === null) {
        await api.crearProducto(form)
        notify.ok('Producto creado')
      } else {
        await api.actualizarProducto(editandoId, form)
        notify.ok('Producto actualizado')
      }
      cancelarEdicion()
      cargar()
    } catch {
      // The interceptor already showed the error.
    }
  }

  const eliminar = async (p: Producto) => {
    if (!window.confirm(`Eliminar "${p.nombre}"?`)) return
    try {
      await api.eliminarProducto(p.id)
      notify.ok('Producto eliminado')
      cargar()
    } catch {
      // The interceptor already showed the error.
    }
  }

  return (
    <>
      <h1>Productos</h1>

      {session.isAdmin && (
        <form className="card" onSubmit={guardar} noValidate>
          <h2>{editandoId === null ? 'Nuevo producto' : `Editar producto #${editandoId}`}</h2>
          <div className="form-row">
            <label>
              Nombre <input value={form.nombre} onChange={(e) => campo('nombre', e.target.value)} />
            </label>
            <label>
              Precio (CLP)
              <input type="number" min={1} value={form.precio} onChange={(e) => campo('precio', e.target.valueAsNumber)} />
            </label>
            <label>
              Stock
              <input type="number" min={0} value={form.stock} onChange={(e) => campo('stock', e.target.valueAsNumber)} />
            </label>
          </div>
          <label>
            Descripcion
            <textarea rows={2} value={form.descripcion} onChange={(e) => campo('descripcion', e.target.value)} />
          </label>
          {error && <p className="error">{error}</p>}
          <div className="actions">
            <button className="btn" type="submit">
              Guardar
            </button>
            {editandoId !== null && (
              <button className="btn secondary" type="button" onClick={cancelarEdicion}>
                Cancelar
              </button>
            )}
          </div>
        </form>
      )}

      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Producto</th>
              <th>Precio</th>
              <th>Stock</th>
              {session.isAdmin && <th />}
            </tr>
          </thead>
          <tbody>
            {productos.map((p) => (
              <tr key={p.id}>
                <td>{p.id}</td>
                <td>
                  <strong>{p.nombre}</strong>
                  <div className="muted">{p.descripcion}</div>
                </td>
                <td>{clp(p.precio)}</td>
                <td>{p.stock}</td>
                {session.isAdmin && (
                  <td className="actions">
                    <button className="btn small secondary" type="button" onClick={() => editar(p)}>
                      Editar
                    </button>
                    <button className="btn small danger" type="button" onClick={() => eliminar(p)}>
                      Eliminar
                    </button>
                  </td>
                )}
              </tr>
            ))}
            {productos.length === 0 && (
              <tr>
                <td colSpan={5} className="muted">
                  No hay productos.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}
