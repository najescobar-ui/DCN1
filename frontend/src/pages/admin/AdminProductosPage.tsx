import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { api } from '../../api/api'
import type { Producto, ProductoRequest } from '../../api/models'
import { Paginacion, ThOrdenable } from '../../components/tabla'
import { useTabla } from '../../components/useTabla'
import { clp } from '../../utils/format'
import { PRODUCT_IMAGES, descuento, imageOf } from '../../utils/images'
import { notify } from '../../utils/notify'

const CATEGORIAS = ['Hamburguesas', 'Pizzas', 'Sushi', 'Bebidas', 'Postres']
/** Valor del selector que habilita escribir una categoría nueva. */
const NUEVA = '__nueva__'

interface Form {
  nombre: string
  descripcion: string
  categoria: string
  precio: string
  precioAnterior: string
  stock: string
  imagenUrl: string
}

const VACIO: Form = { nombre: '', descripcion: '', categoria: CATEGORIAS[0], precio: '', precioAnterior: '', stock: '0', imagenUrl: PRODUCT_IMAGES[0] }

function toRequest(f: Form): ProductoRequest | string {
  const precio = Number(f.precio)
  const precioAnterior = f.precioAnterior ? Number(f.precioAnterior) : null
  const stock = Number(f.stock)
  if (!f.nombre.trim()) return 'El nombre es obligatorio.'
  if (!f.categoria.trim()) return 'La categoría es obligatoria.'
  if (!(precio >= 1)) return 'El precio debe ser mayor a 0.'
  if (precioAnterior !== null && !(precioAnterior > precio)) return 'El precio anterior debe ser mayor que el precio actual.'
  if (!Number.isInteger(stock) || stock < 0) return 'El stock debe ser un entero no negativo.'
  return {
    nombre: f.nombre.trim(),
    descripcion: f.descripcion.trim(),
    categoria: f.categoria.trim(),
    precio,
    precioAnterior,
    stock,
    imagenUrl: f.imagenUrl || null,
  }
}

