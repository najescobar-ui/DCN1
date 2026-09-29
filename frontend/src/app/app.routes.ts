import { Routes } from '@angular/router';
import { autoLoginPartialRoutesGuard } from 'angular-auth-oidc-client';
import { roleGuard } from './core/role.guard';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/home/home.page').then((m) => m.HomePage) },
  { path: 'callback', loadComponent: () => import('./pages/callback/callback.page').then((m) => m.CallbackPage) },
  {
    path: 'dashboard',
    canActivate: [autoLoginPartialRoutesGuard],
    loadComponent: () => import('./pages/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
  {
    path: 'productos',
    canActivate: [autoLoginPartialRoutesGuard],
    loadComponent: () => import('./pages/productos/productos.page').then((m) => m.ProductosPage),
  },
  {
    path: 'pedidos',
    canActivate: [autoLoginPartialRoutesGuard],
    loadComponent: () => import('./pages/pedidos/pedidos.page').then((m) => m.PedidosPage),
  },
  {
    path: 'pedidos/nuevo',
    canActivate: [autoLoginPartialRoutesGuard, roleGuard],
    data: { roles: ['CLIENTE', 'ADMIN'] },
    loadComponent: () => import('./pages/nuevo-pedido/nuevo-pedido.page').then((m) => m.NuevoPedidoPage),
  },
  {
    path: 'perfil',
    canActivate: [autoLoginPartialRoutesGuard],
    loadComponent: () => import('./pages/perfil/perfil.page').then((m) => m.PerfilPage),
  },
  {
    path: 'no-autorizado',
    loadComponent: () => import('./pages/no-autorizado/no-autorizado.page').then((m) => m.NoAutorizadoPage),
  },
  { path: '**', redirectTo: '' },
];
