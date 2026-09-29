import { Injectable, signal } from '@angular/core';

export interface Notification {
  kind: 'ok' | 'error';
  text: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  readonly current = signal<Notification | null>(null);
  private timer?: ReturnType<typeof setTimeout>;

  ok(text: string): void {
    this.show({ kind: 'ok', text });
  }

  error(text: string): void {
    this.show({ kind: 'error', text });
  }

  clear(): void {
    this.current.set(null);
  }

  private show(notification: Notification): void {
    clearTimeout(this.timer);
    this.current.set(notification);
    this.timer = setTimeout(() => this.clear(), 5000);
  }
}
