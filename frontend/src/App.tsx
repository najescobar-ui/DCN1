import { Navigate, Route, Routes } from 'react-router'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AdminLayout } from './components/AdminLayout'
import { ShopLayout } from './components/ShopLayout'
import { CallbackPage } from './pages/CallbackPage'
import { CarritoPage } from './pages/CarritoPage'
import { CatalogoPage } from './pages/CatalogoPage'
import { LoginPage } from './pages/LoginPage'
import { NoAutorizadoPage } from './pages/NoAutorizadoPage'
import { PedidosPage } from './pages/PedidosPage'
import { PerfilPage } from './pages/PerfilPage'
import { ProductoPage } from './pages/ProductoPage'
import { AdminPedidosPage } from './pages/admin/AdminPedidosPage'
import { AdminProductosPage } from './pages/admin/AdminProductosPage'

export default function App() {
  return (
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
  )
}
