import { Navigate, Route, Routes } from 'react-router'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Layout } from './components/Layout'
import { CallbackPage } from './pages/CallbackPage'
import { DashboardPage } from './pages/DashboardPage'
import { HomePage } from './pages/HomePage'
import { NoAutorizadoPage } from './pages/NoAutorizadoPage'
import { NuevoPedidoPage } from './pages/NuevoPedidoPage'
import { PedidosPage } from './pages/PedidosPage'
import { PerfilPage } from './pages/PerfilPage'
import { ProductosPage } from './pages/ProductosPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="callback" element={<CallbackPage />} />
        <Route path="dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
        <Route path="productos" element={<ProtectedRoute><ProductosPage /></ProtectedRoute>} />
        <Route path="pedidos" element={<ProtectedRoute><PedidosPage /></ProtectedRoute>} />
        <Route
          path="pedidos/nuevo"
          element={<ProtectedRoute roles={['CLIENTE', 'ADMIN']}><NuevoPedidoPage /></ProtectedRoute>}
        />
        <Route path="perfil" element={<ProtectedRoute><PerfilPage /></ProtectedRoute>} />
        <Route path="no-autorizado" element={<NoAutorizadoPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
