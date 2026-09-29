export interface Notification {
  kind: 'ok' | 'error'
  text: string
}

type Listener = (notification: Notification) => void
const listeners = new Set<Listener>()

/** Tiny event bus so non-React code (the axios interceptor) can show notifications. */
export const notify = {
  ok: (text: string) => listeners.forEach((l) => l({ kind: 'ok', text })),
  error: (text: string) => listeners.forEach((l) => l({ kind: 'error', text })),
  subscribe(listener: Listener) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}
