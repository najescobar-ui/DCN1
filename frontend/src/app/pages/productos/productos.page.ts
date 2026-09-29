import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Producto } from '../../core/models';
import { NotificationService } from '../../core/notification.service';

@Component({
  selector: 'app-productos',
  imports: [CurrencyPipe, ReactiveFormsModule],
  templateUrl: './productos.page.html',
})
export class ProductosPage {
  private readonly api = inject(ApiService);
  private readonly notifications = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  protected readonly productos = signal<Producto[]>([]);
  protected readonly editandoId = signal<number | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    descripcion: ['', Validators.maxLength(500)],
    precio: [0, [Validators.required, Validators.min(1)]],
    stock: [0, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.api.productos().subscribe((data) => this.productos.set(data));
  }

  editar(producto: Producto): void {
    this.editandoId.set(producto.id);
    this.form.setValue({
      nombre: producto.nombre,
      descripcion: producto.descripcion ?? '',
      precio: producto.precio,
      stock: producto.stock,
    });
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
    this.form.reset();
  }

  guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const id = this.editandoId();
    const request = id === null ? this.api.crearProducto(this.form.getRawValue()) : this.api.actualizarProducto(id, this.form.getRawValue());
    request.subscribe(() => {
      this.notifications.ok(id === null ? 'Producto creado' : 'Producto actualizado');
      this.cancelarEdicion();
      this.cargar();
    });
  }

  eliminar(producto: Producto): void {
    if (!confirm(`Eliminar "${producto.nombre}"?`)) {
      return;
    }
    this.api.eliminarProducto(producto.id).subscribe(() => {
      this.notifications.ok('Producto eliminado');
      this.cargar();
    });
  }
}
