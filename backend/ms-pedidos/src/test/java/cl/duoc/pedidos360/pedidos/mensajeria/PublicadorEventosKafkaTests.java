package cl.duoc.pedidos360.pedidos.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import cl.duoc.pedidos360.pedidos.evento.ActividadPedido;
import cl.duoc.pedidos360.pedidos.evento.TipoEvento;
import cl.duoc.pedidos360.pedidos.model.ItemPedido;
import cl.duoc.pedidos360.pedidos.model.MetodoPago;
import cl.duoc.pedidos360.pedidos.model.Pedido;

/** Key, tópico y contenido del evento de streaming; un Kafka caído no propaga el error al pedido. */
class PublicadorEventosKafkaTests {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ActividadPedido> template = mock(KafkaTemplate.class);
    private final PublicadorEventosKafka publicador = new PublicadorEventosKafka(template,
            new StreamingProperties(true, "pedidos.actividad"));

    private static Pedido pedido() {
        Pedido p = new Pedido("sub-1", "cliente1", "Av. Siempre Viva 742", "+56912345678", -33.4, -70.6, MetodoPago.EFECTIVO);
        p.agregarItem(new ItemPedido(3L, "Sushi Platter", 2, new BigDecimal("14990")));
        return p;
    }

    @Test
    void elEventoEsUnaFotografiaCompletaDelPedidoConSusLineas() {
        ActividadPedido e = ActividadPedido.de(TipoEvento.PEDIDO_CREADO, pedido());
        assertThat(e.version()).isEqualTo(ActividadPedido.VERSION);
        assertThat(e.estado()).isEqualTo("PENDIENTE");
        assertThat(e.metodoPago()).isEqualTo("EFECTIVO");
        assertThat(e.total()).isEqualByComparingTo("29980");
        assertThat(e.items()).singleElement().satisfies(i -> {
            assertThat(i.nombre()).isEqualTo("Sushi Platter");
            assertThat(i.cantidad()).isEqualTo(2);
        });
        assertThat(e.origen()).isEqualTo("ms-pedidos");
    }

    @Test
    void publicaEnElTopicoConElPedidoComoKey() {
        ActividadPedido e = new ActividadPedido(java.util.UUID.randomUUID(), 1, TipoEvento.PAGO_APROBADO, 42L, "sub-1",
                "cliente1", "PENDIENTE", "PAGADO", "TARJETA", BigDecimal.TEN, java.util.List.of(), java.time.Instant.now(),
                "ms-pedidos");
        when(template.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        publicador.publicar(e);
        verify(template).send(eq("pedidos.actividad"), eq("42"), eq(e));
    }

    @Test
    void siKafkaNoEstaDisponibleElErrorNoSalePublicador() {
        ActividadPedido e = new ActividadPedido(java.util.UUID.randomUUID(), 1, TipoEvento.PEDIDO_CREADO, 43L, "s", "c",
                "PENDIENTE", "PENDIENTE", "EFECTIVO", BigDecimal.ONE, java.util.List.of(), java.time.Instant.now(), "ms-pedidos");
        when(template.send(anyString(), anyString(), any())).thenThrow(new KafkaException("sin metadata"));
        assertThatCode(() -> publicador.publicar(e)).doesNotThrowAnyException();
    }
}
