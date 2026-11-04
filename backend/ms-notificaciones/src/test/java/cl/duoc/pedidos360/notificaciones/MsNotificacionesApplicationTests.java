package cl.duoc.pedidos360.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cl.duoc.pedidos360.notificaciones.comprobante.ComprobanteRepository;
import cl.duoc.pedidos360.notificaciones.comprobante.ComprobanteService;
import cl.duoc.pedidos360.notificaciones.evento.EventoPedido;
import cl.duoc.pedidos360.notificaciones.evento.TipoEvento;
import cl.duoc.pedidos360.notificaciones.notificacion.NotificacionRepository;
import cl.duoc.pedidos360.notificaciones.notificacion.NotificacionService;

/** Contexto completo con H2 y sin broker (los listeners no arrancan en las pruebas). */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class MsNotificacionesApplicationTests {

    @Autowired
    Queue colaNotificaciones;
    @Autowired
    Queue colaComprobantes;
    @Autowired
    Queue colaAuditoria;
    @Autowired
    Declarables bindingsNotificaciones;
    @Autowired
    Binding bindingComprobantes;
    @Autowired
    NotificacionService notificaciones;
    @Autowired
    NotificacionRepository notificacionRepository;
    @Autowired
    ComprobanteService comprobantes;
    @Autowired
    ComprobanteRepository comprobanteRepository;

    @Test
    void cadaColaEsQuorumYTieneSuDlq() {
        for (Queue cola : new Queue[] { colaNotificaciones, colaComprobantes, colaAuditoria }) {
            assertThat(cola.getArguments())
                    .containsEntry("x-queue-type", "quorum")
                    .containsEntry("x-dead-letter-exchange", "pedidos.dlx")
                    .containsEntry("x-dead-letter-routing-key", cola.getName() + ".dlq");
        }
    }

    @Test
    void bindingsSegunConfiguracion() {
        assertThat(bindingsNotificaciones.getDeclarablesByType(Binding.class)).extracting(Binding::getRoutingKey)
                .containsExactly("pedido.#", "pago.#");
        assertThat(bindingComprobantes.getExchange()).isEqualTo("pedidos.comandos");
        assertThat(bindingComprobantes.getRoutingKey()).isEqualTo("comprobante.generar");
    }

    @Test
    void unEventoRepetidoGeneraUnaSolaNotificacionYUnSoloComprobante() {
        EventoPedido pago = new EventoPedido(UUID.randomUUID(), TipoEvento.PAGO_APROBADO, 99L, "sub-9", "cliente9",
                "CONFIRMADO", "PAGADO", "TARJETA", new BigDecimal("5000"), Instant.now());
        notificaciones.registrar(pago);
        notificaciones.registrar(pago);
        comprobantes.emitir(pago);
        comprobantes.emitir(pago);
        assertThat(notificacionRepository.findAll()).filteredOn(n -> n.getPedidoId() == 99L).hasSize(1);
        assertThat(comprobanteRepository.findAll()).filteredOn(c -> c.getPedidoId() == 99L).hasSize(1);
    }
}
