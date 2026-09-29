import { useEffect, useRef, useState } from 'react'
import { notify, type Notification } from '../utils/notify'

export function Toast() {
  const [toast, setToast] = useState<Notification | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined)

  useEffect(
    () =>
      notify.subscribe((notification) => {
        clearTimeout(timer.current)
        setToast(notification)
        timer.current = setTimeout(() => setToast(null), 5000)
      }),
    [],
  )

  if (!toast) {
    return null
  }
  return (
    <div className={`toast ${toast.kind === 'error' ? 'toast-error' : ''}`} role="status" onClick={() => setToast(null)}>
      {toast.text}
    </div>
  )
}
