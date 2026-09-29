import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ESTADOS, EstadoPedido, Pedido } from '../../core/models';
import { NotificationService } from '../../core/notification.service';

@Component({
  selector: 'app-pedidos',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './pedidos.page.html',
})
export class PedidosPage {
  private readonly api = inject(ApiService);
  private readonly notifications = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  protected readonly estados = ESTADOS;
  protected readonly pedidos = signal<Pedido[]>([]);

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.api.pedidos().subscribe((data) => this.pedidos.set(data));
  }

  cambiarEstado(pedido: Pedido, estado: string): void {
    this.api.cambiarEstado(pedido.id, estado as EstadoPedido).subscribe(() => {
      this.notifications.ok(`Pedido #${pedido.id} ahora esta ${estado}`);
      this.cargar();
    });
  }

  cancelar(pedido: Pedido): void {
    this.api.cancelarPedido(pedido.id).subscribe(() => {
      this.notifications.ok(`Pedido #${pedido.id} cancelado`);
      this.cargar();
    });
  }
}