export function AdminProductosPage() {
  const [productos, setProductos] = useState<Producto[]>([])
  const [editandoId, setEditandoId] = useState<number | null>(null)
  const [form, setForm] = useState<Form>(VACIO)
  const [error, setError] = useState<string | null>(null)

  const cargar = useCallback(() => {
    api.productos().then(setProductos, () => undefined)
  }, [])

  useEffect(cargar, [cargar])

  // Categorías existentes: las de base más las que ya usan los productos, sin repetir.
  const categorias = useMemo(
    () => [...new Set([...CATEGORIAS, ...productos.map((p) => p.categoria).filter(Boolean) as string[]])].sort((a, b) => a.localeCompare(b, 'es')),
    [productos],
  )
  const [categoriaNueva, setCategoriaNueva] = useState(false)

  const tabla = useTabla(
    productos,
    {
      producto: (p) => p.nombre,
      categoria: (p) => p.categoria,
      precio: (p) => p.precio,
      stock: (p) => p.stock,
    },
    { columna: 'producto', direccion: 'asc' },
  )

  const campo = (key: keyof Form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const editar = (p: Producto) => {
    setCategoriaNueva(false)
    setEditandoId(p.id)
    setError(null)
    setForm({
      nombre: p.nombre,
      descripcion: p.descripcion ?? '',
      categoria: p.categoria ?? '',
      precio: String(p.precio),
      precioAnterior: p.precioAnterior ? String(p.precioAnterior) : '',
      stock: String(p.stock),
      imagenUrl: p.imagenUrl ?? '',
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const limpiar = () => {
    setCategoriaNueva(false)
    setEditandoId(null)
    setForm(VACIO)
    setError(null)
  }

  const guardar = async (event: FormEvent) => {
    event.preventDefault()
    const request = toRequest(form)
    if (typeof request === 'string') {
      setError(request)
      return
    }
    try {
      if (editandoId === null) {
        await api.crearProducto(request)
        notify.ok('Producto creado')
      } else {
        await api.actualizarProducto(editandoId, request)
        notify.ok('Producto actualizado')
      }
      limpiar()
      cargar()
    } catch {
      // The interceptor already showed the error.
    }
  }

  const eliminar = async (p: Producto) => {
    if (!window.confirm(`¿Eliminar "${p.nombre}" del catálogo?`)) return
    try {
      await api.eliminarProducto(p.id)
      notify.ok('Producto eliminado')
      if (editandoId === p.id) limpiar()
      cargar()
    } catch {
      // The interceptor already showed the error.
    }
  }

  return (
    <>
      <div className="admin-head">
        <h1 className="d">Productos</h1>
        <span className="muted">{productos.length} en el catálogo</span>
      </div>

      <form className="card card-pad" onSubmit={guardar} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <h2 className="d" style={{ fontSize: 24 }}>
          {editandoId === null ? 'Nuevo producto' : `Editar producto #${editandoId}`}
        </h2>
        <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
          <img src={imageOf(form.imagenUrl)} alt="" style={{ width: 160, height: 120, objectFit: 'cover', borderRadius: 2 }} />
          <div className="form-grid" style={{ flex: 1, minWidth: 260 }}>
            <label className="field">
              Nombre
              <input className="input" value={form.nombre} maxLength={120} onChange={campo('nombre')} />
            </label>
            <label className="field">
              Categoría
              <select
                className="select"
                value={categoriaNueva ? NUEVA : form.categoria}
                onChange={(e) => {
                  const nueva = e.target.value === NUEVA
                  setCategoriaNueva(nueva)
                  setForm((f) => ({ ...f, categoria: nueva ? '' : e.target.value }))
                }}
              >
                {categorias.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
                <option value={NUEVA}>Nueva categoría…</option>
              </select>
              {categoriaNueva && (
                <input
                  className="input"
                  value={form.categoria}
                  maxLength={40}
                  placeholder="Nombre de la categoría"
                  autoFocus
                  onChange={campo('categoria')}
                />
              )}
            </label>
            <label className="field">
              Imagen
              <select className="select" value={form.imagenUrl} onChange={campo('imagenUrl')}>
                {PRODUCT_IMAGES.map((src) => (
                  <option key={src} value={src}>
                    {src.split('/').pop()}
                  </option>
                ))}
                {form.imagenUrl && !PRODUCT_IMAGES.includes(form.imagenUrl) && <option value={form.imagenUrl}>{form.imagenUrl}</option>}
              </select>
            </label>
            <label className="field">
              Precio (CLP)
              <input className="input" type="number" min={1} value={form.precio} onChange={campo('precio')} />
            </label>
            <label className="field">
              Precio anterior (oferta)
              <input className="input" type="number" min={1} value={form.precioAnterior} placeholder="Opcional" onChange={campo('precioAnterior')} />
            </label>
            <label className="field">
              Stock
              <input className="input" type="number" min={0} value={form.stock} onChange={campo('stock')} />
            </label>
          </div>
        </div>
        <label className="field">
          Descripción
          <textarea className="textarea" rows={2} maxLength={500} value={form.descripcion} onChange={campo('descripcion')} />
        </label>
        {error && <p className="error-text" style={{ margin: 0 }}>{error}</p>}
        <div style={{ display: 'flex', gap: 12 }}>
          <button className="btn" type="submit">
            Guardar
          </button>
          {editandoId !== null && (
            <button className="btn btn-ghost" type="button" onClick={limpiar}>
              Cancelar
            </button>
          )}
        </div>
      </form>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th />
              <ThOrdenable columna="producto" orden={tabla.orden} onOrdenar={tabla.ordenarPor}>
                Producto
              </ThOrdenable>
              <ThOrdenable columna="categoria" orden={tabla.orden} onOrdenar={tabla.ordenarPor}>
                Categoría
              </ThOrdenable>
              <ThOrdenable columna="precio" orden={tabla.orden} onOrdenar={tabla.ordenarPor}>
                Precio
              </ThOrdenable>
              <ThOrdenable columna="stock" orden={tabla.orden} onOrdenar={tabla.ordenarPor}>
                Stock
              </ThOrdenable>
              <th />
            </tr>
          </thead>
          <tbody>
            {tabla.filas.map((p) => {
              const off = descuento(p.precio, p.precioAnterior)
              return (
                <tr key={p.id}>
                  <td>
                    <img className="thumb" src={imageOf(p.imagenUrl)} alt="" />
                  </td>
                  <td>
                    <strong>{p.nombre}</strong>
                    <div className="m faint" style={{ fontSize: 12 }}>
                      #{p.id}
                    </div>
                  </td>
                  <td className="muted">{p.categoria}</td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <strong>{clp(p.precio)}</strong> {off && <span className="off-tag">{off}</span>}
                  </td>
                  <td>{p.stock}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                      <button className="btn btn-sm btn-ghost" type="button" onClick={() => editar(p)}>
                        Editar
                      </button>
                      <button className="btn btn-sm btn-danger" type="button" onClick={() => eliminar(p)}>
                        Eliminar
                      </button>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <Paginacion {...tabla} nombre="productos" />
    </>
  )
}
