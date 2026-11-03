import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from "react";
import { isAxiosError } from "axios";
import { api } from "../../api/api";
import type {
  BindingInfo,
  ClusterInfo,
  ColaInfo,
  ExchangeInfo,
  RegistroAuditoria,
  TipoCola,
  TipoExchange,
} from "../../api/models";
import { fecha } from "../../utils/format";
import { notify } from "../../utils/notify";

/** Cada cuánto se refrescan los contadores de las colas. */
const REFRESCO_MS = 5000;
const SUFIJO_DLQ = ".dlq";

/** Mensajes de prueba para la demostración de las rutas, reintentos y DLQ. */
const PRUEBAS = {
  valido: {
    titulo: "Evento válido",
    detalle:
      "pedido.creado en el topic: llega a notificaciones y auditoría con ACK.",
    exchange: "pedidos.eventos",
    routingKey: "pedido.creado",
    headers: undefined as Record<string, string> | undefined,
  },
  invalido: {
    titulo: "Mensaje inválido",
    detalle:
      "JSON ilegible al exchange direct: NACK sin reencolar, directo a comprobantes.generar.dlq.",
    exchange: "pedidos.comandos",
    routingKey: "comprobante.generar",
    headers: undefined as Record<string, string> | undefined,
  },
  transitorio: {
    titulo: "Fallo transitorio",
    detalle:
      'Simula una caída: NACK con reencolado 3 veces y luego DLQ. Se recupera con "Reprocesar".',
    exchange: "pedidos.comandos",
    routingKey: "comprobante.generar",
    headers: { "x-simular-fallo": "bd-caida" } as
      Record<string, string> | undefined,
  },
};

function cuerpoEvento(tipo: string) {
  const id = 90000 + Math.floor(Math.random() * 9999);
  return JSON.stringify({
    eventoId: crypto.randomUUID(),
    tipo,
    pedidoId: id,
    clienteUsername: "demo",
    estado: "PENDIENTE",
    estadoPago: tipo === "PAGO_APROBADO" ? "PAGADO" : "PENDIENTE",
    metodoPago: "TARJETA",
    total: 9990,
    ocurridoEn: new Date().toISOString(),
  });
}

/** Errores de validación del backend (problem+json con el mapa "errores"). */
function erroresDe(e: unknown): string | null {
  if (!isAxiosError(e)) return null;
  const data = e.response?.data as
    { detail?: string; errores?: Record<string, string> } | undefined;
  if (data?.errores) {
    return Object.entries(data.errores)
      .map(([campo, motivo]) => `${campo}: ${motivo}`)
      .join(" · ");
  }
  return data?.detail ?? null;
}

