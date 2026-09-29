import { describe, expect, it } from 'vitest'
import { notify, type Notification } from './notify'

describe('notify', () => {
  it('delivers a notification emitted before anyone subscribed', () => {
    notify.ok('Pago aprobado')
    const received: Notification[] = []
    const unsubscribe = notify.subscribe((n) => received.push(n))
    expect(received).toEqual([{ kind: 'ok', text: 'Pago aprobado' }])
    notify.error('otro')
    expect(received).toHaveLength(2)
    unsubscribe()
  })
})
