export interface Notification {
  kind: 'ok' | 'error'
  text: string
}

type Listener = (notification: Notification) => void
const listeners = new Set<Listener>()
// Kept until a listener exists: a page effect can fire before the layout's Toast subscribes.
let pending: Notification | null = null

function emit(notification: Notification) {
  if (listeners.size === 0) {
    pending = notification
    return
  }
  listeners.forEach((l) => l(notification))
}

/** Tiny event bus so non-React code (the axios interceptor) can show notifications. */
export const notify = {
  ok: (text: string) => emit({ kind: 'ok', text }),
  error: (text: string) => emit({ kind: 'error', text }),
  subscribe(listener: Listener) {
    listeners.add(listener)
    if (pending) {
      listener(pending)
      pending = null
    }
    return () => {
      listeners.delete(listener)
    }
  },
}
