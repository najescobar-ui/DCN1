const clpFormatter = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 })
const dateFormatter = new Intl.DateTimeFormat('es-CL', { dateStyle: 'medium', timeStyle: 'short', hourCycle: 'h23' })

export const clp = (value: number | undefined | null) => clpFormatter.format(value ?? 0)

export const fecha = (value: string | number | Date | undefined | null) =>
  value === undefined || value === null ? '' : dateFormatter.format(new Date(value))
