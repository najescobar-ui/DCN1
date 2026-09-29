import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AdminLayout } from './components/AdminLayout'
import { ShopLayout } from './components/ShopLayout'
import { CallbackPage } from './pages/CallbackPage'
import { CatalogoPage } from './pages/CatalogoPage'
import { LoginPage } from './pages/LoginPage'
import { NoAutorizadoPage } from './pages/NoAutorizadoPage'
import { PedidosPage } from './pages/PedidosPage'
import { ProductoPage } from './pages/ProductoPage'

// Heavier screens (map, admin, token inspector) load on demand.
const CarritoPage = lazy(() => import('./pages/CarritoPage').then((m) => ({ default: m.CarritoPage })))
const PerfilPage = lazy(() => import('./pages/PerfilPage').then((m) => ({ default: m.PerfilPage })))
const AdminPedidosPage = lazy(() => import('./pages/admin/AdminPedidosPage').then((m) => ({ default: m.AdminPedidosPage })))
const AdminProductosPage = lazy(() => import('./pages/admin/AdminProductosPage').then((m) => ({ default: m.AdminProductosPage })))

export default function App() {
  return (
    <Suspense fallback={<p className="container page muted">Cargando...</p>}>
    <Routes>
      <Route index element={<LoginPage />} />
      <Route path="callback" element={<CallbackPage />} />

      <Route
        element={
          <ProtectedRoute>
            <ShopLayout />
          </ProtectedRoute>
        }
      >
        <Route path="catalogo" element={<CatalogoPage />} />
        <Route path="producto/:id" element={<ProductoPage />} />
        <Route
          path="carrito"
          element={
            <ProtectedRoute roles={['CLIENTE', 'ADMIN']}>
              <CarritoPage />
            </ProtectedRoute>
          }
        />
        <Route path="pedidos" element={<PedidosPage />} />
        <Route path="perfil" element={<PerfilPage />} />
        <Route path="no-autorizado" element={<NoAutorizadoPage />} />
      </Route>

      <Route
        path="admin"
        element={
          <ProtectedRoute roles={['ADMIN']}>
            <AdminLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<AdminPedidosPage />} />
        <Route path="productos" element={<AdminProductosPage />} />
        <Route path="token" element={<PerfilPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </Suspense>
  )
}
