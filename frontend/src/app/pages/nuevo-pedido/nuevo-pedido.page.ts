import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Producto } from '../../core/models';
import { NotificationService } from '../../core/notification.service';

@Component({
  selector: 'app-nuevo-pedido',
  imports: [CurrencyPipe, FormsModule],
  templateUrl: './nuevo-pedido.page.html',
})
export class NuevoPedidoPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly productos = signal<Producto[]>([]);
  protected readonly cantidades = signal<Record<number, number>>({});
  protected readonly enviando = signal(false);
  protected direccion = '';

  /** Preview only: the backend recalculates prices from ms-productos. */
  protected readonly total = computed(() =>
    this.productos().reduce((sum, p) => sum + p.precio * (this.cantidades()[p.id] ?? 0), 0),
  );
  protected readonly items = computed(() =>
    Object.entries(this.cantidades())
      .filter(([, cantidad]) => cantidad > 0)
      .map(([productoId, cantidad]) => ({ productoId: Number(productoId), cantidad })),
  );

  constructor() {
    this.api.productos().subscribe((data) => this.productos.set(data));
  }

  cambiarCantidad(productoId: number, cantidad: number): void {
    this.cantidades.update((c) => ({ ...c, [productoId]: Math.max(0, Math.min(99, cantidad || 0)) }));
  }

  confirmar(): void {
    this.enviando.set(true);
    this.api.crearPedido({ items: this.items(), direccionEntrega: this.direccion || undefined }).subscribe({
      next: (pedido) => {
        this.notifications.ok(`Pedido #${pedido.id} creado`);
        this.router.navigateByUrl('/pedidos');
      },
      error: () => this.enviando.set(false),
    });
  }
}