export function AdminMensajeriaPage() {
  const [cluster, setCluster] = useState<ClusterInfo | null>(null);
  const [colas, setColas] = useState<ColaInfo[]>([]);
  const [exchanges, setExchanges] = useState<ExchangeInfo[]>([]);
  const [bindings, setBindings] = useState<BindingInfo[]>([]);
  const [auditoria, setAuditoria] = useState<RegistroAuditoria[]>([]);

  const cargar = useCallback(() => {
    api.mq.cluster().then(setCluster, () => undefined);
    api.mq.colas().then(setColas, () => undefined);
    api.mq.exchanges().then(setExchanges, () => undefined);
    api.mq.bindings().then(setBindings, () => undefined);
    api.auditoria().then(
      (a) => setAuditoria(a.slice(0, 15)),
      () => undefined,
    );
  }, []);

  useEffect(() => {
    cargar();
    const timer = window.setInterval(() => {
      api.mq.colas().then(setColas, () => undefined);
      api.auditoria().then(
        (a) => setAuditoria(a.slice(0, 15)),
        () => undefined,
      );
    }, REFRESCO_MS);
    return () => window.clearInterval(timer);
  }, [cargar]);

  const enDlq = useMemo(
    () =>
      colas
        .filter((c) => c.nombre.endsWith(SUFIJO_DLQ))
        .reduce((total, c) => total + c.mensajes, 0),
    [colas],
  );
  const nodosActivos = cluster?.nodos.filter((n) => n.activo).length ?? 0;

  // ---------- Formularios ----------
  const [cola, setCola] = useState({
    nombre: "",
    tipo: "QUORUM" as TipoCola,
    conDlq: true,
    ttlMs: "",
    limiteEntregas: "",
  });
  const [errorCola, setErrorCola] = useState<string | null>(null);
  const [exchange, setExchange] = useState({
    nombre: "",
    tipo: "TOPIC" as TipoExchange,
  });
  const [errorExchange, setErrorExchange] = useState<string | null>(null);
  const [binding, setBinding] = useState({
    exchange: "",
    cola: "",
    routingKey: "",
  });
  const [errorBinding, setErrorBinding] = useState<string | null>(null);

  const crearCola = async (event: FormEvent) => {
    event.preventDefault();
    setErrorCola(null);
    try {
      await api.mq.crearCola({
        nombre: cola.nombre.trim(),
        tipo: cola.tipo,
        conDlq: cola.conDlq,
        ttlMs: cola.ttlMs ? Number(cola.ttlMs) : null,
        limiteEntregas: cola.limiteEntregas
          ? Number(cola.limiteEntregas)
          : null,
      });
      notify.ok(
        `Cola ${cola.nombre} creada${cola.conDlq ? " con su DLQ" : ""}`,
      );
      setCola((c) => ({ ...c, nombre: "", ttlMs: "", limiteEntregas: "" }));
      cargar();
    } catch (e) {
      setErrorCola(erroresDe(e));
    }
  };

  const crearExchange = async (event: FormEvent) => {
    event.preventDefault();
    setErrorExchange(null);
    try {
      await api.mq.crearExchange(exchange.nombre.trim(), exchange.tipo);
      notify.ok(`Exchange ${exchange.nombre} creado`);
      setExchange((x) => ({ ...x, nombre: "" }));
      cargar();
    } catch (e) {
      setErrorExchange(erroresDe(e));
    }
  };

  const crearBinding = async (event: FormEvent) => {
    event.preventDefault();
    setErrorBinding(null);
    try {
      await api.mq.crearBinding(binding);
      notify.ok("Binding creado");
      setBinding((b) => ({ ...b, routingKey: "" }));
      cargar();
    } catch (e) {
      setErrorBinding(erroresDe(e));
    }
  };

  // ---------- Acciones ----------
  const accion = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn();
      notify.ok(ok);
      cargar();
    } catch {
      // El interceptor ya mostró el error (409 para lo protegido, por ejemplo).
    }
  };

  const eliminarCola = (c: ColaInfo) => {
    if (!window.confirm(`¿Eliminar la cola ${c.nombre}?`)) return;
    accion(
      () => api.mq.eliminarCola(c.nombre, c.mensajes === 0),
      `Cola ${c.nombre} eliminada`,
    );
  };
  const purgar = (c: ColaInfo) => {
    if (
      !window.confirm(`¿Descartar los ${c.mensajes} mensajes de ${c.nombre}?`)
    )
      return;
    accion(() => api.mq.purgarCola(c.nombre), `Cola ${c.nombre} vaciada`);
  };
  const reprocesar = async (c: ColaInfo) => {
    try {
      const r = await api.mq.reprocesarDlq(c.nombre);
      notify.ok(`${r.reprocesados} mensajes devueltos a su cola de origen`);
      cargar();
    } catch {
      // El interceptor ya mostró el error.
    }
  };
  const probar = async (clave: keyof typeof PRUEBAS) => {
    const p = PRUEBAS[clave];
    const cuerpo =
      clave === "invalido"
        ? "esto no es JSON"
        : cuerpoEvento(clave === "valido" ? "PEDIDO_CREADO" : "PAGO_APROBADO");
    try {
      await api.mq.publicar({
        exchange: p.exchange,
        routingKey: p.routingKey,
        cuerpo,
        headers: p.headers,
      });
      notify.ok(`${p.titulo}: publicado en ${p.exchange} [${p.routingKey}]`);
      window.setTimeout(cargar, 1500);
    } catch {
      // El interceptor ya mostró el error.
    }
  };

  return (
    <>
      <div className="admin-head">
        <h1 className="d">Mensajería</h1>
        <span className="muted">
          RabbitMQ · se actualiza cada {REFRESCO_MS / 1000} s
        </span>
      </div>

      <section className="kpis">
        <div className="card kpi">
          <span className="muted">Clúster</span>
          <strong>{cluster?.nombre ?? "—"}</strong>
          <span className="faint">RabbitMQ {cluster?.version}</span>
        </div>
        <div className="card kpi">
          <span className="muted">Nodos activos</span>
          <strong className="accent">
            {cluster ? `${nodosActivos} / ${cluster.nodos.length}` : "—"}
          </strong>
          <span className="faint">
            {cluster?.nodos
              .map((n) => n.nombre.replace("rabbit@", ""))
              .join(" · ")}
          </span>
        </div>
        <div className="card kpi">
          <span className="muted">Colas</span>
          <strong>{colas.length}</strong>
          <span className="faint">
            {colas.filter((c) => c.nombre.endsWith(SUFIJO_DLQ)).length} son DLQ
          </span>
        </div>
        <div className="card kpi">
          <span className="muted">Mensajes en DLQ</span>
          <strong className={enDlq > 0 ? "accent" : undefined}>{enDlq}</strong>
          <span className="faint">rechazados por los consumidores</span>
        </div>
      </section>

      <section
        className="card card-pad"
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 12,
          marginBottom: 24,
        }}
      >
        <h2 className="d" style={{ fontSize: 22 }}>
          Probar las rutas de mensajería
        </h2>
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
            gap: 12,
          }}
        >
          {(Object.keys(PRUEBAS) as (keyof typeof PRUEBAS)[]).map((k) => (
            <div
              key={k}
              className="card card-pad"
              style={{ display: "flex", flexDirection: "column", gap: 8 }}
            >
              <strong>{PRUEBAS[k].titulo}</strong>
              <span className="muted" style={{ fontSize: 13, flex: 1 }}>
                {PRUEBAS[k].detalle}
              </span>
              <button
                type="button"
                className="btn btn-sm"
                onClick={() => probar(k)}
              >
                Publicar
              </button>
            </div>
          ))}
        </div>
      </section>

      <h2 className="d" style={{ fontSize: 22, margin: "8px 0 12px" }}>
        Colas
      </h2>
      <div className="table-wrap" style={{ marginBottom: 16 }}>
        <table className="table">
          <thead>
            <tr>
              <th>Cola</th>
              <th>Tipo</th>
              <th>Nodo líder</th>
              <th>Listos</th>
              <th>Sin confirmar</th>
              <th>Consumidores</th>
              <th>DLQ</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {colas.map((c) => {
              const esDlq = c.nombre.endsWith(SUFIJO_DLQ);
              const dlq = c.argumentos?.["x-dead-letter-routing-key"];
              return (
                <tr key={c.nombre}>
                  <td className="m" style={{ fontSize: 13 }}>
                    {c.nombre}
                  </td>
                  <td>
                    <span className="badge">{c.tipo}</span>
                  </td>
                  <td className="muted" style={{ fontSize: 13 }}>
                    {c.nodo.replace("rabbit@", "")}
                  </td>
                  <td>
                    <strong
                      className={esDlq && c.listos > 0 ? "accent" : undefined}
                    >
                      {c.listos}
                    </strong>
                  </td>
                  <td>{c.sinConfirmar}</td>
                  <td>{c.consumidores}</td>
                  <td className="faint" style={{ fontSize: 12 }}>
                    {typeof dlq === "string" ? dlq : esDlq ? "(es DLQ)" : "—"}
                  </td>
                  <td>
                    <div
                      style={{
                        display: "flex",
                        gap: 6,
                        flexWrap: "wrap",
                        justifyContent: "flex-end",
                      }}
                    >
                      {esDlq && c.listos > 0 && (
                        <button
                          type="button"
                          className="btn btn-sm btn-ghost"
                          onClick={() => reprocesar(c)}
                        >
                          Reprocesar
                        </button>
                      )}
                      {c.mensajes > 0 && (
                        <button
                          type="button"
                          className="btn btn-sm btn-ghost"
                          onClick={() => purgar(c)}
                        >
                          Purgar
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn btn-sm btn-danger"
                        onClick={() => eliminarCola(c)}
                      >
                        Eliminar
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <form
        className="card card-pad"
        onSubmit={crearCola}
        noValidate
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 12,
          marginBottom: 24,
        }}
      >
        <h3 className="d" style={{ fontSize: 18 }}>
          Nueva cola
        </h3>
        <div className="form-grid">
          <label className="field">
            Nombre
            <input
              className="input"
              value={cola.nombre}
              placeholder="pruebas.demo"
              onChange={(e) => setCola({ ...cola, nombre: e.target.value })}
            />
          </label>
          <label className="field">
            Tipo
            <select
              className="select"
              value={cola.tipo}
              onChange={(e) =>
                setCola({ ...cola, tipo: e.target.value as TipoCola })
              }
            >
              <option value="QUORUM">Quorum (replicada)</option>
              <option value="CLASSIC">Clásica</option>
            </select>
          </label>
          <label className="field">
            TTL de mensajes (ms, opcional)
            <input
              className="input"
              inputMode="numeric"
              value={cola.ttlMs}
              onChange={(e) => setCola({ ...cola, ttlMs: e.target.value })}
            />
          </label>
          <label className="field">
            Límite de entregas (solo quorum)
            <input
              className="input"
              inputMode="numeric"
              value={cola.limiteEntregas}
              onChange={(e) =>
                setCola({ ...cola, limiteEntregas: e.target.value })
              }
            />
          </label>
        </div>
        <label style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <input
            type="checkbox"
            checked={cola.conDlq}
            onChange={(e) => setCola({ ...cola, conDlq: e.target.checked })}
          />
          Crear también su DLQ ({cola.nombre || "nombre"}
          {SUFIJO_DLQ})
        </label>
        {errorCola && <p className="status-err">{errorCola}</p>}
        <div>
          <button type="submit" className="btn">
            Crear cola
          </button>
        </div>
      </form>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))",
          gap: 24,
          marginBottom: 24,
        }}
      >
        <section style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <h2 className="d" style={{ fontSize: 22 }}>
            Exchanges
          </h2>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Exchange</th>
                  <th>Tipo</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {exchanges
                  .filter((x) => !x.nombre.startsWith("amq."))
                  .map((x) => (
                    <tr key={x.nombre}>
                      <td className="m" style={{ fontSize: 13 }}>
                        {x.nombre}
                      </td>
                      <td>
                        <span className="badge">{x.tipo}</span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-sm btn-danger"
                          onClick={() =>
                            window.confirm(
                              `¿Eliminar el exchange ${x.nombre}?`,
                            ) &&
                            accion(
                              () => api.mq.eliminarExchange(x.nombre),
                              `Exchange ${x.nombre} eliminado`,
                            )
                          }
                        >
                          Eliminar
                        </button>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
          <form
            className="card card-pad"
            onSubmit={crearExchange}
            noValidate
            style={{ display: "flex", flexDirection: "column", gap: 12 }}
          >
            <div className="form-grid">
              <label className="field">
                Nombre
                <input
                  className="input"
                  value={exchange.nombre}
                  placeholder="pruebas.topic"
                  onChange={(e) =>
                    setExchange({ ...exchange, nombre: e.target.value })
                  }
                />
              </label>
              <label className="field">
                Tipo
                <select
                  className="select"
                  value={exchange.tipo}
                  onChange={(e) =>
                    setExchange({
                      ...exchange,
                      tipo: e.target.value as TipoExchange,
                    })
                  }
                >
                  {["DIRECT", "TOPIC", "FANOUT", "HEADERS"].map((t) => (
                    <option key={t} value={t}>
                      {t.toLowerCase()}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            {errorExchange && <p className="status-err">{errorExchange}</p>}
            <div>
              <button type="submit" className="btn">
                Crear exchange
              </button>
            </div>
          </form>
        </section>

        <section style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <h2 className="d" style={{ fontSize: 22 }}>
            Bindings
          </h2>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Exchange</th>
                  <th>Routing key</th>
                  <th>Cola</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {bindings.map((b) => (
                  <tr key={`${b.exchange}|${b.routingKey}|${b.destino}`}>
                    <td className="m" style={{ fontSize: 12 }}>
                      {b.exchange}
                    </td>
                    <td className="m accent" style={{ fontSize: 12 }}>
                      {b.routingKey || "(vacía)"}
                    </td>
                    <td className="m" style={{ fontSize: 12 }}>
                      {b.destino}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="btn btn-sm btn-ghost"
                        aria-label="Eliminar binding"
                        onClick={() =>
                          accion(
                            () =>
                              api.mq.eliminarBinding({
                                exchange: b.exchange,
                                cola: b.destino,
                                routingKey: b.routingKey,
                              }),
                            "Binding eliminado",
                          )
                        }
                      >
                        ✕
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <form
            className="card card-pad"
            onSubmit={crearBinding}
            noValidate
            style={{ display: "flex", flexDirection: "column", gap: 12 }}
          >
            <div className="form-grid">
              <label className="field">
                Exchange
                <select
                  className="select"
                  value={binding.exchange}
                  onChange={(e) =>
                    setBinding({ ...binding, exchange: e.target.value })
                  }
                >
                  <option value="">Elegir…</option>
                  {exchanges
                    .filter((x) => !x.nombre.startsWith("amq."))
                    .map((x) => (
                      <option key={x.nombre}>{x.nombre}</option>
                    ))}
                </select>
              </label>
              <label className="field">
                Cola
                <select
                  className="select"
                  value={binding.cola}
                  onChange={(e) =>
                    setBinding({ ...binding, cola: e.target.value })
                  }
                >
                  <option value="">Elegir…</option>
                  {colas.map((c) => (
                    <option key={c.nombre}>{c.nombre}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                Routing key
                <input
                  className="input"
                  value={binding.routingKey}
                  placeholder="pedido.estado.*"
                  onChange={(e) =>
                    setBinding({ ...binding, routingKey: e.target.value })
                  }
                />
              </label>
            </div>
            {errorBinding && <p className="status-err">{errorBinding}</p>}
            <div>
              <button type="submit" className="btn">
                Crear binding
              </button>
            </div>
          </form>
        </section>
      </div>

      <h2 className="d" style={{ fontSize: 22, margin: "8px 0 12px" }}>
        Últimos eventos auditados
      </h2>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Recibido</th>
              <th>Routing key</th>
              <th>Pedido</th>
              <th>Estado</th>
              <th>Pago</th>
              <th>Cliente</th>
            </tr>
          </thead>
          <tbody>
            {auditoria.map((a) => (
              <tr key={a.id}>
                <td
                  className="muted"
                  style={{ fontSize: 13, whiteSpace: "nowrap" }}
                >
                  {fecha(a.recibidoEn)}
                </td>
                <td className="m accent" style={{ fontSize: 12 }}>
                  {a.routingKey}
                </td>
                <td className="m" style={{ fontSize: 13 }}>
                  #P360-{String(a.pedidoId).padStart(5, "0")}
                </td>
                <td>{a.estado}</td>
                <td>{a.estadoPago}</td>
                <td className="muted">{a.clienteUsername}</td>
              </tr>
            ))}
            {auditoria.length === 0 && (
              <tr>
                <td colSpan={6} className="muted">
                  Sin eventos todavía.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
