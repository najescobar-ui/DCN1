import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  CrearPedidoRequest,
  EstadoPedido,
  Pedido,
  Producto,
  ProductoRequest,
  Resumen,
  Usuario,
} from './models';

/** All calls go to API Gateway; the auth interceptor attaches the Bearer access token. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api`;

  resumen() {
    return this.http.get<Resumen>(`${this.base}/bff/resumen`);
  }

  me() {
    return this.http.get<Usuario>(`${this.base}/bff/me`);
  }

  productos() {
    return this.http.get<Producto[]>(`${this.base}/productos`);
  }

  crearProducto(body: ProductoRequest) {
    return this.http.post<Producto>(`${this.base}/productos`, body);
  }

  actualizarProducto(id: number, body: ProductoRequest) {
    return this.http.put<Producto>(`${this.base}/productos/${id}`, body);
  }

  eliminarProducto(id: number) {
    return this.http.delete<void>(`${this.base}/productos/${id}`);
  }

  pedidos() {
    return this.http.get<Pedido[]>(`${this.base}/pedidos`);
  }

  crearPedido(body: CrearPedidoRequest) {
    return this.http.post<Pedido>(`${this.base}/pedidos`, body);
  }

  cambiarEstado(id: number, estado: EstadoPedido) {
    return this.http.patch<Pedido>(`${this.base}/pedidos/${id}/estado`, { estado });
  }

  cancelarPedido(id: number) {
    return this.http.post<Pedido>(`${this.base}/pedidos/${id}/cancelar`, {});
  }
}
